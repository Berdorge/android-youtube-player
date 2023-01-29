package com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import com.pierfrancescosoffritti.androidyoutubeplayer.R
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayerBridge
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.FullscreenListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.YouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.toFloat
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils.Utils
import java.util.*

/**
 * WebView implementation of [YouTubePlayer]. The player runs inside the WebView, using the IFrame Player API.
 */
internal class WebViewYouTubePlayer constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : VideoEnabledWebView(context, attrs, defStyleAttr),
    YouTubePlayer,
    YouTubePlayerBridge.YouTubePlayerBridgeCallbacks {
    val fullscreenHelper by lazy {
        FullscreenHelper()
    }

    private lateinit var youTubePlayerInitListener: (YouTubePlayer) -> Unit

    private val youTubePlayerListeners = HashSet<YouTubePlayerListener>()
    private val mainThreadHandler: Handler = Handler(Looper.getMainLooper())

    internal var isBackgroundPlaybackEnabled = false

    internal fun initialize(
        initListener: (YouTubePlayer) -> Unit,
        playerOptions: IFramePlayerOptions?
    ) {
        youTubePlayerInitListener = initListener
        initWebView(playerOptions ?: IFramePlayerOptions.default)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        requestDisallowInterceptTouchEvent(true)
        return super.onTouchEvent(event)
    }

    override fun onYouTubeIFrameAPIReady() = youTubePlayerInitListener(this)

    override fun getInstance(): YouTubePlayer = this

    override fun loadVideo(videoId: String, startSeconds: Float) {
        mainThreadHandler.post { loadUrl("javascript:loadVideo('$videoId', $startSeconds)") }
    }

    override fun cueVideo(videoId: String, startSeconds: Float) {
        mainThreadHandler.post { loadUrl("javascript:cueVideo('$videoId', $startSeconds)") }
    }

    override fun play() {
        mainThreadHandler.post { loadUrl("javascript:playVideo()") }
    }

    override fun pause() {
        mainThreadHandler.post { loadUrl("javascript:pauseVideo()") }
    }

    override fun mute() {
        mainThreadHandler.post { loadUrl("javascript:mute()") }
    }

    override fun unMute() {
        mainThreadHandler.post { loadUrl("javascript:unMute()") }
    }

    override fun setVolume(volumePercent: Int) {
        require(!(volumePercent < 0 || volumePercent > 100)) { "Volume must be between 0 and 100" }

        mainThreadHandler.post { loadUrl("javascript:setVolume($volumePercent)") }
    }

    override fun seekTo(time: Float) {
        mainThreadHandler.post { loadUrl("javascript:seekTo($time)") }
    }

    override fun setPlaybackRate(playbackRate: PlayerConstants.PlaybackRate) {
        mainThreadHandler.post { loadUrl("javascript:setPlaybackRate(${playbackRate.toFloat()})") }
    }

    override fun destroy() {
        youTubePlayerListeners.clear()
        mainThreadHandler.removeCallbacksAndMessages(null)
        super.destroy()
    }

    override fun getListeners(): Collection<YouTubePlayerListener> {
        return Collections.unmodifiableCollection(HashSet(youTubePlayerListeners))
    }

    override fun addListener(listener: YouTubePlayerListener): Boolean {
        return youTubePlayerListeners.add(listener)
    }

    override fun removeListener(listener: YouTubePlayerListener): Boolean {
        return youTubePlayerListeners.remove(listener)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView(playerOptions: IFramePlayerOptions) {
        settings.javaScriptEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        addJavascriptInterface(YouTubePlayerBridge(this), "YouTubePlayerBridge")

        val htmlPage = Utils
            .readHTMLFromUTF8File(resources.openRawResource(R.raw.ayp_youtube_player))
            .replace("<<injectedPlayerVars>>", playerOptions.toString())

        loadDataWithBaseURL(playerOptions.getOrigin(), htmlPage, "text/html", "utf-8", null)

        val videoClient =
            object : VideoEnabledWebChromeClient(playerOptions.getFullscreenVideoView(), this) {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    println("asdf: new progress: $newProgress")
                }
            }
        webChromeClient = videoClient
        fullscreenHelper.init(videoClient)

        // if the video's thumbnail is not in memory, show a black screen
//        webChromeClient = object : WebChromeClient() {
//            override fun getDefaultVideoPoster(): Bitmap? {
//                val result = super.getDefaultVideoPoster()
//
//                return result ?: Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)
//            }
//        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (isBackgroundPlaybackEnabled && (visibility == View.GONE || visibility == View.INVISIBLE))
            return

        super.onWindowVisibilityChanged(visibility)
    }

    inner class FullscreenHelper {
        var isFullScreen: Boolean = false
            private set

        private var videoClient: VideoEnabledWebChromeClient? = null

        private val fullscreenListeners = mutableSetOf<FullscreenListener>()

        fun exitFullScreen() {
            println("asdf: pressing back")
            videoClient?.onBackPressed()
        }

        fun addFullScreenListener(fullScreenListener: FullscreenListener): Boolean {
            return fullscreenListeners.add(fullScreenListener)
        }

        fun removeFullScreenListener(fullScreenListener: FullscreenListener): Boolean {
            return fullscreenListeners.remove(fullScreenListener)
        }

        internal fun init(videoClient: VideoEnabledWebChromeClient) {
            this.videoClient = videoClient
            videoClient.setOnToggledFullscreen { fullscreen ->
                println("asdf: fullscreen: $fullscreen")
                isFullScreen = fullscreen
                for (listener in fullscreenListeners) {
                    listener.onYouTubePlayerFullscreenToggled(fullscreen)
                }
            }
        }
    }
}
