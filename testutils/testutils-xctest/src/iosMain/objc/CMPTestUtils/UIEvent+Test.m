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

typedef NS_ENUM(NSInteger, CMPHoverPhase) {
    CMPHoverPhaseBegan   = 1,
    CMPHoverPhaseChanged = 2,
    CMPHoverPhaseEnded   = 3,
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

// Hover dispatch drives the recognizer directly (state + location pins, then
// force-fired target-actions) and never routes through `-[UIApplication
// sendEvent:]`, so the synthetic subclass needs no overrides — it just
// provides a UIEvent-typed instance that can hold associated-object state.
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

#pragma mark - State override (UIGestureRecognizer)

// `UIGestureRecognizer.state` is cached outside the recognizer (in
// UIGestureEnvironment / gesture-graph nodes) and rejects backwards
// transitions via `setState:`, so a synthetic gesture session that ends in
// .ended cannot be reopened: the next .began dispatch leaves state stuck at
// .ended and target-actions observe the wrong phase. Swizzle the getter so we
// can pin the value the action handler reads via an associated object.

static const void *kCMPGRStateOverrideKey = &kCMPGRStateOverrideKey;
static IMP gOriginalGRStateImp = NULL;

static UIGestureRecognizerState CMPSwizzledState(id self, SEL _cmd) {
    NSNumber *override = objc_getAssociatedObject(self, kCMPGRStateOverrideKey);
    if (override != nil) {
        return (UIGestureRecognizerState)[override integerValue];
    }
    return ((UIGestureRecognizerState(*)(id, SEL))gOriginalGRStateImp)(self, _cmd);
}

static void CMPInstallStateOverrideOnce(void) {
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        Method m = class_getInstanceMethod([UIGestureRecognizer class], @selector(state));
        if (m == NULL) { return; }
        gOriginalGRStateImp = method_getImplementation(m);
        method_setImplementation(m, (IMP)CMPSwizzledState);
        // UIHoverGestureRecognizer (and possibly other subclasses) have their
        // own `-state` IMP that bypasses the base class swizzle, so install the
        // override directly on the subclass too. We use the same swizzle
        // function — when its associated-object check finds nothing it falls
        // back to `gOriginalGRStateImp`, which is the base-class IMP. That's a
        // fine approximation: if a subclass overrides `state`, its override
        // typically just reads the same underlying storage.
        Class hoverCls = NSClassFromString(@"UIHoverGestureRecognizer");
        if (hoverCls != Nil) {
            Method hm = class_getInstanceMethod(hoverCls, @selector(state));
            const char *typeEncoding = hm ? method_getTypeEncoding(hm) :
                                            method_getTypeEncoding(m);
            BOOL added = class_addMethod(hoverCls, @selector(state),
                                         (IMP)CMPSwizzledState, typeEncoding);
            if (!added && hm != NULL) {
                method_setImplementation(hm, (IMP)CMPSwizzledState);
            }
        }
    });
}

#pragma mark - Location override (UIGestureRecognizer)

// Synthetic hover/pinch dispatch skips `-[UIApplication sendEvent:]`, so UIKit
// never populates the recognizer's internal `_locationInWindow`. Without this,
// `recognizer.location(in: view)` from the target-action handler reports stale
// or zero data. Pin a per-recognizer window-coordinate point via an associated
// object and swizzle the recognizer class's `locationInView:` to convert that
// point into the requested view's coordinate space.

static const void *kCMPGRLocationOverrideKey = &kCMPGRLocationOverrideKey;
static IMP gOriginalHoverLocationInViewImp = NULL;
static IMP gOriginalPinchLocationInViewImp = NULL;

static CGPoint CMPSwizzledLocationInViewImpl(id self, SEL _cmd, UIView *view, IMP original) {
    NSValue *override = objc_getAssociatedObject(self, kCMPGRLocationOverrideKey);
    if (override != nil) {
        CGPoint windowPoint = CGPointZero;
        [override getValue:&windowPoint size:sizeof(CGPoint)];
        UIView *recognizerView = [(UIGestureRecognizer *)self view];
        UIWindow *window = recognizerView.window;
        if (view == nil || window == nil || view == window) { return windowPoint; }
        return [view convertPoint:windowPoint fromView:window];
    }
    if (original != NULL) {
        return ((CGPoint(*)(id, SEL, UIView *))original)(self, _cmd, view);
    }
    return CGPointZero;
}

static CGPoint CMPSwizzledHoverLocationInView(id self, SEL _cmd, UIView *view) {
    return CMPSwizzledLocationInViewImpl(self, _cmd, view, gOriginalHoverLocationInViewImp);
}

static CGPoint CMPSwizzledPinchLocationInView(id self, SEL _cmd, UIView *view) {
    return CMPSwizzledLocationInViewImpl(self, _cmd, view, gOriginalPinchLocationInViewImp);
}

static void CMPInstallLocationOverrideOnClass(Class cls, IMP swizzled, IMP *outOriginal) {
    if (cls == Nil) { return; }
    SEL sel = @selector(locationInView:);
    Method m = class_getInstanceMethod(cls, sel);
    if (m == NULL) { return; }
    *outOriginal = method_getImplementation(m);
    const char *typeEncoding = method_getTypeEncoding(m);
    // If `locationInView:` is inherited from UIGestureRecognizer, add a direct
    // override on the subclass instead of swizzling the base class — keeps
    // blast radius limited to recognizers we synthesize.
    BOOL added = class_addMethod(cls, sel, swizzled, typeEncoding);
    if (!added) {
        method_setImplementation(m, swizzled);
    }
}

static void CMPInstallHoverLocationOverrideOnce(void) {
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        CMPInstallLocationOverrideOnClass(NSClassFromString(@"UIHoverGestureRecognizer"),
                                          (IMP)CMPSwizzledHoverLocationInView,
                                          &gOriginalHoverLocationInViewImp);
    });
}

static void CMPInstallPinchLocationOverrideOnce(void) {
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        CMPInstallLocationOverrideOnClass([UIPinchGestureRecognizer class],
                                          (IMP)CMPSwizzledPinchLocationInView,
                                          &gOriginalPinchLocationInViewImp);
    });
}

#pragma mark - Direct pinch recognizer driver

// UIKit's gesture environment needs a real HID event to transition pinch
// recognizers into Began/Changed — synthetic transform events reach the
// environment but state never moves. Pin the value the target-action handler
// reads via the state-override swizzle, write `scale` (public API), and let
// `CMPForceRecognizerActions` fire the bound target-actions. `setState:` would
// double-fire (UIKit fires the action on the transition AND the force-fire
// fires it again) and would reject backwards transitions, blocking a second
// pinch session after the first ends.
static void CMPDrivePinchRecognizers(NSSet<UIGestureRecognizer *> *recognizers,
                                     CGFloat absoluteScale,
                                     CGPoint anchorInWindow,
                                     CMPTransformPhase phase) {
    UIGestureRecognizerState targetState;
    switch (phase) {
        case CMPTransformPhaseBegan:     targetState = UIGestureRecognizerStateBegan; break;
        case CMPTransformPhaseChanged:   targetState = UIGestureRecognizerStateChanged; break;
        case CMPTransformPhaseEnded:     targetState = UIGestureRecognizerStateEnded; break;
        case CMPTransformPhaseCancelled: targetState = UIGestureRecognizerStateCancelled; break;
        default: return;
    }
    NSValue *anchorBox = [NSValue valueWithBytes:&anchorInWindow objCType:@encode(CGPoint)];
    for (UIGestureRecognizer *recognizer in recognizers) {
        if (![recognizer isKindOfClass:[UIPinchGestureRecognizer class]]) { continue; }
        UIPinchGestureRecognizer *pinch = (UIPinchGestureRecognizer *)recognizer;
        objc_setAssociatedObject(pinch, kCMPGRStateOverrideKey, @(targetState),
                                 OBJC_ASSOCIATION_RETAIN_NONATOMIC);
        // Pin the centroid (in window coords) so `recognizer.location(in: view)`
        // reports the synthetic anchor — UIKit doesn't populate it for us when
        // we skip `sendEvent:`.
        objc_setAssociatedObject(pinch, kCMPGRLocationOverrideKey, anchorBox,
                                 OBJC_ASSOCIATION_RETAIN_NONATOMIC);
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
    CMPInstallStateOverrideOnce();

    CMPSynSetLocation(event, anchor);
    CMPSynSetDelta(event, delta);
    CMPSynSetPhase(event, phase);
    if (window != nil) { CMPSynSetWindow(event, window); }

    NSSet<UIGestureRecognizer *> *recognizers = [event cmp_syntheticGestureRecognizersForWindow:window];

    UIGestureRecognizerState targetState;
    switch (phase) {
        case CMPScrollPhaseBegan:   targetState = UIGestureRecognizerStateBegan;   break;
        case CMPScrollPhaseChanged: targetState = UIGestureRecognizerStateChanged; break;
        case CMPScrollPhaseEnded:   targetState = UIGestureRecognizerStateEnded;   break;
        default:                    targetState = UIGestureRecognizerStatePossible; break;
    }

    // Pin the synthetic state so target-actions observe the right phase even
    // when UIKit's state cache disagrees (e.g. the first dispatch of a second
    // session — the recognizer is still .ended from the previous one).
    for (UIGestureRecognizer *r in recognizers) {
        if (![r isKindOfClass:[UIPanGestureRecognizer class]]) { continue; }
        objc_setAssociatedObject(r, kCMPGRStateOverrideKey, @(targetState),
                                 OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    }

    [[UIApplication sharedApplication] sendEvent:event];

    CMPForceRecognizerActions(recognizers, [UIPanGestureRecognizer class]);
}

+ (void)dispatchTransformOnEvent:(UIEvent *)event
                        atAnchor:(CGPoint)anchor
                           scale:(CGFloat)scale
                           phase:(CMPTransformPhase)phase
                        inWindow:(UIWindow *)window {
    CMPInstallStateOverrideOnce();
    CMPInstallPinchLocationOverrideOnce();

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
    CMPDrivePinchRecognizers(recognizers, scale, anchor, phase);
}

+ (void)dispatchHoverOnEvent:(UIEvent *)event
                    atAnchor:(CGPoint)anchor
                       phase:(CMPHoverPhase)phase
                    inWindow:(UIWindow *)window {
    CMPInstallStateOverrideOnce();
    CMPInstallHoverLocationOverrideOnce();

    CMPSynSetLocation(event, anchor);
    if (window != nil) { CMPSynSetWindow(event, window); }

    UIGestureRecognizerState targetState;
    switch (phase) {
        case CMPHoverPhaseBegan:   targetState = UIGestureRecognizerStateBegan;   break;
        case CMPHoverPhaseChanged: targetState = UIGestureRecognizerStateChanged; break;
        case CMPHoverPhaseEnded:   targetState = UIGestureRecognizerStateEnded;   break;
    }

    // Skip `-[UIApplication sendEvent:]` — UIKit's hover path dereferences
    // private touch-bookkeeping ivars we can't safely populate. Pin synthetic
    // state + location on each candidate recognizer, then force-fire its
    // target-actions. We also forward the event through `shouldReceiveEvent:`
    // so subclasses that override it (e.g. CMPHoverGestureRecognizer caching
    // `lastReceivedEvent`) observe the synthetic event.
    NSSet<UIGestureRecognizer *> *recognizers = [event cmp_syntheticGestureRecognizersForWindow:window];
    NSNumber *boxedState = @(targetState);
    NSValue *boxedAnchor = [NSValue valueWithBytes:&anchor objCType:@encode(CGPoint)];
    SEL shouldReceiveEventSel = @selector(shouldReceiveEvent:);
    for (UIGestureRecognizer *recognizer in recognizers) {
        if (![recognizer isKindOfClass:[UIHoverGestureRecognizer class]]) { continue; }
        ((BOOL(*)(id, SEL, id))objc_msgSend)(recognizer, shouldReceiveEventSel, event);
        objc_setAssociatedObject(recognizer, kCMPGRStateOverrideKey, boxedState,
                                 OBJC_ASSOCIATION_RETAIN_NONATOMIC);
        objc_setAssociatedObject(recognizer, kCMPGRLocationOverrideKey, boxedAnchor,
                                 OBJC_ASSOCIATION_RETAIN_NONATOMIC);
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
                             delta:CGVectorMake(-delta.x, -delta.y)
                             phase:CMPScrollPhaseBegan
                          inWindow:window];
    return (UIEvent *)scrollEvent;
}

- (void)scrollByDelta:(CGPoint)delta inWindow:(UIWindow *)window {
    [UIEvent dispatchScrollOnEvent:self
                          atAnchor:CMPSynGetLocation(self)
                             delta:CGVectorMake(-delta.x, -delta.y)
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
                            phase:CMPHoverPhaseBegan
                         inWindow:window];
    return (UIEvent *)hoverEvent;
}

- (void)hoverMoveToPoint:(CGPoint)point inWindow:(UIWindow *)window {
    [UIEvent dispatchHoverOnEvent:self
                         atAnchor:point
                            phase:CMPHoverPhaseChanged
                         inWindow:window];
}

- (void)endHoverInWindow:(UIWindow *)window {
    [UIEvent dispatchHoverOnEvent:self
                         atAnchor:CMPSynGetLocation(self)
                            phase:CMPHoverPhaseEnded
                         inWindow:window];
}

@end
