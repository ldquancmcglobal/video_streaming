package com.example.live_streaming

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.embedding.engine.plugins.lifecycle.FlutterLifecycleAdapter
import io.flutter.plugin.common.EventChannel
import io.flutter.embedding.android.FlutterActivity
import java.util.logging.StreamHandler

class LiveStreamingPlugin : FlutterPlugin, ActivityAware, FlutterActivity() {
  private var lifecycle: Lifecycle? = null

  private val orientationChangedChannel = "com.example.live_streaming/orientationChanged"
  private val closeStreamingChannel = "com.example.live_streaming/closeStreaming"

  private var eventOrientationChangeSink: EventChannel.EventSink? = null
  private var eventCloseStreamChangeSink: EventChannel.EventSink? = null

  companion object {
    private const val VIEW_TYPE_ID = "video_live_streaming"
    private val TAG = LiveStreamingPlugin::class.java.simpleName

    @Suppress("deprecation")
    @JvmStatic
    fun registerWith(registrar: io.flutter.plugin.common.PluginRegistry.Registrar) {
      val activity = registrar.activity() ?: return

      if (activity is LifecycleOwner) {
        registrar
          .platformViewRegistry()
          .registerViewFactory(
            VIEW_TYPE_ID,
            VideoLiveStreamingFactory(
              registrar.messenger(),
              iLiveEventHandler = object: ILiveEventHandler{
                override fun onCloseStream() {
                  Log.d(TAG, "#registerWith: onCloseStream")
                }

                override fun onOrientationChanged(orientation: Int) {
                  Log.d(TAG, "#registerWith: orientation = $orientation")
                }

              },
              object : LifecycleProvider {
                override fun getLifecycle(): Lifecycle? {
                  return (activity as LifecycleOwner).lifecycle
                }

              }
            )
          )
      }
    }
  }

  override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    EventChannel(binding.binaryMessenger, orientationChangedChannel)
      .setStreamHandler( object: EventChannel.StreamHandler {
        override fun onListen(p0: Any?, eventSink: EventChannel.EventSink) {
          eventOrientationChangeSink = eventSink
        }

        override fun onCancel(p0: Any) {
          eventOrientationChangeSink = null
        }
      })

    EventChannel(binding.binaryMessenger, closeStreamingChannel)
      .setStreamHandler( object: EventChannel.StreamHandler {
        override fun onListen(p0: Any?, eventSink: EventChannel.EventSink) {
          eventCloseStreamChangeSink = eventSink
        }

        override fun onCancel(p0: Any) {
          eventCloseStreamChangeSink = null
        }
      })


    binding
      .platformViewRegistry
      .registerViewFactory(
        VIEW_TYPE_ID,
        VideoLiveStreamingFactory(
          binding.binaryMessenger,
          object : ILiveEventHandler{
            override fun onCloseStream() {
              eventCloseStreamChangeSink?.success(null)
              Log.d(TAG, "#onAttachedToEngine: onCLoseStream")
            }

            override fun onOrientationChanged(orientation: Int) {
              Log.d(TAG, "#onAttachedToEngine: orientation = $orientation")
              eventOrientationChangeSink?.success(mapOf("orientation" to orientation))
            }

          },
          object : LifecycleProvider {
            override fun getLifecycle(): Lifecycle? {
              return lifecycle
            }
          }
        )
      )
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    lifecycle = FlutterLifecycleAdapter.getActivityLifecycle(binding)
  }

  override fun onDetachedFromActivityForConfigChanges() {
    Log.d(TAG, "#registerWith: onDetachedFromActivityForConfigChanges")
    onDetachedFromActivity()
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    Log.d(TAG, "#registerWith: onReattachedToActivityForConfigChanges")
    onAttachedToActivity(binding)
  }

  override fun onDetachedFromActivity() {
    lifecycle = null
  }
}
