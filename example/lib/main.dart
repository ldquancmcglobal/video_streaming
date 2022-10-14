import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:live_streaming/live_streaming.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
    DeviceOrientation.portraitDown,
    DeviceOrientation.landscapeLeft,
    DeviceOrientation.landscapeRight,
  ]);
  runApp(const MaterialApp(debugShowCheckedModeBanner: false, home: MyApp()));
}

class MyApp extends StatefulWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  bool isLandscape = false;

  @override
  void initState() {
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Scaffold(
        body: _buildVideoLiveStreamingWidget(),
      ),
    );
  }

  Widget _buildVideoLiveStreamingWidget() {
    return Container(
      color: Colors.transparent,
      height: isLandscape ? double.infinity : 250,
      width: double.infinity,
      alignment: Alignment.center,
      child: VideoLiveStreamingView(
        appId: 'a023c5b9b23344c1a40e901d3900c0ee',
        onCreated: (controller) {
          controller.startStreaming(Authenticate(
              accessToken:
              '006a023c5b9b23344c1a40e901d3900c0eeIAC//aQ9LzsfGgpmA0kx2JOOAOoWU9Cg0aKg6uauZm1PtvONdXoNvtUaEACMgwAApJg/YwEAAQDkG0Bj',
              channelId: 'butai-channel-112',
              uid: 2));
        },
        closeStream: () {
          print("closeStream");
        },
        orientationChanged: (orientation) async {
          print("orientationChanged = $orientation");
          /**
           * 0 - PORTRAIT
           * 1 - LANDSCAPE
           */
          if (orientation == 0) {
            isLandscape = true;
            SystemChrome.setPreferredOrientations([
              DeviceOrientation.landscapeLeft,
              DeviceOrientation.landscapeRight,
            ]);
          } else {
            isLandscape = false;
            SystemChrome.setPreferredOrientations([
              DeviceOrientation.portraitUp,
              DeviceOrientation.portraitDown,
            ]);
          }
          setState(() {});
        },
      ),
    );
  }
}
