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

#import "CMPTextSegmenter.h"

static CFOptionFlags CMPTokenizerUnit(CMPTextSegmentUnit unit) {
    switch (unit) {
        case CMPTextSegmentUnitSentence:
            return kCFStringTokenizerUnitSentence;
        case CMPTextSegmentUnitLine:
            return kCFStringTokenizerUnitLineBreak;
        case CMPTextSegmentUnitWord:
        default:
            // Unlike `kCFStringTokenizerUnitWord`, it also yields whitespace and punctuation tokens.
            return kCFStringTokenizerUnitWordBoundary;
    }
}

/// Whether `c` is in `NSCharacterSet.newlineCharacterSet`.
static inline BOOL CMPIsNewlineCharacter(UniChar c) {
    return (c >= 0x000A && c <= 0x000D) || c == 0x0085 || c == 0x2028 || c == 0x2029;
}

// Paragraph scanning reads characters through `CFStringInlineBuffer`: directly from the UTF-16
// storage when the string has one, as bridged Kotlin strings do, instead of a message send per
// character.

/// The offset of the newline at or after `offset`, or `length`. `offset` must be in `[0, length]`.
static NSInteger CMPParagraphEnd(CFStringInlineBuffer *characters, NSInteger length, NSInteger offset) {
    NSInteger location = offset;
    while (location < length && !CMPIsNewlineCharacter(CFStringGetCharacterFromInlineBuffer(characters, location))) {
        location++;
    }
    return location;
}

/// The offset after the newline before `offset`, or `0`. `offset` must be in `[0, length]`.
static NSInteger CMPParagraphStart(CFStringInlineBuffer *characters, NSInteger offset) {
    NSInteger location = offset;
    while (location > 0 && !CMPIsNewlineCharacter(CFStringGetCharacterFromInlineBuffer(characters, location - 1))) {
        location--;
    }
    return location;
}

static void CMPInitCharacters(CFStringInlineBuffer *characters, NSString *string, NSInteger length) {
    CFStringInitInlineBuffer((__bridge CFStringRef)string, characters, CFRangeMake(0, length));
}

/// The paragraph containing the character at `index`, or the newline at `index`. `index` must be in
/// `[0, length)`.
static NSRange CMPParagraphRange(NSString *string, NSInteger index, NSInteger length) {
    CFStringInlineBuffer characters;
    CMPInitCharacters(&characters, string, length);
    if (CMPIsNewlineCharacter(CFStringGetCharacterFromInlineBuffer(&characters, index))) {
        return NSMakeRange(index, 1);
    }
    NSInteger start = CMPParagraphStart(&characters, index);
    return NSMakeRange(start, CMPParagraphEnd(&characters, length, index) - start);
}

/// `index` must be in `[0, length)`.
static NSRange CMPComposedCharacterRange(NSString *string, NSInteger index, NSInteger length) {
    CFRange range = CFStringGetRangeOfComposedCharactersAtIndex((__bridge CFStringRef)string, index);
    NSInteger start = range.location;
    NSInteger end = range.location + range.length;
    // Composed character sequences split CR LF, while it is a single grapheme cluster for ICU and
    // for the rest of Compose.
    if (end - start == 1) {
        unichar c = [string characterAtIndex:start];
        if (c == '\r' && end < length && [string characterAtIndex:end] == '\n') {
            end++;
        } else if (c == '\n' && start > 0 && [string characterAtIndex:start - 1] == '\r') {
            start--;
        }
    }
    return NSMakeRange(start, end - start);
}

@implementation CMPTextSegmenter {
    /// Created on the first query of a unit backed by `CFStringTokenizer`.
    CFStringTokenizerRef _tokenizer;
    /// The string `_tokenizer` is set to.
    NSString *_tokenizerString;
    /// The last queried string; it has the same contents as `_tokenizerString`. Keeping it saves the
    /// contents comparison when another instance of the same text is queried repeatedly.
    NSString *_lastString;
}

- (instancetype)initWithUnit:(CMPTextSegmentUnit)unit {
    self = [super init];
    if (self) {
        _unit = unit;
    }
    return self;
}

- (void)dealloc {
    if (_tokenizer != NULL) {
        CFRelease(_tokenizer);
    }
}

/// Points `_tokenizer` at `string` unless it is already set to the same contents. Setting the
/// string of an existing tokenizer is an order of magnitude cheaper than creating a new one.
- (void)bindString:(NSString *)string {
    if (string == _lastString) {
        return;
    }
    // `copy` returns the same instance for immutable strings (including bridged Kotlin strings), so
    // the identity check above keeps working, and protects against later mutation otherwise.
    NSString *copy = [string copy];
    if (_tokenizerString != nil && [copy isEqualToString:_tokenizerString]) {
        _lastString = copy;
        return;
    }
    CFRange range = CFRangeMake(0, copy.length);
    if (_tokenizer == NULL) {
        // The root locale (`NULL` is not the current locale here) segments all CJK languages well,
        // while locale-specific models split text of other CJK languages poorly.
        _tokenizer = CFStringTokenizerCreate(kCFAllocatorDefault, (__bridge CFStringRef)copy, range,
                                             CMPTokenizerUnit(_unit), NULL);
    } else {
        CFStringTokenizerSetString(_tokenizer, (__bridge CFStringRef)copy, range);
    }
    _tokenizerString = copy;
    _lastString = copy;
}

/// `index` must be in `[0, length)`.
- (NSRange)segmentRangeAtValidIndex:(NSInteger)index inString:(NSString *)string length:(NSInteger)length {
    if (_unit == CMPTextSegmentUnitParagraph) {
        return CMPParagraphRange(string, index, length);
    }
    if (_unit != CMPTextSegmentUnitCharacter) {
        [self bindString:string];
        if (CFStringTokenizerGoToTokenAtIndex(_tokenizer, index) != kCFStringTokenizerTokenNone) {
            CFRange token = CFStringTokenizerGetCurrentTokenRange(_tokenizer);
            if (token.location <= index && index < token.location + token.length) {
                return NSMakeRange(token.location, token.length);
            }
        }
        // The tokenizer covers the whole string with tokens, so this is not expected to happen.
        // Fall back to a character to never return an empty segment.
    }
    return CMPComposedCharacterRange(string, index, length);
}

- (NSRange)segmentRangeAtIndex:(NSInteger)index inString:(NSString *)string {
    NSInteger length = (NSInteger)string.length;
    if (index < 0 || index >= length) {
        return NSMakeRange(MIN(MAX(index, 0), length), 0);
    }
    return [self segmentRangeAtValidIndex:index inString:string length:length];
}

- (NSInteger)followingBoundary:(NSInteger)offset inString:(NSString *)string {
    NSInteger length = (NSInteger)string.length;
    if (_unit == CMPTextSegmentUnitParagraph) {
        CFStringInlineBuffer characters;
        CMPInitCharacters(&characters, string, length);
        return CMPParagraphEnd(&characters, length, MIN(MAX(offset, 0), length));
    }
    if (offset < 0) {
        return 0;
    }
    if (offset >= length) {
        return length;
    }
    return NSMaxRange([self segmentRangeAtValidIndex:offset inString:string length:length]);
}

- (NSInteger)precedingBoundary:(NSInteger)offset inString:(NSString *)string {
    NSInteger length = (NSInteger)string.length;
    if (_unit == CMPTextSegmentUnitParagraph) {
        CFStringInlineBuffer characters;
        CMPInitCharacters(&characters, string, length);
        return CMPParagraphStart(&characters, MIN(MAX(offset, 0), length));
    }
    if (offset <= 0) {
        return 0;
    }
    if (offset > length) {
        return length;
    }
    return [self segmentRangeAtValidIndex:offset - 1 inString:string length:length].location;
}

- (BOOL)isBoundary:(NSInteger)offset inString:(NSString *)string {
    NSInteger length = (NSInteger)string.length;
    if (offset < 0 || offset > length) {
        return NO;
    }
    if (offset == 0 || offset == length) {
        return YES;
    }
    if (_unit == CMPTextSegmentUnitParagraph) {
        // The boundaries are the sides of newlines; no need to find the whole paragraph.
        return CMPIsNewlineCharacter([string characterAtIndex:offset]) ||
            CMPIsNewlineCharacter([string characterAtIndex:offset - 1]);
    }
    return [self segmentRangeAtValidIndex:offset inString:string length:length].location == (NSUInteger)offset;
}

@end
