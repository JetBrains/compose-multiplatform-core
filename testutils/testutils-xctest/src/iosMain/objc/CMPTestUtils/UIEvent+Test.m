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

#import "UIEvent+Test.h"
#import <UIKit/UIKit.h>
#import <objc/runtime.h>
#import <objc/message.h>

// Private enums; values confirmed via swizzle traces on iPadOS 17+.
// Scroll and transform use different numbering — mixing them silently drops
// synthetic events at the gesture environment.
typedef NS_ENUM(NSInteger, CMPScrollPhase) {
    CMPScrollPhaseBegan   = 2,
    CMPScrollPhaseChanged = 3,
    CMPScrollPhaseEnded   = 4,
};

typedef NS_ENUM(NSInteger, CMPTransformPhase) {
    CMPTransformPhaseNone      = 0,
    CMPTransformPhaseBegan     = 1,
    CMPTransformPhaseChanged   = 2,
    CMPTransformPhaseEnded     = 3,
    CMPTransformPhaseCancelled = 4,
};

#pragma mark - Synthetic event state (associated objects)

static const void *kCMPSynPhaseKey = &kCMPSynPhaseKey;
static const void *kCMPSynLocationKey = &kCMPSynLocationKey;
static const void *kCMPSynDeltaKey = &kCMPSynDeltaKey;
static const void *kCMPSynWindowKey = &kCMPSynWindowKey;
static const void *kCMPSynScaleKey = &kCMPSynScaleKey;

static void CMPSynSetPhase(id evt, NSInteger phase) {
    objc_setAssociatedObject(evt, kCMPSynPhaseKey, @(phase),
                             OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

static CMPScrollPhase CMPSynGetPhase(id evt) {
    NSNumber *n = objc_getAssociatedObject(evt, kCMPSynPhaseKey);
    return n ? (CMPScrollPhase)[n integerValue] : 0;
}

static void CMPSynSetLocation(id evt, CGPoint location) {
    objc_setAssociatedObject(evt, kCMPSynLocationKey,
                             [NSValue valueWithBytes:&location objCType:@encode(CGPoint)],
                             OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

static CGPoint CMPSynGetLocation(id evt) {
    NSValue *v = objc_getAssociatedObject(evt, kCMPSynLocationKey);
    CGPoint p = CGPointZero;
    if (v) { [v getValue:&p size:sizeof(CGPoint)]; }
    return p;
}

static void CMPSynSetDelta(id evt, CGVector delta) {
    objc_setAssociatedObject(evt, kCMPSynDeltaKey,
                             [NSValue valueWithBytes:&delta objCType:@encode(CGVector)],
                             OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

static CGVector CMPSynGetDelta(id evt) {
    NSValue *v = objc_getAssociatedObject(evt, kCMPSynDeltaKey);
    CGVector d = {0, 0};
    if (v) { [v getValue:&d size:sizeof(CGVector)]; }
    return d;
}

static void CMPSynSetWindow(id evt, UIWindow *window) {
    objc_setAssociatedObject(evt, kCMPSynWindowKey, window, OBJC_ASSOCIATION_ASSIGN);
}

static UIWindow *CMPSynGetWindow(id evt) {
    return objc_getAssociatedObject(evt, kCMPSynWindowKey);
}

static void CMPSynSetScale(id evt, CGFloat scale) {
    objc_setAssociatedObject(evt, kCMPSynScaleKey, @(scale),
                             OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

static CGFloat CMPSynGetScale(id evt) {
    NSNumber *n = objc_getAssociatedObject(evt, kCMPSynScaleKey);
    return n ? (CGFloat)[n doubleValue] : 1.0;
}


@interface UIEvent (CMPSyntheticOverrides)

- (NSInteger)cmp_syntheticScrollType;
- (CMPScrollPhase)cmp_syntheticPhase;
- (CGPoint)cmp_syntheticLocationInView:(UIView *)view;
- (CGVector)cmp_syntheticDelta;
- (NSSet<UIWindow *> *)cmp_syntheticAllWindows;
- (NSSet<UIGestureRecognizer *> *)cmp_syntheticGestureRecognizersForWindow:(UIWindow *)window;

@end

static void CMPCollectRecognizers(UIView *view, NSMutableSet<UIGestureRecognizer *> *sink) {
    for (UIGestureRecognizer *g in view.gestureRecognizers) {
        [sink addObject:g];
    }
    for (UIView *sub in view.subviews) {
        CMPCollectRecognizers(sub, sink);
    }
}

@implementation UIEvent (CMPSyntheticOverrides)

- (NSInteger)cmp_syntheticScrollType {
    return UIEventTypeScroll;
}

- (CMPScrollPhase)cmp_syntheticPhase {
    return CMPSynGetPhase(self);
}

- (CGPoint)cmp_syntheticLocationInView:(UIView *)view {
    // Location is stored in window coordinates; convert to the target view's
    // coordinate space on request, matching UIKit's own contract.
    CGPoint location = CMPSynGetLocation(self);
    if (view == nil) { return location; }
    UIWindow *window = CMPSynGetWindow(self) ?: view.window;
    if (window == nil || window == view) { return location; }
    return [view convertPoint:location fromView:window];
}

- (CGVector)cmp_syntheticDelta {
    return CMPSynGetDelta(self);
}

- (NSSet<UIWindow *> *)cmp_syntheticAllWindows {
    UIWindow *w = CMPSynGetWindow(self);
    return w ? [NSSet setWithObject:w] : [NSSet set];
}

- (NSSet<UIGestureRecognizer *> *)cmp_syntheticGestureRecognizersForWindow:(UIWindow *)window {
    NSMutableSet<UIGestureRecognizer *> *set = [NSMutableSet set];
    UIWindow *w = window ?: CMPSynGetWindow(self);
    if (w != nil) { CMPCollectRecognizers(w, set); }
    return set;
}

@end

#pragma mark - UIEvent (CMPTransformSyntheticOverrides)

@interface UIEvent (CMPTransformSyntheticOverrides)

- (NSInteger)cmp_syntheticTransformType;
- (CMPTransformPhase)cmp_syntheticTransformPhase;
- (CGFloat)cmp_syntheticScale;

@end

@implementation UIEvent (CMPTransformSyntheticOverrides)

- (NSInteger)cmp_syntheticTransformType {
    return UIEventTypeTransform;
}

- (CMPTransformPhase)cmp_syntheticTransformPhase {
    return (CMPTransformPhase)CMPSynGetPhase(self);
}

- (CGFloat)cmp_syntheticScale {
    return CMPSynGetScale(self);
}

@end

#pragma mark - Runtime subclass registration

static void CMPInstallOverride(Class synCls, Class parent, SEL uikitSel, SEL srcSel) {
    Method parentMethod = class_getInstanceMethod(parent, uikitSel);
    if (parentMethod == NULL) { return; }
    Method srcMethod = class_getInstanceMethod([UIEvent class], srcSel);
    if (srcMethod == NULL) { return; }
    class_addMethod(synCls, uikitSel,
                    method_getImplementation(srcMethod),
                    method_getTypeEncoding(parentMethod));
}

static Class CMPSyntheticScrollEventClass(void) {
    static Class cls;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        Class parent = NSClassFromString(@"UIScrollEvent");
        if (parent == Nil) { return; }
        cls = objc_allocateClassPair(parent, "CMPSyntheticScrollEvent", 0);
        if (cls == Nil) { return; }

        CMPInstallOverride(cls, parent, @selector(type), @selector(cmp_syntheticScrollType));
        CMPInstallOverride(cls, parent, NSSelectorFromString(@"phase"), @selector(cmp_syntheticPhase));
        CMPInstallOverride(cls, parent, NSSelectorFromString(@"locationInView:"), @selector(cmp_syntheticLocationInView:));
        CMPInstallOverride(cls, parent, NSSelectorFromString(@"acceleratedDelta"), @selector(cmp_syntheticDelta));
        CMPInstallOverride(cls, parent, NSSelectorFromString(@"_allWindows"), @selector(cmp_syntheticAllWindows));
        CMPInstallOverride(cls, parent, NSSelectorFromString(@"_gestureRecognizersForWindow:"), @selector(cmp_syntheticGestureRecognizersForWindow:));

        objc_registerClassPair(cls);
    });
    return cls;
}

static Class CMPSyntheticTransformEventClass(void) {
    static Class cls;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        Class parent = NSClassFromString(@"UITransformEvent");
        if (parent == Nil) { return; }
        cls = objc_allocateClassPair(parent, "CMPSyntheticTransformEvent", 0);
        if (cls == Nil) { return; }

        CMPInstallOverride(cls, parent, @selector(type), @selector(cmp_syntheticTransformType));
        CMPInstallOverride(cls, parent, NSSelectorFromString(@"phase"), @selector(cmp_syntheticTransformPhase));
        CMPInstallOverride(cls, parent, NSSelectorFromString(@"scale"), @selector(cmp_syntheticScale));

        objc_registerClassPair(cls);
    });
    return cls;
}

// Hover dispatch drives the recognizer via `shouldReceiveEvent:` directly and
// skips `-[UIApplication sendEvent:]`, so the synthetic subclass needs no
// overrides — it exists only to carry associated-object state on an instance
// that passes `isKindOfClass:[UIHoverEvent class]` checks.
static Class CMPSyntheticHoverEventClass(void) {
    static Class cls;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        Class parent = NSClassFromString(@"UIHoverEvent");
        if (parent == Nil) { return; }
        cls = objc_allocateClassPair(parent, "CMPSyntheticHoverEvent", 0);
        if (cls == Nil) { return; }

        objc_registerClassPair(cls);
    });
    return cls;
}

#pragma mark - Target-action forcing

// UIKit's deferred action cycle only runs for events delivered through the
// hardware HID pipeline. `sendEvent:` on a synthetic event drives the
// recognizer's state machine (state transitions to Began/Changed/Ended) but
// skips the subsequent "invoke target-action" pass — so we reach into the
// recognizer's private `_targets` array and fire each `(target, action)` pair
// ourselves, with the same shape UIKit's own dispatcher would produce.
static void CMPForceRecognizerActions(NSSet<UIGestureRecognizer *> *recognizers, Class cls) {
    Ivar targetsIvar = class_getInstanceVariable([UIGestureRecognizer class], "_targets");
    if (targetsIvar == NULL) { return; }
    for (UIGestureRecognizer *recognizer in recognizers) {
        if (![recognizer isKindOfClass:cls]) { continue; }
        UIGestureRecognizerState state = recognizer.state;
        if (state != UIGestureRecognizerStateBegan &&
            state != UIGestureRecognizerStateChanged &&
            state != UIGestureRecognizerStateEnded &&
            state != UIGestureRecognizerStateCancelled) {
            continue;
        }
        id targets = object_getIvar(recognizer, targetsIvar);
        if (![targets isKindOfClass:[NSArray class]]) { continue; }
        for (id pair in (NSArray *)targets) {
            Ivar targetIvar = class_getInstanceVariable([pair class], "_target");
            Ivar actionIvar = class_getInstanceVariable([pair class], "_action");
            if (targetIvar == NULL || actionIvar == NULL) { continue; }
            id tgt = object_getIvar(pair, targetIvar);
            // `_action` holds a SEL, which `object_getIvar` can't return; read raw.
            SEL act = *(SEL *)((uint8_t *)(__bridge void *)pair + ivar_getOffset(actionIvar));
            if (tgt != nil && act != NULL) {
                ((void(*)(id, SEL, id))objc_msgSend)(tgt, act, recognizer);
            }
        }
    }
}

#pragma mark - Direct pinch recognizer driver

// UIKit's gesture environment needs a real HID event to transition pinch
// recognizers into Began/Changed — synthetic transform events reach the
// environment but state never moves. Instead of trying to fake deeper into
// the pipeline, we drive the recognizer directly: force its state via
// private `-setState:`, write `scale` (public API), and let
// `CMPForceRecognizerActions` fire the bound target-actions.
// `UIScrollViewPinchGestureRecognizer`'s action reads `scale` and mutates
// `zoomScale`, so this path delivers a real zoom.
static void CMPDrivePinchRecognizers(NSSet<UIGestureRecognizer *> *recognizers,
                                     CGFloat absoluteScale,
                                     CMPTransformPhase phase) {
    UIGestureRecognizerState targetState;
    switch (phase) {
        case CMPTransformPhaseBegan:     targetState = UIGestureRecognizerStateBegan; break;
        case CMPTransformPhaseChanged:   targetState = UIGestureRecognizerStateChanged; break;
        case CMPTransformPhaseEnded:     targetState = UIGestureRecognizerStateEnded; break;
        case CMPTransformPhaseCancelled: targetState = UIGestureRecognizerStateCancelled; break;
        default: return;
    }
    SEL setStateSel = NSSelectorFromString(@"setState:");
    for (UIGestureRecognizer *recognizer in recognizers) {
        if (![recognizer isKindOfClass:[UIPinchGestureRecognizer class]]) { continue; }
        UIPinchGestureRecognizer *pinch = (UIPinchGestureRecognizer *)recognizer;

        // `-setState:Began` resets internal `_scale` to 1.0, so write `scale`
        // after the transition to avoid having the reset clobber our value.
        ((void(*)(id, SEL, NSInteger))objc_msgSend)(pinch, setStateSel, targetState);
        pinch.scale = absoluteScale;
    }
    CMPForceRecognizerActions(recognizers, [UIPinchGestureRecognizer class]);
}

#pragma mark - UIEvent

@implementation UIEvent (CMPScrollDispatch)

+ (void)dispatchScrollOnEvent:(UIEvent *)event
                     atAnchor:(CGPoint)anchor
                        delta:(CGVector)delta
                        phase:(CMPScrollPhase)phase
                     inWindow:(UIWindow *)window {
    CMPSynSetLocation(event, anchor);
    CMPSynSetDelta(event, delta);
    CMPSynSetPhase(event, phase);
    if (window != nil) { CMPSynSetWindow(event, window); }

    NSSet<UIGestureRecognizer *> *recognizers = [event cmp_syntheticGestureRecognizersForWindow:window];

    [[UIApplication sharedApplication] sendEvent:event];

    CMPForceRecognizerActions(recognizers, [UIPanGestureRecognizer class]);
}

+ (void)dispatchTransformOnEvent:(UIEvent *)event
                        atAnchor:(CGPoint)anchor
                           scale:(CGFloat)scale
                           phase:(CMPTransformPhase)phase
                        inWindow:(UIWindow *)window {
    CMPSynSetLocation(event, anchor);
    CMPSynSetScale(event, scale);
    CMPSynSetPhase(event, phase);
    if (window != nil) { CMPSynSetWindow(event, window); }

    NSSet<UIGestureRecognizer *> *recognizers = [event cmp_syntheticGestureRecognizersForWindow:window];

    // DO NOT route through `sendEvent:`. UIKit's transform dispatch reads
    // zeroed private ivars (`_dispatchWindows`, `_deliveryTableByTouch`, …)
    // as object pointers and retains them unconditionally, crashing on iOS 26.
    // We drive the pinch recognizer directly below, so UIKit's routing is
    // unnecessary anyway.
    CMPDrivePinchRecognizers(recognizers, scale, phase);
}

+ (void)dispatchHoverOnEvent:(UIEvent *)event
                    atAnchor:(CGPoint)anchor
                    inWindow:(UIWindow *)window {
    CMPSynSetLocation(event, anchor);
    if (window != nil) { CMPSynSetWindow(event, window); }

    NSSet<UIGestureRecognizer *> *recognizers = [event cmp_syntheticGestureRecognizersForWindow:window];

    // Skip `-[UIApplication sendEvent:]` entirely — UIKit's hover path
    // dereferences private touch-bookkeeping ivars we can't safely populate.
    // `shouldReceiveEvent:` only needs the event, so invoke it directly as
    // the delivery entry point.
    SEL shouldReceiveEventSel = @selector(shouldReceiveEvent:);
    for (UIGestureRecognizer *recognizer in recognizers) {
        if (![recognizer isKindOfClass:[UIHoverGestureRecognizer class]]) { continue; }
        ((BOOL(*)(id, SEL, id))objc_msgSend)(recognizer, shouldReceiveEventSel, event);
    }

    CMPForceRecognizerActions(recognizers, [UIHoverGestureRecognizer class]);
}

+ (void)forcePanRecognizerActionsIn:(NSSet<UIGestureRecognizer *> *)recognizers {
    CMPForceRecognizerActions(recognizers, [UIPanGestureRecognizer class]);
}

@end

@implementation UIEvent (CMPScroll)

+ (nullable instancetype)scrollEventAtPoint:(CGPoint)point
                                      delta:(CGPoint)delta
                                   inWindow:(UIWindow *)window {
    Class cls = CMPSyntheticScrollEventClass();
    if (cls == Nil) { return nil; }
    id scrollEvent = [[cls alloc] init];
    if (scrollEvent == nil) { return nil; }

    CMPSynSetWindow(scrollEvent, window);
    [UIEvent dispatchScrollOnEvent:scrollEvent
                          atAnchor:point
                             delta:CGVectorMake(delta.x, delta.y)
                             phase:CMPScrollPhaseBegan
                          inWindow:window];
    return (UIEvent *)scrollEvent;
}

- (void)scrollByDelta:(CGPoint)delta inWindow:(UIWindow *)window {
    [UIEvent dispatchScrollOnEvent:self
                          atAnchor:CMPSynGetLocation(self)
                             delta:CGVectorMake(delta.x, delta.y)
                             phase:CMPScrollPhaseChanged
                          inWindow:window];
}

- (void)endInWindow:(UIWindow *)window {
    [UIEvent dispatchScrollOnEvent:self
                          atAnchor:CMPSynGetLocation(self)
                             delta:CGVectorMake(0, 0)
                             phase:CMPScrollPhaseEnded
                          inWindow:window];
}

@end

@implementation UIEvent (CMPPinch)

+ (nullable instancetype)pinchEventAtPoint:(CGPoint)point
                                     scale:(CGFloat)scale
                                  inWindow:(UIWindow *)window {
    Class cls = CMPSyntheticTransformEventClass();
    if (cls == Nil) { return nil; }
    id transformEvent = [[cls alloc] init];
    if (transformEvent == nil) { return nil; }

    CMPSynSetWindow(transformEvent, window);
    [UIEvent dispatchTransformOnEvent:transformEvent
                             atAnchor:point
                                scale:scale
                                phase:CMPTransformPhaseBegan
                             inWindow:window];
    return (UIEvent *)transformEvent;
}

- (void)pinchByScale:(CGFloat)scale inWindow:(UIWindow *)window {
    [UIEvent dispatchTransformOnEvent:self
                             atAnchor:CMPSynGetLocation(self)
                                scale:scale
                                phase:CMPTransformPhaseChanged
                             inWindow:window];
}

- (void)endPinchInWindow:(UIWindow *)window {
    [UIEvent dispatchTransformOnEvent:self
                             atAnchor:CMPSynGetLocation(self)
                                scale:CMPSynGetScale(self)
                                phase:CMPTransformPhaseEnded
                             inWindow:window];
}

@end

@implementation UIEvent (CMPHover)

+ (nullable instancetype)hoverEventAtPoint:(CGPoint)point
                                  inWindow:(UIWindow *)window {
    Class cls = CMPSyntheticHoverEventClass();
    if (cls == Nil) { return nil; }
    id hoverEvent = [[cls alloc] init];
    if (hoverEvent == nil) { return nil; }

    CMPSynSetWindow(hoverEvent, window);
    [UIEvent dispatchHoverOnEvent:hoverEvent
                         atAnchor:point
                         inWindow:window];
    return (UIEvent *)hoverEvent;
}

- (void)hoverMoveToPoint:(CGPoint)point inWindow:(UIWindow *)window {
    [UIEvent dispatchHoverOnEvent:self
                         atAnchor:point
                         inWindow:window];
}

- (void)endHoverInWindow:(UIWindow *)window {
    [UIEvent dispatchHoverOnEvent:self
                         atAnchor:CMPSynGetLocation(self)
                         inWindow:window];
}

@end

#pragma mark - UIEvent (CMPSyntheticLocation) — Public API

@implementation UIEvent (CMPSyntheticLocation)

- (CGPoint)cmp_locationInView:(UIView *)view {
    return [self cmp_syntheticLocationInView:view];
}

@end
