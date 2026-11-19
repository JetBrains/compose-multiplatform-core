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

static BOOL CMPIsNewline(NSString *string, NSInteger index) {
    return [NSCharacterSet.newlineCharacterSet characterIsMember:[string characterAtIndex:index]];
}

/// The offset of the newline at or after `offset`, or the end of `string`.
static NSInteger CMPParagraphEnd(NSString *string, NSInteger offset) {
    NSInteger length = (NSInteger)string.length;
    NSInteger location = MIN(MAX(offset, 0), length);
    while (location < length && !CMPIsNewline(string, location)) {
        location++;
    }
    return location;
}

/// The offset after the newline at or before `offset - 1`, or the start of `string`.
static NSInteger CMPParagraphStart(NSString *string, NSInteger offset) {
    NSInteger location = MIN(MAX(offset, 0), (NSInteger)string.length);
    while (location > 0 && !CMPIsNewline(string, location - 1)) {
        location--;
    }
    return location;
}

static BOOL CMPIsParagraphBoundary(NSString *string, NSInteger offset) {
    NSInteger length = (NSInteger)string.length;
    if (offset < 0 || offset > length) {
        return NO;
    }
    if (offset == 0 || offset == length) {
        return YES;
    }
    return CMPIsNewline(string, offset) || CMPIsNewline(string, offset - 1);
}

static BOOL CMPIsSegmentedGranularity(UITextGranularity granularity) {
    switch (granularity) {
        case UITextGranularityCharacter:
        case UITextGranularityWord:
        case UITextGranularitySentence:
        case UITextGranularityLine:
            return YES;
        default:
            return NO;
    }
}

#pragma mark - Select action

static const NSRange CMPNoRange = {NSNotFound, 0};

static BOOL CMPIsWhitespace(NSString *text, NSInteger index) {
    return [NSCharacterSet.whitespaceAndNewlineCharacterSet characterIsMember:[text characterAtIndex:index]];
}

/// The word token of `tokenizer` containing `index`, or `CMPNoRange`.
static NSRange CMPWordTokenAt(CFStringTokenizerRef tokenizer, NSInteger index) {
    if (CFStringTokenizerGoToTokenAtIndex(tokenizer, index) == kCFStringTokenizerTokenNone) {
        return CMPNoRange;
    }
    CFRange token = CFStringTokenizerGetCurrentTokenRange(tokenizer);
    return NSMakeRange(token.location, token.length);
}

/// The whitespace-delimited run containing the non-whitespace character at `index`.
static NSRange CMPRunAt(NSString *text, NSInteger length, NSInteger index) {
    NSInteger start = index;
    NSInteger end = index + 1;
    while (start > 0 && !CMPIsWhitespace(text, start - 1)) {
        start--;
    }
    while (end < length && !CMPIsWhitespace(text, end)) {
        end++;
    }
    return NSMakeRange(start, end - start);
}

/// Whether `[start, end)` contains a word token.
static BOOL CMPHasWordToken(CFStringTokenizerRef tokenizer, NSInteger start, NSInteger end) {
    for (NSInteger i = start; i < end; i++) {
        if (CMPWordTokenAt(tokenizer, i).location != NSNotFound) {
            return YES;
        }
    }
    return NO;
}

/// The word containing the character at `index`, or `CMPNoRange`.
static NSRange CMPWordAt(CFStringTokenizerRef tokenizer, NSString *text, NSInteger length, NSInteger index) {
    if (index >= length || CMPIsWhitespace(text, index)) {
        return CMPNoRange;
    }
    NSRange token = CMPWordTokenAt(tokenizer, index);
    if (token.location != NSNotFound) {
        return token;
    }
    NSRange run = CMPRunAt(text, length, index);
    return CMPHasWordToken(tokenizer, run.location, NSMaxRange(run)) ? CMPNoRange : run;
}

/// The last word ending at or before `offset`, or `CMPNoRange`. `offset` must not be inside a word.
static NSRange CMPWordBefore(CFStringTokenizerRef tokenizer, NSString *text, NSInteger length, NSInteger offset) {
    NSInteger index = offset - 1;
    while (index >= 0) {
        if (CMPIsWhitespace(text, index)) {
            index--;
            continue;
        }
        // Visit each character of the run once: its word tokens, or the run itself as a word.
        NSRange run = CMPRunAt(text, length, index);
        for (NSInteger i = index; i >= (NSInteger)run.location; i--) {
            NSRange token = CMPWordTokenAt(tokenizer, i);
            if (token.location != NSNotFound) {
                return token;
            }
        }
        if (!CMPHasWordToken(tokenizer, index + 1, NSMaxRange(run))) {
            return run;
        }
        index = (NSInteger)run.location - 1;
    }
    return CMPNoRange;
}

/// The first word starting at or after `offset`, or `CMPNoRange`. No word must precede `offset`.
static NSRange CMPWordAfter(CFStringTokenizerRef tokenizer, NSString *text, NSInteger length, NSInteger offset) {
    NSInteger index = offset;
    while (index < length) {
        if (CMPIsWhitespace(text, index)) {
            index++;
            continue;
        }
        NSRange run = CMPRunAt(text, length, index);
        for (NSInteger i = index; i < (NSInteger)NSMaxRange(run); i++) {
            NSRange token = CMPWordTokenAt(tokenizer, i);
            if (token.location != NSNotFound) {
                return token;
            }
        }
        // No word precedes `offset`, so the run has no word tokens.
        return run;
    }
    return CMPNoRange;
}

static NSRange CMPSelectActionRange(CFStringTokenizerRef tokenizer, NSString *text, NSInteger caret) {
    NSInteger length = (NSInteger)text.length;

    NSRange word = CMPWordAt(tokenizer, text, length, caret);
    if (word.location != NSNotFound) {
        return word;
    }

    NSInteger paragraphStart = CMPParagraphStart(text, caret);
    NSRange previous = CMPWordBefore(tokenizer, text, length, caret);
    if (previous.location != NSNotFound) {
        NSInteger start = MAX((NSInteger)previous.location, paragraphStart);
        return NSMakeRange(start, caret - start);
    }

    NSRange next = CMPWordAfter(tokenizer, text, length, caret);
    if (next.location != NSNotFound) {
        return NSMakeRange(caret, next.location - caret);
    }

    // There are no words. At the end of the text, `UITextView` selects the last paragraph.
    if (caret == length) {
        return NSMakeRange(paragraphStart, caret - paragraphStart);
    }
    return NSMakeRange(caret, 0);
}

#pragma mark - CMPTextInputStringTokenizer

@implementation CMPTextInputStringTokenizer {
    /// Segmenters for character, word, sentence and line granularities, created on first use.
    /// Each keeps its tokenizer bound to the last text, so repeated queries are cheap.
    CMPTextSegmenter *_segmenters[UITextGranularityLine + 1];
}

+ (NSRange)selectActionRangeForCaretAtOffset:(NSInteger)offset inText:(NSString *)text {
    NSInteger length = (NSInteger)text.length;
    NSInteger caret = MIN(MAX(offset, 0), length);
    // A one-off user action, so the tokenizer isn't cached. Unlike `kCFStringTokenizerUnitWordBoundary`,
    // `kCFStringTokenizerUnitWord` yields only words and splits them at punctuation the way
    // `UITextView` does ("foo.bar" is two words).
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
    if (granularity == UITextGranularityParagraph) {
        NSString *text = [self tokenizedText];
        result = isForward ? CMPParagraphEnd(text, offset) : CMPParagraphStart(text, offset);
    } else if (CMPIsSegmentedGranularity(granularity)) {
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
    if (granularity == UITextGranularityParagraph) {
        return CMPIsParagraphBoundary([self tokenizedText], offset);
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
