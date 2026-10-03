/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import UIKit

@objc(CMPReservedRegion)
@objcMembers
public final class CMPReservedRegion: NSObject {
    public let frame: CGRect
    public let margins: UIEdgeInsets
    public let isActive: Bool

    public init(frame: CGRect, margins: UIEdgeInsets, isActive: Bool) {
        self.frame = frame
        self.margins = margins
        self.isActive = isActive
    }

    #if compiler(>=6.4)
    @available(iOS 27.1, *)
    fileprivate convenience init(_ region: UIView.ReservedRegion) {
        self.init(frame: region.frame, margins: region.margins, isActive: region.isActive)
    }
    #endif
}

@objc(CMPReservedRegionFactory)
@objcMembers
public final class CMPReservedRegionFactory: NSObject {
    public static func supportsReservedRegions() -> Bool {
        #if compiler(>=6.4)
        if #available(iOS 27.1, *) {
            return true
        }
        #endif
        return false
    }

    @objc(occlusionRegionsInView:)
    public static func occlusionRegions(in view: UIView) -> [CMPReservedRegion] {
        #if compiler(>=6.4)
        if #available(iOS 27.1, *) {
            return view.reservedRegions(kind: .occlusion).map(CMPReservedRegion.init)
        }
        #endif
        return []
    }
}
