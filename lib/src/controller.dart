part of video_live_streaming;

class LiveStreamingController {
  /// MethodChannel to call methods from the platform
  MethodChannel? _channel;

  EventChannel? _liveControllerChannel;

  EventChannel? _closeStreamingChannel;

  ValueChanged? _orientationChanged;

  VoidCallback? _closeStream;

  LiveStreamingController(int id, this._orientationChanged, this._closeStream) {
    _channel = MethodChannel('com.example.live_streaming/video_live_streaming_$id');
    _liveControllerChannel = EventChannel('com.example.live_streaming/orientationChanged');
    _closeStreamingChannel = EventChannel('com.example.live_streaming/closeStreaming');
    _channel?.setMethodCallHandler(_handleMethodCall);
    _liveControllerChannel?.receiveBroadcastStream().listen((event) {
      _orientationChanged?.call(event["orientation"] as int);
    });

    _closeStreamingChannel?.receiveBroadcastStream().listen((event) {
      _closeStream?.call();
    });
  }

  Future<void> startStreaming(Authenticate authenticate) async {
    try {
      await _channel?.invokeMethod('stream#startStream', authenticate.toJson());
    } catch (ex) {
      ex.toString();
    }
  }

  Future<void> setOrientationLiveStreaming(OrientationMode mode) async {
    try {
      Map<String, dynamic> args = {"orientation": mode.name};
      await _channel?.invokeMethod('stream#orientation', args);
    } catch (ex) {
      ex.toString();
    }
  }

  Future<void> reJoinChannel() async {
    try {
      await _channel?.invokeMethod('stream#rejoinChannel');
    } catch (ex) {
      ex.toString();
    }
  }

  Future<dynamic> _handleMethodCall(MethodCall call) async {}
}
