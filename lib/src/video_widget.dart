part of video_live_streaming;

/// Callback that is called when the view is created and ready.
typedef ViewCreateCallback = void Function(LiveStreamingController controller);

/// Callback that is called when the playback of a video is completed.
typedef CompletionCallback = void Function(LiveStreamingController controller);

class VideoLiveStreamingView extends StatelessWidget {
  final String? appId;

  /// Instance of [ViewCreatedCallback] to notify
  /// when the view is finished creating.
  final ViewCreateCallback onCreated;

  final ValueChanged orientationChanged;

  final VoidCallback closeStream;

  const VideoLiveStreamingView({
    required this.appId,
    required this.onCreated,
    required this.closeStream,
    required this.orientationChanged,
    Key? key,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    const String viewType = 'video_live_streaming';
    // Pass parameters to the platform side.
    if (defaultTargetPlatform == TargetPlatform.android) {
      final Map<String, dynamic> creationParams = <String, dynamic>{
        'appId': appId
      };
      return AndroidView(
        viewType: viewType,
        onPlatformViewCreated: onPlatformViewCreated,
        creationParams: creationParams,
        creationParamsCodec: const StandardMessageCodec(),
      );
    } else if (defaultTargetPlatform == TargetPlatform.iOS) {
      return UiKitView(
        viewType: viewType,
        onPlatformViewCreated: onPlatformViewCreated,
        creationParams: null,
        creationParamsCodec: const StandardMessageCodec(),
      );
    }

    return Text('$defaultTargetPlatform is not yet supported by this plugin.');
  }

  Future<void> onPlatformViewCreated(int id) async {
    final LiveStreamingController controller =
        await LiveStreamingController(id, orientationChanged, closeStream);
    onCreated(controller);
  }
}
