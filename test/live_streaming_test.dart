import 'package:flutter_test/flutter_test.dart';
import 'package:live_streaming/live_streaming.dart';
import 'package:live_streaming/live_streaming_platform_interface.dart';
import 'package:live_streaming/live_streaming_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockLiveStreamingPlatform
    with MockPlatformInterfaceMixin
    implements LiveStreamingPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final LiveStreamingPlatform initialPlatform = LiveStreamingPlatform.instance;

  test('$MethodChannelLiveStreaming is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelLiveStreaming>());
  });

  test('getPlatformVersion', () async {
    LiveStreaming liveStreamingPlugin = LiveStreaming();
    MockLiveStreamingPlatform fakePlatform = MockLiveStreamingPlatform();
    LiveStreamingPlatform.instance = fakePlatform;

    expect(await liveStreamingPlugin.getPlatformVersion(), '42');
  });
}
