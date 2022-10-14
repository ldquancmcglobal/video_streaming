package com.example.live_streaming

import android.content.Context
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

class VideoLiveStreamingFactory(
    private val binaryMessenger: BinaryMessenger,
    private val iLiveEventHandler: ILiveEventHandler? = null,
    private val lifecycleProvider: LifecycleProvider
) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {

    private val APP_ID = "appId"

    @Suppress("UNCHECKED_CAST")
    override fun create(context: Context?, viewId: Int, args: Any?): PlatformView {
        val params = args as Map<String, Any?>
        val builder = VideoLiveStreamingBuilder()
        if (params.containsKey(APP_ID)) {
            val appId = params[APP_ID] as String
            builder.setAppIdArgument(appId)
        }
        return builder.build(viewId, context, binaryMessenger, iLiveEventHandler, lifecycleProvider)
    }
}