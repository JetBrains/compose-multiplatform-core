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

#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// A hinge reported by the platform, in the observed view's UIKit point coordinate space.
@interface CMPWindowHingeInfo : NSObject

- (instancetype)init NS_UNAVAILABLE;

@property (nonatomic, readonly) CGFloat x;
@property (nonatomic, readonly) CGFloat y;
@property (nonatomic, readonly) CGFloat width;
@property (nonatomic, readonly) CGFloat height;
@property (nonatomic, readonly) BOOL isFlat;
@property (nonatomic, readonly) BOOL isVertical;
@property (nonatomic, readonly) BOOL isSeparating;
@property (nonatomic, readonly) BOOL isOccluding;

@end

typedef void (^CMPWindowPostureUpdateHandler)(NSArray<CMPWindowHingeInfo *> *hinges);

/// Observes the hinge of the view's hierarchy and reports its geometry as hinge info values.
///
/// The reported rectangle is the hinge itself; reserved-region interaction margins are not
/// included.
///
/// The observer hides iOS version differences from Kotlin callers. On iOS versions without hinge
/// support it immediately reports an empty list.
///
/// Hinge geometry is refreshed when the observed view's bounds or the window scene geometry
/// changes, so orientation updates are reflected without requiring a new hinge-state event.
@interface CMPWindowPostureObserver : NSObject

- (instancetype)init NS_UNAVAILABLE;

- (instancetype)initWithView:(UIView *)view
                     onUpdate:(CMPWindowPostureUpdateHandler)onUpdate NS_DESIGNATED_INITIALIZER;

- (void)start;
- (void)stop;

@end

NS_ASSUME_NONNULL_END
