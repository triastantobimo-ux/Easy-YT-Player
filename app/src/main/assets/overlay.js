(function () {
  var B = window.AndroidBridge;
  if (!B) { return; }
  if (window.__eyp) { window.__eyp.ensure(); return; }

  // Create floating PiP button
  var btn = document.createElement('div');
  btn.id = 'eyp-pip';
  btn.textContent = 'PiP';
  btn.setAttribute('style',
    'position:fixed;right:12px;bottom:72px;z-index:2147483647;' +
    'width:44px;height:44px;border-radius:22px;' +
    'background:rgba(0,0,0,0.6);color:#fff;font-size:12px;font-weight:700;' +
    'font-family:Roboto,Arial,sans-serif;display:flex;align-items:center;' +
    'justify-content:center;user-select:none;-webkit-user-select:none;' +
    'cursor:pointer;touch-action:none;box-shadow:0 2px 8px rgba(0,0,0,0.4);' +
    'text-decoration:none;pointer-events:auto;');

  var moved = false;
  var startY = 0;
  var lastTouch = 0;

  btn.addEventListener('touchstart', function (e) {
    startY = e.touches[0].clientY;
    moved = false;
  }, { passive: true });

  btn.addEventListener('touchmove', function (e) {
    var y = e.touches[0].clientY;
    if (Math.abs(y - startY) > 10) { moved = true; }
    var top = Math.min(Math.max(y - 22, 8), window.innerHeight - 52);
    btn.style.top = top + 'px';
    btn.style.bottom = 'auto';
  }, { passive: true });

  btn.addEventListener('touchend', function () {
    lastTouch = Date.now();
    if (!moved) { B.enterPip(); }
  });

  btn.addEventListener('click', function () {
    if (Date.now() - lastTouch < 500) { return; }
    if (!moved) { B.enterPip(); }
  });

  function ensure() {
    if (!document.body) { return; }
    if (!document.getElementById('eyp-pip')) {
      document.body.appendChild(btn);
    }
  }

  function setVisible(v) {
    btn.style.display = v ? 'flex' : 'none';
  }

  function report(state) {
    try { B.notifyPlaying(state); } catch (e) { /* bridge gone */ }
  }

  // Hook into YouTube player events
  // Desktop YouTube uses HTML5 video element inside shadow DOM or iframe
  // We need to observe the page for video elements appearing
  var observer = new MutationObserver(function(mutations) {
    mutations.forEach(function(mutation) {
      mutation.addedNodes.forEach(function(node) {
        if (node.tagName === 'VIDEO') {
          setupVideoListeners(node);
        } else if (node.querySelectorAll) {
          var videos = node.querySelectorAll('video');
          videos.forEach(setupVideoListeners);
        }
      });
    });
  });

  function setupVideoListeners(video) {
    if (video.__eypHooked) return;
    video.__eypHooked = true;
    
    video.addEventListener('play', function() { report(true); });
    video.addEventListener('pause', function() { report(false); });
    video.addEventListener('ended', function() { report(false); });
    
    // Also check current state
    if (!video.paused) { report(true); }
  }

  // Start observing
  observer.observe(document.body || document.documentElement, {
    childList: true,
    subtree: true
  });

  // Also periodically check for videos (fallback)
  setInterval(function() {
    var videos = document.querySelectorAll('video');
    videos.forEach(function(v) {
      if (!v.__eypHooked) {
        setupVideoListeners(v);
      }
    });
    // Check if any video is playing
    var playing = Array.from(videos).some(function(v) { return !v.paused; });
    if (playing !== window.__eypLastState) {
      window.__eypLastState = playing;
      report(playing);
    }
  }, 2000);

  setInterval(ensure, 1500);
  ensure();

  window.__eyp = { ensure: ensure, setVisible: setVisible };
})();
