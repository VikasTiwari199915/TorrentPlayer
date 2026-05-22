package com.vikas.torrentplayer.ui.downloads;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Renders a torrent's piece status as a heat-strip — one tiny cell per piece,
 * green if downloaded, dim if not. Cheap, fast, all on the UI thread.
 */
public class PieceBitmapView extends View {

    private boolean[] pieces = new boolean[0];
    private final Paint donePaint = new Paint();
    private final Paint todoPaint = new Paint();

    public PieceBitmapView(Context context) { this(context, null); }

    public PieceBitmapView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        donePaint.setColor(0xFF4ECDC4);
        donePaint.setStyle(Paint.Style.FILL);
        todoPaint.setColor(0x33000000);
        todoPaint.setStyle(Paint.Style.FILL);
    }

    public void setPieces(boolean[] pieces) {
        this.pieces = pieces == null ? new boolean[0] : pieces;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int total = pieces.length;
        int w = getWidth();
        int h = getHeight();
        if (total == 0 || w == 0 || h == 0) {
            canvas.drawRect(0, 0, w, h, todoPaint);
            return;
        }
        // Aggregate when there are more pieces than pixels so we don't draw a
        // sub-pixel rectangle per piece.
        if (total <= w) {
            float cellW = (float) w / total;
            for (int i = 0; i < total; i++) {
                Paint p = pieces[i] ? donePaint : todoPaint;
                canvas.drawRect(i * cellW, 0, (i + 1) * cellW, h, p);
            }
        } else {
            float piecesPerPixel = (float) total / w;
            for (int x = 0; x < w; x++) {
                int from = (int) (x * piecesPerPixel);
                int to = Math.min(total, (int) ((x + 1) * piecesPerPixel));
                int done = 0;
                int count = to - from;
                for (int i = from; i < to; i++) if (pieces[i]) done++;
                if (count <= 0) continue;
                float ratio = (float) done / count;
                int color = blend(0x33000000, 0xFF4ECDC4, ratio);
                Paint p = new Paint();
                p.setColor(color);
                canvas.drawRect(x, 0, x + 1, h, p);
            }
        }
    }

    private static int blend(int a, int b, float t) {
        int aA = Color.alpha(a), aR = Color.red(a), aG = Color.green(a), aB = Color.blue(a);
        int bA = Color.alpha(b), bR = Color.red(b), bG = Color.green(b), bB = Color.blue(b);
        int rA = (int) (aA + (bA - aA) * t);
        int rR = (int) (aR + (bR - aR) * t);
        int rG = (int) (aG + (bG - aG) * t);
        int rB = (int) (aB + (bB - aB) * t);
        return Color.argb(rA, rR, rG, rB);
    }
}
