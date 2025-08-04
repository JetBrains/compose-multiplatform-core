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

package androidx.privacysandbox.ui.integration.testapp.fragments.hidden.views

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.setMargins
import androidx.privacysandbox.ui.integration.testapp.R
import androidx.privacysandbox.ui.integration.testapp.fragments.hidden.AbstractReusableListHiddenFragment
import androidx.privacysandbox.ui.integration.testapp.util.AdHolder
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PoolingContainerHiddenFragment : AbstractReusableListHiddenFragment() {
    private val detachedLatch = CountDownLatch(1)
    lateinit var recyclerView: RecyclerView
    var firstRowAdHolder: AdHolder? = null
    override val itemCount: Int
        get() = recyclerView.adapter?.itemCount ?: 0

    private val onAttachStateChangeListener =
        object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}

            override fun onViewDetachedFromWindow(v: View) {
                detachedLatch.countDown()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val inflatedView =
            inflater.inflate(R.layout.fragment_poolingcontainer_hidden, container, false)
        recyclerView = inflatedView.findViewById(R.id.recycler_view)
        setRecyclerViewAdapter()
        return inflatedView
    }

    override fun loadAd(automatedTestCallbackBundle: Bundle) {
        val adFormat = currentAdFormat
        firstRowAdHolder?.apply {
            currentAdFormat = adFormat
            sandboxedSdkViews.forEach { it.setEventListener(eventListener) }
            CoroutineScope(Dispatchers.Main).launch {
                populateAd(
                    loadAdapterBundle(automatedTestCallbackBundle),
                    adFormat,
                    providerUiOnTop,
                )
            }
        }
    }

    override fun ensureUiIsDetached(callBackWaitMs: Long): Boolean {
        return detachedLatch.await(callBackWaitMs, TimeUnit.MILLISECONDS)
    }

    override fun scrollToPosition(position: Int) {
        recyclerView.smoothScrollToPosition(position)
    }

    override fun removeReusableListFromLayout() {
        (recyclerView.parent as ViewGroup).removeView(recyclerView)
    }

    private fun setRecyclerViewAdapter() {
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.itemAnimator = DefaultItemAnimator()
        recyclerView.adapter = CustomAdapter()
    }

    inner class CustomAdapter() : RecyclerView.Adapter<CustomAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val adHolder: AdHolder =
                view.findViewById<AdHolder>(R.id.recyclerview_hidden_ad_view_holder).apply {
                    adViewLayoutParams =
                        ViewGroup.MarginLayoutParams(adViewLayoutParams).apply {
                            setMargins(convertFromDpToPixels(DEFAULT_MARGIN_DP))
                        }
                    adViewBackgroundColor = Color.RED
                }
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
            val view =
                LayoutInflater.from(viewGroup.context)
                    .inflate(R.layout.recyclerview_row_item_hidden, viewGroup, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            val childAdHolder = viewHolder.adHolder
            if (firstRowAdHolder == null) {
                firstRowAdHolder = childAdHolder
                firstRowAdHolder?.addOnAttachStateChangeListener(onAttachStateChangeListener)
            }
        }

        override fun getItemCount(): Int = CHILD_COUNT
    }

    companion object {
        const val CHILD_COUNT = 3
    }
}
