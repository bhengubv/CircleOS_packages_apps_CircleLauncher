/*
 * Copyright (C) 2026 The Geek (Pty) Ltd
 * SPDX-License-Identifier: Apache-2.0
 */
package com.circleos.launcher.metro;

import com.circleos.launcher.R;

import android.graphics.drawable.Drawable;

/** One installed, launchable application. */
final class LaunchableApp implements Comparable<LaunchableApp> {
    final String label;
    final String packageName;
    final Drawable icon;

    LaunchableApp(String label, String packageName, Drawable icon) {
        this.label = label;
        this.packageName = packageName;
        this.icon = icon;
    }

    @Override
    public int compareTo(LaunchableApp other) {
        return label.compareToIgnoreCase(other.label);
    }
}
