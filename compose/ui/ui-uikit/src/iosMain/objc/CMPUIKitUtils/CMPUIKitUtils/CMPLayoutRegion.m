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

#import "CMPLayoutRegion.h"
#import <UIKit/UIKit.h>

// Referencing the `UIViewLayoutRegion` class directly emits an `_OBJC_CLASS_$_UIViewLayoutRegion`
// link-time symbol into the prebuilt static library shipped inside the klib. The class only exists
// in the iOS 26 SDK, so applications linking against older SDKs fail to link (CMP-10200,
// CMP-10417). Declaring the API locally and resolving the class with `NSClassFromString` leaves
// only selector references, which the Objective-C runtime resolves. Enum constants and types are
// resolved at compile time and don't contribute any symbols, so they are used as is.
@protocol CMPUIViewLayoutRegionFactory <NSObject>
+ (id)marginsLayoutRegionWithCornerAdaptation:(UIViewLayoutRegionAdaptivityAxis)axis
    API_AVAILABLE(ios(26.0));
+ (id)readableContentLayoutRegionWithCornerAdaptation:(UIViewLayoutRegionAdaptivityAxis)axis
    API_AVAILABLE(ios(26.0));
+ (id)safeAreaLayoutRegionWithCornerAdaptation:(UIViewLayoutRegionAdaptivityAxis)axis
    API_AVAILABLE(ios(26.0));
@end

@interface UIView (CMPLayoutRegion)
- (UIEdgeInsets)edgeInsetsForLayoutRegion:(id)region;
@end

typedef NS_ENUM(NSInteger, CMPLayoutRegionKind) {
    CMPLayoutRegionKindMargins = 0,
    CMPLayoutRegionKindReadableContent = 1,
    CMPLayoutRegionKindSafeArea = 2,
};

@interface CMPLayoutRegion ()
- (instancetype)initWithKind:(CMPLayoutRegionKind)kind
                        axis:(CMPLayoutRegionAdaptivityAxis)axis NS_DESIGNATED_INITIALIZER;
@end

@implementation CMPLayoutRegion {
    CMPLayoutRegionKind _kind;
    CMPLayoutRegionAdaptivityAxis _axis;
}

- (instancetype)initWithKind:(CMPLayoutRegionKind)kind
                        axis:(CMPLayoutRegionAdaptivityAxis)axis {
    if (self = [super init]) {
        _kind = kind;
        _axis = axis;
    }
    return self;
}

+ (instancetype)marginsWithCornerAdaptation:(CMPLayoutRegionAdaptivityAxis)axis {
    return [[self alloc] initWithKind:CMPLayoutRegionKindMargins axis:axis];
}

+ (instancetype)readableContentWithCornerAdaptation:(CMPLayoutRegionAdaptivityAxis)axis {
    return [[self alloc] initWithKind:CMPLayoutRegionKindReadableContent axis:axis];
}

+ (instancetype)safeAreaWithCornerAdaptation:(CMPLayoutRegionAdaptivityAxis)axis {
    return [[self alloc] initWithKind:CMPLayoutRegionKindSafeArea axis:axis];
}

- (UIEdgeInsets)edgeInsetsInView:(UIView *)view {
    // Starting iOS 26, UIKit exposes `UIViewLayoutRegion`s (e.g. safe area / margins with corner
    // adaptation) which are needed to properly adopt iOS 26 macOS-like system controls.
    //
    // However, iOS 26.0 has a bug that can add spurious horizontal insets when using layout
    // regions. This affects both iPhone and iPad.
    //
    // - iOS 26.1+: use layout regions everywhere (bug fixed).
    // - iOS 26.0 iPhone: fall back to pre-iOS-26 insets to avoid unexpected horizontal shifts.
    // - iOS 26.0 iPad: keep layout regions despite the horizontal insets in favor of correct insets
    // for the new navigation elements.
    if (@available(iOS 26.1, *)) {
        return [self edgeInsetsForLayoutRegionInView: view];
    }

    if (@available(iOS 26.0, *)) {
        if (UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad) {
            return [self edgeInsetsForLayoutRegionInView: view];
        }
    }

    return [self edgeInsetsFallbackInView: view];
}

- (UIEdgeInsets)edgeInsetsForLayoutRegionInView:(UIView *)view API_AVAILABLE(ios(26.0)) {
    Class<CMPUIViewLayoutRegionFactory> layoutRegionClass =
        (Class<CMPUIViewLayoutRegionFactory>)NSClassFromString(@"UIViewLayoutRegion");

    if (layoutRegionClass == nil) {
        return [self edgeInsetsFallbackInView: view];
    }

    UIViewLayoutRegionAdaptivityAxis axis;
    switch (_axis) {
        case CMPLayoutRegionAdaptivityAxisNone:
            axis = UIViewLayoutRegionAdaptivityAxisNone;
            break;
        case CMPLayoutRegionAdaptivityAxisHorizontal:
            axis = UIViewLayoutRegionAdaptivityAxisHorizontal;
            break;
        case CMPLayoutRegionAdaptivityAxisVertical:
            axis = UIViewLayoutRegionAdaptivityAxisVertical;
            break;
    }

    id layoutRegion;
    switch (_kind) {
        case CMPLayoutRegionKindMargins:
            layoutRegion = [layoutRegionClass marginsLayoutRegionWithCornerAdaptation:axis];
            break;
        case CMPLayoutRegionKindReadableContent:
            layoutRegion = [layoutRegionClass readableContentLayoutRegionWithCornerAdaptation:axis];
            break;
        case CMPLayoutRegionKindSafeArea:
            layoutRegion = [layoutRegionClass safeAreaLayoutRegionWithCornerAdaptation:axis];
            break;
    }

    if (layoutRegion == nil) {
        return [self edgeInsetsFallbackInView: view];
    }

    return [view edgeInsetsForLayoutRegion:layoutRegion];
}

- (UIEdgeInsets)edgeInsetsFallbackInView:(UIView *)view {
    switch (_kind) {
        case CMPLayoutRegionKindMargins:
            return view.layoutMargins;
        case CMPLayoutRegionKindReadableContent: {
            CGRect layoutFrame = view.readableContentGuide.layoutFrame;
            CGFloat top = layoutFrame.origin.y;
            CGFloat left = layoutFrame.origin.x;
            CGFloat right = view.frame.size.width - (left + layoutFrame.size.width);
            CGFloat bottom = view.frame.size.height - (top + layoutFrame.size.height);
            return UIEdgeInsetsMake(top, left, right, bottom);
        }
        case CMPLayoutRegionKindSafeArea:
        default:
            return view.safeAreaInsets;
    }
}

@end
