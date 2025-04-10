/*
 * Copyright 2022 The Android Open Source Project
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

package com.example.androidx.mediarouting.data;

import android.app.PendingIntent;
import android.net.Uri;
import android.os.SystemClock;

import androidx.mediarouter.media.MediaItemStatus;

import org.jspecify.annotations.NonNull;

/**
 * PlaylistItem helps keep track of the current status of an media item.
 */
public final class PlaylistItem {
    // immutables
    private final String mSessionId;
    private final String mItemId;
    private final Uri mUri;
    private final String mMime;
    private final String mTitle;
    private final PendingIntent mUpdateReceiver;
    // changeable states
    private int mPlaybackState = MediaItemStatus.PLAYBACK_STATE_PENDING;
    private long mContentPosition;
    private long mContentDuration;
    private long mTimestamp;
    private String mRemoteItemId;

    public PlaylistItem(@NonNull PlaylistItem item) {
        mSessionId = item.mSessionId;
        mItemId = item.mItemId;
        mTitle = item.mTitle;
        mUri = item.mUri;
        mMime = item.mMime;
        mUpdateReceiver = null;

        mPlaybackState = item.mPlaybackState;
        mContentPosition = item.mContentPosition;
        mContentDuration = item.mContentDuration;
        mTimestamp = item.mTimestamp;
        mRemoteItemId = item.mRemoteItemId;
    }

    public PlaylistItem(@NonNull String qid, @NonNull String iid, @NonNull String title,
            @NonNull Uri uri, @NonNull String mime, @NonNull PendingIntent pi) {
        mSessionId = qid;
        mItemId = iid;
        mTitle = title;
        mUri = uri;
        mMime = mime;
        mUpdateReceiver = pi;
        setTimestamp(SystemClock.elapsedRealtime());
    }

    public void setRemoteItemId(@NonNull String riid) {
        mRemoteItemId = riid;
    }

    public void setState(int state) {
        mPlaybackState = state;
    }

    public void setPosition(long pos) {
        mContentPosition = pos;
    }

    public void setTimestamp(long ts) {
        mTimestamp = ts;
    }

    public void setDuration(long duration) {
        mContentDuration = duration;
    }

    public @NonNull String getSessionId() {
        return mSessionId;
    }

    public @NonNull String getItemId() {
        return mItemId;
    }

    public @NonNull String getRemoteItemId() {
        return mRemoteItemId;
    }

    public @NonNull String getTitle() {
        return mTitle;
    }

    public @NonNull Uri getUri() {
        return mUri;
    }

    public @NonNull String getMime() {
        return mMime;
    }

    public @NonNull PendingIntent getUpdateReceiver() {
        return mUpdateReceiver;
    }

    public int getState() {
        return mPlaybackState;
    }

    public long getPosition() {
        return mContentPosition;
    }

    public long getDuration() {
        return mContentDuration;
    }

    public long getTimestamp() {
        return mTimestamp;
    }

    public @NonNull MediaItemStatus getStatus() {
        return new MediaItemStatus.Builder(mPlaybackState)
                .setContentPosition(mContentPosition)
                .setContentDuration(mContentDuration)
                .setTimestamp(mTimestamp)
                .build();
    }

    @Override
    public @NonNull String toString() {
        String[] state = {
                "PENDING",
                "PLAYING",
                "PAUSED",
                "BUFFERING",
                "FINISHED",
                "CANCELED",
                "INVALIDATED",
                "ERROR"
        };
        return "[" + mSessionId + "|" + mItemId + "|"
                + (mRemoteItemId != null ? mRemoteItemId : "-") + "|"
                + state[mPlaybackState] + "] " + mTitle + ": " + mUri.toString();
    }
}
