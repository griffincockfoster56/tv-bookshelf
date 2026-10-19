package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/**
 * Paints the room once into a bitmap: an old New York brick wall (tan and ochre brick, grey-beige
 * mortar, warm lamplight) with a honey-walnut built-in bookcase. Geometry is in dp, shared with the activity.
 */
final class WallPainter {

    // Bookcase geometry, in dp from the top-left of a 960 x 540 dp screen (scaled to the real width).
    static final float CASE_INSET = 28f;     // brick showing either side
    static final float CORNICE_TOP = 92f;
    static final float CORNICE_BOTTOM = 112f;
    static final float UPRIGHT = 18f;
    static final float BAY = 200f;           // clear height of each shelf bay
    static final float PLANK = 16f;
    static final int SHELVES = 2;

    static float bayTop(int shelf) {
        return CORNICE_BOTTOM + shelf * (BAY + PLANK);
    }

    static float plankTop(int shelf) {
        return bayTop(shelf) + BAY;
    }

    private static final int[] BRICKS = {0xFF8C6542, 0xFF9B7149, 0xFF7A5636, 0xFFA57C51, 0xFF6A4A30,
            0xFFB38A5C, 0xFF84603F, 0xFF936A44, 0xFF5E4330, 0xFFA47A4C};

    static Bitmap paint(int widthPx, int heightPx, float d) {
        Bitmap bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Random r = new Random(1926);
        float wDp = widthPx / d;

        bricks(c, p, r, widthPx, heightPx, d);
        bookcase(c, p, r, wDp, heightPx, d);

        // Warm lamplight from the two lamps either side, and a gentle vignette.
        p.setColor(0xFF000000);
        p.setShader(new RadialGradient(widthPx * 0.08f, heightPx * 0.32f, widthPx * 0.45f,
                0x38FFB862, 0x00FFB862, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, widthPx, heightPx, p);
        p.setColor(0xFF000000);
        p.setShader(new RadialGradient(widthPx * 0.94f, heightPx * 0.3f, widthPx * 0.4f,
                0x30FFB862, 0x00FFB862, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, widthPx, heightPx, p);
        p.setColor(0xFF000000);
        p.setShader(new RadialGradient(widthPx * 0.5f, heightPx * 0.45f, widthPx * 0.75f,
                new int[]{0x00000000, 0x00000000, 0x8C000000}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, widthPx, heightPx, p);
        p.setShader(null);
        return bmp;
    }

    private static void bricks(Canvas c, Paint p, Random r, int w, int h, float d) {
        // Mortar: grey-beige, darker where it is recessed.
        c.drawColor(0xFFA2957F);
        p.setColor(0x22000000);
        for (int i = 0; i < 1400; i++) c.drawCircle(r.nextFloat() * w, r.nextFloat() * h, d * (0.6f + r.nextFloat()), p);

        float bw = 47 * d, bh = 15 * d, m = 3.2f * d;
        RectF rect = new RectF();
        int row = 0;
        for (float y = -bh / 2; y < h; y += bh + m, row++) {
            float offset = (row % 2 == 0) ? 0 : -(bw + m) / 2f;
            for (float x = offset; x < w; x += bw + m) {
                // Old handmade brick: slightly irregular size, colour and edges.
                float jx = (r.nextFloat() - 0.5f) * 1.6f * d, jy = (r.nextFloat() - 0.5f) * 1.2f * d;
                rect.set(x + jx, y + jy, x + bw + jx + (r.nextFloat() - 0.5f) * 2 * d, y + bh + jy + (r.nextFloat() - 0.5f) * d);
                int base = BRICKS[r.nextInt(BRICKS.length)];
                if (r.nextFloat() < 0.07f) base = Bindery.shade(base, 0.62f);     // sooty bricks
                float lift = 0.9f + r.nextFloat() * 0.2f;
                p.setColor(0xFF000000);
                p.setShader(new LinearGradient(rect.left, rect.top, rect.left, rect.bottom,
                        Bindery.shade(base, lift * 1.08f), Bindery.shade(base, lift * 0.84f), Shader.TileMode.CLAMP));
                c.drawRoundRect(rect, 2 * d, 2 * d, p);
                p.setShader(null);
                // Pitting and speckle.
                for (int k = 0; k < 14; k++) {
                    p.setColor(r.nextBoolean() ? 0x24000000 : 0x18FFFFFF);
                    c.drawCircle(rect.left + r.nextFloat() * rect.width(), rect.top + r.nextFloat() * rect.height(),
                            d * (0.4f + r.nextFloat() * 0.9f), p);
                }
                // Traces of old whitewash and limewash.
                if (r.nextFloat() < 0.16f) {
                    p.setColor(0x26E6D7BC);
                    float cx = rect.left + r.nextFloat() * rect.width();
                    c.drawOval(cx - 9 * d, rect.top + 2 * d, cx + 9 * d, rect.bottom - 2 * d, p);
                }
                // Chipped corner.
                if (r.nextFloat() < 0.1f) {
                    p.setColor(0xFFA2957F);
                    c.drawCircle(r.nextBoolean() ? rect.left : rect.right, r.nextBoolean() ? rect.top : rect.bottom, 2.4f * d, p);
                }
            }
        }
    }

    private static void bookcase(Canvas c, Paint p, Random r, float wDp, int hPx, float d) {
        float left = CASE_INSET * d, right = (wDp - CASE_INSET) * d;
        float top = CORNICE_TOP * d;

        // Shadow the case casts on the brick.
        p.setShader(null);
        p.setColor(0x66000000);
        c.drawRect(left - 6 * d, top + 4 * d, right + 6 * d, hPx, p);

        // Back panel: dark stained beadboard.
        float innerL = left + UPRIGHT * d, innerR = right - UPRIGHT * d;
        p.setColor(0xFF2B1A0E);
        c.drawRect(innerL, top, innerR, hPx, p);
        p.setColor(0x33000000);
        for (float x = innerL; x < innerR; x += 22 * d) c.drawRect(x, top, x + 1.4f * d, hPx, p);
        p.setColor(0x10FFFFFF);
        for (float x = innerL + 1.4f * d; x < innerR; x += 22 * d) c.drawRect(x, top, x + 0.8f * d, hPx, p);

        // Each bay: deep shadow under the shelf above, lighter towards the front.
        for (int s = 0; s < SHELVES; s++) {
            float bt = bayTop(s) * d;
            p.setColor(0xFF000000);
            p.setShader(new LinearGradient(0, bt, 0, bt + 46 * d, 0xC0000000, 0x00000000, Shader.TileMode.CLAMP));
            c.drawRect(innerL, bt, innerR, bt + 46 * d, p);
            p.setShader(null);
        }

        // Uprights.
        wood(c, p, r, left, top, innerL, hPx, d, true);
        wood(c, p, r, innerR, top, right, hPx, d, true);
        p.setColor(0x55000000);
        c.drawRect(innerL, top, innerL + 5 * d, hPx, p);       // inner shadow on the back panel
        c.drawRect(innerR - 5 * d, top, innerR, hPx, p);

        // Planks: top surface, front edge with a moulded lip, and the shadow beneath.
        for (int s = 0; s < SHELVES; s++) {
            float pt = plankTop(s) * d, pb = pt + PLANK * d;
            wood(c, p, r, innerL, pt, innerR, pb, d, false);
            p.setColor(0x44FFE0B0);
            c.drawRect(innerL, pt, innerR, pt + 1.2f * d, p);
            p.setColor(0x55000000);
            c.drawRect(innerL, pb - 2 * d, innerR, pb, p);
        }

        // Cornice: stepped crown moulding across the top.
        float cb = CORNICE_BOTTOM * d;
        wood(c, p, r, left - 10 * d, top, right + 10 * d, cb, d, false);
        p.setColor(0x50FFE2B4);
        c.drawRect(left - 10 * d, top, right + 10 * d, top + 1.5f * d, p);
        p.setColor(0x40000000);
        c.drawRect(left - 10 * d, top + 7 * d, right + 10 * d, top + 8.5f * d, p);
        p.setColor(0x28FFE2B4);
        c.drawRect(left - 10 * d, top + 8.5f * d, right + 10 * d, top + 9.5f * d, p);
        p.setColor(0x60000000);
        c.drawRect(left - 10 * d, cb - 3 * d, right + 10 * d, cb, p);
    }

    /** Honey walnut with a soft sheen and grain lines, running vertically or horizontally. */
    private static void wood(Canvas c, Paint p, Random r, float l, float t, float rt, float b, float d, boolean vertical) {
        int light = 0xFF8A5A2E, mid = 0xFF6E4524, dark = 0xFF4A2C16;
        p.setColor(0xFF000000);
        p.setShader(vertical
                ? new LinearGradient(l, 0, rt, 0, new int[]{dark, light, mid, dark}, new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP)
                : new LinearGradient(0, t, 0, b, new int[]{light, mid, dark}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(l, t, rt, b, p);
        p.setShader(null);
        p.setStrokeWidth(0.8f * d);
        int lines = (int) ((vertical ? (rt - l) : (b - t)) / (1.6f * d));
        for (int i = 0; i < lines; i++) {
            p.setColor(r.nextBoolean() ? 0x1E000000 : 0x10FFE0B0);
            float pos = (vertical ? l : t) + r.nextFloat() * (vertical ? (rt - l) : (b - t));
            float wave = (r.nextFloat() - 0.5f) * 3 * d;
            if (vertical) c.drawLine(pos, t, pos + wave, b, p);
            else c.drawLine(l, pos, rt, pos + wave, p);
        }
    }
}
