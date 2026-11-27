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

@interface UIFocusSystem (CMPFocusMovement)

/**
 * Moves the focus in [heading] the way UIKit does for a hardware keyboard: `UIFocusHeadingNext`
 * is Tab and `UIFocusHeadingPrevious` is Shift-Tab, which is also what Full Keyboard Access
 * sends. The movement goes through the focus engine of the receiver, so it reads the same focus
 * items, frames and focus groups as a key press does.
 *
 * @return `NO` when the private UIKit API this relies on is not available on the runtime.
 */
- (BOOL)moveFocusInHeading:(UIFocusHeading)heading;

/**
 * Performs the initial focus movement, which puts the focus on the first focusable item — the
 * movement Full Keyboard Access performs itself when it activates. Needed on iOS versions (e.g.
 * iOS 18) where Full Keyboard Access activates without moving the initial focus anywhere.
 *
 * @return `NO` when the private UIKit API this relies on is not available on the runtime.
 */
- (BOOL)performInitialFocusMovement;

@end

NS_ASSUME_NONNULL_END
