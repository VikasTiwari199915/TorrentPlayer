package com.vikas.torrentplayer.ui.downloads;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Renders a torrent's piece status as a compact grid: complete, active, or
 * missing. Cheap, fast, all on the UI thread.
 */
public class PieceBitmapView extends View {

    private static final int CELL_DP = 5;
    private static final int GAP_DP = 1;

    private int[] states = new int[0];
    private final Paint donePaint = new Paint();
    private final Paint activePaint = new Paint();
    private final Paint todoPaint = new Paint();
    private final Paint skippedPaint = new Paint();
    private final Paint gapPaint = new Paint();

    public PieceBitmapView(Context context) { this(context, null); }

    public PieceBitmapView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        donePaint.setColor(0xFF4ECDC4);
        donePaint.setStyle(Paint.Style.FILL);
        activePaint.setColor(0xFFFFC857);
        activePaint.setStyle(Paint.Style.FILL);
        todoPaint.setColor(withAlpha(resolveColor(
                com.google.android.material.R.attr.colorOutlineVariant, 0xFF6F7978), 0.65f));
        todoPaint.setStyle(Paint.Style.FILL);
        skippedPaint.setColor(withAlpha(resolveColor(
                com.google.android.material.R.attr.colorOutlineVariant, 0xFF6F7978), 0.25f));
        skippedPaint.setStyle(Paint.Style.FILL);
        gapPaint.setColor(0x00000000);
        gapPaint.setStyle(Paint.Style.FILL);
    }

    public void setPieces(boolean[] pieces) {
        if (pieces == null) {
            states = new int[0];
        } else {
            states = new int[pieces.length];
            for (int i = 0; i < pieces.length; i++) states[i] = pieces[i] ? 1 : 0;
        }
        requestLayout();
        invalidate();
    }

    /** 0 = missing, 1 = complete, 2 = active/requested/downloading, 3 = skipped. */
    public void setPieceStates(int[] states) {
        this.states = states == null ? new int[0] : states;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int fallbackWidth = dp(320) + getPaddingLeft() + getPaddingRight();
        int width = resolveSize(Math.max(getSuggestedMinimumWidth(), fallbackWidth),
                widthMeasureSpec);
        int columns = columnsForWidth(width);
        int rows = states.length == 0
                ? 1
                : (int) Math.ceil(states.length / (double) columns);
        int contentHeight = rows * dp(CELL_DP) + Math.max(0, rows - 1) * dp(GAP_DP);
        int desiredHeight = getPaddingTop() + contentHeight + getPaddingBottom();
        desiredHeight = Math.max(desiredHeight, getSuggestedMinimumHeight());
        setMeasuredDimension(width, resolveSize(desiredHeight, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int total = states.length;
        int contentLeft = getPaddingLeft();
        int contentTop = getPaddingTop();
        int w = getWidth() - getPaddingLeft() - getPaddingRight();
        int h = getHeight() - getPaddingTop() - getPaddingBottom();
        if (total == 0 || w == 0 || h == 0) {
            canvas.drawRect(contentLeft, contentTop, contentLeft + w, contentTop + h,
                    todoPaint);
            return;
        }

        int columns = columnsForWidth(getWidth());
        float gap = dp(GAP_DP);
        float cellSize = dp(CELL_DP);
        for (int i = 0; i < total; i++) {
            int row = i / columns;
            int col = i % columns;
            float left = contentLeft + col * (cellSize + gap);
            float top = contentTop + row * (cellSize + gap);
            Paint p;
            switch (states[i]) {
                case 2: p = activePaint; break;
                case 1: p = donePaint; break;
                case 3: p = skippedPaint; break;
                case 0:
                default: p = todoPaint; break;
            }
            canvas.drawRect(left, top, left + cellSize, top + cellSize, p);
        }
    }

    private int columnsForWidth(int measuredWidth) {
        int available = Math.max(dp(CELL_DP),
                measuredWidth - getPaddingLeft() - getPaddingRight());
        return Math.max(1, (available + dp(GAP_DP)) / (dp(CELL_DP) + dp(GAP_DP)));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private int resolveColor(int attr, int fallback) {
        TypedValue value = new TypedValue();
        if (getContext().getTheme().resolveAttribute(attr, value, true)
                && value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return value.data;
        }
        return fallback;
    }

    private static int withAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        return (color & 0x00FFFFFF) | (a << 24);
    }
}
