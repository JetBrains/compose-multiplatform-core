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

@file:JvmName("PagerAsState")

package androidx.paging

import kotlin.jvm.JvmName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow

/**
 * Converts a Flow of [PagingData] into a Flow of [ItemSnapshotList]. An emitted [ItemSnapshotList]
 * contains a snapshot of all loaded data and can be cached for repeated reads.
 *
 * To reflect the latest snapshot, this flow emits a new [ItemSnapshotList] whenever new items are
 * loaded or whenever loaded items are dropped (i.e. to fulfill [PagingConfig.maxSize]).
 *
 * The flow is kept active as long as the collection scope is active. To avoid leaks, make sure to
 * use a scope that is already managed (like a ViewModel scope) or manually cancel it when you don't
 * need paging anymore.
 *
 * To use this flow with multiple collectors, convert it to a SharedFlow with Flow operators such as
 * stateIn() or sharedIn().
 *
 * Note that this Flow remains as a cold flow and does not start any loading until collected upon.
 */
public fun <T : Any> Flow<PagingData<T>>.asState(): Flow<ItemSnapshotList<T>> {
    return channelFlow {
        this@asState.collectLatest { pagingData ->
            pagingData.flow
                .filter { it is PageEvent.Insert || it is PageEvent.Drop }
                .simpleScan(null) { pageStore: PageStore<T>?, pageEvent ->
                    var currPageStore = pageStore
                    when {
                        pageEvent is PageEvent.Insert && pageEvent.loadType == LoadType.REFRESH -> {
                            require(currPageStore == null) {
                                "PageStore should be null on REFRESH. This likely " +
                                    "indicates an error in the library. Please file a bug " +
                                    "in the Buganizer"
                            }
                            currPageStore =
                                PageStore(
                                    pages = pageEvent.pages,
                                    placeholdersBefore = pageEvent.placeholdersBefore,
                                    placeholdersAfter = pageEvent.placeholdersAfter,
                                )
                        }
                        else -> {
                            requireNotNull(pageStore) {
                                "PageStore should only be null on REFRESH. This likely " +
                                    "indicates an error in the library. Please file a bug " +
                                    "in the Buganizer"
                            }
                            currPageStore.processEvent(pageEvent)
                        }
                    }
                    currPageStore
                }
                .filterNotNull()
                .collect { pageStore ->
                    // send to outer channelFlow
                    send(pageStore.snapshot())
                }
        }
    }
}
