package androidx.compose.mpp.demo

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalWideNavigationRail
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue.Expanded
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.mpp.demo.components.material.AlertDialogExample
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch

class App(
    private val initialScreenName: String? = null,
    private val extraScreens: List<Screen> = listOf()
) {
    @Composable
    fun Content(navController: NavHostController = rememberNavController()) {
        ModalWideNavigationRailExample1()
        // TextWithInline()
//        val animationSpec = tween<IntOffset>(500)
//        NavHost(
//            navController = navController,
//            startDestination = initialScreenName ?: MainScreen.title,
//
//            // Custom animations
//            enterTransition = { fadeIn() },
//            exitTransition = { ExitTransition.None },
//            popEnterTransition = { EnterTransition.None },
//            popExitTransition = {
//                slideOutOfContainer(
//                    towards = SlideDirection.Right,
//                    targetOffset = { it / 2 },
//                    animationSpec = animationSpec
//                )
//            }
//        ) {
//            buildScreen(MainScreen.mergedWith(extraScreens), navController)
//        }
    }


    @Composable
    fun TextWithInline(modifier: Modifier = Modifier) {
        val buttonClicked = remember { mutableStateOf(0) }
        val clickAt = remember { mutableStateOf("") }
        val content = buildAnnotatedString {
            append("Compose Multiplatform is a declarative framework for sharing UI code across multiple platforms with Kotlin. It is based on Jetpack Compose and developed by JetBrains and open-source contributors.")
            appendInlineContent("[QuiteLongInlineTag]", "[ ]")
            withLink(LinkAnnotation.Clickable("Button") {
                print("Click on button")
                buttonClicked.value += 1
            }) {
                append(" Button")
            }
        }
        val textLayoutResultState = remember {
            mutableStateOf<TextLayoutResult?>(null)
        }
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(buttonClicked.value.toString())
            Text(clickAt.value)
            Text(
                text = content,
                modifier = Modifier.fillMaxWidth().pointerInput(content) {
                    awaitEachGesture {
                        val result = textLayoutResultState.value ?: return@awaitEachGesture
                        val down =
                            awaitFirstDown(
                                pass = PointerEventPass.Initial,
                            )
                        val downOffset = down.position
                        val textIndex = result.getOffsetForPosition(downOffset)
                        val downText = content.getOrNull(textIndex - 1)
                        clickAt.value =
                            "display text: $content down at $downOffset, index $textIndex, char=$downText"

                    }
                },
                inlineContent = mapOf(
                    "[QuiteLongInlineTag]" to InlineTextContent(
                        placeholder = Placeholder(
                            width = 15.sp,
                            height = 15.sp,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                        ),
                    ) {
                        val size = with(LocalDensity.current) {
                            15.sp.toDp()
                        }
                        Box(modifier = Modifier.size(size).background(color = Color.Red))
                    }),
                onTextLayout = {
                    textLayoutResultState.value = it
                },
            )
        }
    }



    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ModalWideNavigationRailExample1() {
        var selectedItem by remember { mutableIntStateOf(0) }
        val items = listOf("Home", "Search", "Settings")
        val selectedIcons = listOf(Icons.Filled.Home, Icons.Filled.Search, Icons.Filled.Settings)
        val unselectedIcons =
            listOf(Icons.Outlined.Home, Icons.Outlined.Search, Icons.Outlined.Settings)
        val state = rememberWideNavigationRailState()
        val scope = rememberCoroutineScope()

        Row(Modifier.fillMaxWidth()) {
            ModalWideNavigationRail(
                state = state,
                expandedHeaderTopPadding = 86.dp,
                modifier = Modifier.padding(16.dp),
                header = {
                    IconButton(
                        modifier =
                            Modifier.padding(start = 24.dp).semantics {
                                stateDescription =
                                    if (state.currentValue == Expanded) {
                                        "Expanded"
                                    } else {
                                        "Collapsed"
                                    }
                            },
                        onClick = {
                            scope.launch {
                                if (state.currentValue == Expanded)
                                    state.collapse()
                                else state.expand()
                            }
                        }
                    ) {
                        if (state.currentValue == Expanded) {
                            Icon(Icons.AutoMirrored.Filled.List, "Collapse rail")
                        } else {
                            Icon(Icons.Filled.Menu, "Expand rail")
                        }
                    }
                }
            ) {
                items.forEachIndexed { index, item ->
                    WideNavigationRailItem(
                        railExpanded = state.currentValue == Expanded,
                        icon = {
                            Icon(
                                if (selectedItem == index) selectedIcons[index]
                                else unselectedIcons[index],
                                contentDescription = item
                            )
                        },
                        label = { Text(item) },
                        selected = selectedItem == index,
                        onClick = { selectedItem = index }
                    )
                }
            }

            var showDialog by remember { mutableStateOf(false) }
            val textString = if (state.currentValue == Expanded) "Expanded" else "Collapsed"
            Column {
                Text(modifier = Modifier.padding(100.dp), text = "The rail is $textString")
                Text(modifier = Modifier.padding(50.dp), text = "Test content")
                Button({
                    showDialog = true
                }) {
                    Text("Dialog")
                }
            }
            if (showDialog) {
                AlertDialog(
                    onDismissRequest = { showDialog = false },
                    confirmButton = {
                        androidx.compose.material.Button(onClick = { showDialog = false }) {
                            Text("OK")
                        }
                    },
                    dismissButton = {
                        Button(onClick = { showDialog = false }) {
                            Text("Cancel")
                        }
                    },
                    title = { Text("Alert Dialog") },
                    text = { Text("loremIpsum()") },
                )
            }
        }
    }

    private fun NavGraphBuilder.buildScreen(screen: Screen, navController: NavController) {
        if (screen is Screen.Selection) {
            for (i in screen.screens) {
                buildScreen(i, navController)
            }
        }
        if (screen is Screen.Dialog) {
            dialog(screen.title) { ScreenContent(screen, navController) }
        } else {
            composable(screen.title) { ScreenContent(screen, navController) }
        }
    }

    @Composable
    private fun ScreenContent(screen: Screen, navController: NavController) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val currentBackStack = remember(screen) { navController.currentBackStack.value }
        screen.Content(
            title = currentBackStack.drop(1)
                .joinToString("/") { it.destination.route ?: it.destination.displayName },
            navigate = { navController.navigate(it) },
            back = back@{
                // Filter multi-click by current lifecycle state: it's not [RESUMED] in case if
                // a navigation transaction is in progress or the window is not focused.
                if (lifecycle.currentState < Lifecycle.State.RESUMED) {
                    return@back
                }
                if (navController.previousBackStackEntry != null) {
                    navController.popBackStack()
                }
            }
        )
    }
}
