package com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views

import android.graphics.Bitmap
import android.view.ViewGroup

class YouTubePlayerWebChromeClient(
    activityVideoView: ViewGroup?,
    webView: VideoEnabledWebView
) : VideoEnabledWebChromeClient(activityVideoView, webView) {
    override fun getDefaultVideoPoster(): Bitmap? {
        val result = super.getDefaultVideoPoster()

        return result ?: Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)
    }
}
