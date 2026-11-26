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

#import "CMPWindowPostureUtils.h"

@interface CMPWindowHingeInfo ()

- (instancetype)initWithX:(CGFloat)x
                        y:(CGFloat)y
                    width:(CGFloat)width
                   height:(CGFloat)height
                   isFlat:(BOOL)isFlat
                isVertical:(BOOL)isVertical
              isSeparating:(BOOL)isSeparating
               isOccluding:(BOOL)isOccluding NS_DESIGNATED_INITIALIZER;

@end

@implementation CMPWindowHingeInfo {
    CGFloat _x;
    CGFloat _y;
    CGFloat _width;
    CGFloat _height;
    BOOL _isFlat;
    BOOL _isVertical;
    BOOL _isSeparating;
    BOOL _isOccluding;
}

- (instancetype)initWithX:(CGFloat)x
                        y:(CGFloat)y
                    width:(CGFloat)width
                   height:(CGFloat)height
                   isFlat:(BOOL)isFlat
                isVertical:(BOOL)isVertical
              isSeparating:(BOOL)isSeparating
               isOccluding:(BOOL)isOccluding {
    self = [super init];
    if (self == nil) {
        return nil;
    }

    _x = x;
    _y = y;
    _width = width;
    _height = height;
    _isFlat = isFlat;
    _isVertical = isVertical;
    _isSeparating = isSeparating;
    _isOccluding = isOccluding;
    return self;
}

@end

@interface CMPWindowPostureObserver ()

@property (nonatomic, weak, readonly) UIView *view;
@property (nonatomic, copy, readonly) CMPWindowPostureUpdateHandler onUpdate;
@property (nonatomic, nullable) UIHingeInteraction *interaction API_AVAILABLE(ios(27.1));
@property (nonatomic, nullable) UIHinge *lastHinge API_AVAILABLE(ios(27.1));
@property (nonatomic, assign) BOOL isStarted;
@property (nonatomic, assign) BOOL isObservingGeometry;
@property (nonatomic, nullable) UIWindowScene *windowScene;
@property (nonatomic, assign) BOOL isObservingSceneGeometry;

@end

@implementation CMPWindowPostureObserver

- (instancetype)initWithView:(UIView *)view
                     onUpdate:(CMPWindowPostureUpdateHandler)onUpdate {
    self = [super init];
    if (self == nil) {
        return nil;
    }

    _view = view;
    _onUpdate = [onUpdate copy];
    return self;
}

- (void)start {
    if (self.isStarted) {
        return;
    }
    self.isStarted = YES;

    if (@available(iOS 27.1, *)) {
        [self.view addObserver:self
                     forKeyPath:@"bounds"
                        options:0
                        context:nil];
        self.isObservingGeometry = YES;

        self.windowScene = self.view.window.windowScene;
        [self.windowScene addObserver:self
                         forKeyPath:@"effectiveGeometry"
                            options:0
                            context:nil];
        self.isObservingSceneGeometry = YES;

        __weak typeof(self) weakSelf = self;
        UIHingeInteraction *interaction = [[UIHingeInteraction alloc]
            initWithUpdateHandler:^(UIHingeInteraction *interaction,
                                    UIHingeInteractionUpdate *update) {
                __strong typeof(self) strongSelf = weakSelf;
                strongSelf.lastHinge = update.hinge;
                [strongSelf handleHinge:update.hinge];
            }];
        self.interaction = interaction;
        [self.view addInteraction:interaction];
    } else {
        self.onUpdate(@[]);
    }
}

- (void)stop {
    if (!self.isStarted) {
        return;
    }

    if (@available(iOS 27.1, *)) {
        if (self.isObservingGeometry) {
            [self.view removeObserver:self forKeyPath:@"bounds"];
            self.isObservingGeometry = NO;
        }
        if (self.isObservingSceneGeometry) {
            [self.windowScene removeObserver:self forKeyPath:@"effectiveGeometry"];
            self.isObservingSceneGeometry = NO;
        }
        self.windowScene = nil;
        [self.view removeInteraction:self.interaction];
        self.interaction = nil;
        self.lastHinge = nil;
    }
    self.isStarted = NO;
}

- (void)observeValueForKeyPath:(NSString *)keyPath
                       ofObject:(id)object
                         change:(NSDictionary<NSKeyValueChangeKey, id> *)change
                        context:(void *)context {
    if (![keyPath isEqualToString:@"bounds"] &&
        ![keyPath isEqualToString:@"effectiveGeometry"]) {
        return;
    }

    if (@available(iOS 27.1, *)) {
        [self handleHinge:self.lastHinge];
    }
}

- (void)handleHinge:(UIHinge *)hinge API_AVAILABLE(ios(27.1)) {
    if (hinge == nil) {
        self.onUpdate(@[]);
        return;
    }

    UIView *view = self.view;
    if (view == nil) {
        self.onUpdate(@[]);
        return;
    }

    if (hinge.status != UIHingeStatusPartiallyOpen &&
        hinge.status != UIHingeStatusFullyOpen) {
        self.onUpdate(@[]);
        return;
    }

    // A division region may be inactive when the hinge is fully open, but its geometry still
    // represents the physical fold. Android also reports a flat FoldingFeature with its bounds.
    BOOL isFlat = hinge.status == UIHingeStatusFullyOpen;
    // Android treats a continuous fold as separating only while it is not flat.
    BOOL isSeparating = hinge.status == UIHingeStatusPartiallyOpen;
    NSArray<UIViewReservedRegion *> *divisionRegions =
        [view reservedRegionsOfKind:[UIViewReservedRegionKind divisionRegionKind]
                         options:UIViewReservedRegionQueryOptionsIncludeInactive];
    NSArray<UIViewReservedRegion *> *occlusionRegions =
        [view reservedRegionsOfKind:[UIViewReservedRegionKind occlusionRegionKind]
                         options:UIViewReservedRegionQueryOptionsNone];
    NSMutableArray<CMPWindowHingeInfo *> *hinges = [NSMutableArray array];
    for (UIViewReservedRegion *region in divisionRegions) {
        CGRect frame = region.frame;
        UIEdgeInsets margins = region.margins;
        CGFloat x = frame.origin.x + margins.left;
        CGFloat y = frame.origin.y + margins.top;
        CGFloat width = frame.size.width - margins.left - margins.right;
        CGFloat height = frame.size.height - margins.top - margins.bottom;
        if (width < 0.0) {
            width = 0.0;
        }
        if (height < 0.0) {
            height = 0.0;
        }

        CGRect hingeRect = CGRectMake(x, y, width, height);
        BOOL isOccluding = NO;
        for (UIViewReservedRegion *occlusionRegion in occlusionRegions) {
            if (CMPRectsIntersectInclusive(hingeRect, occlusionRegion.frame)) {
                isOccluding = YES;
                break;
            }
        }

        CMPWindowHingeInfo *info = [[CMPWindowHingeInfo alloc]
            initWithX:x
                     y:y
                 width:width
                height:height
                isFlat:isFlat
             isVertical:width <= height
           isSeparating:isSeparating
            isOccluding:isOccluding];
        [hinges addObject:info];
    }

    self.onUpdate(hinges);
}

static BOOL CMPRectsIntersectInclusive(CGRect lhs, CGRect rhs) {
    return lhs.origin.x <= CGRectGetMaxX(rhs) && CGRectGetMaxX(lhs) >= rhs.origin.x &&
        lhs.origin.y <= CGRectGetMaxY(rhs) && CGRectGetMaxY(lhs) >= rhs.origin.y;
}

@end
