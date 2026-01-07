/**
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package androidx.xr.glimmer.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.xr.glimmer.Button
import androidx.xr.glimmer.Text
import androidx.xr.glimmer.list.VerticalList
import androidx.xr.glimmer.surface

internal val ListDemos =
    listOf(
        ComposableDemo("List with a controllable number of items") {
            VerticalListWithControllableNumberOfItems()
        }
    )

@Composable
private fun VerticalListWithControllableNumberOfItems() {
    var itemsCount by remember { mutableIntStateOf(5) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ItemCounter(
            itemsCount = itemsCount,
            onClick = { newValue -> itemsCount = maxOf(0, newValue) },
        )

        VerticalList(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(itemsCount) { index ->
                Box(
                    Modifier.fillMaxWidth().surface().padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    Text("Item-$index")
                }
            }
        }
    }
}

@Composable
private fun ItemCounter(itemsCount: Int, onClick: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Button({ onClick(itemsCount - 50) }) { Text(text = "-50", fontSize = 16.sp) }
        Button({ onClick(itemsCount - 1) }) { Text(text = "-1", fontSize = 16.sp) }
        Text(
            text = itemsCount.toString(),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(12.dp),
        )
        Button({ onClick(itemsCount + 1) }) { Text(text = "+1", fontSize = 16.sp) }
        Button({ onClick(itemsCount + 50) }) { Text(text = "+50", fontSize = 16.sp) }
    }
}
