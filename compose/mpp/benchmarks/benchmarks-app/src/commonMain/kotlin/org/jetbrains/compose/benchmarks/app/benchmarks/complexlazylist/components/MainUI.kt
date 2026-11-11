package org.jetbrains.compose.benchmarks.app.benchmarks.complexlazylist.components

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.benchmarks.app.benchmarks.complexlazylist.components.refresh.LOADING_MORE
import org.jetbrains.compose.benchmarks.app.benchmarks.complexlazylist.components.refresh.LoadingIndicatorDefault
import org.jetbrains.compose.benchmarks.app.benchmarks.complexlazylist.components.refresh.NORMAL
import org.jetbrains.compose.benchmarks.app.benchmarks.complexlazylist.components.refresh.REFRESHING
import org.jetbrains.compose.benchmarks.app.benchmarks.complexlazylist.components.refresh.SwipeRefreshLayout
import org.jetbrains.compose.benchmarks.app.benchmarks.complexlazylist.components.refresh.rememberSwipeRefreshState
import org.jetbrains.compose.benchmarks.app.benchmarks.complexlazylist.models.IBaseViewModel
import org.jetbrains.compose.benchmarks.app.benchmarks.complexlazylist.models.ICompositionModel
import org.jetbrains.compose.benchmarks.app.benchmarks.complexlazylist.models.fetchCompositionModels

// just for demo
internal var models: MutableList<IBaseViewModel> = mutableStateListOf()

@Composable
fun MainUiNoImageUseModel() {
    MaterialTheme {
        MainLazyColumnItemsList(noImage = true, useJson = false)
        DisposableEffect(Unit) {
            onDispose {
                models.clear()
            }
        }
    }
}

@Composable
fun MainLazyColumnItemsList(noImage: Boolean, useJson: Boolean) {
    val scope = rememberCoroutineScope()
    val state = rememberSwipeRefreshState(NORMAL)

    LaunchedEffect(scope) {
        scope.launch(Dispatchers.Default) {
            fetchCompositionModels(false) { list ->
                for (item in list)
                    models.add(item)
            }
        }
    }

    SwipeRefreshLayout(
        state = state,
        indicator = { modifier, s, indicatorHeight ->
            LoadingIndicatorDefault(modifier, s, indicatorHeight)
        },
        onRefresh = {
            scope.launch {
                state.loadState = REFRESHING
                delay(2000)
                fetchCompositionModels(useJson) {
                    models.clear()
                    for (item in it)
                        models.add(item)
                    state.loadState = NORMAL
                }
            }

        },
        onLoadMore = {
            scope.launch {
                state.loadState = LOADING_MORE
                delay(2000L)
                fetchCompositionModels(useJson) {
                    for (item in it)
                        models.add(item)
                    state.loadState = NORMAL
                }
            }
        }
    ) { modifier ->
        run {
            val state = rememberLazyListState()
            LaunchedEffect(Unit) {
                while (true) {
                    withFrameMillis { }
                    state.scrollBy(20f)
                }
            }
            LazyColumn(modifier, state = state) {
                itemsIndexed(
                    items = models,
                    key = { index, _ ->
                        index
                    }
                ) { _, item ->
                    when (item) {
                        is ICompositionModel -> MultiCellUI(item)
                        // .. todo need more types
                        else -> throw RuntimeException("Unexpected")
                    }
                }
            }
        }
    }
}

@Composable
fun MultiCellUI(item: IBaseViewModel) {
    DecoratedCell(item)
}