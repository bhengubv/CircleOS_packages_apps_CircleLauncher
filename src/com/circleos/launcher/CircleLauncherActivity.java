/*
 * Copyright (C) 2024 CircleOS
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circle OS Home Launcher — fork of AOSP Launcher3.
 *
 * Key differences from stock Launcher3:
 *   - No Google Search bar
 *   - Privacy status widget on home screen (shows network blocks, threat blocks)
 *   - Dark navy theme matching Circle OS brand
 *   - Lock screen shows VPN status and last threat block
 */
package com.circleos.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.circleos.server.network.CircleNetworkMonitor;

import java.util.List;

/**
 * Minimal Circle OS home screen launcher.
 *
 * Displays:
 *   - Privacy status widget (top): live network block count
 *   - App grid (center): installed user apps
 *   - Dock (bottom): 5 pinned apps
 *
 * Full Launcher3 integration (drag-drop, folders, widgets) is Phase 5.
 * This establishes the shell and privacy widget.
 */
public class CircleLauncherActivity extends Activity {

    private static final String TAG = "CircleLauncher";

    private TextView mBlockCountView;
    private int      mBlockCount = 0;

    private final BroadcastReceiver mNetworkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getStringExtra(CircleNetworkMonitor.EXTRA_ACTION);
            if ("BLOCKED".equals(action)) {
                mBlockCount++;
                updatePrivacyWidget();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);
        mBlockCountView = findViewById(R.id.privacy_block_count);
        updatePrivacyWidget();

        // Tap privacy widget → open CircleSettings
        View privacyWidget = findViewById(R.id.privacy_widget);
        privacyWidget.setOnClickListener(v -> {
            Intent settings = getPackageManager()
                    .getLaunchIntentForPackage("com.circleos.settings");
            if (settings != null) startActivity(settings);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(CircleNetworkMonitor.ACTION_NETWORK_EVENT);
        registerReceiver(mNetworkReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mNetworkReceiver);
    }

    private void updatePrivacyWidget() {
        if (mBlockCountView != null) {
            String text = mBlockCount == 0
                    ? "Protected — no threats detected"
                    : mBlockCount + " connection" + (mBlockCount == 1 ? "" : "s") + " blocked";
            mBlockCountView.setText(text);
        }
    }
}
