/*
 * Copyright (C) 2026 The Geek (Pty) Ltd
 * SPDX-License-Identifier: Apache-2.0
 */
package com.circleos.launcher.metro;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ViewFlipper;

import com.circleos.launcher.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Metro skin, as a surface rather than an application.
 *
 * Why this is not a launcher
 * --------------------------
 * CircleOS_Theme_Skins_Scout.md is explicit about this, and it was written
 * after an earlier pass had already made the mistake:
 *
 *   "Launcher = a whole home-screen app. (An earlier pass wandered here -
 *    out of scope, dropped.)
 *    Skin = a visual theme layer the OS paints over itself. Pick a skin ->
 *    the system re-dresses."
 *
 * Shipping Metro as a second launcher meant two apps competing for the HOME
 * role, which produced a chooser on first boot, and a workaround
 * (android:priority) that made the default unchangeable, and a second
 * workaround (a role claimer) for that. None of it was necessary. One app
 * that can draw itself two ways has no contention to resolve: the skin is a
 * setting, and switching it re-dresses the screen.
 *
 * Built to CircleOS_Skin_Design_Guide.md - section 1 for the Metro
 * discipline, section 2 for what makes it Circle OS rather than Windows
 * Phone. See CircleOS/tools/check-skin.sh for what is and is not built.
 */
public final class MetroSurface {

    private static final int PAGE_START = 0;
    private static final int PAGE_APPS  = 1;

    private static final int SWIPE_MIN_DISTANCE = 140;
    private static final int SWIPE_MIN_VELOCITY = 220;

    /** Guide 1.5: "tiles depress toward the touch". */
    private static final float PRESS_SCALE = 0.94f;
    private static final int   PRESS_MS    = 90;

    /** Guide 1.5: turnstile, rotating about the left edge, staggered so the
     *  grid assembles in a sweep rather than appearing all at once. */
    private static final int   TURNSTILE_MS      = 260;
    private static final int   TURNSTILE_STAGGER = 26;
    private static final float TURNSTILE_FROM    = -84f;

    private final Activity host;
    private final List<LaunchableApp> apps = new ArrayList<>();
    private final AppRepository repository;

    private View root;
    private ViewFlipper flipper;
    private TileGridLayout grid;
    private TileGridLayout soulGrid;
    private ListView appList;
    private GestureDetector gestures;

    public MetroSurface(Activity host) {
        this.host = host;
        this.repository = new AppRepository(host);
    }

    /** Inflates the skin and wires it up. Returns the view to set as content. */
    public View create() {
        root = LayoutInflater.from(host).inflate(R.layout.launcher, null);

        flipper  = root.findViewById(R.id.flipper);
        grid     = root.findViewById(R.id.tile_grid);
        soulGrid = root.findViewById(R.id.soul_grid);
        appList  = root.findViewById(R.id.apps);

        appList.setAdapter(new AppListAdapter(host, apps));
        appList.setOnItemClickListener(
                (parent, view, position, id) -> launch(position, view));

        gestures = new GestureDetector(host, new SwipeListener());
        View.OnTouchListener forward = (v, event) -> {
            gestures.onTouchEvent(event);
            return false;
        };
        appList.setOnTouchListener(forward);
        flipper.setOnTouchListener(forward);
        return root;
    }

    /** Called from the host's onResume. */
    public void refresh() {
        apps.clear();
        apps.addAll(repository.load());
        ((AppListAdapter) appList.getAdapter()).notifyDataSetChanged();
        buildSoulTiles();
        bindPrivacyState();
        buildTiles();
    }

    /** True when the surface consumed the press. */
    public boolean onBackPressed() {
        if (flipper != null && flipper.getDisplayedChild() == PAGE_APPS) {
            flipper.setDisplayedChild(PAGE_START);
            return true;
        }
        return false;
    }

    /** Home pressed while already home: return to start. */
    public void onHome() {
        if (flipper != null) {
            flipper.setDisplayedChild(PAGE_START);
        }
    }

    // ----------------------------------------------------------- guide 2.3
    private void buildSoulTiles() {
        soulGrid.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(host);

        List<SoulTile> souls = SoulTile.all(host);
        for (int i = 0; i < souls.size(); i++) {
            final SoulTile soul = souls.get(i);
            View tile = inflater.inflate(R.layout.tile_soul, soulGrid, false);

            // Medium tiles are 2 of a 4-column grid, so an odd count leaves
            // the last row half empty - a hole in the lattice, which is the
            // one thing a tile grid must not have.
            boolean lastOfOdd = (i == souls.size() - 1) && (souls.size() % 2 == 1);
            TileSize size = lastOfOdd ? TileSize.WIDE : TileSize.MEDIUM;
            tile.setLayoutParams(new TileGridLayout.Span(size.cols, size.rows));

            ((TextView) tile.findViewById(R.id.soul_label)).setText(soul.labelRes);
            ((TextView) tile.findViewById(R.id.soul_state)).setText(soul.stateRes);
            tile.findViewById(R.id.soul_presence)
                    .setVisibility(soul.live ? View.VISIBLE : View.GONE);
            ((ImageView) tile.findViewById(R.id.soul_glyph))
                    .setImageDrawable(repository.iconFor(soul.packageName));

            tile.setOnClickListener(v -> launchPackage(soul.packageName, v));
            tile.setOnTouchListener(this::tilt);
            soulGrid.addView(tile);
        }
        soulGrid.setVisibility(souls.isEmpty() ? View.GONE : View.VISIBLE);
        soulGrid.requestLayout();
        soulGrid.post(this::cascade);
    }

    // ----------------------------------------------------------- guide 2.5
    private void bindPrivacyState() {
        boolean encrypted = SoulTile.commsEncrypted(host);
        View dot = root.findViewById(R.id.privacy_dot);
        TextView label = root.findViewById(R.id.privacy_label);
        if (dot == null || label == null) {
            return;
        }
        dot.getBackground().setTint(host.getColor(
                encrypted ? R.color.privacy_on : R.color.privacy_off));
        label.setText(encrypted
                ? R.string.privacy_encrypted : R.string.privacy_not_encrypted);
    }

    private void buildTiles() {
        grid.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(host);

        for (int i = 0; i < apps.size(); i++) {
            final LaunchableApp app = apps.get(i);
            TileSize size = TileSize.defaultFor(app.packageName);

            View tile = inflater.inflate(R.layout.tile, grid, false);
            tile.setLayoutParams(new TileGridLayout.Span(size.cols, size.rows));

            TextView label = tile.findViewById(R.id.tile_label);
            label.setText(app.label.toLowerCase(Locale.getDefault()));

            ImageView glyph = tile.findViewById(R.id.tile_glyph);
            glyph.setImageDrawable(app.icon);
            int px = host.getResources().getDimensionPixelSize(
                    size == TileSize.SMALL ? R.dimen.glyph_small : R.dimen.glyph_medium);
            glyph.getLayoutParams().width = px;
            glyph.getLayoutParams().height = px;
            label.setVisibility(size == TileSize.SMALL ? View.GONE : View.VISIBLE);

            final int position = i;
            tile.setOnClickListener(v -> launch(position, v));
            tile.setOnTouchListener(this::tilt);
            grid.addView(tile);
        }
        grid.requestLayout();
    }

    // ----------------------------------------------------------- guide 1.5
    private void cascade() {
        int index = turnstile(soulGrid, 0);
        turnstile(grid, index);
    }

    private int turnstile(TileGridLayout container, int index) {
        if (container == null || container.getVisibility() != View.VISIBLE) {
            return index;
        }
        for (int i = 0; i < container.getChildCount(); i++) {
            View tile = container.getChildAt(i);
            tile.setPivotX(0f);
            tile.setPivotY(tile.getHeight() / 2f);
            tile.setRotationY(TURNSTILE_FROM);
            tile.setAlpha(0f);
            tile.animate()
                .rotationY(0f)
                .alpha(1f)
                .setStartDelay((long) index * TURNSTILE_STAGGER)
                .setDuration(TURNSTILE_MS)
                .start();
            index++;
        }
        return index;
    }

    private boolean tilt(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                scale(view, PRESS_SCALE);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                scale(view, 1f);
                break;
            default:
                break;
        }
        gestures.onTouchEvent(event);
        return false;
    }

    private void scale(View view, float to) {
        ObjectAnimator.ofFloat(view, "scaleX", to).setDuration(PRESS_MS).start();
        ObjectAnimator.ofFloat(view, "scaleY", to).setDuration(PRESS_MS).start();
    }

    private void launch(int position, View from) {
        if (position < 0 || position >= apps.size()) {
            return;
        }
        Intent intent = repository.launchIntent(apps.get(position));
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            host.startActivity(intent, revealFrom(from));
        }
    }

    private void launchPackage(String packageName, View from) {
        Intent intent = host.getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent == null) {
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        host.startActivity(intent, revealFrom(from));
    }

    /**
     * Guide 2.4 asks for a circular reveal from the tile centre. There is no
     * public API for one across an activity boundary - createCircularReveal
     * animates within a single window. Scaling up from the pressed point is
     * the closest honest approximation.
     */
    private Bundle revealFrom(View from) {
        if (from == null) {
            return null;
        }
        return ActivityOptions.makeScaleUpAnimation(
                from, from.getWidth() / 2, from.getHeight() / 2, 0, 0).toBundle();
    }

    private final class SwipeListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onFling(MotionEvent down, MotionEvent up,
                               float velocityX, float velocityY) {
            if (down == null || up == null) {
                return false;
            }
            float dx = up.getX() - down.getX();
            float dy = up.getY() - down.getY();
            if (Math.abs(dx) < Math.abs(dy)
                    || Math.abs(dx) < SWIPE_MIN_DISTANCE
                    || Math.abs(velocityX) < SWIPE_MIN_VELOCITY) {
                return false;
            }
            if (dx < 0 && flipper.getDisplayedChild() == PAGE_START) {
                flipper.setDisplayedChild(PAGE_APPS);
                return true;
            }
            if (dx > 0 && flipper.getDisplayedChild() == PAGE_APPS) {
                flipper.setDisplayedChild(PAGE_START);
                return true;
            }
            return false;
        }
    }
}
