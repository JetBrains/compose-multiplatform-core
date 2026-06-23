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

package androidx.compose.ui.node

internal class FrameRateVotingManager(
    private val voteFrameRate: (frameRate: Float, frameRateCategory: Float) -> Unit,
) {
    private var frameRateVote = Float.NaN
    private var frameRateCategoryVote = 0f

    private val isFrameRateVoteSet get() = !frameRateVote.isNaN()
    private val isFrameRateCategoryVoteSet get() = frameRateCategoryVote != 0f
    private val isAnyFrameRateVoteSet get() = isFrameRateVoteSet || isFrameRateCategoryVoteSet

    fun prepareForVoting(frameRate: Float) {
        if (frameRate > 0) {
            if (!isFrameRateVoteSet || frameRate > frameRateVote) {
                frameRateVote = frameRate
            }
        } else if (frameRate.isNaN() && !isFrameRateCategoryVoteSet) {
            frameRateCategoryVote = frameRate
        } else if (!frameRate.isNaN() && frameRate < 0 && (frameRateCategoryVote.isNaN() || frameRate < frameRateCategoryVote)) {
            frameRateCategoryVote = frameRate
        }
    }

    fun voteFrameRateIfNeeded() {
        if (isAnyFrameRateVoteSet) {
            voteFrameRate(frameRateVote, frameRateCategoryVote)
            clear()
        }
    }

    private fun clear() {
        frameRateVote = Float.NaN
        frameRateCategoryVote = 0f
    }
}