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

#import "CMPTextInputView.h"

@interface CMPTextInputView ()

- (nullable UITextField *)cmp_proxyTextField;

@end

@implementation CMPTextInputView {
    UITextField *_textField;
    BOOL _isDeallocating;
}

@synthesize beginningOfDocument;
@synthesize hasText;
@synthesize inputDelegate;
@synthesize markedTextRange;
@synthesize selectedTextRange;
@synthesize tokenizer;
@synthesize endOfDocument;
@synthesize markedTextStyle;

- (UITextPosition *)positionWithinRange:(UITextRange *)range atCharacterOffset:(NSInteger)offset {
    return [self positionWithinRangeAtCharacterOffset:range atCharacterOffset:offset];
}

- (UITextPosition *)positionWithinRange:(UITextRange *)range farthestInDirection:(UITextLayoutDirection)direction {
    return [self positionWithinRangeFarthestInDirection:range farthestInDirection:direction];
}

- (NSWritingDirection)baseWritingDirectionForPosition:(nonnull UITextPosition *)position inDirection:(UITextStorageDirection)direction {
    CMP_ABSTRACT_FUNCTION_CALLED
}

- (CGRect)caretRectForPosition:(nonnull UITextPosition *)position {
    CMP_ABSTRACT_FUNCTION_CALLED
}

- (nullable UITextRange *)characterRangeAtPoint:(CGPoint)point {
    CMP_ABSTRACT_FUNCTION_CALLED
}

- (nullable UITextRange *)characterRangeByExtendingPosition:(nonnull UITextPosition *)position inDirection:(UITextLayoutDirection)direction {
    CMP_ABSTRACT_FUNCTION_CALLED
}

- (nullable UITextPosition *)closestPositionToPoint:(CGPoint)point {
    CMP_ABSTRACT_FUNCTION_CALLED
}

- (nullable UITextPosition *)closestPositionToPoint:(CGPoint)point withinRange:(nonnull UITextRange *)range {
    CMP_ABSTRACT_FUNCTION_CALLED
}

- (NSComparisonResult)comparePosition:(nonnull UITextPosition *)position toPosition:(nonnull UITextPosition *)other {
    CMP_ABSTRACT_FUNCTION_CALLED
}

- (CGRect)firstRectForRange:(nonnull UITextRange *)range {
    CMP_ABSTRACT_FUNCTION_CALLED
}

- (NSInteger)offsetFromPosition:(nonnull UITextPosition *)from toPosition:(nonnull UITextPosition *)toPosition {
    CMP_ABSTRACT_FUNCTION_CALLED
}

- (nullable UITextPosition *)positionFromPosition:(nonnull UITextPosition *)position inDirection:(UITextLayoutDirection)direction offset:(NSInteger)offset {
    CMP_ABSTRACT_FUNCTION_CALLED
}

- (nullable UITextPosition *)positionFromPosition:(nonnull UITextPosition *)position offset:(NSInteger)offset {
    CMP_ABSTRACT_FUNCTION_CALLED
}

- (void)replaceRange:(nonnull UITextRange *)range withText:(nonnull NSString *)text {
    CMP_ABSTRACT_FUNCTION_CALLED
}

- (nonnull NSArray<UITextSelectionRect *> *)selectionRectsForRange:(nonnull UITextRange *)range {
    CMP_ABSTRACT_FUNCTION_CALLED
}

- (void)setBaseWritingDirection:(NSWritingDirection)writingDirection forRange:(nonnull UITextRange *)range {
    CMP_ABSTRACT_FUNCTION_CALLED
}

- (void)setMarkedText:(nullable NSString *)markedText selectedRange:(NSRange)selectedRange {
    CMP_ABSTRACT_FUNCTION_CALLED
}

- (nullable NSString *)textInRange:(nonnull UITextRange *)range {
    CMP_ABSTRACT_FUNCTION_CALLED
}

- (nullable UITextRange *)textRangeFromPosition:(nonnull UITextPosition *)fromPosition toPosition:(nonnull UITextPosition *)toPosition {
    CMP_ABSTRACT_FUNCTION_CALLED
}

- (void)unmarkText {
    CMP_ABSTRACT_FUNCTION_CALLED
}

- (nullable UITextPosition *)positionWithinRangeFarthestInDirection:(UITextRange *)range
                                                farthestInDirection:(UITextLayoutDirection)direction {
    CMP_ABSTRACT_FUNCTION_CALLED
}

- (nullable UITextPosition *)positionWithinRangeAtCharacterOffset:(UITextRange *)range
                                                atCharacterOffset:(NSInteger)offset {
    CMP_ABSTRACT_FUNCTION_CALLED
}

- (void)deleteBackward {
    CMP_ABSTRACT_FUNCTION_CALLED
}

- (void)insertText:(nonnull NSString *)text {
    CMP_ABSTRACT_FUNCTION_CALLED
}

- (BOOL)isSecureTextEntry {
    CMP_ABSTRACT_FUNCTION_CALLED
}

- (UITextField *)cmp_proxyTextField {
    if (![self isSecureTextEntry]) {
        return nil;
    }
    if (!_textField) {
        _textField = [[UITextField alloc] init];
    }
    return _textField;
}

/// `-[UIView dealloc]` still queries the view while tearing it down (`-isKindOfClass:` from
/// `-_removeAllGestureRecognizers`, for example). The Kotlin subclass releases its state in its own
/// `-dealloc` before `super` runs, so from here on the subclass can no longer be asked anything.
- (void)dealloc {
    _isDeallocating = YES;
}

- (BOOL)isKindOfClass:(Class)aClass {
    if ([super isKindOfClass:aClass]) {
        return YES;
    }
    if (_isDeallocating) {
        return NO;
    }
    UITextField *proxyTextField = [self cmp_proxyTextField];
    return proxyTextField != nil && [proxyTextField isKindOfClass:aClass];
}

- (NSMethodSignature*)methodSignatureForSelector:(SEL)aSelector {
    NSMethodSignature* signature = [super methodSignatureForSelector:aSelector];
    if (!signature) {
        signature = [[self cmp_proxyTextField] methodSignatureForSelector:aSelector];
    }
    return signature;
}

- (void)forwardInvocation:(NSInvocation*)anInvocation {
    UITextField *proxyTextField = [self cmp_proxyTextField];
    if (proxyTextField != nil) {
        [anInvocation invokeWithTarget:proxyTextField];
    } else {
        [super forwardInvocation:anInvocation];
    }
}

- (nullable NSString *)text {
    UITextRange *range = [self textRangeFromPosition:self.beginningOfDocument
                                         toPosition:self.endOfDocument];
    return range != nil ? [self textInRange:range] : nil;
}

- (void)activateTextInputInteractionIfNeeded {
    if (@available(iOS 17, *)) {
        for (id<UIInteraction> interaction in self.interactions) {
            if ([interaction isKindOfClass:[UITextSelectionDisplayInteraction class]]) {
                [((UITextSelectionDisplayInteraction *)interaction) setActivated:YES];
            }
        }
    }
}

- (void)deactivateTextInputInteractionIfNeeded {
    if (@available(iOS 17, *)) {
        for (id<UIInteraction> interaction in self.interactions) {
            if ([interaction isKindOfClass:[UITextSelectionDisplayInteraction class]]) {
                [((UITextSelectionDisplayInteraction *)interaction) setActivated:NO];
            }
        }
    }
}

- (UIView *)inputView {
    CMP_ABSTRACT_FUNCTION_CALLED;
}

- (UIView *)inputAccessoryView {
    CMP_ABSTRACT_FUNCTION_CALLED;
}

@end
