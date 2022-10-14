package com.example.live_streaming

import android.content.Context
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.platform.PlatformView

class VideoLiveStreamingBuilder {

    private var appId: String = ""

    fun build(id: Int, context: Context?, binaryMessenger: BinaryMessenger,
              iLiveEventHandler: ILiveEventHandler? = null,
              lifecycleProvider: LifecycleProvider
    ): PlatformView {
        return VideoLiveStreamingController(
            id, appId, context!!, binaryMessenger,
            iLiveEventHandler,
            lifecycleProvider
        )
    }

    fun setAppIdArgument(appId: String) {
        this.appId = appId
    }
}