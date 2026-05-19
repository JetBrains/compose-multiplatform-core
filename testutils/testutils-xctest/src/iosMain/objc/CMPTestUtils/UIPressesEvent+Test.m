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

#import "UIPressesEvent+Test.h"
#import <UIKit/UIKit.h>
#import <objc/runtime.h>
#import <objc/message.h>

@interface UIPress (CMPTestPrivate)
- (void)setPhase:(UIPressPhase)phase;
- (void)setType:(UIPressType)type;
- (void)setForce:(CGFloat)force;
- (void)setWindow:(UIWindow *)window;
- (void)setTimestamp:(NSTimeInterval)timestamp;
- (void)setClickCount:(NSUInteger)clickCount;
- (void)setModifierFlags:(UIKeyModifierFlags)modifierFlags;
- (void)setGestureRecognizers:(NSArray<UIGestureRecognizer *> *)gestureRecognizers;
- (void)setKey:(UIKey *)key;
- (void)_setResponder:(UIResponder *)responder;
- (void)_setSource:(NSUInteger)source;
@end

@interface UIApplication (CMPTestPrivate)
- (UIEvent *)_touchesEvent;
@end

@protocol CMPUIKeyInput <NSObject>
- (void)insertText:(NSString *)text;
@end

#pragma mark - Responder lookup

static UIResponder *CMPFindFirstResponder(UIView *view) {
    if (view.isFirstResponder) { return view; }
    for (UIView *sub in view.subviews) {
        UIResponder *firstResponder = CMPFindFirstResponder(sub);
        if (firstResponder != nil) { return firstResponder; }
    }
    return nil;
}

#pragma mark - Event / press construction

// UIApplication's `_touchesEvent` is the only stable UIKit-produced event we
// can reach from outside UIKit. We borrow its `_eventEnvironment` because the
// native press dispatcher reads that ivar; without it, `sendEvent:` silently
// drops the synthetic event (verified via diagnostic dumps).
static id CMPSharedEventEnvironment(void) {
    static id env;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        SEL sel = @selector(_touchesEvent);
        if (![[UIApplication sharedApplication] respondsToSelector:sel]) { return; }
        UIEvent *touchesEvent = ((id(*)(id, SEL))objc_msgSend)([UIApplication sharedApplication], sel);
        Ivar envIvar = class_getInstanceVariable([UIEvent class], "_eventEnvironment");
        if (envIvar != NULL && touchesEvent != nil) {
            env = object_getIvar(touchesEvent, envIvar);
        }
    });
    return env;
}

// Subclassing `UIPressesEvent` (objc_allocateClassPair + method overrides) did
// not survive UIKit's native dispatch — `sendEvent:` checked the concrete class
// or read backing ivars and dropped the event. We allocate the real class and
// populate its private ivars instead.
static UIPressesEvent *CMPMakePressesEvent(UIPress *press) {
    Class cls = NSClassFromString(@"UIPressesEvent");
    if (cls == Nil) { return nil; }
    SEL initSel = NSSelectorFromString(@"_init");
    UIPressesEvent *event = nil;
    if ([cls instancesRespondToSelector:initSel]) {
        event = ((id(*)(id, SEL))objc_msgSend)([cls alloc], initSel);
    } else {
        event = [[cls alloc] init];
    }
    if (event == nil) { return nil; }

    Ivar allPressesIvar = class_getInstanceVariable(cls, "_allPresses");
    if (allPressesIvar != NULL) {
        object_setIvar(event, allPressesIvar, [NSMutableSet setWithObject:press]);
    }
    Ivar lastPreparedIvar = class_getInstanceVariable(cls, "_lastPreparedPress");
    if (lastPreparedIvar != NULL) {
        object_setIvar(event, lastPreparedIvar, press);
    }

    Ivar envIvar = class_getInstanceVariable([UIEvent class], "_eventEnvironment");
    id env = CMPSharedEventEnvironment();
    if (envIvar != NULL && env != nil) {
        object_setIvar(event, envIvar, env);
    }

    return event;
}

static UIPress *CMPMakePress(UIPressType pressType,
                             UIPressPhase phase,
                             UIWindow *window,
                             UIResponder *responder) {
    UIPress *press = [[UIPress alloc] init];
    if (press == nil) { return nil; }

    [press setPhase:phase];
    [press setType:pressType];
    [press setForce:phase == UIPressPhaseEnded ? 0.0 : 1.0];
    [press setWindow:window];
    [press setTimestamp:[[NSProcessInfo processInfo] systemUptime]];
    [press setClickCount:1];
    [press setModifierFlags:0];
    [press setGestureRecognizers:@[]];
    [press _setResponder:responder];
    [press _setSource:1];

    return press;
}

#pragma mark - Dispatch

static void CMPDispatchPresses(UIEvent *event,
                               UIPress *press,
                               UIPressPhase phase) {
    [press setPhase:phase];
    [press setForce:phase == UIPressPhaseEnded ? 0.0 : 1.0];
    [press setTimestamp:[[NSProcessInfo processInfo] systemUptime]];

    [[UIApplication sharedApplication] sendEvent:event];
}

#pragma mark - UIKey construction (for typing)

static Ivar CMPFindIvar(Class cls, NSArray<NSString *> *candidateNames) {
    Class current = cls;
    while (current != Nil) {
        for (NSString *name in candidateNames) {
            Ivar iv = class_getInstanceVariable(current, name.UTF8String);
            if (iv != NULL) { return iv; }
        }
        current = class_getSuperclass(current);
    }
    return NULL;
}

static void CMPSetObjectIvar(id obj, NSArray<NSString *> *names, id value) {
    Ivar iv = CMPFindIvar([obj class], names);
    if (iv != NULL) { object_setIvar(obj, iv, value); }
}

static void CMPSetIntegerIvar(id obj, NSArray<NSString *> *names, NSInteger value) {
    Ivar iv = CMPFindIvar([obj class], names);
    if (iv == NULL) { return; }
    uint8_t *base = (uint8_t *)(__bridge void *)obj;
    *(NSInteger *)(base + ivar_getOffset(iv)) = value;
}

static UIKey *CMPMakeUIKey(NSString *characters,
                           NSString *unmodifiedCharacters,
                           NSInteger keyCode,
                           NSInteger modifierFlags) {
    // UIKey has no public initializer — set its private ivars directly.
    Class cls = NSClassFromString(@"UIKey");
    if (cls == Nil) { return nil; }
    UIKey *key = [[cls alloc] init];
    if (key == nil) { return nil; }

    CMPSetObjectIvar(key, @[ @"_characters" ], characters);
    CMPSetObjectIvar(key,
                     @[ @"_charactersIgnoringModifiers", @"_unmodifiedCharacters" ],
                     unmodifiedCharacters ?: characters);
    CMPSetIntegerIvar(key, @[ @"_keyCode" ], keyCode);
    CMPSetIntegerIvar(key, @[ @"_modifierFlags" ], modifierFlags);

    return key;
}

static BOOL CMPHIDKeyCodeForModifier(UIKeyModifierFlags modifierKey,
                                     NSInteger *outKeyCode) {
    // Map a single modifier flag to the HID usage code of the left-side key.
    if (modifierKey == UIKeyModifierAlphaShift) { *outKeyCode = 57;  return YES; } // Caps Lock
    if (modifierKey == UIKeyModifierShift)      { *outKeyCode = 225; return YES; } // Left Shift
    if (modifierKey == UIKeyModifierControl)    { *outKeyCode = 224; return YES; } // Left Control
    if (modifierKey == UIKeyModifierAlternate)  { *outKeyCode = 226; return YES; } // Left Alt
    if (modifierKey == UIKeyModifierCommand)    { *outKeyCode = 227; return YES; } // Left GUI (Cmd)
    return NO;
}

static BOOL CMPHIDKeyCodeForCharacter(unichar c,
                                      NSInteger *outKeyCode,
                                      NSInteger *outModifierFlags,
                                      NSString **outUnmodifiedCharacters) {
    unichar lower = c;
    NSInteger modifierFlags = 0;
    if (c >= 'A' && c <= 'Z') {
        lower = (unichar)(c + ('a' - 'A'));
        modifierFlags = UIKeyModifierShift;
    }
    NSInteger keyCode = 0;
    if (lower >= 'a' && lower <= 'z') {
        keyCode = 4 + (lower - 'a');
    } else if (lower >= '1' && lower <= '9') {
        keyCode = 30 + (lower - '1');
    } else if (lower == '0') {
        keyCode = 39;
    } else if (lower == ' ') {
        keyCode = 44;
    } else {
        return NO;
    }
    if (outKeyCode != NULL) { *outKeyCode = keyCode; }
    if (outModifierFlags != NULL) { *outModifierFlags = modifierFlags; }
    if (outUnmodifiedCharacters != NULL) {
        *outUnmodifiedCharacters = [NSString stringWithCharacters:&lower length:1];
    }
    return YES;
}

static UIPress *CMPMakeKeyboardPress(NSString *characters,
                                     NSString *unmodifiedCharacters,
                                     NSInteger keyCode,
                                     NSInteger modifierFlags,
                                     UIWindow *window,
                                     UIResponder *responder) {
    UIPress *press = [[UIPress alloc] init];
    if (press == nil) { return nil; }

    [press setPhase:UIPressPhaseBegan];
    [press setType:(UIPressType)(2000 + keyCode)];
    [press setForce:1.0];
    [press setWindow:window];
    [press setTimestamp:[[NSProcessInfo processInfo] systemUptime]];
    [press setClickCount:1];
    [press setModifierFlags:(UIKeyModifierFlags)modifierFlags];
    [press setGestureRecognizers:@[]];
    [press _setResponder:responder];
    [press _setSource:1];

    UIKey *key = CMPMakeUIKey(characters, unmodifiedCharacters, keyCode, modifierFlags);
    if (key != nil && [press respondsToSelector:@selector(setKey:)]) {
        [press setKey:key];
    }

    return press;
}

@implementation UIPressesEvent (CMPPresses)

+ (nullable instancetype)pressesEventOfType:(UIPressType)pressType
                                   inWindow:(UIWindow *)window {
    UIResponder *target = CMPFindFirstResponder(window);

    UIPress *press = CMPMakePress(pressType, UIPressPhaseBegan, window, target);
    if (press == nil) { return nil; }

    UIPressesEvent *event = CMPMakePressesEvent(press);
    if (event == nil) { return nil; }

    CMPDispatchPresses(event, press, UIPressPhaseBegan);
    return event;
}

+ (nullable instancetype)keyboardPressEventForCharacter:(NSString *)character
                                          modifierFlags:(UIKeyModifierFlags)extraModifierFlags
                                               inWindow:(UIWindow *)window {
    if (character.length != 1) { return nil; }
    unichar c = [character characterAtIndex:0];
    NSInteger keyCode = 0;
    NSInteger modifierFlags = 0;
    NSString *unmodifiedCharacters = nil;
    if (!CMPHIDKeyCodeForCharacter(c, &keyCode, &modifierFlags, &unmodifiedCharacters)) {
        return nil;
    }
    modifierFlags |= (NSInteger)extraModifierFlags;

    UIResponder *target = CMPFindFirstResponder(window);
    UIPress *press = CMPMakeKeyboardPress(character, unmodifiedCharacters,
                                          keyCode, modifierFlags, window, target);
    if (press == nil) { return nil; }

    UIPressesEvent *event = CMPMakePressesEvent(press);
    if (event == nil) { return nil; }

    CMPDispatchPresses(event, press, UIPressPhaseBegan);

    // UIKit's hardware-key→text pipeline (`insertText:` on a focused
    // `UIKeyInput`) doesn't fire reliably for synthetic events. Drive that
    // final hop ourselves so a focused TextField actually receives the typed
    // character; the press dispatch above still runs the standard responder
    // hooks for tests that observe pressesBegan/Ended.
    // Skip when a command/control/alt modifier is set — those are keyboard
    // shortcuts (e.g. ⌘V), not text insertion.
    UIKeyModifierFlags shortcutModifiers =
        UIKeyModifierCommand | UIKeyModifierControl | UIKeyModifierAlternate;
    BOOL isShortcut = (extraModifierFlags & shortcutModifiers) != 0;
    if (!isShortcut && target != nil &&
        [target respondsToSelector:@selector(insertText:)]) {
        [(id<CMPUIKeyInput>)target insertText:character];
    }

    return event;
}

+ (nullable instancetype)keyboardPressEventForModifierKey:(UIKeyModifierFlags)modifierKey
                                         currentModifiers:(UIKeyModifierFlags)currentModifiers
                                                 inWindow:(UIWindow *)window {
    NSInteger keyCode = 0;
    if (!CMPHIDKeyCodeForModifier(modifierKey, &keyCode)) { return nil; }

    UIResponder *target = CMPFindFirstResponder(window);
    UIPress *press = CMPMakeKeyboardPress(@"", @"", keyCode, (NSInteger)(currentModifiers | modifierKey), window, target);
    if (press == nil) { return nil; }

    UIPressesEvent *event = CMPMakePressesEvent(press);
    if (event == nil) { return nil; }

    CMPDispatchPresses(event, press, UIPressPhaseBegan);
    return event;
}

- (void)pressChanged {
    UIPress *press = ((UIPressesEvent *)self).allPresses.anyObject;
    if (press == nil) { return; }
    CMPDispatchPresses(self, press, UIPressPhaseChanged);
}

- (void)endPress {
    UIPress *press = ((UIPressesEvent *)self).allPresses.anyObject;
    if (press == nil) { return; }
    CMPDispatchPresses(self, press, UIPressPhaseEnded);
}

- (void)cancelPress {
    UIPress *press = ((UIPressesEvent *)self).allPresses.anyObject;
    if (press == nil) { return; }
    CMPDispatchPresses(self, press, UIPressPhaseCancelled);
}

@end
