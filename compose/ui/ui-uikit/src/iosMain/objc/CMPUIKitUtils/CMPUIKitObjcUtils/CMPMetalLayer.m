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

#import "CMPMetalLayer.h"
#import "CMPDrawable.h"
#import <IOSurface/IOSurfaceRef.h>
#import <CoreVideo/CoreVideo.h>
#import <UIKit/UIKit.h>

static const NSUInteger kMaxDrawables = 3;
static const NSTimeInterval kDrawableAcquisitionTimeout = 1.0;
static const NSUInteger kBGRA32ColorFormatBytesPerPixel = 4;

@implementation CMPMetalLayer {
    NSCondition *_drawablesCondition;
    NSMutableSet<CMPDrawable *> *_availableDrawables;
    NSUInteger _totalDrawables;
    CMPDrawable *_lastPresentedDrawable;
    CGSize _drawableSize;
    CFTimeInterval _lastDrawablePresentedTime;
    BOOL _areDrawableAcquisitionsCancelled;
}

- (instancetype)init {
    self = [super init];
    if (self) {
        _device = MTLCreateSystemDefaultDevice();
        _drawablesCondition = [[NSCondition alloc] init];
        _availableDrawables = [NSMutableSet set];
        _totalDrawables = 0;
        _lastPresentedDrawable = nil;
        _drawableSize = CGSizeZero;
        _drawablesGeneration = 0;
        _lastDrawablePresentedTime = 0;
        _areDrawableAcquisitionsCancelled = NO;
    }
    return self;
}

- (void)setDrawableSize:(CGSize)size {
    NSAssert([NSThread isMainThread], @"setDrawableSize - must be called on main thread");
    if (!CGSizeEqualToSize(_drawableSize, size)) {
        [_drawablesCondition lock];
        _drawableSize = size;
        [self drainDrawablesUnsafe];
        [_drawablesCondition broadcast];
        [_drawablesCondition unlock];
    }
}

- (CGSize)drawableSize {
    NSAssert([NSThread isMainThread], @"getDrawableSize - must be called on main thread");
    return _drawableSize;
}

- (IOSurfaceRef)IOSurface {
    NSInteger width = (NSInteger)_drawableSize.width;
    NSInteger height = (NSInteger)_drawableSize.height;

    if (width <= 0 || height <= 0) {
        return NULL;
    }

    NSUInteger bytesPerRow = IOSurfaceAlignProperty(kIOSurfaceBytesPerRow, width * kBGRA32ColorFormatBytesPerPixel);
    NSUInteger allocSize = IOSurfaceAlignProperty(kIOSurfaceAllocSize, height * bytesPerRow);

    NSDictionary *properties = @{
        (id)kIOSurfaceWidth: @(width),
        (id)kIOSurfaceHeight: @(height),
        (id)kIOSurfacePixelFormat: @(kCVPixelFormatType_32BGRA),
        (id)kIOSurfaceBytesPerRow: @(bytesPerRow),
        (id)kIOSurfaceAllocSize: @(allocSize),
        (id)kIOSurfaceBytesPerElement: @(kBGRA32ColorFormatBytesPerPixel)
    };

    return IOSurfaceCreate((__bridge CFDictionaryRef)properties);
}

- (nullable CMPDrawable *)findNextDrawableUnsafe {
    if (_lastPresentedDrawable != nil && _lastPresentedDrawable.isWaitingForCommandBufferCompletion) {
        return nil;
    }

    CMPDrawable *result = nil;

    if (_totalDrawables < kMaxDrawables) {
        IOSurfaceRef surface = [self IOSurface];
        if (surface != NULL) {
            MTLTextureDescriptor *descriptor = [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:MTLPixelFormatBGRA8Unorm
                                                                                                  width:(NSUInteger)_drawableSize.width
                                                                                                 height:(NSUInteger)_drawableSize.height
                                                                                              mipmapped:NO];
            descriptor.usage = MTLTextureUsageRenderTarget | MTLTextureUsageShaderRead | MTLTextureUsageShaderWrite;

            id<MTLTexture> texture = [_device newTextureWithDescriptor:descriptor iosurface:surface plane:0];

            if (texture != nil) {
                result = [[CMPDrawable alloc] initWithTexture:texture size:_drawableSize surface:surface];
                _totalDrawables++;
            }

            CFRelease(surface);
        }
    } else {
        // Prefer a drawable that is not in use, or the oldest presented drawable
        // Separate idle and in-use drawables for clarity
        CMPDrawable *oldestIdle = nil;
        CMPDrawable *oldestInUse = nil;
        CFTimeInterval oldestIdleTime = INFINITY;
        CFTimeInterval oldestInUseTime = INFINITY;

        for (CMPDrawable *drawable in _availableDrawables) {
            BOOL inUse = IOSurfaceIsInUse(drawable.surface);

            if (!inUse) {
                if (drawable.presentedTime < oldestIdleTime) {
                    oldestIdle = drawable;
                    oldestIdleTime = drawable.presentedTime;
                }
            } else {
                if (drawable.presentedTime < oldestInUseTime) {
                    oldestInUse = drawable;
                    oldestInUseTime = drawable.presentedTime;
                }
            }
        }

        // Prefer oldest idle, fall back to oldest in-use
        CMPDrawable *bestDrawable = oldestIdle ?: oldestInUse;

        if (bestDrawable != nil) {
            result = bestDrawable;
            [_availableDrawables removeObject:bestDrawable];
        }
    }

    return result;
}

- (nullable CMPDrawable *)nextDrawable {
    NSTimeInterval startTime = CACurrentMediaTime();
    CMPDrawable *drawable = nil;

    [_drawablesCondition lock];

    while (true) {
        drawable = [self findNextDrawableUnsafe];
        if (drawable != nil) {
            break;
        }

        if (_areDrawableAcquisitionsCancelled) {
            break;
        }

        NSTimeInterval remainingTime = kDrawableAcquisitionTimeout - (CACurrentMediaTime() - startTime);
        if (remainingTime <= 0) {
            break;
        }

        [_drawablesCondition waitUntilDate:
            [NSDate dateWithTimeIntervalSinceNow:remainingTime]];
    }

    [_drawablesCondition unlock];
    return drawable;
}

- (void)cancelDrawableAcquisitions {
    [_drawablesCondition lock];
    _areDrawableAcquisitionsCancelled = YES;
    [_drawablesCondition broadcast];
    [_drawablesCondition unlock];
}

- (void)resumeDrawableAcquisitions {
    [_drawablesCondition lock];
    _areDrawableAcquisitionsCancelled = NO;
    [_drawablesCondition broadcast];
    [_drawablesCondition unlock];
}

- (void)prepareDrawableForPresent:(CMPDrawable *)drawable
                    commandBuffer:(id<MTLCommandBuffer>)commandBuffer {
    [_drawablesCondition lock];
    drawable.isWaitingForCommandBufferCompletion = YES;
    [_drawablesCondition unlock];

    __weak CMPMetalLayer *weakSelf = self;
    __weak CMPDrawable *weakDrawable = drawable;
    [commandBuffer addCompletedHandler:^(id<MTLCommandBuffer> _Nonnull buffer) {
        CMPDrawable *strongDrawable = weakDrawable;
        CMPMetalLayer *strongSelf = weakSelf;
        if (strongDrawable != nil && strongSelf != nil) {
            [strongSelf drawableDidCompleteCommandBuffer:strongDrawable];
        }
    }];
}

- (void)drawableDidCompleteCommandBuffer:(CMPDrawable *)drawable {
    [_drawablesCondition lock];
    drawable.isWaitingForCommandBufferCompletion = NO;
    [_drawablesCondition broadcast];
    [_drawablesCondition unlock];
}

- (void)presentDrawable:(CMPDrawable *)drawable
              onDisplay:(void (^)(void))displayHandler {
    [_drawablesCondition lock];
    drawable.presentedTime = CACurrentMediaTime();
    [_drawablesCondition unlock];

    if ([NSThread isMainThread]) {
        [self presentOnMainThread:drawable onDisplay: displayHandler];
    } else {
        dispatch_async(dispatch_get_main_queue(), ^{
            [self presentOnMainThread:drawable onDisplay: displayHandler];
        });
    }
}

- (void)presentOnMainThread:(CMPDrawable *)drawable
                  onDisplay:(void (^)(void))displayHandler {
    NSAssert([NSThread isMainThread], @"presentOnMainThread - must be called on main thread");

    [_drawablesCondition lock];

    if (!CGSizeEqualToSize(drawable.textureSize, _drawableSize)) {
        [drawable dispose];
        // Invalid drawable size. Disposing.
        [_drawablesCondition unlock];
        return;
    }

    if (_lastDrawablePresentedTime > drawable.presentedTime) {
        [self releaseDrawableUnsafe:drawable];
        // Drop drawable that was scheduled before the already presented one
        [_drawablesCondition broadcast];
        [_drawablesCondition unlock];
        return;
    }

    _lastDrawablePresentedTime = drawable.presentedTime;

    if (_lastPresentedDrawable != nil) {
        [_availableDrawables addObject:_lastPresentedDrawable];
    }
    _lastPresentedDrawable = drawable;
    [_drawablesCondition broadcast];

    [_drawablesCondition unlock];

    [self setNeedsDisplay]; // Prevents frame drops during touch events

    [CATransaction begin];
    [CATransaction setDisableActions:YES];
    self.contents = (__bridge id)drawable.surface;
    if (displayHandler != nil) {
        displayHandler();
    }
    [CATransaction commit];
}

- (void)releaseDrawableUnsafe:(CMPDrawable *)drawable {
    [_availableDrawables addObject:drawable];
}

- (void)releaseDrawable:(CMPDrawable *)drawable {
    [_drawablesCondition lock];
    [self releaseDrawableUnsafe:drawable];
    [_drawablesCondition broadcast];
    [_drawablesCondition unlock];
}

- (void)drainDrawablesUnsafe {
    for (CMPDrawable *drawable in _availableDrawables) {
        [drawable dispose];
    }
    [_availableDrawables removeAllObjects];
    [_lastPresentedDrawable dispose];
    _lastPresentedDrawable = nil;
    _totalDrawables = 0;
    _drawablesGeneration++;
}

- (void)drainDrawables {
    [_drawablesCondition lock];
    [self drainDrawablesUnsafe];
    [_drawablesCondition broadcast];
    [_drawablesCondition unlock];
}

@end
