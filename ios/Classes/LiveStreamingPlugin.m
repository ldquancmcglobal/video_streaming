#import "LiveStreamingPlugin.h"
#if __has_include(<live_streaming/live_streaming-Swift.h>)
#import <live_streaming/live_streaming-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "live_streaming-Swift.h"
#endif

@implementation LiveStreamingPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftLiveStreamingPlugin registerWithRegistrar:registrar];
}
@end
