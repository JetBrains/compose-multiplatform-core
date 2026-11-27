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

#import "UIFocusSystem+Test.h"
#import <objc/message.h>

@implementation UIFocusSystem (CMPFocusMovement)

- (BOOL)moveFocusInHeading:(UIFocusHeading)heading {
    // A `_UIFocusMovementRequest` carrying a `_UIFocusMovementInfo` is what UIKit hands to
    // `-[UIFocusSystem _performFocusMovement:]` for the keyboard focus commands.
    Class movementClass = NSClassFromString(@"_UIFocusMovementInfo");
    Class requestClass = NSClassFromString(@"_UIFocusMovementRequest");
    SEL movementSelector = NSSelectorFromString(@"_movementWithHeading:isInitial:fallbackMovementOriginatingFrame:");
    SEL initSelector = NSSelectorFromString(@"initWithFocusSystem:");
    SEL setMovementSelector = NSSelectorFromString(@"setMovementInfo:");
    SEL performSelector = NSSelectorFromString(@"_performFocusMovement:");
    if (movementClass == Nil || requestClass == Nil ||
        ![movementClass respondsToSelector:movementSelector] ||
        ![requestClass instancesRespondToSelector:initSelector] ||
        ![requestClass instancesRespondToSelector:setMovementSelector] ||
        ![self respondsToSelector:performSelector]) {
        return NO;
    }

    id movement = ((id (*)(id, SEL, UIFocusHeading, BOOL, CGRect))objc_msgSend)(
        movementClass, movementSelector, heading, NO, CGRectNull);
    id request = ((id (*)(id, SEL, id))objc_msgSend)([requestClass alloc], initSelector, self);
    if (movement == nil || request == nil) {
        return NO;
    }
    ((void (*)(id, SEL, id))objc_msgSend)(request, setMovementSelector, movement);
    ((void (*)(id, SEL, id))objc_msgSend)(self, performSelector, request);
    return YES;
}

@end
