/*
 * Copyright (C) 2026 The Geek (Pty) Ltd
 * SPDX-License-Identifier: Apache-2.0
 */
package com.circleos.launcher.metro;

import com.circleos.launcher.R;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Everything on the device that can be launched.
 *
 * Queried once on resume rather than watched: a launcher that rebuilds its
 * list on every package broadcast spends its life doing it during a boot,
 * when dozens of packages settle at once.
 */
final class AppRepository {

    private final Context context;

    AppRepository(Context context) {
        this.context = context;
    }

    List<LaunchableApp> load() {
        PackageManager pm = context.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN, null);
        probe.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> found = pm.queryIntentActivities(probe, 0);
        List<LaunchableApp> apps = new ArrayList<>(found.size());
        String self = context.getPackageName();

        for (ResolveInfo info : found) {
            String pkg = info.activityInfo.packageName;
            // A launcher listing itself is a dead tile.
            if (self.equals(pkg)) {
                continue;
            }
            apps.add(new LaunchableApp(
                    info.loadLabel(pm).toString(),
                    pkg,
                    info.loadIcon(pm)));
        }

        Collections.sort(apps);
        return apps;
    }

    /** The intent that starts an app, or null if it has no launcher entry. */
    Intent launchIntent(LaunchableApp app) {
        return context.getPackageManager()
                .getLaunchIntentForPackage(app.packageName);
    }

    /**
     * The launcher icon for a package, or null when the package is not on
     * this build. Used by the soul tiles (guide 2.3), which are named by
     * package rather than discovered by query - they are a fixed set the OS
     * declares about itself, not whatever happens to be installed.
     */
    Drawable iconFor(String packageName) {
        try {
            return context.getPackageManager().getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}
