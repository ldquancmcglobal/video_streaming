//
//  RawVideoData.swift
//  APIExample
//
//  Created by Arlin on 2022/4/11.
//  Copyright © 2022 Agora Corp. All rights reserved.
//


/// Raw Video Data
/// This module show how to get origin raw video frame data.
/// 1.Register obesever: agoraKit.setVideoFrameDelegate(self)
/// 2.Call back AgoraVideoFrameDelegate to get raw video frame data
///
/// More detail: https://docs.agora.io/en/Interactive%20Broadcast/raw_data_video_apple?platform=iOS

import AgoraRtcKit
import CoreGraphics
import Foundation
import Flutter

public class LiveStreamViewController: NSObject, FlutterPlatformView  {
    var viewId: Int64
    var videoContainer: LiveStreamView?
    var scrollView: UIScrollView!
    var imageView: UIImageView!
    
    var agoraKit: AgoraRtcEngineKit!
    var isExpanded = false
    var imgTest: UIImageView!
    var configs: [String:Any] = [:]
    
    private var methodChannel: FlutterMethodChannel
    
    init(frame:CGRect, viewId: Int64, registrar: FlutterPluginRegistrar) {
        self.viewId = viewId
        self.videoContainer = LiveStreamView(frame: frame)
        self.methodChannel = FlutterMethodChannel(name: "com.example.live_streaming/video_live_streaming_\(viewId)", binaryMessenger: registrar.messenger())
        super.init()
        self.methodChannel.setMethodCallHandler {
            [weak self] (call: FlutterMethodCall, result: @escaping FlutterResult) -> Void in
             self?.handle(call: call, result: result)
        }
        
        scrollView.maximumZoomScale = 4
        scrollView.minimumZoomScale = -1
        
        scrollView.delegate = self
    }
    
    public func view() -> UIView {
        return videoContainer!
    }
    
    func handle(call: FlutterMethodCall, result: FlutterResult) -> Void {
        switch(call.method) {
        case "stream#startStream":
            scrollView = UIScrollView.init(frame: CGRect.init(x: 0, y: 0, width: UIScreen.main.bounds.width, height: UIScreen.main.bounds.height))
            imageView = UIImageView.init(frame: CGRect.init(x: 0, y: 0, width: UIScreen.main.bounds.width, height: UIScreen.main.bounds.height))
            self.videoContainer.addSubview(scrollView)
            self.scrollView.addSubview(imageView)
            let arguments = call.arguments as? [String:Any]
            if let args = arguments {
                let appId = args["appId"] as? String
                let accessToken = args["accessToken"] as? String
                let channelId = args["channelId"] as? String
                let uid = args["uid"] as? Int
                let resolution = CGSize(width: 640, height: 360)
                let fps = AgoraVideoFrameRate.fps30
                let orientation = AgoraVideoOutputOrientationMode.fixedPortrait
                
                agoraKit = AgoraRtcEngineKit.sharedEngine(withAppId: appId, delegate: self)
                // Setup raw video data frame observer
                agoraKit.setVideoFrameDelegate(self)
                
                agoraKit.enableVideo()
                agoraKit.enableAudio()
                agoraKit.setVideoEncoderConfiguration(AgoraVideoEncoderConfiguration(size: resolution,
                                                                                     frameRate: fps,
                                                                                     bitrate: AgoraVideoBitrateStandard,
                                                                                     orientationMode: orientation,
                                                                                     mirrorMode: .auto))
                
                agoraKit.startPreview()
                
                let option = AgoraRtcChannelMediaOptions()
                option.publishCameraTrack = true
                option.publishMicrophoneTrack = true
                
                let result = agoraKit.joinChannel(byToken: accessToken, channelId: channelId, uid: UInt(uid), mediaOptions: option, joinSuccess: nil)
                if result != 0 {
                    /// Error code description: https://docs.agora.io/en/Interactive%20Broadcast/error_rtc
                    print("Error\nJoin channel failed with errorCode: \(result)")
                }
            }
            result(nil)
            break
        }
    }
    
    override func didMove(toParent parent: UIViewController?) {
        if parent == nil {
            agoraKit.disableAudio()
            agoraKit.disableVideo()
            agoraKit.setVideoFrameDelegate(nil)
            agoraKit.leaveChannel(nil)
        }
    }
    
    @IBAction func fullScreenClicked(_ sender: Any) {
        if !isExpanded {
            //Expand the video
            UIView.animate(withDuration: 0.8, delay: 0, usingSpringWithDamping: 1, initialSpringVelocity: 1, options: .curveEaseOut, animations: {
                self.imageView.isHidden = true
                self.imgTest = UIImageView(frame: CGRect(x: 0, y: 0, width: self.scrollView.frame.height, height: self.scrollView.frame.width))
                self.imgTest.image = UIImage(named: "kung_fu_panda_3_2016-wallpaper-1920x1080.jpg")
                self.imgTest.transform = CGAffineTransform(rotationAngle: CGFloat(Double.pi / 2))
                self.imgTest.layer.anchorPoint = CGPoint(x: 0.27, y: 0.08)
                self.scrollView.addSubview(self.imgTest)
            }, completion: nil)
        } else {
            //Shrink the video again
            UIView.animate(withDuration: 0.8, delay: 0, usingSpringWithDamping: 1, initialSpringVelocity: 1, options: .curveEaseOut, animations: {
                self.imageView.transform = CGAffineTransform.identity
                self.imgTest.removeFromSuperview()
                self.imageView.isHidden = false
                self.imageView.layoutSubviews()
            }, completion: nil)
        }
        
        isExpanded = !isExpanded
    }
}

// MARK: - AgoraVideoFrameDelegate
extension LiveStreamViewController: AgoraVideoFrameDelegate {
    func onCapture(_ videoFrame: AgoraOutputVideoFrame) -> Bool {
//        if isSnapShoting {
//            isSnapShoting = false
//            let image = MediaUtils.pixelBuffer(toImage: videoFrame.pixelBuffer!)
//            DispatchQueue.main.async {
//                self.imageView.image = image
//            }
//        }
        return true
    }
    
    func onRenderVideoFrame(_ videoFrame: AgoraOutputVideoFrame, uid: UInt, channelId: String) -> Bool {
        let image = MediaUtils.i420(toImage: videoFrame.yBuffer, srcU: videoFrame.uBuffer, srcV: videoFrame.vBuffer, width: videoFrame.width, height: videoFrame.height)
//        self.image = image
        DispatchQueue.main.async {
            self.imageView.image = image
        }
        return true
    }
}

extension LiveStreamViewController: UIScrollViewDelegate {
    func viewForZooming(in scrollView: UIScrollView) -> UIView? {
        if !isExpanded {
            return imageView
        } else {
            return imgTest
        }
    }
    
    func scrollViewDidZoom(_ scrollView: UIScrollView) {
        if !isExpanded {
            if let image = imageView.image {
                let ratioW = imageView.frame.width / image.size.width
                let ratioH = imageView.frame.height / image.size.height
                
                let ratio = ratioW < ratioH ? ratioW : ratioH
                let newWidth = image.size.width * ratio
                let newHeight = image.size.height * ratio
                let conditionLeft = newWidth*scrollView.zoomScale > imageView.frame.width
                let left = 0.5 * (conditionLeft ? newWidth - imageView.frame.width : (scrollView.frame.width - scrollView.contentSize.width))
                let conditioTop = newHeight*scrollView.zoomScale > imageView.frame.height
                
                let top = 0.5 * (conditioTop ? newHeight - imageView.frame.height : (scrollView.frame.height - scrollView.contentSize.height))
                
                scrollView.contentInset = UIEdgeInsets(top: top, left: left, bottom: top, right: left)
            }
        } else {
            if let image = imgTest.image {
                let ratioW = image.size.width / imgTest.frame.width
                let ratioH = image.size.height / imgTest.frame.height
                
                let ratio = ratioW < ratioH ? ratioW : ratioH
                let newWidth = image.size.width * ratio
                let newHeight = image.size.height * ratio
                let conditionLeft = newWidth*scrollView.zoomScale > imgTest.frame.width
                let left = 0.5 * (conditionLeft ? newWidth - imgTest.frame.width : (scrollView.frame.width - scrollView.contentSize.width))
                let conditioTop = newHeight*scrollView.zoomScale > imgTest.frame.height
                
                let top = 0.5 * (conditioTop ? newHeight - imgTest.frame.height : (scrollView.frame.height - scrollView.contentSize.height))
                
                scrollView.contentInset = UIEdgeInsets(top: top, left: left, bottom: top, right: left)
            }
        }
        
    }
}

// MARK: - AgoraRtcEngineDelegate
extension LiveStreamViewController: AgoraRtcEngineDelegate {
    func rtcEngine(_ engine: AgoraRtcEngineKit, didOccurError errorCode: AgoraErrorCode) {
        /// Error code description: https://docs.agora.io/en/Interactive%20Broadcast/error_rtc
        LogUtils.log(message: "Error occur: \(errorCode)", level: .error)
        self.showAlert(title: "Error", message: "Error: \(errorCode.description)")
    }
    
    func rtcEngine(_ engine: AgoraRtcEngineKit, didJoinChannel channel: String, withUid uid: UInt, elapsed: Int) {
        LogUtils.log(message: "Join \(channel) with uid \(uid) elapsed \(elapsed)ms", level: .info)
    }
    
    func rtcEngine(_ engine: AgoraRtcEngineKit, didJoinedOfUid uid: UInt, elapsed: Int) {
        LogUtils.log(message: "Remote user \(uid) joined elapsed \(elapsed)ms", level: .info)
        
        // Render remote user video frame at a UIView
        let videoCanvas = AgoraRtcVideoCanvas()
        videoCanvas.view = videoContainer.view
        videoCanvas.uid = uid
        videoCanvas.renderMode = .hidden
        agoraKit.setupRemoteVideo(videoCanvas)
    }
    
    func rtcEngine(_ engine: AgoraRtcEngineKit, didOfflineOfUid uid: UInt, reason: AgoraUserOfflineReason) {
        LogUtils.log(message: "Remote user \(uid) offline with reason \(reason)", level: .info)
        
        // Stop render remote user video frame
        let videoCanvas = AgoraRtcVideoCanvas()
        videoCanvas.view = nil
        videoCanvas.uid = uid
        agoraKit.setupRemoteVideo(videoCanvas)
    }
    
    /// Reports the statistics of the current call. The SDK triggers this callback once every two seconds after the user joins the channel.
    /// @param stats stats struct
    func rtcEngine(_ engine: AgoraRtcEngineKit, reportRtcStats stats: AgoraChannelStats) {
        localVideo.statsInfo?.updateChannelStats(stats)
    }
    
    /// Reports the statistics of the uploading local audio streams once every two seconds.
    /// @param stats stats struct
    func rtcEngine(_ engine: AgoraRtcEngineKit, localAudioStats stats: AgoraRtcLocalAudioStats) {
        localVideo.statsInfo?.updateLocalAudioStats(stats)
    }
    
    /// Reports the statistics of the video stream from each remote user/host.
    /// @param stats stats struct
    func rtcEngine(_ engine: AgoraRtcEngineKit, remoteVideoStats stats: AgoraRtcRemoteVideoStats) {
        
    }
    
    /// Reports the statistics of the audio stream from each remote user/host.
    /// @param stats stats struct for current call statistics
    func rtcEngine(_ engine: AgoraRtcEngineKit, remoteAudioStats stats: AgoraRtcRemoteAudioStats) {
        
    }
}


