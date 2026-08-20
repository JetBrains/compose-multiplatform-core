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

package org.jetbrains.androidx.build.util

import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtTokens

/** A significant token of a build script. Whitespace and comments are not represented. */
internal class ScriptToken(
    val kind: Kind,
    val start: Int,
    val end: Int,
    val text: String,
) {
    enum class Kind { IDENTIFIER, DOT, OPENING_BRACE, CLOSING_BRACE, OTHER }
}

/**
 * Splits [text] into significant tokens using the Kotlin lexer.
 *
 * Lexing rather than scanning characters is what makes block detection reliable: a brace in a
 * string literal, in a comment, or delimiting a string template entry is never reported as
 * [ScriptToken.Kind.OPENING_BRACE] or [ScriptToken.Kind.CLOSING_BRACE]. Note that a `"${'$'}{...}"`
 * entry is delimited by LONG_TEMPLATE_ENTRY_START/END rather than by braces, so it doesn't affect
 * the brace depth either.
 *
 * The Kotlin lexer is used for Groovy build scripts as well. Their lexical structure only differs
 * in ways that don't change where the braces are: Groovy's `'...'` and `'''...'''` strings are
 * lexed as (invalid, but irrelevant here) character literals that still hide the braces inside
 * them, and Groovy's remaining string, comment and template forms are shared with Kotlin. Lexing
 * Groovy with Groovy's own ANTLR lexer isn't an option, because it rejects Kotlin-only syntax such
 * as backtick identifiers by throwing, and the language of a script isn't known here.
 */
internal fun tokenizeScript(text: String): List<ScriptToken> = buildList {
    val lexer = KotlinLexer()
    lexer.start(text)
    while (true) {
        val type = lexer.tokenType ?: break
        if (!KtTokens.WHITE_SPACE_OR_COMMENT_BIT_SET.contains(type)) {
            val kind = when (type) {
                KtTokens.LBRACE -> ScriptToken.Kind.OPENING_BRACE
                KtTokens.RBRACE -> ScriptToken.Kind.CLOSING_BRACE
                KtTokens.DOT -> ScriptToken.Kind.DOT
                KtTokens.IDENTIFIER -> ScriptToken.Kind.IDENTIFIER
                else -> ScriptToken.Kind.OTHER
            }
            add(ScriptToken(kind, lexer.tokenStart, lexer.tokenEnd, lexer.tokenText))
        }
        lexer.advance()
    }
}
