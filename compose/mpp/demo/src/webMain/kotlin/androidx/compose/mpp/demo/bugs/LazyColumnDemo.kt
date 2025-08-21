/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.compose.mpp.demo.bugs

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LazyColumnDemo() {
    MaterialTheme {
        val names = remember {
            listOf(
                "Alice Johnson",
                "Bob Smith",
                "Charlie Brown",
                "Diana Prince",
                "Edward Norton",
                "Fiona Apple",
                "George Clooney",
                "Hannah Montana",
                "Ian McKellen",
                "Julia Roberts",
                "Kevin Hart",
                "Laura Dern",
                "Michael Jordan",
                "Nancy Drew",
                "Oscar Wilde",
                "Penelope Cruz",
                "Quentin Tarantino",
                "Rachel Green",
                "Samuel Jackson",
                "Taylor Swift",
                "Uma Thurman",
                "Vincent Vega",
                "Walter White",
                "Xavier Woods",
                "Yara Greyjoy"
            )
        }

        LazyColumn {
            items(names) { name ->
                println("$name placed as view")
                Text(
                    text = name,
                    modifier = Modifier
                        .padding(16.dp)

                )
            }
        }
    }
}

