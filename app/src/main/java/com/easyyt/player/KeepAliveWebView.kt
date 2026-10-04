package com.easyyt.player

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.webkit.WebView

/**
 * Custom WebView that prevents media playback from pausing when the window becomes invisible.
 * This is critical for background playback support.
 */
class KeepAliveWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs) {

    override fun onWindowVisibilityChanged(visibility: Int) {
        // CRITICAL: Do NOT call super.onWindowVisibilityChanged()
        // This prevents Chromium from pausing media when window becomes invisible
        // Instead, just make sure the view is still visible to the system
        if (visibility != View.GONE) {
            super.onWindowVisibilityChanged(visibility)
        }
        // If visibility is GONE, we still don't call super to keep media playing
    }
}
