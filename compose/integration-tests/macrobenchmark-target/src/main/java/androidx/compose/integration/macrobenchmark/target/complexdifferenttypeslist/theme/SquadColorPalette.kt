/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.compose.integration.macrobenchmark.target.complexdifferenttypeslist.theme

import androidx.compose.ui.graphics.Color

sealed interface SquadColorPalette {
    val primaryText: Color
    val secondaryText: Color
    val darkerTableRow: Color
    val highlightedTableRow: Color
    val sectionHeaderTitle: Color
    val shirtNumber: Color
    val shirtNumberBorder: Color
}

class SquadLightColorPalette : SquadColorPalette {
    override val primaryText: Color = SquadColors.greyishBrownTwo
    override val secondaryText: Color = SquadColors.brownGreyTwo
    override val darkerTableRow: Color = SquadColors.whiteTwo
    override val highlightedTableRow: Color = SquadColors.eggshell
    override val sectionHeaderTitle: Color = SquadColors.greyishBrownTwo
    override val shirtNumber: Color = SquadColors.greyishBrown
    override val shirtNumberBorder: Color = SquadColors.veryLightPinkThree
}

class SquadDarkColorPalette : SquadColorPalette {
    override val primaryText: Color = SquadColors.lightPeriwinkle
    override val secondaryText: Color = SquadColors.steelGrey
    override val darkerTableRow: Color = SquadColors.darkSeven
    override val highlightedTableRow: Color = SquadColors.tePapaGreen
    override val sectionHeaderTitle: Color = SquadColors.white
    override val shirtNumber: Color = SquadColors.white
    override val shirtNumberBorder: Color = SquadColors.charcoalGreyThree
}
