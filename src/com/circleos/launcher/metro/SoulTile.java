/*
 * Copyright (C) 2026 The Geek (Pty) Ltd
 * SPDX-License-Identifier: Apache-2.0
 */
package com.circleos.launcher.metro;

import com.circleos.launcher.R;

import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * Guide 2.3 - "soul tiles by default".
 *
 * "The out-of-box home surfaces what Circle OS *means*, not generic widgets:
 *  a live AetherNet mesh tile, the SDPKT wallet tile, B! - the tile content
 *  is itself a differentiator nobody else has."
 *
 * Honesty rule for this class: a soul tile reports only what can actually be
 * read on this build. Where there is no API to ask, it says so rather than
 * showing a plausible number. A tile that invents "3 peers" is worse than a
 * tile that admits it does not know - it trains you to distrust the screen,
 * and on a privacy OS the home screen is the last place that should lie.
 */
public final class SoulTile {

    public final String packageName;
    public final int labelRes;

    /** True when this component exists on the build at all. */
    public boolean installed;

    /** Resolved at bind time; a resource id from strings.xml. */
    public int stateRes;

    /** Presence is shown only when the state is genuinely live. */
    public boolean live;

    private SoulTile(String packageName, int labelRes) {
        this.packageName = packageName;
        this.labelRes = labelRes;
    }

    /**
     * The three the guide names, in the order it names them.
     */
    public static List<SoulTile> all(Context context) {
        List<SoulTile> candidates = new ArrayList<>(3);
        candidates.add(new SoulTile("com.circleos.aether",  R.string.soul_mesh));
        candidates.add(new SoulTile("za.co.circleos.sdpkt", R.string.soul_wallet));
        candidates.add(new SoulTile("com.circleos.heyb",    R.string.soul_assistant));

        // Only what is actually on the device. A tile for absent code is a
        // placeholder, and a placeholder is the opposite of what guide 2.3
        // asks for - the home screen is supposed to surface what Circle OS
        // MEANS, and a dimmed rectangle saying "not installed" means nothing
        // except that something is missing. It also took the most prominent
        // row on the screen to say it.
        List<SoulTile> present = new ArrayList<>(candidates.size());
        for (SoulTile tile : candidates) {
            tile.resolve(context);
            if (tile.installed) {
                present.add(tile);
            }
        }
        return present;
    }

    private void resolve(Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo(packageName, 0);
            installed = true;
        } catch (PackageManager.NameNotFoundException e) {
            installed = false;
        }

        if (!installed) {
            // all() drops these; nothing will render it.
            stateRes = R.string.state_unavailable;
            live = false;
            return;
        }

        // Installed, but none of these expose a status API on this build yet.
        // An em dash is the honest answer until one does. When AetherNet
        // publishes a peer count and SDPKT a balance, this is where they bind.
        stateRes = R.string.state_unknown;
        live = false;
    }

    /**
     * Guide 2.5 - "privacy as a visible state".
     *
     * Reads the actual private-DNS setting rather than asserting a posture.
     * Returns true only when DNS queries really are encrypted, so the cue on
     * the home screen means something.
     */
    public static boolean commsEncrypted(Context context) {
        String mode = Settings.Global.getString(
                context.getContentResolver(), "private_dns_mode");
        return "hostname".equals(mode) || "opportunistic".equals(mode);
    }
}
