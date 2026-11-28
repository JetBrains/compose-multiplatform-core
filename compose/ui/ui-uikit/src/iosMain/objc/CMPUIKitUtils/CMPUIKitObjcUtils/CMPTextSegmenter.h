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

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

typedef NS_ENUM(NSInteger, CMPTextSegmentUnit) {
    /// Composed character sequences (grapheme clusters). `\r\n` is a single segment.
    CMPTextSegmentUnitCharacter,
    /// Word boundaries: words, whitespace runs and punctuation are separate segments.
    CMPTextSegmentUnitWord,
    /// Sentences, including their trailing whitespace.
    CMPTextSegmentUnitSentence,
    /// Text between line break opportunities, including trailing whitespace.
    CMPTextSegmentUnitLine,
    /// Paragraphs as `UITextView` reports them: text between newline characters, each of which is a
    /// segment of its own. Both sides of a newline are boundaries, and moving to a boundary stays at
    /// the paragraph's own end (forward) or start (backward): see `followingBoundary:inString:` and
    /// `precedingBoundary:inString:`.
    CMPTextSegmentUnitParagraph,
};

/// Splits a string into contiguous segments with the system text segmentation: `CFStringTokenizer`
/// with the root locale for words, sentences and lines; composed character sequences for characters;
/// newline characters for paragraphs.
///
/// The segments cover the whole string without gaps, which is the semantics of an ICU break
/// iterator, so it can replace one. Unlike the ICU bundled with Skia, it has the dictionaries for
/// CJK word segmentation.
///
/// The tokenizer is bound to the last queried string and reused while the same string is queried.
/// Kotlin/Native bridges a given `String` instance to the same `NSString`, so repeated queries on
/// one Kotlin string neither copy the string nor rebind the tokenizer.
///
/// Offsets are UTF-16 code unit indices. Not thread-safe: use one instance per thread.
@interface CMPTextSegmenter : NSObject

- (instancetype)initWithUnit:(CMPTextSegmentUnit)unit NS_DESIGNATED_INITIALIZER;
- (instancetype)init NS_UNAVAILABLE;

@property (nonatomic, readonly) CMPTextSegmentUnit unit;

/// The segment containing the character at `index`. An `index` outside `[0, string.length)` yields
/// an empty range at `index` clamped to `[0, string.length]`.
- (NSRange)segmentRangeAtIndex:(NSInteger)index inString:(NSString *)string;

/// The first boundary after `offset`, or `string.length` if there is none.
///
/// For paragraphs, the end of the paragraph at `offset`: `offset` itself when it's at a paragraph
/// end, so moving from a paragraph's end doesn't skip the next paragraph.
- (NSInteger)followingBoundary:(NSInteger)offset inString:(NSString *)string;

/// The last boundary before `offset`, or `0` if there is none.
///
/// For paragraphs, the start of the paragraph at `offset`: `offset` itself when it's at a paragraph
/// start.
- (NSInteger)precedingBoundary:(NSInteger)offset inString:(NSString *)string;

/// Whether `offset` is a segment boundary. The start and the end of the string are boundaries;
/// offsets outside `[0, string.length]` are not.
- (BOOL)isBoundary:(NSInteger)offset inString:(NSString *)string;

@end

NS_ASSUME_NONNULL_END
