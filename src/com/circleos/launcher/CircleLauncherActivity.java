/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle Launcher — Circle OS home screen.
 *
 * Design elements from the Circle OS design language:
 *   - 4-column app grid with rounded superellipse icon masks
 *   - Bottom dock with 5 pinned apps (auto-detected from common packages)
 *   - Flat search bar above dock
 *   - Privacy status card at top (Circle's unique element)
 *   - True-black canvas, flat system bars
 *   - Clean typography, generous spacing
 *
 * Tap search bar → opens Android global search.
 * Tap privacy card → opens CircleSettings Privacy Dashboard.
 * Tap dock app → launches it.
 * Tap grid app → launches it.
 */
package com.circleos.launcher;

import android.app.Activity;
import android.app.SearchManager;
import android.circleos.privacy.ICirclePrivacyManagerService;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CircleLauncherActivity extends Activity {

    private static final String TAG = "CircleLauncher";

    /** Dock candidate packages in priority order. First 5 found get pinned. */
    private static final String[] DOCK_CANDIDATES = {
            "com.android.dialer",          // Phone
            "com.android.messaging",       // Messages (AOSP)
            "za.co.circleos.messages",     // CircleMessages
            "com.android.chrome",          // Browser
            "org.chromium.chrome",          // Chromium
            "com.android.browser",         // AOSP browser fallback
            "com.android.camera2",         // Camera
            "com.android.camera",          // Camera fallback
            "za.co.circleos.settings",     // CircleSettings
            "com.circleos.settings",       // CircleSettings (upstream pkg)
            "org.fdroid.fdroid",           // F-Droid (app store)
            "za.co.circleos.butler",       // Butler AI
    };

    private static final int DOCK_SIZE = 5;
    private static final int ICON_SIZE_DP = 56;
    private static final float SQUIRCLE_RADIUS_RATIO = 0.28f;

    private TextView mPrivacyStatusText;
    private TextView mPrivacyScoreText;
    private GridView mAppGrid;
    private final ImageView[] mDockIcons = new ImageView[DOCK_SIZE];
    private final ResolveInfo[] mDockApps = new ResolveInfo[DOCK_SIZE];
    private TextView mClockTime;
    private TextView mClockDate;

    /** Updates the live clock tile on every minute tick / time change. */
    private final android.content.BroadcastReceiver mTimeReceiver =
            new android.content.BroadcastReceiver() {
        @Override public void onReceive(android.content.Context c, Intent i) { updateClock(); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);

        mPrivacyStatusText = findViewById(R.id.privacy_status_text);
        mPrivacyScoreText  = findViewById(R.id.privacy_score_text);
        mAppGrid           = findViewById(R.id.app_grid);
        mClockTime = findViewById(R.id.clock_time);
        mClockDate = findViewById(R.id.clock_date);

        mDockIcons[0] = findViewById(R.id.dock_1);
        mDockIcons[1] = findViewById(R.id.dock_2);
        mDockIcons[2] = findViewById(R.id.dock_3);
        mDockIcons[3] = findViewById(R.id.dock_4);
        mDockIcons[4] = findViewById(R.id.dock_5);

        // Privacy card → CircleSettings Privacy Dashboard
        findViewById(R.id.privacy_card).setOnClickListener(v -> openPrivacyDashboard());

        // Search bar → Android global search
        findViewById(R.id.search_bar).setOnClickListener(v -> openSearch());

        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPrivacyWidget();
        updateClock();
        android.content.IntentFilter tf = new android.content.IntentFilter();
        tf.addAction(Intent.ACTION_TIME_TICK);
        tf.addAction(Intent.ACTION_TIME_CHANGED);
        tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(mTimeReceiver, tf);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(mTimeReceiver); } catch (Throwable ignored) {}
    }

    /** Live clock tile - time respects the user's 12/24h setting. */
    private void updateClock() {
        java.util.Date now = new java.util.Date();
        if (mClockTime != null) {
            mClockTime.setText(
                    android.text.format.DateFormat.getTimeFormat(this).format(now));
        }
        if (mClockDate != null) {
            mClockDate.setText(new java.text.SimpleDateFormat(
                    "EEEE, d MMMM", java.util.Locale.getDefault()).format(now));
        }
    }

    // ------------------------------------------------------------------
    //  Privacy widget
    // ------------------------------------------------------------------

    private void refreshPrivacyWidget() {
        String status = "Protected";
        String score  = "";
        try {
            IBinder b = ServiceManager.getService("circle.privacy");
            if (b != null) {
                ICirclePrivacyManagerService svc =
                        ICirclePrivacyManagerService.Stub.asInterface(b);
                int denied = svc.getDeniedPermissionCount();
                int faked  = svc.getFakedIdentifierCount();
                int grants = svc.getNetworkGrantCount();
                if (denied + faked > 0) {
                    status = denied + " blocked, " + faked + " faked";
                }
                score = grants + " net grants";
            }
        } catch (RemoteException e) {
            Log.w(TAG, "circle.privacy unreachable", e);
        } catch (Throwable t) {
            Log.w(TAG, "Privacy widget failed", t);
        }
        mPrivacyStatusText.setText(status);
        mPrivacyScoreText.setText(score);
    }

    private void openPrivacyDashboard() {
        // Try the explicit action first
        Intent i = new Intent("com.circleos.action.OPEN_PRIVACY_DASHBOARD");
        if (i.resolveActivity(getPackageManager()) != null) {
            startActivity(i);
            return;
        }
        // Fallback: launch CircleSettings main activity
        for (String pkg : new String[]{
                "za.co.circleos.settings", "com.circleos.settings"}) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) {
                startActivity(launch);
                return;
            }
        }
    }

    private void openSearch() {
        try {
            Intent search = new Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH);
            search.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(search);
        } catch (Throwable t) {
            Log.w(TAG, "Global search not available", t);
        }
    }

    // ------------------------------------------------------------------
    //  App grid + dock loading
    // ------------------------------------------------------------------

    private void loadApps() {
        final PackageManager pm = getPackageManager();
        final Intent main = new Intent(Intent.ACTION_MAIN, null);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        final List<ResolveInfo> allApps = pm.queryIntentActivities(main, 0);

        // Determine dock apps: pinned first (WP-12), then DOCK_CANDIDATES
        final Set<String> dockedPkgs = new LinkedHashSet<>();
        int dockIdx = 0;
        final List<String> dockOrder = new ArrayList<>(loadPinned());
        for (String c : DOCK_CANDIDATES) dockOrder.add(c);
        for (String candidate : dockOrder) {
            if (dockIdx >= DOCK_SIZE) break;
            for (ResolveInfo ri : allApps) {
                if (candidate.equals(ri.activityInfo.packageName)) {
                    if (dockedPkgs.add(candidate)) {
                        mDockApps[dockIdx] = ri;
                        dockIdx++;
                    }
                    break;
                }
            }
        }

        // Fill remaining dock slots with the first non-docked, non-self apps
        if (dockIdx < DOCK_SIZE) {
            for (ResolveInfo ri : allApps) {
                if (dockIdx >= DOCK_SIZE) break;
                String pkg = ri.activityInfo.packageName;
                if (dockedPkgs.contains(pkg)) continue;
                if (getPackageName().equals(pkg)) continue;
                mDockApps[dockIdx] = ri;
                dockedPkgs.add(pkg);
                dockIdx++;
            }
        }

        // Set dock icons
        final int iconPx = dpToPx(48);
        for (int i = 0; i < DOCK_SIZE; i++) {
            if (mDockApps[i] != null) {
                mDockIcons[i].setImageDrawable(
                        roundIcon(mDockApps[i].loadIcon(pm), iconPx));
                final int idx = i;
                mDockIcons[i].setOnClickListener(v -> launchApp(mDockApps[idx]));
                mDockIcons[i].setOnLongClickListener(v -> {
                    ResolveInfo di = mDockApps[idx];
                    if (di == null) return false;
                    final String pkg = di.activityInfo.packageName;
                    new android.app.AlertDialog.Builder(CircleLauncherActivity.this)
                            .setTitle(di.loadLabel(pm))
                            .setItems(new CharSequence[]{"Unpin from dock"},
                                    (d, w) -> unpinFromDock(pkg))
                            .show();
                    return true;
                });
            } else {
                mDockIcons[i].setVisibility(View.INVISIBLE);
            }
        }

        // Grid apps = all apps minus self minus docked, sorted A->Z by label
        final List<ResolveInfo> gridApps = new ArrayList<>();
        for (ResolveInfo ri : allApps) {
            String pkg = ri.activityInfo.packageName;
            if (getPackageName().equals(pkg)) continue;
            if (dockedPkgs.contains(pkg)) continue;
            gridApps.add(ri);
        }
        final java.util.Map<ResolveInfo, String> labels = new java.util.HashMap<>();
        for (ResolveInfo ri : gridApps) labels.put(ri, String.valueOf(ri.loadLabel(pm)));
        gridApps.sort((a, b) -> labels.get(a).compareToIgnoreCase(labels.get(b)));

        mAppGrid.setAdapter(new AppAdapter(gridApps, pm, labels));
        mAppGrid.setFastScrollEnabled(true);          // WP-78: alphabetical jump
        mAppGrid.setOnItemClickListener((parent, view, position, id) ->
                launchApp((ResolveInfo) parent.getItemAtPosition(position)));
        mAppGrid.setOnItemLongClickListener((parent, view, position, id) -> {
            ResolveInfo gi = (ResolveInfo) parent.getItemAtPosition(position);
            if (gi == null) return false;
            final String pkg = gi.activityInfo.packageName;
            new android.app.AlertDialog.Builder(CircleLauncherActivity.this)
                    .setTitle(gi.loadLabel(getPackageManager()))
                    .setItems(new CharSequence[]{"Pin to dock"},
                            (d, w) -> pinToDock(pkg))
                    .show();
            return true;
        });
    }

    // WP-12: pin apps to the dock, persisted in SharedPreferences.
    private java.util.List<String> loadPinned() {
        String s = getSharedPreferences("circle_launcher", MODE_PRIVATE)
                .getString("pinned_dock", "");
        java.util.List<String> out = new ArrayList<>();
        if (!s.isEmpty()) {
            for (String p : s.split(",")) if (!p.isEmpty()) out.add(p);
        }
        return out;
    }

    private void savePinned(java.util.List<String> pinned) {
        getSharedPreferences("circle_launcher", MODE_PRIVATE).edit()
                .putString("pinned_dock", String.join(",", pinned)).apply();
    }

    private void pinToDock(String pkg) {
        java.util.List<String> p = loadPinned();
        p.remove(pkg);
        p.add(0, pkg);
        while (p.size() > DOCK_SIZE) p.remove(p.size() - 1);
        savePinned(p);
        loadApps();
    }

    private void unpinFromDock(String pkg) {
        java.util.List<String> p = loadPinned();
        if (p.remove(pkg)) {
            savePinned(p);
            loadApps();
        }
    }

    private void launchApp(ResolveInfo ri) {
        if (ri == null) return;
        Intent i = getPackageManager().getLaunchIntentForPackage(
                ri.activityInfo.packageName);
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        }
    }

    // ------------------------------------------------------------------
    //  Adapter
    // ------------------------------------------------------------------

    private final class AppAdapter extends BaseAdapter
            implements android.widget.SectionIndexer {
        private final List<ResolveInfo> mApps;
        private final PackageManager mPm;
        private final int mIconPx;
        private final java.util.Map<ResolveInfo, String> mLabels;
        private final String[] mSections;

        AppAdapter(List<ResolveInfo> apps, PackageManager pm,
                   java.util.Map<ResolveInfo, String> labels) {
            mApps   = apps;
            mPm     = pm;
            mLabels = labels;
            mIconPx = dpToPx(ICON_SIZE_DP);
            java.util.LinkedHashSet<String> secs = new java.util.LinkedHashSet<>();
            for (ResolveInfo ri : apps) secs.add(sectionOf(ri));
            mSections = secs.toArray(new String[0]);
        }

        private String sectionOf(ResolveInfo ri) {
            String l = mLabels.get(ri);
            if (l == null || l.isEmpty()) return "#";
            char c = Character.toUpperCase(l.charAt(0));
            return Character.isLetter(c) ? String.valueOf(c) : "#";
        }

        @Override public int getCount()              { return mApps.size(); }
        @Override public ResolveInfo getItem(int p)  { return mApps.get(p); }
        @Override public long getItemId(int p)       { return p; }

        @Override public Object[] getSections()      { return mSections; }

        @Override
        public int getPositionForSection(int section) {
            if (section < 0) section = 0;
            if (section >= mSections.length) section = mSections.length - 1;
            String want = mSections[section];
            for (int i = 0; i < mApps.size(); i++) {
                if (want.equals(sectionOf(mApps.get(i)))) return i;
            }
            return 0;
        }

        @Override
        public int getSectionForPosition(int position) {
            if (position < 0 || position >= mApps.size()) return 0;
            String sec = sectionOf(mApps.get(position));
            for (int i = 0; i < mSections.length; i++) {
                if (mSections[i].equals(sec)) return i;
            }
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView != null
                    ? convertView
                    : LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.app_grid_cell, parent, false);
            ResolveInfo info = mApps.get(position);
            ImageView icon = row.findViewById(R.id.app_icon);
            icon.setImageDrawable(roundIcon(info.loadIcon(mPm), mIconPx));
            ((TextView) row.findViewById(R.id.app_label))
                    .setText(mLabels.get(info));
            return row;
        }
    }

    // ------------------------------------------------------------------
    //  Rounded superellipse icon mask (Circle OS style)
    // ------------------------------------------------------------------

    /**
     * Applies a rounded superellipse (squircle) clip to the drawable,
     * matching Circle OS's icon shape. The radius ratio controls how
     * "square" vs "round" the corners are — 0.28 matches Circle OS closely.
     */
    private Drawable roundIcon(Drawable original, int sizePx) {
        if (original == null) return null;
        try {
            Bitmap src = drawableToBitmap(original, sizePx);
            Bitmap out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(out);

            // Superellipse approximation via RoundRect with large radius
            float radius = sizePx * SQUIRCLE_RADIUS_RATIO;
            Path path = new Path();
            RectF rect = new RectF(0, 0, sizePx, sizePx);
            path.addRoundRect(rect, radius, radius, Path.Direction.CW);

            canvas.clipPath(path);
            canvas.drawBitmap(src, 0, 0, null);

            return new BitmapDrawable(getResources(), out);
        } catch (Throwable t) {
            return original;
        }
    }

    private static Bitmap drawableToBitmap(Drawable d, int sizePx) {
        if (d instanceof BitmapDrawable) {
            Bitmap bm = ((BitmapDrawable) d).getBitmap();
            if (bm != null && bm.getWidth() > 0) {
                return Bitmap.createScaledBitmap(bm, sizePx, sizePx, true);
            }
        }
        Bitmap bm = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        d.setBounds(0, 0, sizePx, sizePx);
        d.draw(c);
        return bm;
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
