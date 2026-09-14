/*
 * Copyright (C) 2026 The Geek (Pty) Ltd
 * SPDX-License-Identifier: Apache-2.0
 */
package com.circleos.launcher;

import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.util.Log;

import java.util.List;

/**
 * Claims the HOME role once, on the first boot where nobody holds it.
 *
 * Why this exists rather than android:priority
 * --------------------------------------------
 * A launcher needs to be the default without the user being asked, AND the
 * user needs to be able to change it in Settings. On Android 16 those pull
 * against each other:
 *
 *   android:priority > 0   no chooser, but priority wins intent resolution
 *                          outright and permanently. Choosing the other
 *                          launcher in Settings > Default home app updates
 *                          the role holder and changes nothing; even
 *                          `cmd package set-home-activity` cannot override
 *                          it. Measured on a Pixel 7a, 2026-09-15.
 *
 *   android:priority = 0   switchable, but two launchers tie,
 *                          HomeRoleBehavior.getFallbackHolderAsUser()
 *                          returns null on a tie, and HOME resolves to
 *                          com.android.internal.app.ResolverActivity - the
 *                          chooser - on first boot.
 *
 * AOSP offers no third option declaratively: roles.xml gives
 * defaultHolders="config_default*" to ASSISTANT, BROWSER, DIALER and SMS,
 * but android.app.role.HOME has none.
 *
 * So the default is set the same way a user would set it, once, at first
 * boot. After that the stored holder exists and this does nothing - which
 * is what makes the Settings switch stick.
 */
public final class DefaultHomeClaimer extends BroadcastReceiver {

    private static final String TAG = "CircleHome";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        claimIfUnheld(context);
    }

    static void claimIfUnheld(Context context) {
        RoleManager roles = context.getSystemService(RoleManager.class);
        if (roles == null) {
            return;
        }
        try {
            List<String> holders = roles.getRoleHoldersAsUser(
                    RoleManager.ROLE_HOME, Process.myUserHandle());

            // Somebody already holds it. That somebody may be this app, or it
            // may be the Metro launcher because the user chose it in
            // Settings. Either way it is not ours to overwrite - a launcher
            // that re-claims the role on every boot would silently undo the
            // user's choice, which is worse than the chooser this avoids.
            if (holders != null && !holders.isEmpty()) {
                return;
            }

            roles.addRoleHolderAsUser(
                    RoleManager.ROLE_HOME,
                    context.getPackageName(),
                    /* flags= */ 0,
                    Process.myUserHandle(),
                    context.getMainExecutor(),
                    granted -> Log.i(TAG, "claimed HOME role: " + granted));
        } catch (SecurityException e) {
            // MANAGE_ROLE_HOLDERS is signature-level. If this build is not
            // platform-signed the claim cannot happen, and the user gets the
            // chooser - degraded, not broken. Log it rather than crash the
            // boot.
            Log.w(TAG, "cannot claim the HOME role", e);
        }
    }
}
