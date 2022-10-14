package com.example.live_streaming

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.*
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.live_streaming.Constants.DEFAULT_DELAY_TIME
import com.example.live_streaming.Constants.METHOD_CHANNEL
import com.otaliastudios.zoom.ZoomLayout
import io.agora.rtc.Constants
import io.agora.rtc.IRtcEngineEventHandler
import io.agora.rtc.RtcEngine
import io.agora.rtc.models.ChannelMediaOptions
import io.agora.rtc.video.VideoCanvas
import io.agora.rtc.video.VideoCanvas.RENDER_MODE_HIDDEN
import io.agora.rtc.video.VideoEncoderConfiguration
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView
import java.util.*


enum class OrientationMode(val value: Int) {
    PORTRAIT(0),
    LANDSCAPE(1)
}

interface ILiveEventHandler {
    fun onCloseStream()

    /**
     * 0 - PORTRAIT
     * 1 - LANDSCAPE
     */
    fun onOrientationChanged(orientation: Int)
}

class VideoLiveStreamingController(
    private val id: Int,
    private val appId: String,
    private val context: Context,
    private val binaryMessenger: BinaryMessenger,
    private val iLiveEventHandler: ILiveEventHandler? = null,
    private val lifecycleProvider: LifecycleProvider
) : DefaultLifecycleObserver, MethodChannel.MethodCallHandler, PlatformView, View.OnClickListener {

    companion object {
        private val TAG = VideoLiveStreamingController::class.java.simpleName
    }

    private val methodChannelStream by lazy { "${METHOD_CHANNEL}_$id" }

    private val methodChannel: MethodChannel = MethodChannel(binaryMessenger, methodChannelStream)

    private val lifeCycleHashcode: Int
    private val constraintLayout: ConstraintLayout

    private var frameContainer: FrameLayout? = null
    private var localVideoView: FrameLayout? = null
    private var surfaceView: SurfaceView? = null
    private var imgClose: ImageView? = null
    private var imgVolume: ImageView? = null
    private var imgOrientationScreen: ImageView? = null
    private var liveController: ConstraintLayout? = null

    private var rtcEngine: RtcEngine? = null
    private var handler: Handler? = null

    private var uid: Int = 0

    private var timer: Timer? = null

    private var disposed: Boolean = false

    private var orientationMode: Int = OrientationMode.PORTRAIT.value
    var scaleFactor = 1f

    private var isMuted: Boolean = false
    private var isControllerShowing = false

    private val gestureDetector: GestureDetector by lazy {
        GestureDetector(context, SingleTapConfirm())
    }

    /**
     * IRtcEngineEventHandler is an abstract class providing default implementation.
     * The SDK uses this class to report to the app on SDK runtime events.
     */
    private val iRtcEngineEventHandler = object : IRtcEngineEventHandler() {
        /**Reports a warning during SDK runtime.
         * Warning code: https://docs.agora.io/en/Voice/API%20Reference/java/classio_1_1agora_1_1rtc_1_1_i_rtc_engine_event_handler_1_1_warn_code.html*/
        override fun onWarning(warn: Int) {
            super.onWarning(warn)
            Log.d(
                TAG,
                String.format(
                    "#onWarning code %d message %s",
                    warn,
                    RtcEngine.getErrorDescription(warn)
                )
            )
        }

        /**Reports an error during SDK runtime.
         * Error code: https://docs.agora.io/en/Voice/API%20Reference/java/classio_1_1agora_1_1rtc_1_1_i_rtc_engine_event_handler_1_1_error_code.html*/
        override fun onError(err: Int) {
            super.onError(err)
            Log.e(
                TAG,
                String.format(
                    "#onError code %d message %s",
                    err,
                    RtcEngine.getErrorDescription(err)
                )
            )
        }

        /**Occurs when a user leaves the channel.
         * @param stats With this callback, the application retrieves the channel information,
         *              such as the call duration and statistics.*/
        override fun onLeaveChannel(stats: RtcStats?) {
            super.onLeaveChannel(stats)
            Log.i(TAG, String.format("#onLeaveChannel: local user %d leaveChannel!", uid))
        }

        /**Occurs when the local user joins a specified channel.
         * The channel name assignment is based on channelName specified in the joinChannel method.
         * If the uid is not specified when joinChannel is called, the server automatically assigns a uid.
         * @param channel Channel name
         * @param uid User ID
         * @param elapsed Time elapsed (ms) from the user calling joinChannel until this callback is triggered*/
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            super.onJoinChannelSuccess(channel, uid, elapsed)
            Log.i(TAG, String.format("#onJoinChannelSuccess channel %s uid %d", channel, uid))
            this@VideoLiveStreamingController.uid = uid
        }

        override fun onFirstLocalVideoFramePublished(elapsed: Int) {
            super.onFirstLocalVideoFramePublished(elapsed)
            Log.d(TAG, "#onFirstLocalVideoFramePublished")
        }

        /**Since v2.9.0.
         * This callback indicates the state change of the remote audio stream.
         * PS: This callback does not work properly when the number of users (in the Communication profile) or
         *     broadcasters (in the Live-broadcast profile) in the channel exceeds 17.
         * @param uid ID of the user whose audio state changes.
         * @param state State of the remote audio
         *   REMOTE_AUDIO_STATE_STOPPED(0): The remote audio is in the default state, probably due
         *              to REMOTE_AUDIO_REASON_LOCAL_MUTED(3), REMOTE_AUDIO_REASON_REMOTE_MUTED(5),
         *              or REMOTE_AUDIO_REASON_REMOTE_OFFLINE(7).
         *   REMOTE_AUDIO_STATE_STARTING(1): The first remote audio packet is received.
         *   REMOTE_AUDIO_STATE_DECODING(2): The remote audio stream is decoded and plays normally,
         *              probably due to REMOTE_AUDIO_REASON_NETWORK_RECOVERY(2),
         *              REMOTE_AUDIO_REASON_LOCAL_UNMUTED(4) or REMOTE_AUDIO_REASON_REMOTE_UNMUTED(6).
         *   REMOTE_AUDIO_STATE_FROZEN(3): The remote audio is frozen, probably due to
         *              REMOTE_AUDIO_REASON_NETWORK_CONGESTION(1).
         *   REMOTE_AUDIO_STATE_FAILED(4): The remote audio fails to start, probably due to
         *              REMOTE_AUDIO_REASON_INTERNAL(0).
         * @param reason The reason of the remote audio state change.
         *   REMOTE_AUDIO_REASON_INTERNAL(0): Internal reasons.
         *   REMOTE_AUDIO_REASON_NETWORK_CONGESTION(1): Network congestion.
         *   REMOTE_AUDIO_REASON_NETWORK_RECOVERY(2): Network recovery.
         *   REMOTE_AUDIO_REASON_LOCAL_MUTED(3): The local user stops receiving the remote audio
         *               stream or disables the audio module.
         *   REMOTE_AUDIO_REASON_LOCAL_UNMUTED(4): The local user resumes receiving the remote audio
         *              stream or enables the audio module.
         *   REMOTE_AUDIO_REASON_REMOTE_MUTED(5): The remote user stops sending the audio stream or
         *               disables the audio module.
         *   REMOTE_AUDIO_REASON_REMOTE_UNMUTED(6): The remote user resumes sending the audio stream
         *              or enables the audio module.
         *   REMOTE_AUDIO_REASON_REMOTE_OFFLINE(7): The remote user leaves the channel.
         * @param elapsed Time elapsed (ms) from the local user calling the joinChannel method
         *                  until the SDK triggers this callback.*/
        override fun onRemoteAudioStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
            super.onRemoteAudioStateChanged(uid, state, reason, elapsed)
            Log.i(TAG, "#onRemoteAudioStateChanged->$uid, state->$state, reason->$reason")
        }

        /**Since v2.9.0.
         * Occurs when the remote video state changes.
         * PS: This callback does not work properly when the number of users (in the Communication
         * profile) or broadcasters (in the Live-broadcast profile) in the channel exceeds 17.
         * @param uid ID of the remote user whose video state changes.
         * @param state State of the remote video:
         * REMOTE_VIDEO_STATE_STOPPED(0): The remote video is in the default state, probably due
         * to REMOTE_VIDEO_STATE_REASON_LOCAL_MUTED(3), REMOTE_VIDEO_STATE_REASON_REMOTE_MUTED(5),
         * or REMOTE_VIDEO_STATE_REASON_REMOTE_OFFLINE(7).
         * REMOTE_VIDEO_STATE_STARTING(1): The first remote video packet is received.
         * REMOTE_VIDEO_STATE_DECODING(2): The remote video stream is decoded and plays normally,
         * probably due to REMOTE_VIDEO_STATE_REASON_NETWORK_RECOVERY (2),
         * REMOTE_VIDEO_STATE_REASON_LOCAL_UNMUTED(4), REMOTE_VIDEO_STATE_REASON_REMOTE_UNMUTED(6),
         * or REMOTE_VIDEO_STATE_REASON_AUDIO_FALLBACK_RECOVERY(9).
         * REMOTE_VIDEO_STATE_FROZEN(3): The remote video is frozen, probably due to
         * REMOTE_VIDEO_STATE_REASON_NETWORK_CONGESTION(1) or REMOTE_VIDEO_STATE_REASON_AUDIO_FALLBACK(8).
         * REMOTE_VIDEO_STATE_FAILED(4): The remote video fails to start, probably due to
         * REMOTE_VIDEO_STATE_REASON_INTERNAL(0).
         * @param reason The reason of the remote video state change:
         * REMOTE_VIDEO_STATE_REASON_INTERNAL(0): Internal reasons.
         * REMOTE_VIDEO_STATE_REASON_NETWORK_CONGESTION(1): Network congestion.
         * REMOTE_VIDEO_STATE_REASON_NETWORK_RECOVERY(2): Network recovery.
         * REMOTE_VIDEO_STATE_REASON_LOCAL_MUTED(3): The local user stops receiving the remote
         * video stream or disables the video module.
         * REMOTE_VIDEO_STATE_REASON_LOCAL_UNMUTED(4): The local user resumes receiving the remote
         * video stream or enables the video module.
         * REMOTE_VIDEO_STATE_REASON_REMOTE_MUTED(5): The remote user stops sending the video
         * stream or disables the video module.
         * REMOTE_VIDEO_STATE_REASON_REMOTE_UNMUTED(6): The remote user resumes sending the video
         * stream or enables the video module.
         * REMOTE_VIDEO_STATE_REASON_REMOTE_OFFLINE(7): The remote user leaves the channel.
         * REMOTE_VIDEO_STATE_REASON_AUDIO_FALLBACK(8): The remote media stream falls back to the
         * audio-only stream due to poor network conditions.
         * REMOTE_VIDEO_STATE_REASON_AUDIO_FALLBACK_RECOVERY(9): The remote media stream switches
         * back to the video stream after the network conditions improve.
         * @param elapsed Time elapsed (ms) from the local user calling the joinChannel method until
         * the SDK triggers this callback.
         */
        override fun onRemoteVideoStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
            super.onRemoteVideoStateChanged(uid, state, reason, elapsed)
            Log.i(TAG, "#onRemoteVideoStateChanged->$uid, state->$state, reason->$reason")
        }

        /**Occurs when a remote user (Communication)/host (Live Broadcast) joins the channel.
         * @param uid ID of the user whose audio state changes.
         * @param elapsed Time delay (ms) from the local user calling joinChannel/setClientRole
         *                until this callback is triggered.*/
        @SuppressLint("ClickableViewAccessibility")
        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun onUserJoined(uid: Int, elapsed: Int) {
            super.onUserJoined(uid, elapsed)
            Log.i(TAG, "#onUserJoined->$uid")
            handler?.post {

                /**Display remote video stream*/
                frameContainer?.removeAllViews()

                surfaceView = RtcEngine.CreateRendererView(context)

                surfaceView?.holder?.addCallback(surfaceViewCallBack)

                // Add to the local video view
                localVideoView = FrameLayout(context)

                localVideoView?.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )

                frameContainer?.addView(localVideoView)

                localVideoView?.setOnTouchListener { _, motionEvent ->
//                    if (gestureDetector.onTouchEvent(motionEvent)) true
                    scaleGestureDetector.onTouchEvent(motionEvent)
                    gestureDetector.onTouchEvent(motionEvent)
                    true
                }

                setLayoutParamRemoteVideo(surfaceView, orientationMode)

                // Add to the remote video view
                frameContainer?.addView(surfaceView)

                // Setup remote video to render
                rtcEngine?.setupRemoteVideo(VideoCanvas(surfaceView, RENDER_MODE_HIDDEN, uid))
            }
        }

        /**Occurs when a remote user (Communication)/host (Live Broadcast) leaves the channel.
         * @param uid ID of the user whose audio state changes.
         * @param reason Reason why the user goes offline:
         *   USER_OFFLINE_QUIT(0): The user left the current channel.
         *   USER_OFFLINE_DROPPED(1): The SDK timed out and the user dropped offline because no data
         *              packet was received within a certain period of time. If a user quits the
         *               call and the message is not passed to the SDK (due to an unreliable channel),
         *               the SDK assumes the user dropped offline.
         *   USER_OFFLINE_BECOME_AUDIENCE(2): (Live broadcast only.) The client role switched from
         *               the host to the audience.*/
        override fun onUserOffline(uid: Int, reason: Int) {
            super.onUserOffline(uid, reason)
            Log.i(TAG, String.format("#onUserOffline: user %d offline! reason:%d", uid, reason))
            handler?.post {
                /**Clear render view
                Note: The video will stay at its last frame, to completely remove it you will need to
                remove the SurfaceView from its parent*/
                rtcEngine?.setupRemoteVideo(VideoCanvas(null, RENDER_MODE_HIDDEN, uid))
            }
        }
    }

    val surfaceViewCallBack = object : SurfaceHolder.Callback {
        override fun surfaceCreated(p0: SurfaceHolder) {
            Log.d(TAG, "#surfaceCreated")

        }

        override fun surfaceChanged(p0: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
            Log.d(TAG, "#surfaceChanged")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                timer = Timer()
                timer?.scheduleAtFixedRate(object : TimerTask() {
                    override fun run() {
                        surfaceView?.let {
                            getBitMapFromSurfaceView(it)
                        }
                    }
                }, 50, 50)
            }
        }

        override fun surfaceDestroyed(p0: SurfaceHolder) {
            Log.d(TAG, "#surfaceDestroyed")
            timer?.cancel()
        }
    }

    val scaleGestureDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor = if (scaleFactor < 1) 1f else scaleFactor
                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(1f, 3f)

                localVideoView?.scaleX = scaleFactor
                localVideoView?.scaleY = scaleFactor

                return super.onScale(detector)
            }

            override fun onScaleBegin(detector: ScaleGestureDetector?): Boolean {
                localVideoView?.scaleX = scaleFactor
                localVideoView?.scaleY = scaleFactor
                return super.onScaleBegin(detector)
            }
        }

    )

    init {
        methodChannel.setMethodCallHandler(this)
        lifeCycleHashcode = lifecycleProvider.getLifecycle().hashCode()
        constraintLayout = LayoutInflater.from(context)
            .inflate(R.layout.video_live_streaming_layout, null) as ConstraintLayout
        frameContainer = constraintLayout.findViewById(R.id.frame_container)
        imgClose = constraintLayout.findViewById(R.id.img_close)
        imgVolume = constraintLayout.findViewById(R.id.img_volume)
        imgOrientationScreen = constraintLayout.findViewById(R.id.img_orientation)
        liveController = constraintLayout.findViewById(R.id.live_controller)
        lifecycleProvider.getLifecycle()?.also { it.addObserver(this) }
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        Log.d(TAG, "#onMethodCall: method = ${call.method}")
        when (call.method) {
            "stream#startStream" -> {
                val accessToken = call.argument("accessToken") as? String
                val channelId = call.argument("channelId") as? String
                uid = call.argument("uid") ?: 0
                joinChannel(channelId, accessToken)
            }

            "stream#orientation" -> {
                (call.argument("orientation") as? Int)?.let { orientationMode = it }
                setLayoutParamRemoteVideo(surfaceView, orientationMode)
            }

        }
    }

    override fun getView(): View = constraintLayout

    override fun dispose() {
        Log.d(TAG, "dispose:: $disposed")
        if (disposed) return
        disposed = true
        methodChannel.setMethodCallHandler(null)
        destroyVideoLiveStreaming()
        lifecycleProvider.getLifecycle()?.also {
            it.removeObserver(this)
        }
    }

    private fun destroyVideoLiveStreaming() {
        /**leaveChannel and Destroy the RtcEngine instance*/
        rtcEngine?.leaveChannel()
        handler?.post(RtcEngine::destroy)
        rtcEngine = null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        Log.d(TAG, "#onCreate")
        handler = Handler(Looper.getMainLooper())

        // initialization RtcEngine
        try {
            rtcEngine = RtcEngine.create(context.applicationContext, appId, iRtcEngineEventHandler)
            val config = VideoEncoderConfiguration()
            rtcEngine?.setVideoEncoderConfiguration(config)
            // Create render view by RtcEngine
        } catch (e: Exception) {
            e.printStackTrace()
        }
        imgVolume?.setOnClickListener(this)
        imgClose?.setOnClickListener(this)
        imgOrientationScreen?.setOnClickListener(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        Log.d(TAG, "#onResume")
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        Log.d(TAG, "#onPause")
        timer?.cancel()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        destroyVideoLiveStreaming()

    }

    private fun joinChannel(channelId: String? = null, accessToken: String? = null) {
        /** Sets the channel profile of the Agora RtcEngine.
        CHANNEL_PROFILE_COMMUNICATION(0): (Default) The Communication profile.
        Use this profile in one-on-one calls or group calls, where all users can talk freely.
        CHANNEL_PROFILE_LIVE_BROADCASTING(1): The Live-Broadcast profile. Users in a live-broadcast
        channel have a role as either broadcaster or audience. A broadcaster can both send and receive streams;
        an audience can only receive streams.*/

        rtcEngine?.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING)
        rtcEngine?.setClientRole(IRtcEngineEventHandler.ClientRole.CLIENT_ROLE_BROADCASTER)
        // Enable video module
        rtcEngine?.enableVideo()

        /** Allows a user to join a channel.
        if you do not specify the uid, we will generate the uid for you*/
        val option = ChannelMediaOptions().apply {
            autoSubscribeAudio = false
            autoSubscribeVideo = true
        }

        val result =
            rtcEngine?.joinChannel(accessToken, channelId, "Extra Optional Data", uid, option)
        if (result == null || result != 0) {
            // Usually happens with invalid parameters
            // Error code description can be found at:
            // en: https://docs.agora.io/en/Voice/API%20Reference/java/classio_1_1agora_1_1rtc_1_1_i_rtc_engine_event_handler_1_1_error_code.html
            // cn: https://docs.agora.io/cn/Voice/API%20Reference/java/classio_1_1agora_1_1rtc_1_1_i_rtc_engine_event_handler_1_1_error_code.html
            return
        }
    }

    private fun setLayoutParamRemoteVideo(
        surfaceView: SurfaceView? = null,
        orientationMode: Int,
        zoomLayout: ZoomLayout? = null
    ) {
        if (orientationMode == OrientationMode.PORTRAIT.value) {
            surfaceView?.layoutParams = FrameLayout.LayoutParams(350, 200, Gravity.BOTTOM).apply {
                setMargins(50, 0, 0, 50)
            }

        } else {
            surfaceView?.layoutParams = FrameLayout.LayoutParams(350, 200, Gravity.TOP).apply {
                setMargins(50, 50, 0, 0)
            }
        }

        surfaceView?.background =
            ContextCompat.getDrawable(context, R.drawable.custom_background_remote_video_view)

    }

    /**
     * Pixel copy to copy SurfaceView/VideoView into BitMap
     * Work with Surface View, Video View
     * Won't work on Normal View
     */
    @RequiresApi(Build.VERSION_CODES.N)
    private fun getBitMapFromSurfaceView(videoView: SurfaceView) {
        Log.d(TAG, "#getBitMapFromSurfaceView")
        if (videoView.width <= 0 || videoView.height <= 0) return

        val bitmap: Bitmap = Bitmap.createBitmap(
            videoView.width,
            videoView.height,
            Bitmap.Config.ARGB_8888
        );
        try {

            // Create a handler thread to offload the processing of the image.
            val handlerThread = HandlerThread("PixelCopier");
            handlerThread.start()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                PixelCopy.request(
                    videoView, bitmap, { copyResult ->
                        if (copyResult == PixelCopy.SUCCESS) {
                            handler?.post {
                                val drawable: Drawable = BitmapDrawable(context.resources, bitmap)
                                localVideoView?.background = drawable
                            }
                        }
                        handlerThread.quitSafely()
                    },
                    Handler(handlerThread.looper)
                )
            }
        } catch (e: IllegalArgumentException) {
            // PixelCopy may throw IllegalArgumentException, make sure to handle it
            e.printStackTrace()
        }
    }

    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.img_close -> iLiveEventHandler?.onCloseStream()
            R.id.img_orientation -> {
                iLiveEventHandler?.onOrientationChanged(orientationMode)
                if (orientationMode == OrientationMode.PORTRAIT.value) {
                    orientationMode = OrientationMode.LANDSCAPE.value
                    setLayoutParamRemoteVideo(surfaceView, OrientationMode.LANDSCAPE.value)
                    imgOrientationScreen?.setImageResource(R.drawable.ic_baseline_fullscreen_exit_24)
                } else {
                    orientationMode = OrientationMode.PORTRAIT.value
                    setLayoutParamRemoteVideo(surfaceView, OrientationMode.PORTRAIT.value)
                    imgOrientationScreen?.setImageResource(R.drawable.ic_baseline_fullscreen_24)
                }
            }

            R.id.img_volume -> {
                imgVolume?.setImageResource(if (!isMuted) R.drawable.ic_baseline_volume_off_24 else R.drawable.ic_baseline_volume_up_24)
                rtcEngine?.muteAllRemoteAudioStreams(isMuted)
                isMuted = !isMuted
            }
        }
    }

    inner class SingleTapConfirm : SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
            if (!isControllerShowing) {
                isControllerShowing = true
                liveController?.visibility = View.VISIBLE
                val animationSlideDown =
                    AnimationUtils.loadAnimation(context.applicationContext, R.anim.slide_down)
                liveController?.startAnimation(animationSlideDown)
                liveController?.postDelayed({
                    liveController?.visibility = View.GONE
                    isControllerShowing = false
                }, DEFAULT_DELAY_TIME)
            }
            return super.onSingleTapConfirmed(e)
        }
    }
}
