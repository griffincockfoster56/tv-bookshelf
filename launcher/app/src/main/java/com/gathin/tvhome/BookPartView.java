package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

import java.util.Calendar;
import java.util.Random;

/**
 * The pieces of the open book, each a flat view that the activity turns in 3D:
 * the front cover, the back board, the marbled inside cover, blank leaves, the title page and the plate.
 */
final class BookPartView extends View {

    enum Part { COVER, BOARD, ENDPAPER, LEAF, TITLE, PLATE }

    private final Part part;
    private final Book book;
    private final float d;
    private final float margin;     // leather overhang around the page block (boards only)
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float[][] grain;
    private boolean paintingFull;   // painting the finished page, whatever reveal says
    private int width, height;      // the size being painted, known before layout
    private float reveal = 1f;      // 0..1: how much of the printed content has appeared
    // Painted once: the TV's GPU is slow at gradients and grain, but quick at copying bitmaps.
    private Bitmap base, full;      // the bare paper or leather; the finished page once fully revealed

    BookPartView(Context c, Part part, Book book, float marginPx) {
        super(c);
        this.part = part;
        this.book = book;
        this.margin = marginPx;
        d = c.getResources().getDisplayMetrics().density;
        t.setTextAlign(Paint.Align.CENTER);
        setCameraDistance(9000 * d);
    }

    void setReveal(float v) {
        reveal = v;
        invalidate();
    }

    float getReveal() {
        return reveal;
    }

    /**
     * Paints the bitmaps for the given size. Safe to call from a background thread before the view is laid
     * out, so the first frames of the animation don't stall on painting.
     */
    synchronized void prepare(int w, int h) {
        if (w <= 0 || h <= 0 || (base != null && w == width && h == height)) return;
        width = w;
        height = h;
        grain = Bindery.grain(book.seed + part.ordinal(), w, h, d);
        base = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        background(new Canvas(base));
        if (part == Part.TITLE || part == Part.PLATE) {
            paintingFull = true;
            full = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(full);
            c.drawBitmap(base, 0, 0, null);
            if (part == Part.TITLE) titlePage(c);
            else plate(c);
            paintingFull = false;
        } else {
            full = null;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        prepare(getWidth(), getHeight());
        if (base == null) return;
        if (full != null && reveal >= 1f) {
            c.drawBitmap(full, 0, 0, null);
        } else {
            c.drawBitmap(base, 0, 0, null);
            if (full != null && reveal > 0f) printIn(c);
        }
    }

    /**
     * The finished page appearing over the bare paper: the title page inks in from top to bottom behind a
     * soft edge, the plate fades up. Only bitmaps are drawn, so it stays smooth on the TV.
     */
    private void printIn(Canvas c) {
        Shader page = new BitmapShader(full, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        if (part == Part.TITLE) {
            float soft = height * 0.3f, edge = -soft + reveal * (height + soft);
            Shader wipe = new LinearGradient(0, edge, 0, edge + soft, 0xFF000000, 0x00000000, Shader.TileMode.CLAMP);
            p.setShader(new ComposeShader(page, wipe, PorterDuff.Mode.DST_IN));
            p.setColor(0xFF000000);
        } else {
            p.setShader(page);
            p.setColor(withAlpha(0xFF000000, reveal));
        }
        p.setStyle(Paint.Style.FILL);
        c.drawRect(0, 0, width, height, p);
        p.setShader(null);
    }

    private void background(Canvas c) {
        switch (part) {
            case COVER: cover(c); break;
            case BOARD: board(c); break;
            case ENDPAPER: endpaper(c); break;
            case LEAF: paper(c, true); leafText(c); break;
            case TITLE: paper(c, true); break;
            case PLATE: paper(c, false); break;
        }
    }

    // ---- leather -----------------------------------------------------------------------------------

    private void leather(Canvas c, float l, float tp, float rt, float b, boolean hingeLeft) {
        int base = book.leather;
        p.setColor(0xFF000000);
        p.setShader(new LinearGradient(hingeLeft ? l : rt, 0, hingeLeft ? rt : l, 0,
                new int[]{Bindery.shade(base, 0.55f), Bindery.shade(base, 1.08f), Bindery.shade(base, 0.9f), Bindery.shade(base, 0.62f)},
                new float[]{0f, 0.12f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        r.set(l, tp, rt, b);
        c.drawRoundRect(r, 4 * d, 4 * d, p);
        p.setShader(null);
        if (grain != null) Bindery.drawGrain(c, grain, p, d);
        p.setStyle(Paint.Style.FILL);
    }

    private void cover(Canvas c) {
        float w = width, h = height;
        leather(c, 0, 0, w, h, true);
        // Hinge groove near the spine edge.
        p.setColor(0x40000000);
        c.drawRect(9 * d, 0, 11 * d, h, p);

        // Double gilt fillet border with corner lozenges.
        Shader gold = Bindery.goldAcross(0, w);
        p.setColor(0xFF000000);
        p.setShader(gold);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.4f * d);
        r.set(22 * d, 18 * d, w - 18 * d, h - 18 * d);
        c.drawRect(r, p);
        p.setStrokeWidth(0.8f * d);
        r.inset(6 * d, 6 * d);
        c.drawRect(r, p);
        p.setStyle(Paint.Style.FILL);
        float[][] corners = {{r.left, r.top}, {r.right, r.top}, {r.left, r.bottom}, {r.right, r.bottom}};
        for (float[] k : corners) Bindery.lozenge(c, k[0], k[1], 5 * d, p);

        // Central panel: ornament, title, ornament.
        float cx = (22 * d + w - 18 * d) / 2f, cy = h / 2f;
        t.setTypeface(Bindery.cinzel(getContext()));
        t.setFontVariationSettings("'wght' 700");
        float size = Bindery.fit(t, book.title.toUpperCase(), w * 0.62f, 34 * d);
        t.setTextSize(size);
        t.setColor(0xFF000000);
        t.setShader(Bindery.goldDown(cy - size, cy + size * 0.2f));
        // Blind-stamped shadow, then the gilt.
        t.setShader(null);
        t.setColor(0x66000000);
        c.drawText(book.title.toUpperCase(), cx + 1.2f * d, cy + 1.2f * d, t);
        t.setColor(0xFF000000);
        t.setShader(Bindery.goldDown(cy - size, cy + size * 0.2f));
        c.drawText(book.title.toUpperCase(), cx, cy, t);

        p.setColor(0xFF000000);

        p.setShader(gold);
        c.drawRect(cx - 40 * d, cy - size - 16 * d, cx + 40 * d, cy - size - 15 * d, p);
        Bindery.lozenge(c, cx, cy - size - 15.5f * d, 4.5f * d, p);
        c.drawRect(cx - 40 * d, cy + 18 * d, cx + 40 * d, cy + 19 * d, p);
        Bindery.lozenge(c, cx, cy + 18.5f * d, 4.5f * d, p);

        t.setFontVariationSettings("'wght' 500");
        t.setTextSize(11 * d);
        t.setLetterSpacing(0.3f);
        c.drawText("VOLUME I", cx, cy + 42 * d, t);
        t.setLetterSpacing(0f);
        p.setShader(null);
    }

    private void board(Canvas c) {
        float w = width, h = height;
        leather(c, 0, 0, w, h, true);
        // Page block edges peeking out on the fore-edge: thin cream lines suggest the thickness.
        p.setColor(0xFFE4D6B5);
        c.drawRect(w - margin - 2 * d, margin + 2 * d, w - margin + 3 * d, h - margin - 2 * d, p);
        p.setColor(0x33000000);
        for (float x = w - margin - 2 * d; x < w - margin + 3 * d; x += 1.3f * d) c.drawLine(x, margin + 2 * d, x, h - margin - 2 * d, p);
    }

    /** Inside of the front cover once turned: a leather turn-in around a sheet of marbled paper. */
    private void endpaper(Canvas c) {
        float w = width, h = height;
        leather(c, 0, 0, w, h, false);
        float l = margin, tp = margin, rt = w, b = h - margin;
        c.save();
        c.clipRect(l, tp, rt, b);
        c.drawColor(0xFFE3D5B5);
        // Turkish marbling: combed waves of colour.
        Random rnd = new Random(book.seed);
        int[] inks = {0x8C2F4868, 0x70803A2E, 0x66B08A45, 0x5C3C5A4C};   // muted Turkish marbling
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        Path path = new Path();
        for (float y = tp - 10 * d; y < b + 10 * d; y += 3.5f * d) {
            p.setColor(inks[rnd.nextInt(inks.length)]);
            p.setStrokeWidth((0.8f + rnd.nextFloat() * 2.6f) * d);
            float amp = (6 + rnd.nextFloat() * 4) * d, freq = 0.03f / d, phase = (y / d) * 0.05f;
            path.reset();
            path.moveTo(l, y);
            for (float x = l; x <= rt; x += 6 * d) {
                float yy = y + amp * (float) Math.sin(x * freq + phase) + amp * 0.5f * (float) Math.sin(x * freq * 3.1f + phase * 2);
                path.lineTo(x, yy);
            }
            c.drawPath(path, p);
        }
        p.setStyle(Paint.Style.FILL);
        // Gutter shadow along the hinge.
        p.setColor(0xFF000000);
        p.setShader(new LinearGradient(rt - 26 * d, 0, rt, 0, 0x00000000, 0x55000000, Shader.TileMode.CLAMP));
        c.drawRect(rt - 26 * d, tp, rt, b, p);
        p.setShader(null);
        c.restore();
    }

    // ---- paper -------------------------------------------------------------------------------------

    /** Aged laid paper with darker edges and a gutter shadow on the hinge side. */
    private void paper(Canvas c, boolean hingeLeft) {
        float w = width, h = height;
        p.setColor(0xFF000000);
        p.setShader(new RadialGradient(w / 2f, h / 2f, Math.max(w, h) * 0.75f,
                new int[]{Bindery.PAPER, Bindery.PAPER, Bindery.PAPER_EDGE}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setColor(0xFF000000);
        p.setShader(hingeLeft
                ? new LinearGradient(0, 0, 34 * d, 0, 0x50000000, 0x00000000, Shader.TileMode.CLAMP)
                : new LinearGradient(w - 34 * d, 0, w, 0, 0x00000000, 0x50000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        // Faint foxing.
        Random rnd = new Random(book.seed + 7);
        p.setColor(0x10804010);
        for (int i = 0; i < 18; i++) c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, (1 + rnd.nextFloat() * 3) * d, p);
    }

    private void leafText(Canvas c) {
        float w = width, h = height;
        Random rnd = new Random(book.seed + getId());
        p.setColor(0x28000000);
        p.setStrokeWidth(2f * d);
        float l = 44 * d, rt = w - 36 * d;
        c.drawLine(w / 2f - 30 * d, 34 * d, w / 2f + 30 * d, 34 * d, p);   // running head
        for (float y = 58 * d; y < h - 44 * d; y += 10 * d) {
            boolean paraEnd = rnd.nextFloat() < 0.12f;
            c.drawLine(l + (paraEnd ? 0 : 0), y, paraEnd ? l + (rt - l) * (0.3f + rnd.nextFloat() * 0.5f) : rt, y, p);
            if (paraEnd) y += 4 * d;
        }
    }

    /** Title page, printed in black and red, appearing line by line as reveal goes from 0 to 1. */
    private void titlePage(Canvas c) {
        float w = width, h = height;
        float cx = w / 2f + 6 * d;
        // Ruled border.
        p.setColor(withAlpha(Bindery.INK, stage(0f, 0.25f)));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.2f * d);
        r.set(38 * d, 30 * d, w - 30 * d, h - 30 * d);
        c.drawRect(r, p);
        p.setStrokeWidth(0.6f * d);
        r.inset(4 * d, 4 * d);
        c.drawRect(r, p);
        p.setStyle(Paint.Style.FILL);

        float y = h * 0.2f;
        t.setShader(null);
        t.setTypeface(Bindery.garamond(getContext()));
        t.setFontVariationSettings("'wght' 500");
        t.setLetterSpacing(0.35f);
        t.setTextSize(12 * d);
        t.setColor(withAlpha(Bindery.RUBRIC, stage(0.1f, 0.35f)));
        c.drawText("THE  HOUSEHOLD  LIBRARY", cx, y, t);
        t.setLetterSpacing(0f);

        // The title.
        y = h * 0.37f;
        t.setTypeface(Bindery.cinzel(getContext()));
        t.setFontVariationSettings("'wght' 700");
        float size = Bindery.fit(t, book.title.toUpperCase(), w - 110 * d, 44 * d);
        t.setTextSize(size);
        t.setColor(withAlpha(Bindery.INK, stage(0.2f, 0.5f)));
        c.drawText(book.title.toUpperCase(), cx, y, t);

        // Rule with a fleuron.
        float a = stage(0.35f, 0.6f);
        p.setColor(withAlpha(Bindery.INK, a));
        c.drawRect(cx - 70 * d * a, y + 20 * d, cx + 70 * d * a, y + 21 * d, p);
        p.setColor(withAlpha(Bindery.RUBRIC, a));
        Bindery.lozenge(c, cx, y + 20.5f * d, 5 * d, p);

        // Tagline in italic.
        t.setTypeface(Bindery.garamondItalic(getContext()));
        t.setFontVariationSettings("'wght' 400");
        t.setTextSize(Bindery.fit(t, book.tagline(), w - 120 * d, 21 * d));
        t.setColor(withAlpha(Bindery.INK, stage(0.45f, 0.75f)));
        c.drawText(book.tagline(), cx, y + 54 * d, t);

        // Imprint.
        t.setTypeface(Bindery.garamond(getContext()));
        t.setFontVariationSettings("'wght' 500");
        t.setLetterSpacing(0.3f);
        t.setTextSize(12 * d);
        t.setColor(withAlpha(Bindery.RUBRIC, stage(0.6f, 0.85f)));
        c.drawText("NEW  YORK", cx, h * 0.76f, t);
        t.setColor(withAlpha(Bindery.INK, stage(0.6f, 0.85f)));
        c.drawText(Book.roman(Calendar.getInstance().get(Calendar.YEAR)), cx, h * 0.76f + 18 * d, t);
        t.setLetterSpacing(0f);

        // How to proceed.
        t.setTypeface(Bindery.garamondItalic(getContext()));
        t.setTextSize(13 * d);
        t.setColor(withAlpha(0xFF6B4A2E, stage(0.8f, 1f)));
        c.drawText("Press OK to open   ·   Back to return it to the shelf", cx, h - 46 * d, t);
    }

    /** The frontispiece: the app's artwork as a tipped-in plate with a caption. */
    private void plate(Canvas c) {
        float w = width, h = height;
        float a = stage(0f, 1f);
        float pw = w - 96 * d, ph = pw * 9f / 16f;
        float l = (w - pw) / 2f - 6 * d, tp = h * 0.42f - ph / 2f;
        // Plate mark: the slightly darker impression around an engraving.
        p.setColor(withAlpha(0xFFE2D3B1, a));
        c.drawRect(l - 14 * d, tp - 14 * d, l + pw + 14 * d, tp + ph + 14 * d, p);
        p.setColor(withAlpha(0x22000000, a));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.8f * d);
        c.drawRect(l - 14 * d, tp - 14 * d, l + pw + 14 * d, tp + ph + 14 * d, p);
        p.setStyle(Paint.Style.FILL);

        Drawable art = book.banner != null ? book.banner : book.icon;
        if (art != null) {
            Drawable copy = art.getConstantState() != null ? art.getConstantState().newDrawable().mutate() : art;
            Rect saved = new Rect(copy.getBounds());
            if (book.banner != null) {
                copy.setBounds((int) l, (int) tp, (int) (l + pw), (int) (tp + ph));
            } else {
                int s = (int) (ph * 0.6f);
                int cx = (int) (l + pw / 2f), cy = (int) (tp + ph / 2f);
                copy.setBounds(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2);
            }
            copy.setAlpha((int) (255 * a));
            copy.draw(c);
            copy.setBounds(saved);
        }
        p.setColor(withAlpha(Bindery.INK, a));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.4f * d);
        c.drawRect(l, tp, l + pw, tp + ph, p);
        p.setStyle(Paint.Style.FILL);

        t.setShader(null);
        t.setTypeface(Bindery.garamondItalic(getContext()));
        t.setFontVariationSettings("'wght' 400");
        t.setTextSize(15 * d);
        t.setColor(withAlpha(Bindery.INK, stage(0.4f, 1f)));
        c.drawText("Plate I.  —  " + book.title, l + pw / 2f, tp + ph + 44 * d, t);
    }

    /** Fade-in factor for an element that appears while reveal goes from a to b. */
    private float stage(float a, float b) {
        return paintingFull ? 1f : Math.max(0f, Math.min(1f, (reveal - a) / (b - a)));
    }

    private static int withAlpha(int color, float a) {
        return (Math.round(((color >>> 24) & 0xFF) * a) << 24) | (color & 0xFFFFFF);
    }
}
