import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:live_streaming/live_streaming_method_channel.dart';

void main() {
  MethodChannelLiveStreaming platform = MethodChannelLiveStreaming();
  const MethodChannel channel = MethodChannel('live_streaming');

  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    channel.setMockMethodCallHandler((MethodCall methodCall) async {
      return '42';
    });
  });

  tearDown(() {
    channel.setMockMethodCallHandler(null);
  });

  test('getPlatformVersion', () async {
    expect(await platform.getPlatformVersion(), '42');
  });
}
