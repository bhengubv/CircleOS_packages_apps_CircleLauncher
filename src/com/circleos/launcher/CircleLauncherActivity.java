/*
 * Copyright (C) 2026 CircleOS
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle OS Home Launcher.
 *
 * Minimal viable launcher:
 *   - Privacy status widget at top (queries circle.privacy service for
 *     live counters)
 *   - Grid of installed user-facing apps (queries PackageManager for
 *     LAUNCHER-category activities)
 *   - Tap an app icon to launch it
 *   - Tap the privacy widget to open CircleSettings
 *
 * Phase-2 work (not in this CL): drag-and-drop reorder, folders,
 * AppWidget host, dock row, wallpaper picker. Phase 2 is when we replace
 * this with a Launcher3 fork; this scaffold gets us a buildable, bootable
 * default home for alpha.
 */
package com.circleos.launcher;

import android.app.Activity;
import android.circleos.privacy.ICirclePrivacyManagerService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

public class CircleLauncherActivity extends Activity {

    private static final String TAG = "CircleLauncher";

    private TextView mPrivacyScoreView;
    private GridView mAppGrid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);

        mPrivacyScoreView = findViewById(R.id.privacy_status_text);
        mAppGrid          = findViewById(R.id.app_grid);

        // Tap privacy widget -> open CircleSettings
        View privacyWidget = findViewById(R.id.privacy_widget);
        privacyWidget.setOnClickListener(v -> openCircleSettings());

        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPrivacyWidget();
    }

    // ------------------------------------------------------------------
    //  Privacy widget
    // ------------------------------------------------------------------

    private void refreshPrivacyWidget() {
        // Try to read live counters from circle.privacy. Falls back to
        // a static "Protected" message if the service hasn't published
        // yet (boot race) or the binder errors.
        String text = "Protected";
        try {
            IBinder b = ServiceManager.getService("circle.privacy");
            if (b != null) {
                ICirclePrivacyManagerService svc =
                        ICirclePrivacyManagerService.Stub.asInterface(b);
                int denied = svc.getDeniedPermissionCount();
                int grants = svc.getNetworkGrantCount();
                text = "Protected -- " + denied + " denied, " + grants + " network grants";
            }
        } catch (RemoteException e) {
            Log.w(TAG, "circle.privacy unreachable", e);
        } catch (Throwable t) {
            // Defensive: never crash the home screen because of a stat fetch.
            Log.w(TAG, "Privacy widget refresh failed", t);
        }
        mPrivacyScoreView.setText(text);
    }

    private void openCircleSettings() {
        Intent i = getPackageManager().getLaunchIntentForPackage("com.circleos.settings");
        if (i == null) {
            // CircleSettings not installed; try the vendor companion.
            i = getPackageManager().getLaunchIntentForPackage("za.co.circleos.settings");
        }
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } else {
            Log.w(TAG, "Neither CircleSettings nor CircleOsSettings installed");
        }
    }

    // ------------------------------------------------------------------
    //  App grid
    // ------------------------------------------------------------------

    private void loadApps() {
        Intent main = new Intent(Intent.ACTION_MAIN, null);
        main.addCategory(Intent.CATEGORY_LAUNCHER);

        final PackageManager pm = getPackageManager();
        List<ResolveInfo> apps = pm.queryIntentActivities(main, 0);

        // Exclude self so the launcher doesn't appear in its own grid.
        apps.removeIf(info -> getPackageName().equals(info.activityInfo.packageName));

        mAppGrid.setAdapter(new AppAdapter(apps, pm));
        mAppGrid.setOnItemClickListener((parent, view, position, id) -> {
            ResolveInfo info = (ResolveInfo) parent.getItemAtPosition(position);
            Intent launch = pm.getLaunchIntentForPackage(info.activityInfo.packageName);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launch);
            }
        });
    }

    private final class AppAdapter extends BaseAdapter {
        private final List<ResolveInfo> mApps;
        private final PackageManager    mPm;

        AppAdapter(List<ResolveInfo> apps, PackageManager pm) {
            mApps = apps;
            mPm   = pm;
        }

        @Override public int getCount()              { return mApps.size(); }
        @Override public ResolveInfo getItem(int p)  { return mApps.get(p); }
        @Override public long getItemId(int p)       { return p; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView != null
                    ? convertView
                    : LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.app_grid_cell, parent, false);
            ResolveInfo info = mApps.get(position);
            ((ImageView) row.findViewById(R.id.app_icon)).setImageDrawable(
                    info.loadIcon(mPm));
            ((TextView)  row.findViewById(R.id.app_label)).setText(info.loadLabel(mPm));
            return row;
        }
    }
}
