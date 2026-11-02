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

#import "CMPBaseProxy.h"
#import <objc/runtime.h>

@implementation CMPBaseProxy {
    id _object;
    Protocol *_protocol;
}

- (instancetype)initWithObject:(nullable id)object protocol:(Protocol *)protocol {
    _object = object;
    _protocol = protocol;
    return self;
}

- (BOOL)conformsToProtocol:(Protocol *)protocol {
    return protocol_isEqual(_protocol, protocol) ||
        protocol_conformsToProtocol(_protocol, protocol) ||
        [_object conformsToProtocol:protocol];
}

- (BOOL)respondsToSelector:(SEL)selector {
    NSMethodSignature *protocolSignature = [self cmp_protocolSignatureForSelector:selector];
    if (protocolSignature != nil) {
        return [self cmp_canForwardSelector:selector withSignature:protocolSignature];
    }
    return [_object respondsToSelector:selector];
}

- (NSMethodSignature *)methodSignatureForSelector:(SEL)selector {
    // A message that nothing declares gets a signature returning an object, so it can return nil
    return [self cmp_protocolSignatureForSelector:selector] ?:
        [_object methodSignatureForSelector:selector] ?:
        [NSMethodSignature signatureWithObjCTypes:"@@:"];
}

- (void)forwardInvocation:(NSInvocation *)invocation {
    SEL selector = invocation.selector;
    NSMethodSignature *protocolSignature = [self cmp_protocolSignatureForSelector:selector];
    BOOL canForward = protocolSignature != nil
        ? [self cmp_canForwardSelector:selector withSignature:protocolSignature]
        : [_object respondsToSelector:selector];
    if (canForward) {
        [invocation invokeWithTarget:_object];
        return;
    }
    NSUInteger returnLength = invocation.methodSignature.methodReturnLength;
    if (returnLength > 0) {
        void *zero = calloc(1, returnLength);
        [invocation setReturnValue:zero];
        free(zero);
    }
}

- (BOOL)cmp_canForwardSelector:(SEL)selector withSignature:(NSMethodSignature *)signature {
    if (![_object respondsToSelector:selector]) {
        return NO;
    }
    NSMethodSignature *objectSignature = [_object methodSignatureForSelector:selector];
    if (objectSignature == nil ||
        objectSignature.numberOfArguments != signature.numberOfArguments ||
        strcmp(objectSignature.methodReturnType, signature.methodReturnType) != 0) {
        return NO;
    }
    for (NSUInteger i = 0; i < signature.numberOfArguments; i++) {
        if (strcmp([objectSignature getArgumentTypeAtIndex:i], [signature getArgumentTypeAtIndex:i]) != 0) {
            return NO;
        }
    }
    return YES;
}

/// Returns the signature of an instance method declared in the protocol or in the protocols it incorporates.
- (nullable NSMethodSignature *)cmp_protocolSignatureForSelector:(SEL)selector {
    const char *types = protocol_getMethodDescription(_protocol, selector, YES, YES).types ?:
        protocol_getMethodDescription(_protocol, selector, NO, YES).types;
    return types != NULL ? [NSMethodSignature signatureWithObjCTypes:types] : nil;
}

@end
