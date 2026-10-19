package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.Random;

/** Shared bookbinding materials: typefaces, gold leaf, leather grain and colour arithmetic. */
final class Bindery {

    private static Typeface cinzel, garamond, garamondItalic;

    static Typeface cinzel(Context c) {
        if (cinzel == null) cinzel = c.getResources().getFont(R.font.cinzel);
        return cinzel;
    }

    static Typeface garamond(Context c) {
        if (garamond == null) garamond = c.getResources().getFont(R.font.ebgaramond);
        return garamond;
    }

    static Typeface garamondItalic(Context c) {
        if (garamondItalic == null) garamondItalic = c.getResources().getFont(R.font.ebgaramond_italic);
        return garamondItalic;
    }

    static final int GOLD = 0xFFD9B565;
    static final int INK = 0xFF2A1B10;
    static final int RUBRIC = 0xFF8E2A1E;      // the red of old title pages
    static final int PAPER = 0xFFF1E6CC;
    static final int PAPER_EDGE = 0xFFDCCBA6;

    /** Burnished gold leaf running across a band of the given span (horizontal gradient). */
    static Shader goldAcross(float x0, float x1) {
        return new LinearGradient(x0, 0, x1, 0,
                new int[]{0xFF8A6526, 0xFFF4DC92, 0xFFC99D45, 0xFFF0D488, 0xFF7E5A20},
                new float[]{0f, 0.3f, 0.55f, 0.75f, 1f}, Shader.TileMode.CLAMP);
    }

    /** Gold leaf running down a span (vertical gradient), for lettering. */
    static Shader goldDown(float y0, float y1) {
        return new LinearGradient(0, y0, 0, y1,
                new int[]{0xFFF6E19C, 0xFFC99A41, 0xFFEFD083, 0xFFA87A2E},
                new float[]{0f, 0.45f, 0.7f, 1f}, Shader.TileMode.CLAMP);
    }

    static int shade(int c, float f) {
        return Color.argb(Color.alpha(c), clamp(Color.red(c) * f), clamp(Color.green(c) * f), clamp(Color.blue(c) * f));
    }

    private static int clamp(float v) {
        return (int) Math.max(0, Math.min(255, v));
    }

    /** Fine leather grain: a scatter of light and dark specks, drawn once per view size. */
    static float[][] grain(long seed, float w, float h, float density) {
        Random r = new Random(seed);
        int n = (int) (w * h / (density * density * 9));
        float[] dark = new float[n * 2], light = new float[n];
        for (int i = 0; i < n; i++) {
            dark[i * 2] = r.nextFloat() * w;
            dark[i * 2 + 1] = r.nextFloat() * h;
        }
        for (int i = 0; i < n / 2; i++) {
            light[i * 2] = r.nextFloat() * w;
            light[i * 2 + 1] = r.nextFloat() * h;
        }
        return new float[][]{dark, light};
    }

    static void drawGrain(Canvas c, float[][] grain, Paint p, float density) {
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(density * 0.9f);
        p.setColor(0x26000000);
        c.drawPoints(grain[0], p);
        p.setColor(0x14FFFFFF);
        c.drawPoints(grain[1], p);
    }

    /** Largest text size (px) at which text fits within maxLen along its baseline, capped at maxSize. */
    static float fit(Paint p, String text, float maxLen, float maxSize) {
        p.setTextSize(maxSize);
        float w = p.measureText(text);
        return w <= maxLen ? maxSize : maxSize * maxLen / w;
    }

    /** Splits a title at the space closest to its middle, or returns null for one-word titles. */
    static String[] splitMiddle(String text) {
        int mid = text.length() / 2, best = -1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == ' ' && (best < 0 || Math.abs(i - mid) < Math.abs(best - mid))) best = i;
        }
        return best < 0 ? null : new String[]{text.substring(0, best), text.substring(best + 1)};
    }

    /** A small gilt lozenge ornament centred on (cx, cy). */
    static void lozenge(Canvas c, float cx, float cy, float r, Paint gold) {
        android.graphics.Path p = new android.graphics.Path();
        p.moveTo(cx, cy - r);
        p.lineTo(cx + r * 0.62f, cy);
        p.lineTo(cx, cy + r);
        p.lineTo(cx - r * 0.62f, cy);
        p.close();
        c.drawPath(p, gold);
    }
}
