//
//  NativeVideoVIewFactory.swift
//  Runner
//
//  Created by Luis Jara Castillo on 11/4/19.
//

import Foundation
import Flutter

public class LiveStreamViewFactory:NSObject, FlutterPlatformViewFactory {
    private let registrar: FlutterPluginRegistrar
    
    init(registrar:FlutterPluginRegistrar){
        self.registrar = registrar
    }
    
    public func create(withFrame frame: CGRect, viewIdentifier viewId: Int64, arguments args: Any?) -> FlutterPlatformView {
        return LiveStreamViewController(frame: frame, viewId: viewId, registrar: registrar)
    }
}
