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

#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@interface UIEvent (CMPScroll)

/**
 * Opens a scroll session at [point] with initial [delta] (phase Began).
 * Returns `nil` if `UIScrollEvent` is unavailable (pre-iOS 13.4).
 */
+ (nullable instancetype)scrollEventAtPoint:(CGPoint)point
                                      delta:(CGPoint)delta
                                   inWindow:(UIWindow *)window;

/** Emits a phase-Changed scroll event with the given [delta]. */
- (void)scrollByDelta:(CGPoint)delta
             inWindow:(UIWindow *)window;

/** Emits a phase-Ended scroll event and closes the session. */
- (void)endInWindow:(UIWindow *)window;

@end


@interface UIEvent (CMPPinch)

/**
 * Opens a pinch session at [point] with initial absolute [scale]
 * (typically `1.0`), phase Began. Returns `nil` if `UITransformEvent`
 * is unavailable.
 */
+ (nullable instancetype)pinchEventAtPoint:(CGPoint)point
                                     scale:(CGFloat)scale
                                  inWindow:(UIWindow *)window;

/** Emits a phase-Changed pinch event with the new absolute [scale]. */
- (void)pinchByScale:(CGFloat)scale
            inWindow:(UIWindow *)window;

/** Emits a phase-Ended pinch event and closes the session. */
- (void)endPinchInWindow:(UIWindow *)window;

@end


@interface UIEvent (CMPHover)

/**
 * Opens a hover session at [point]. Returns `nil` if `UIHoverEvent`
 * is unavailable.
 */
+ (nullable instancetype)hoverEventAtPoint:(CGPoint)point
                                  inWindow:(UIWindow *)window;

/** Emits a hover-moved event with the cursor now at [point]. */
- (void)hoverMoveToPoint:(CGPoint)point
                inWindow:(UIWindow *)window;

/** Closes the hover session. */
- (void)endHoverInWindow:(UIWindow *)window;

@end

NS_ASSUME_NONNULL_END
