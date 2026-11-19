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
    return [self segmentRangeAtValidIndex:offset inString:string length:length].location == (NSUInteger)offset;
}

@end
