/*
 * Copyright 2017 The Android Open Source Project
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

package androidx.recyclerview.selection;

import static androidx.core.util.Preconditions.checkArgument;

import android.view.MotionEvent;

import androidx.recyclerview.selection.ItemDetailsLookup.ItemDetails;
import androidx.recyclerview.widget.RecyclerView;

import org.jspecify.annotations.NonNull;

/**
 * A MotionInputHandler that provides the high-level glue for mouse driven selection. This
 * class works with {@link RecyclerView}, {@link GestureRouter}, and {@link GestureSelectionHelper}
 * to implement the primary policies around mouse input.
 */
final class MouseInputHandler<K> extends MotionInputHandler<K> {

    private static final String TAG = "MouseInputHandler";

    private final ItemDetailsLookup<K> mDetailsLookup;
    private final OnContextClickListener mOnContextClickListener;
    private final OnItemActivatedListener<K> mOnItemActivatedListener;
    private final FocusDelegate<K> mFocusDelegate;

    MouseInputHandler(
            @NonNull SelectionTracker<K> selectionTracker,
            @NonNull ItemKeyProvider<K> keyProvider,
            @NonNull ItemDetailsLookup<K> detailsLookup,
            @NonNull OnContextClickListener onContextClickListener,
            @NonNull OnItemActivatedListener<K> onItemActivatedListener,
            @NonNull FocusDelegate<K> focusDelegate) {

        super(selectionTracker, keyProvider, focusDelegate);

        checkArgument(detailsLookup != null);
        checkArgument(onContextClickListener != null);
        checkArgument(onItemActivatedListener != null);

        mDetailsLookup = detailsLookup;
        mOnContextClickListener = onContextClickListener;
        mOnItemActivatedListener = onItemActivatedListener;
        mFocusDelegate = focusDelegate;
    }

    @Override
    public boolean onDown(@NonNull MotionEvent e) {
        if ((MotionEvents.isAltKeyPressed(e) && MotionEvents.isPrimaryMouseButtonPressed(e))
                || MotionEvents.isSecondaryMouseButtonPressed(e)) {
            return onRightClick(e);
        }

        return false;
    }

    @Override
    public boolean onScroll(@NonNull MotionEvent e1, @NonNull MotionEvent e2,
            float distanceX, float distanceY) {
        // Don't scroll content window in response to mouse drag
        // If it's two-finger trackpad scrolling, we want to scroll
        return !MotionEvents.isTouchpadScroll(e2);
    }

    // Called when left-clicking on an item and there is an existing selection (which may or may
    // not include that item). We extend / clear / modify the selection (and adjust focus).
    private void onLeftClickWhenSomethingSelected(
            @NonNull MotionEvent e, @NonNull ItemDetails<K> item) {
        checkArgument(item != null);

        if (shouldExtendRange(e)) {
            extendSelectionRange(item);
        } else {
            if (shouldClearSelection(e, item)) {
                mSelectionTracker.clearSelection();
            }
            if (mSelectionTracker.isSelected(item.getSelectionKey())) {
                if (mSelectionTracker.deselect(item.getSelectionKey())) {
                    mFocusDelegate.clearFocus();
                }
            } else {
                selectOrFocusItem(item, e);
            }
        }
    }

    @Override
    public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
        if (MotionEvents.isAltKeyPressed(e) || !MotionEvents.isPrimaryMouseButtonPressed(e)) {
            return false;
        }

        ItemDetails<K> item = mDetailsLookup.overItemWithSelectionKeyAsItem(e);
        if (item == null) {
            mSelectionTracker.clearSelection();
            mFocusDelegate.clearFocus();
            return false;
        }

        if (mSelectionTracker.hasSelection()) {
            onLeftClickWhenSomethingSelected(e, item);
            return true;
        }

        if (mFocusDelegate.hasFocusedItem() && MotionEvents.isShiftKeyPressed(e)) {
            mSelectionTracker.startRange(mFocusDelegate.getFocusedPosition());
            mSelectionTracker.extendRange(item.getPosition());
        } else {
            selectOrFocusItem(item, e);
        }
        return true;
    }

    @Override
    public boolean onDoubleTap(@NonNull MotionEvent e) {
        if (MotionEvents.isAltKeyPressed(e) || !MotionEvents.isPrimaryMouseButtonPressed(e)) {
            return false;
        }

        ItemDetails<K> item = mDetailsLookup.overItemWithSelectionKeyAsItem(e);
        return (item != null) && mOnItemActivatedListener.onItemActivated(item, e);
    }

    private boolean onRightClick(@NonNull MotionEvent e) {
        ItemDetails<K> item = mDetailsLookup.overItemWithSelectionKeyAsItem(e);
        if ((item != null) && !mSelectionTracker.isSelected(item.getSelectionKey())) {
            mSelectionTracker.clearSelection();
            selectItem(item);
        }

        // We always delegate final handling of the event,
        // since the handler might want to show a context menu
        // in an empty area or some other weirdo view.
        return mOnContextClickListener.onContextClick(e);
    }

    private void selectOrFocusItem(@NonNull ItemDetails<K> item, @NonNull MotionEvent e) {
        if (item.inSelectionHotspot(e) || MotionEvents.isCtrlKeyPressed(e)) {
            selectItem(item);
        } else {
            focusItem(item);
        }
    }
}
