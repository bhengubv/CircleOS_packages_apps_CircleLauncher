/*
 * Copyright (C) 2026 The Geek (Pty) Ltd
 * SPDX-License-Identifier: Apache-2.0
 */
package com.circleos.launcher.metro;

import com.circleos.launcher.R;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/** The long list. Guide 1.6: type leads, chrome is minimal. */
final class AppListAdapter extends BaseAdapter {

    private final Context context;
    private final List<LaunchableApp> apps;

    AppListAdapter(Context context, List<LaunchableApp> apps) {
        this.context = context;
        this.apps = apps;
    }

    @Override public int getCount() { return apps.size(); }
    @Override public Object getItem(int position) { return apps.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View recycled, ViewGroup parent) {
        View view = recycled;
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.app_row, parent, false);
        }
        LaunchableApp app = apps.get(position);
        ((TextView) view.findViewById(R.id.row_label))
                .setText(app.label.toLowerCase(Locale.getDefault()));
        ((ImageView) view.findViewById(R.id.row_glyph)).setImageDrawable(app.icon);
        return view;
    }
}
