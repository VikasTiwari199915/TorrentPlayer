package com.vikas.torrentplayer.ui.downloads;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Renders a torrent's piece status as a compact grid: complete, active, or
 * missing. Cheap, fast, all on the UI thread.
 */
public class PieceBitmapView extends View {

    private int[] states = new int[0];
    private final Paint donePaint = new Paint();
    private final Paint activePaint = new Paint();
    private final Paint todoPaint = new Paint();
    private final Paint gapPaint = new Paint();

    public PieceBitmapView(Context context) { this(context, null); }

    public PieceBitmapView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        donePaint.setColor(0xFF4ECDC4);
        donePaint.setStyle(Paint.Style.FILL);
        activePaint.setColor(0xFFFFC857);
        activePaint.setStyle(Paint.Style.FILL);
        todoPaint.setColor(0x33000000);
        todoPaint.setStyle(Paint.Style.FILL);
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
        invalidate();
    }

    /** 0 = missing, 1 = complete, 2 = active/requested/downloading. */
    public void setPieceStates(int[] states) {
        this.states = states == null ? new int[0] : states;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int total = states.length;
        int w = getWidth();
        int h = getHeight();
        if (total == 0 || w == 0 || h == 0) {
            canvas.drawRect(0, 0, w, h, todoPaint);
            return;
        }

        int columns = Math.max(24, Math.min(96, w / dp(5)));
        int rows = (int) Math.ceil(total / (double) columns);
        float gap = dp(1);
        float cellW = (w - gap * (columns - 1)) / columns;
        float cellH = Math.max(dp(3), (h - gap * (rows - 1)) / Math.max(1, rows));
        for (int i = 0; i < total; i++) {
            int row = i / columns;
            int col = i % columns;
            float left = col * (cellW + gap);
            float top = row * (cellH + gap);
            float bottom = Math.min(h, top + cellH);
            if (top >= h) break;
            Paint p;
            switch (states[i]) {
                case 2: p = activePaint; break;
                case 1: p = donePaint; break;
                case 0:
                default: p = todoPaint; break;
            }
            canvas.drawRect(left, top, left + cellW, bottom, p);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
