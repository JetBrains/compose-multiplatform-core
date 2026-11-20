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

#import "CMPTextInputStringTokenizer.h"
#import "CMPTextSegmenter.h"

static BOOL CMPIsForward(UITextDirection direction) {
    return direction == UITextStorageDirectionForward ||
        direction == UITextLayoutDirectionRight ||
        direction == UITextLayoutDirectionDown;
}

static BOOL CMPIsSegmentedGranularity(UITextGranularity granularity) {
    switch (granularity) {
        case UITextGranularityCharacter:
        case UITextGranularityWord:
        case UITextGranularitySentence:
        case UITextGranularityParagraph:
        case UITextGranularityLine:
            return YES;
        default:
            return NO;
    }
}

#pragma mark - Select action

/// The token of `tokenizer` containing `index`, or a range with `NSNotFound` location.
static NSRange CMPWordAt(CFStringTokenizerRef tokenizer, NSInteger index) {
    if (CFStringTokenizerGoToTokenAtIndex(tokenizer, index) == kCFStringTokenizerTokenNone) {
        return NSMakeRange(NSNotFound, 0);
    }
    CFRange token = CFStringTokenizerGetCurrentTokenRange(tokenizer);
    return NSMakeRange(token.location, token.length);
}

/// `tokenizer` must be a `kCFStringTokenizerUnitWord` tokenizer of `text`.
static NSRange CMPSelectActionRange(CFStringTokenizerRef tokenizer, NSString *text, NSInteger caret) {
    NSInteger length = (NSInteger)text.length;

    // The word containing the caret or starting at it.
    if (caret < length) {
        NSRange word = CMPWordAt(tokenizer, caret);
        if (word.location != NSNotFound) {
            return word;
        }
    }

    // From the previous word to the caret, within the caret's paragraph.
    CMPTextSegmenter *paragraphs = [[CMPTextSegmenter alloc] initWithUnit:CMPTextSegmentUnitParagraph];
    NSInteger paragraphStart = [paragraphs precedingBoundary:caret inString:text];
    for (NSInteger index = caret - 1; index >= 0; index--) {
        NSRange word = CMPWordAt(tokenizer, index);
        if (word.location != NSNotFound) {
            NSInteger start = MAX((NSInteger)word.location, paragraphStart);
            return NSMakeRange(start, caret - start);
        }
    }

    // No word before the caret: up to the next word.
    for (NSInteger index = caret; index < length; index++) {
        NSRange word = CMPWordAt(tokenizer, index);
        if (word.location != NSNotFound) {
            return NSMakeRange(caret, word.location - caret);
        }
    }

    // There are no words. At the end of the text, `UITextView` selects the last paragraph.
    if (caret == length) {
        return NSMakeRange(paragraphStart, caret - paragraphStart);
    }
    return NSMakeRange(caret, 0);
}

#pragma mark - CMPTextInputStringTokenizer

@implementation CMPTextInputStringTokenizer {
    /// Segmenters by granularity (see `CMPIsSegmentedGranularity`), created on first use. Each keeps
    /// its tokenizer bound to the last text, so repeated queries are cheap.
    CMPTextSegmenter *_segmenters[UITextGranularityLine + 1];
}

+ (NSRange)selectActionRangeForCaretAtOffset:(NSInteger)offset inText:(NSString *)text {
    NSInteger length = (NSInteger)text.length;
    NSInteger caret = MIN(MAX(offset, 0), length);
    // A one-off user action, so the tokenizer isn't cached. Unlike `kCFStringTokenizerUnitWordBoundary`
    // (`CMPTextSegmenter`), `kCFStringTokenizerUnitWord` tokens are the words `UITextView` selects:
    // they split at punctuation ("example.com" is two words), and a token is found at an index of
    // whitespace-delimited punctuation too ("--", ":)").
    CFStringTokenizerRef tokenizer = CFStringTokenizerCreate(kCFAllocatorDefault, (__bridge CFStringRef)text,
                                                             CFRangeMake(0, length), kCFStringTokenizerUnitWord, NULL);
    NSRange range = CMPSelectActionRange(tokenizer, text, caret);
    CFRelease(tokenizer);
    return range;
}

- (NSString *)tokenizedText {
    CMP_ABSTRACT_FUNCTION_CALLED
    return @"";
}

- (NSInteger)offsetOfTextPosition:(UITextPosition *)position {
    CMP_ABSTRACT_FUNCTION_CALLED
    return NSNotFound;
}

- (UITextPosition *)textPositionAtOffset:(NSInteger)offset {
    CMP_ABSTRACT_FUNCTION_CALLED
    return [UITextPosition new];
}

/// `granularity` must be one of `CMPIsSegmentedGranularity`.
- (CMPTextSegmenter *)segmenterForGranularity:(UITextGranularity)granularity {
    CMPTextSegmenter *segmenter = _segmenters[granularity];
    if (segmenter == nil) {
        CMPTextSegmentUnit unit;
        switch (granularity) {
            case UITextGranularityWord:
                unit = CMPTextSegmentUnitWord;
                break;
            case UITextGranularitySentence:
                unit = CMPTextSegmentUnitSentence;
                break;
            case UITextGranularityParagraph:
                unit = CMPTextSegmentUnitParagraph;
                break;
            case UITextGranularityLine:
                unit = CMPTextSegmentUnitLine;
                break;
            case UITextGranularityCharacter:
            default:
                unit = CMPTextSegmentUnitCharacter;
                break;
        }
        segmenter = [[CMPTextSegmenter alloc] initWithUnit:unit];
        _segmenters[granularity] = segmenter;
    }
    return segmenter;
}

- (nullable UITextPosition *)positionFromPosition:(UITextPosition *)position
                                       toBoundary:(UITextGranularity)granularity
                                      inDirection:(UITextDirection)direction {
    NSInteger offset = [self offsetOfTextPosition:position];
    if (offset == NSNotFound) {
        return nil;
    }
    BOOL isForward = CMPIsForward(direction);
    NSInteger result;
    if (CMPIsSegmentedGranularity(granularity)) {
        NSString *text = [self tokenizedText];
        CMPTextSegmenter *segmenter = [self segmenterForGranularity:granularity];
        // Both are clamped to the text bounds.
        result = isForward
            ? [segmenter followingBoundary:offset inString:text]
            : [segmenter precedingBoundary:offset inString:text];
    } else {
        return [super positionFromPosition:position toBoundary:granularity inDirection:direction];
    }
    return [self textPositionAtOffset:result];
}

- (BOOL)isPosition:(UITextPosition *)position atBoundary:(UITextGranularity)granularity inDirection:(UITextDirection)direction {
    return [self isPositionAtBoundary:position atBoundary:granularity inDirection:direction];
}

- (BOOL)isPosition:(UITextPosition *)position withinTextUnit:(UITextGranularity)granularity inDirection:(UITextDirection)direction {
    return [self isPositionWithinTextUnit:position withinTextUnit:granularity inDirection:direction];
}

- (BOOL)isPositionAtBoundary:(UITextPosition *)position atBoundary:(UITextGranularity)granularity inDirection:(UITextDirection)direction {
    NSInteger offset = [self offsetOfTextPosition:position];
    if (offset == NSNotFound) {
        return NO;
    }
    if (CMPIsSegmentedGranularity(granularity)) {
        return [[self segmenterForGranularity:granularity] isBoundary:offset inString:[self tokenizedText]];
    }
    return [super isPosition:position atBoundary:granularity inDirection:direction];
}

- (BOOL)isPositionWithinTextUnit:(UITextPosition *)position withinTextUnit:(UITextGranularity)granularity inDirection:(UITextDirection)direction {
    return [super isPosition:position withinTextUnit:granularity inDirection:direction];
}

@end
