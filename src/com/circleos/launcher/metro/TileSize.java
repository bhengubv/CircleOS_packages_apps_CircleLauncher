/*
 * Copyright (C) 2026 The Geek (Pty) Ltd
 * SPDX-License-Identifier: Apache-2.0
 */
package com.circleos.launcher.metro;

import com.circleos.launcher.R;

/**
 * Tile sizes from CircleOS_Skin_Design_Guide.md 1.1, in small-cell units:
 * small 1x1, medium 2x2, wide 4x2, large 4x4.
 *
 * The guide also calls for the user to resize and pin (WP-11, WP-12). Until
 * that lands, a tile's size is decided once, by rule, in
 * {@link #defaultFor}. A start screen where every tile is the same size is
 * a grid of icons; the mixture is what makes it Metro.
 */
enum TileSize {
    SMALL(1, 1),
    MEDIUM(2, 2),
    WIDE(4, 2),
    LARGE(4, 4);

    final int cols;
    final int rows;

    TileSize(int cols, int rows) {
        this.cols = cols;
        this.rows = rows;
    }

    /**
     * Circle's own apps are the reason the device exists, so they get the
     * medium tile that carries a readable name. Everything else - the
     * handful of AOSP and store apps - takes a small tile, which is also
     * how Windows Phone treated things you had not chosen to feature.
     */
    static TileSize defaultFor(String packageName) {
        if (packageName.startsWith("za.co.circleos")
                || packageName.startsWith("com.circleos")
                || packageName.startsWith("co.za.circleos")) {
            return MEDIUM;
        }
        return SMALL;
    }
}
