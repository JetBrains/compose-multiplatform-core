package androidx.compose.mpp.demo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.mpp.demo.components.InteractiveList
import androidx.compose.mpp.demo.components.rememberInteractiveListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeymapCommands() {
    val n = 10
    var textFieldValue by remember { mutableStateOf(TextFieldValue()) }
    val listState = rememberInteractiveListState(n)
    var listHasFocus by remember { mutableStateOf(false) }
    InteractiveList(
        listState,
        nestedContent = { focusRequester ->
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .fillMaxWidth(),
                trailingIcon = { IconButton({}) { Text("🦦") } },
                singleLine = true,
            )
        },
        Modifier
            .fillMaxSize()
            .border(
                if (listHasFocus) {
                    BorderStroke(1.dp, MaterialTheme.colors.primary)
                } else {
                    BorderStroke(0.dp, Color.Unspecified)
                },
                MaterialTheme.shapes.medium,
            )
            .padding(4.dp)
            .onFocusChanged { listHasFocus = it.isFocused || it.hasFocus },
    ) {
        Text("Item $item", Modifier.padding(2.dp))
        if (item == listState.cursorIndex) IconButton({}) { Text("🦆") }
    }
}
