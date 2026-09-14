/*
 * Copyright (C) 2026 The Geek (Pty) Ltd
 * SPDX-License-Identifier: Apache-2.0
 */
package com.circleos.launcher.metro;

import com.circleos.launcher.R;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * The start screen lattice, per CircleOS_Skin_Design_Guide.md section 1.1:
 *
 *   "Phone start screen = a tile grid, 4 small columns wide.
 *    Tile sizes (in small-cell units): small 1x1, medium 2x2, wide 4x2,
 *    large 4x4. Everything snaps to this grid. No free placement."
 *
 * GridView and GridLayout were both considered and neither fits: GridView
 * has one cell size for every child, and GridLayout needs spans declared in
 * XML rather than decided per app at runtime. So the packing is done here -
 * it is about forty lines, and it is the difference between a Metro start
 * screen and a grid of identical squares.
 *
 * Packing rule: first-fit, top-left to bottom-right, scanning row by row
 * for the first place a tile of that span fits. That is what Windows Phone
 * did, and it is what makes a mixed grid look deliberate rather than ragged.
 */
public final class TileGridLayout extends ViewGroup {

    /** The grid is four small cells wide. Section 1.1. */
    public static final int COLUMNS = 4;

    /** Rows are grown as needed; this is only the initial allocation. */
    private static final int INITIAL_ROWS = 16;

    private int cell;
    private int gutter;
    private boolean[][] taken = new boolean[INITIAL_ROWS][COLUMNS];
    private int rowsUsed;

    public TileGridLayout(Context context) {
        this(context, null);
    }

    public TileGridLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        gutter = getResources().getDimensionPixelSize(R.dimen.tile_gutter);
        setClipToPadding(false);
    }

    /** Span of a tile, in small cells. */
    public static final class Span extends ViewGroup.LayoutParams {
        final int cols;
        final int rows;
        int col = -1;
        int row = -1;

        public Span(int cols, int rows) {
            super(WRAP_CONTENT, WRAP_CONTENT);
            this.cols = Math.max(1, Math.min(COLUMNS, cols));
            this.rows = Math.max(1, rows);
        }
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new Span(2, 2);      // medium, the Metro default
    }

    @Override
    protected boolean checkLayoutParams(LayoutParams p) {
        return p instanceof Span;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int usable = width - getPaddingStart() - getPaddingEnd();

        // Four columns and three gutters have to fit the usable width. The
        // cell size follows from the screen rather than being fixed, so the
        // grid stays four columns on any device - which is the part of the
        // spec that matters, more than the exact 76dp.
        cell = Math.max(1, (usable - gutter * (COLUMNS - 1)) / COLUMNS);

        pack();

        int height = getPaddingTop() + getPaddingBottom();
        if (rowsUsed > 0) {
            height += rowsUsed * cell + (rowsUsed - 1) * gutter;
        }

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            Span span = (Span) child.getLayoutParams();
            child.measure(
                    MeasureSpec.makeMeasureSpec(sizeOf(span.cols), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(sizeOf(span.rows), MeasureSpec.EXACTLY));
        }

        setMeasuredDimension(width, height);
    }

    /** Pixel size of n cells including the gutters between them. */
    private int sizeOf(int cells) {
        return cells * cell + (cells - 1) * gutter;
    }

    /** First-fit packing. Recomputed on measure because the app list can
     *  change while the launcher is alive. */
    private void pack() {
        for (boolean[] row : taken) {
            java.util.Arrays.fill(row, false);
        }
        rowsUsed = 0;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            Span span = (Span) child.getLayoutParams();
            place(span);
        }
    }

    private void place(Span span) {
        for (int row = 0; ; row++) {
            ensureRows(row + span.rows);
            for (int col = 0; col + span.cols <= COLUMNS; col++) {
                if (fits(row, col, span)) {
                    occupy(row, col, span);
                    span.row = row;
                    span.col = col;
                    rowsUsed = Math.max(rowsUsed, row + span.rows);
                    return;
                }
            }
        }
    }

    private boolean fits(int row, int col, Span span) {
        for (int r = row; r < row + span.rows; r++) {
            for (int c = col; c < col + span.cols; c++) {
                if (taken[r][c]) {
                    return false;
                }
            }
        }
        return true;
    }

    private void occupy(int row, int col, Span span) {
        for (int r = row; r < row + span.rows; r++) {
            for (int c = col; c < col + span.cols; c++) {
                taken[r][c] = true;
            }
        }
    }

    private void ensureRows(int needed) {
        if (needed <= taken.length) {
            return;
        }
        boolean[][] bigger = new boolean[Math.max(needed, taken.length * 2)][COLUMNS];
        for (int r = 0; r < taken.length; r++) {
            System.arraycopy(taken[r], 0, bigger[r], 0, COLUMNS);
        }
        taken = bigger;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int left = getPaddingStart();
        int top = getPaddingTop();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            Span span = (Span) child.getLayoutParams();
            if (span.col < 0) {
                continue;
            }
            int x = left + span.col * (cell + gutter);
            int y = top + span.row * (cell + gutter);
            child.layout(x, y,
                    x + child.getMeasuredWidth(),
                    y + child.getMeasuredHeight());
        }
    }
}
