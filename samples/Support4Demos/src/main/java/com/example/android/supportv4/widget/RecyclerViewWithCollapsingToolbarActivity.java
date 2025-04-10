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
package com.example.android.supportv4.widget;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.android.supportv4.R;
import com.google.android.material.appbar.CollapsingToolbarLayout;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Demonstrates the use of recycler view in nested scrolling (testing scrolling with a keyboard).
 * See the associated layout file for details.
 */
public class RecyclerViewWithCollapsingToolbarActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recycler_view_with_collapsing_toolbar);

        CollapsingToolbarLayout collapsingToolbar = findViewById(R.id.collapsing_toolbar_layout);
        collapsingToolbar.setTitle(
                getResources().getString(R.string.preference_screen_collapsing_appbar_title)
        );
        collapsingToolbar.setContentScrimColor(getResources().getColor(R.color.color1));

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        List<String> data = new ArrayList<String>();
        for (int index = 0; index < 14; index++) {
            data.add(String.valueOf(index));
        }

        MyAdapter adapter = new MyAdapter(data);
        recyclerView.setAdapter(adapter);
    }

    public static class MyAdapter extends RecyclerView.Adapter<MyAdapter.ViewHolder> {
        private final List<String> mDataForItems;

        public MyAdapter(@NonNull List<String> items) {
            this.mDataForItems = items;
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            @NonNull public TextView textViewHeader;
            @NonNull public TextView textViewSubHeader;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                textViewHeader = itemView.findViewById(R.id.textViewHeader);
                textViewSubHeader = itemView.findViewById(R.id.textViewSubHeader);
            }
        }

        @Override
        public MyAdapter.@NonNull ViewHolder onCreateViewHolder(
                @NonNull ViewGroup parent,
                int viewType
        ) {
            View itemView = LayoutInflater.from(parent.getContext()).inflate(
                    R.layout.recycler_view_with_collapsing_toolbar_list_item,
                    parent,
                    false
            );
            return new ViewHolder(itemView);
        }

        @Override
        public void onBindViewHolder(
                MyAdapter.@NonNull ViewHolder holder,
                int position
        ) {
            String number = mDataForItems.get(position);

            holder.textViewHeader.setText(number);
            holder.textViewSubHeader.setText("Sub Header for " + number);
        }

        @Override
        public int getItemCount() {
            return mDataForItems.size();
        }
    }
}
