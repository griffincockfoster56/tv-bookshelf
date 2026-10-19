package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import java.util.Random;

/**
 * The spine of a leather-bound book: rounded calf with raised bands, gilt fillets, a morocco title label
 * and gold lettering running down the spine. App books are focusable; focus lifts the book instantly.
 */
final class SpineView extends View {

    final Book book;
    private final float d;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float[][] grain;
    private Bitmap cache;           // the binding, painted once; the TV's GPU only has to copy it
    private float[] bands;          // band centres as fractions of height
    private float labelTop, labelBottom, authorTop, authorBottom;

    SpineView(Context c, Book book) {
        super(c);
        this.book = book;
        d = c.getResources().getDisplayMetrics().density;
        text.setTypeface(Bindery.cinzel(c));
        text.setTextAlign(Paint.Align.CENTER);
        setFocusable(book.isApp());
        setClickable(book.isApp());
        Random rnd = new Random(book.seed);
        // Four or five raised bands; the title label sits in the second panel, the author lower down.
        boolean five = rnd.nextBoolean();
        bands = five ? new float[]{0.09f, 0.2f, 0.67f, 0.8f, 0.92f} : new float[]{0.1f, 0.21f, 0.7f, 0.9f};
        labelTop = bands[1] + 0.025f;
        labelBottom = bands[2] - 0.025f;
        authorTop = bands[2] + 0.025f;
        authorBottom = bands[3] - 0.02f;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        grain = Bindery.grain(book.seed, w, h, d);
        if (cache != null && (cache.getWidth() != w || cache.getHeight() != h)) cache = null;
    }

    /** Reuses another spine's painted binding (same book, same size), e.g. for the copy that flies off the shelf. */
    void shareBinding(SpineView other) {
        cache = other.cache;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (cache == null) {
            cache = Bitmap.createBitmap((int) w, (int) h, Bitmap.Config.ARGB_8888);
            bind(new Canvas(cache), w, h);
        }
        canvas.drawBitmap(cache, 0, 0, null);

        // Focused: warm lamplight on the leather and a gilt edge.
        if (isFocused()) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF000000);
            paint.setShader(new LinearGradient(0, 0, 0, h, 0x24FFE2A8, 0x0AFFC070, Shader.TileMode.CLAMP));
            r.set(0, 0, w, h);
            canvas.drawRoundRect(r, 2.5f * d, 2.5f * d, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f * d);
            paint.setColor(0xFFF2D58C);
            r.inset(d, d);
            canvas.drawRoundRect(r, 2.5f * d, 2.5f * d, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void bind(Canvas c, float w, float h) {
        int leather = book.leather;

        // Rounded spine: dark at both edges, a soft highlight a third of the way across.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF000000);
        paint.setShader(new LinearGradient(0, 0, w, 0,
                new int[]{Bindery.shade(leather, 0.42f), Bindery.shade(leather, 0.92f), Bindery.shade(leather, 1.18f),
                        Bindery.shade(leather, 0.86f), Bindery.shade(leather, 0.48f)},
                new float[]{0f, 0.2f, 0.4f, 0.78f, 1f}, Shader.TileMode.CLAMP));
        r.set(0, 0, w, h);
        c.drawRoundRect(r, 2.5f * d, 2.5f * d, paint);
        if (grain != null) Bindery.drawGrain(c, grain, paint, d);

        // Head and tail caps: a little darker and worn.
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(0x33000000);
        c.drawRect(0, 0, w, 4 * d, paint);
        c.drawRect(0, h - 4 * d, w, h, paint);

        // Raised bands with gilt fillets either side.
        Shader goldBand = Bindery.goldAcross(0, w);
        for (float f : bands) {
            float y = f * h;
            paint.setShader(null);
            paint.setColor(0x55000000);
            c.drawRect(0, y + 2.2f * d, w, y + 3.6f * d, paint);              // shadow under the ridge
            paint.setColor(0xFF000000);
            paint.setShader(new LinearGradient(0, y - 2 * d, 0, y + 2 * d,
                    Bindery.shade(leather, 1.35f), Bindery.shade(leather, 0.7f), Shader.TileMode.CLAMP));
            c.drawRect(0, y - 2 * d, w, y + 2.2f * d, paint);                 // the ridge
            paint.setColor(0xFF000000);
            paint.setShader(goldBand);
            c.drawRect(0, y - 4.2f * d, w, y - 3.4f * d, paint);              // fillet above
            c.drawRect(0, y + 4.4f * d, w, y + 5.2f * d, paint);              // fillet below
        }

        // Title label: a panel of contrasting morocco with a gilt rule.
        float inset = Math.max(2.5f * d, w * 0.1f);
        float lt = labelTop * h + 3 * d, lb = labelBottom * h - 3 * d;
        paint.setShader(null);
        paint.setColor(book.label);
        r.set(inset, lt, w - inset, lb);
        c.drawRect(r, paint);
        paint.setColor(0x40000000);
        c.drawRect(inset, lt, w - inset, lt + 1.2f * d, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.8f * d);
        paint.setColor(0xFF000000);
        paint.setShader(goldBand);
        r.inset(1.6f * d, 1.6f * d);
        c.drawRect(r, paint);
        paint.setStyle(Paint.Style.FILL);

        // Small lozenge ornaments in the head and tail panels.
        float headY = (bands[0] * h + 4.5f * d) / 2f + 1.5f * d;
        Bindery.lozenge(c, w / 2f, headY, Math.min(3.2f * d, w * 0.12f), paint);
        float lastBand = bands[bands.length - 1] * h;
        Bindery.lozenge(c, w / 2f, (lastBand + h) / 2f, Math.min(2.6f * d, w * 0.1f), paint);

        // Lettering, running down the spine.
        float labelLen = (lb - lt) - 8 * d;
        float across = (w - 2 * inset) - 3 * d;
        boolean vellumInk = book.vellum;
        drawVertical(c, book.isApp() ? book.title.toUpperCase() : book.title.toUpperCase(), w / 2f, (lt + lb) / 2f,
                labelLen, across, book.isApp() ? 600 : 560, true);

        if (book.author != null) {
            float at = authorTop * h + 4 * d, ab = authorBottom * h - 4 * d;
            if (book.kind == Book.Kind.SET) {
                // Volume numeral, upright, as on a real set.
                text.setFontVariationSettings("'wght' 700");
                text.setShader(vellumInk ? null : Bindery.goldDown(at, ab));
                text.setColor(vellumInk ? Bindery.INK : Bindery.GOLD);
                float size = Bindery.fit(text, book.author, w - 2 * inset, 13 * d);
                text.setTextSize(size);
                c.drawText(book.author, w / 2f, (at + ab) / 2f + size * 0.35f, text);
            } else {
                drawVertical(c, book.author.toUpperCase(), w / 2f, (at + ab) / 2f, ab - at, across * 0.8f, 500, false);
            }
        }
    }

    /**
     * Draws text rotated to run down the spine, centred on (cx, cy). Long titles that would get too small
     * on one line are set on two.
     */
    private void drawVertical(Canvas c, String s, float cx, float cy, float maxLen, float across, int weight, boolean onLabel) {
        text.setFontVariationSettings("'wght' " + weight);
        float capRatio = 0.72f;     // Cinzel cap height relative to the text size
        float maxSize = across / capRatio;
        float one = Bindery.fit(text, s, maxLen, maxSize);
        String[] two = Bindery.splitMiddle(s);
        float twoSize = 0;
        if (two != null) {
            // Two cap heights plus a gap of 0.6 of one must fit across the label.
            float lineMax = across / 2.6f / capRatio;
            twoSize = Math.min(Bindery.fit(text, two[0], maxLen, lineMax), Bindery.fit(text, two[1], maxLen, lineMax));
        }
        c.save();
        c.rotate(90, cx, cy);
        boolean ink = book.vellum && !onLabel;
        text.setColor(ink ? Bindery.INK : Bindery.GOLD);
        text.setShader(ink ? null : Bindery.goldDown(cy - across / 2f, cy + across / 2f));
        if (two != null && twoSize > one * 1.25f) {
            text.setTextSize(twoSize);
            float cap = twoSize * capRatio, gap = cap * 0.6f;
            c.drawText(two[0], cx, cy - gap / 2f, text);
            c.drawText(two[1], cx, cy + gap / 2f + cap, text);
        } else {
            text.setTextSize(one);
            c.drawText(s, cx, cy + one * capRatio / 2f, text);
        }
        c.restore();
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        // Instant, no animation: the book slides a little way out of the shelf.
        setPivotX(getWidth() / 2f);
        setPivotY(getHeight());
        setScaleX(gainFocus ? 1.07f : 1f);
        setScaleY(gainFocus ? 1.05f : 1f);
        setTranslationY(gainFocus ? -5 * d : 0f);
        setTranslationZ(gainFocus ? 8 * d : 0f);
        invalidate();
    }
}
