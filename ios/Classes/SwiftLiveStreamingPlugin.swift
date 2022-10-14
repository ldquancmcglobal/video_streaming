import Flutter
import UIKit

public class SwiftLiveStreamingPlugin: NSObject, FlutterPlugin {
  public static func register(with registrar: FlutterPluginRegistrar) {
      let liveStreamViewFactory = LiveStreamViewFactory(registrar: registrar)
      registrar.register(liveStreamViewFactory, withId: "com.example.live_streaming/video_live_streaming")
  }
}
