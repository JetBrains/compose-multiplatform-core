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
#import "CMPMacros.h"

NS_ASSUME_NONNULL_BEGIN

/// A tokenizer that finds character, word, sentence, line and paragraph boundaries with
/// `CMPTextSegmenter`. Other granularities are handled by `UITextInputStringTokenizer`.
///
/// Subclasses provide the text and the mapping between text positions and UTF-16 offsets.
@interface CMPTextInputStringTokenizer: UITextInputStringTokenizer <UITextInputTokenizer>

/// The text of the text input.
- (NSString *)tokenizedText CMP_ABSTRACT_FUNCTION;

/// The UTF-16 offset of `position` in `tokenizedText`, or `NSNotFound` if `position` doesn't
/// belong to the text input.
- (NSInteger)offsetOfTextPosition:(UITextPosition *)position CMP_ABSTRACT_FUNCTION;

/// The text position at the UTF-16 `offset` in `tokenizedText`.
- (UITextPosition *)textPositionAtOffset:(NSInteger)offset CMP_ABSTRACT_FUNCTION;

/// The range that the "Select" edit menu action of `UITextView` selects in `text` for a caret at
/// `offset`:
/// 1. the word containing the caret or starting at it;
/// 2. otherwise, the text from the start of the previous word to the caret, within the caret's
///    paragraph;
/// 3. otherwise, if no word precedes the caret, the text from the caret to the next word.
///
/// Words are the tokens of `kCFStringTokenizerUnitWord`, including whitespace-delimited runs of
/// punctuation such as "--".
+ (NSRange)selectActionRangeForCaretAtOffset:(NSInteger)offset inText:(NSString *)text;

// Distinct names for `isPosition:atBoundary:inDirection:` and
// `isPosition:withinTextUnit:inDirection:`, whose Kotlin names clash.
- (BOOL)isPositionAtBoundary:(UITextPosition *)position atBoundary:(UITextGranularity)granularity inDirection:(UITextDirection)direction;
- (BOOL)isPositionWithinTextUnit:(UITextPosition *)position withinTextUnit:(UITextGranularity)granularity inDirection:(UITextDirection)direction;

@end

NS_ASSUME_NONNULL_END
