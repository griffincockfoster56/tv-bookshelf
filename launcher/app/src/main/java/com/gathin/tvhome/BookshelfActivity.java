package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Bookshelf home: an old brick wall with a walnut bookcase. Every app is a leather-bound volume among the
 * classics. Moving selects a book (instantly); OK takes it off the shelf and opens it to its title page in 3D;
 * OK again opens the app; Back returns the book to the shelf.
 */
public class BookshelfActivity extends Activity {

    private static Bitmap wallBitmap;            // painted once per process
    private static String lastKey;               // the last selected book, kept across app launches

    private float d;
    private int screenW, screenH;
    private FrameLayout root, booksLayer, stage;
    private TextView btnInputs, btnWifi, btnSettings;
    private String liveTvPkg;

    private final List<SpineView> appSpines = new ArrayList<>();     // reading order
    private final List<Integer> appShelf = new ArrayList<>();
    private String shelfSignature;

    // The book that is out.
    private SpineView openSpine;
    private boolean busy;        // animating: keys are swallowed
    private boolean open;        // title page showing: OK launches
    private View scrim, shadow;
    private FrameLayout group;
    private SpineView flyer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bookshelf);
        d = getResources().getDisplayMetrics().density;
        screenW = getResources().getDisplayMetrics().widthPixels;
        screenH = getResources().getDisplayMetrics().heightPixels;
        liveTvPkg = getString(R.string.live_tv_package).trim();

        root = findViewById(R.id.shelfRoot);
        booksLayer = findViewById(R.id.books);
        stage = findViewById(R.id.stage);
        if (wallBitmap == null || wallBitmap.getWidth() != screenW) wallBitmap = WallPainter.paint(screenW, screenH, d);
        ((ImageView) findViewById(R.id.wall)).setImageBitmap(wallBitmap);

        btnInputs = findViewById(R.id.btnInputs);
        btnWifi = findViewById(R.id.btnWifi);
        btnSettings = findViewById(R.id.btnSettings);
        btnInputs.setOnClickListener(v -> new InputsMenu(this, root, liveTvPkg).show(btnInputs));
        btnWifi.setOnClickListener(v -> InputsMenu.open(this, new Intent(Settings.ACTION_WIFI_SETTINGS)));
        btnSettings.setOnClickListener(v -> InputsMenu.open(this, new Intent(Settings.ACTION_SETTINGS)));
        View.OnKeyListener tagKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && v == btnInputs) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v == btnSettings) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                focusBook(lastKey);
                return true;
            }
            return false;
        };
        btnInputs.setOnKeyListener(tagKeys);
        btnWifi.setOnKeyListener(tagKeys);
        btnSettings.setOnKeyListener(tagKeys);
    }

    @Override
    protected void onResume() {
        super.onResume();
        putBookBackNow();
        buildShelvesIfChanged();
        btnWifi.setText(InputsMenu.networkLabel(this));
        booksLayer.post(() -> focusBook(lastKey));
    }

    @Override
    public void onBackPressed() {
        // Home screen: Back only puts a book back.
        if (open) putBookBack();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!busy && !open) return super.dispatchKeyEvent(event);
        if (event.getAction() == KeyEvent.ACTION_DOWN && open) {
            int k = event.getKeyCode();
            if (k == KeyEvent.KEYCODE_BACK) {
                putBookBack();
            } else if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                launch(openSpine.book);
            }
        }
        return true;    // nothing else reaches the shelf while a book is out
    }

    // ---- shelves -----------------------------------------------------------------------------------

    private void buildShelvesIfChanged() {
        List<Book> apps = Book.apps(this);
        StringBuilder sig = new StringBuilder();
        for (Book b : apps) sig.append(b.key()).append(';');
        if (sig.toString().equals(shelfSignature)) return;
        shelfSignature = sig.toString();

        booksLayer.removeAllViews();
        appSpines.clear();
        appShelf.clear();
        int dock = Book.dockCount(this, apps);
        if (dock == 0) dock = Math.min(6, apps.size());
        List<List<Book>> perShelf = new ArrayList<>();
        perShelf.add(new ArrayList<>(apps.subList(0, dock)));
        perShelf.add(new ArrayList<>(apps.subList(dock, apps.size())));

        List<Book> classics = Book.classics();
        int[] next = {0, 0};     // next classic, next encyclopaedia volume
        for (int s = 0; s < WallPainter.SHELVES; s++) layoutShelf(s, perShelf.get(s), classics, next);
    }

    /** Places one shelf: its apps spread evenly among classics, filled out with the encyclopaedia set. */
    private void layoutShelf(int shelf, List<Book> apps, List<Book> classics, int[] next) {
        float wDp = screenW / d;
        float innerL = (WallPainter.CASE_INSET + WallPainter.UPRIGHT + 6) * d;
        float innerR = (wDp - WallPainter.CASE_INSET - WallPainter.UPRIGHT - 6) * d;
        float span = innerR - innerL, gap = 1.5f * d;

        float appWidth = 0;
        for (Book b : apps) appWidth += b.widthDp * d + gap;
        int fillers = Math.max(0, (int) ((span - appWidth) / (38 * d + gap)));
        int slots = apps.size() + 1;

        List<Book> row = new ArrayList<>();
        for (int i = 0; i < slots; i++) {
            int n = fillers / slots + (i < fillers % slots ? 1 : 0);
            for (int k = 0; k < n; k++) {
                row.add(next[0] < classics.size() ? classics.get(next[0]++) : Book.setVolume(next[1]++));
            }
            if (i < apps.size()) row.add(apps.get(i));
        }
        // Trim fillers from the end until the row fits, then share any spare space as extra gaps.
        float total = 0;
        for (Book b : row) total += b.widthDp * d + gap;
        for (int i = row.size() - 1; i >= 0 && total > span; i--) {
            if (row.get(i).isApp()) continue;
            total -= row.get(i).widthDp * d + gap;
            row.remove(i);
        }
        float extra = row.size() > 1 ? Math.min(3 * d, Math.max(0, span - total) / (row.size() - 1)) : 0;
        float x = innerL + Math.max(0, (span - total - extra * (row.size() - 1)) / 2f);
        float bottom = WallPainter.plankTop(shelf) * d;

        for (Book b : row) {
            int w = Math.round(b.widthDp * d), h = Math.round(b.heightDp * d);
            SpineView v = new SpineView(this, b);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
            lp.leftMargin = Math.round(x);
            lp.topMargin = Math.round(bottom - h);
            booksLayer.addView(v, lp);
            if (b.isApp()) {
                appSpines.add(v);
                appShelf.add(shelf);
                v.setOnKeyListener(this::onBookKey);
                v.setOnClickListener(view -> takeDown((SpineView) view));
                v.setOnFocusChangeListener((view, has) -> {
                    if (has) lastKey = ((SpineView) view).book.key();
                });
            }
            x += w + gap + extra;
        }
    }

    /** Deterministic D-pad: Left/Right walk the apps in reading order; Up/Down jump to the nearest app on the next shelf. */
    private boolean onBookKey(View v, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        int i = appSpines.indexOf(v);
        if (i < 0) return false;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (i > 0) appSpines.get(i - 1).requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (i < appSpines.size() - 1) appSpines.get(i + 1).requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP: {
                View target = nearestOnShelf(appShelf.get(i) - 1, v);
                if (target == null) target = nearestTag(v);
                target.requestFocus();
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_DOWN: {
                View target = nearestOnShelf(appShelf.get(i) + 1, v);
                if (target != null) target.requestFocus();
                return true;
            }
            default:
                return false;
        }
    }

    private View nearestOnShelf(int shelf, View from) {
        float cx = from.getLeft() + from.getWidth() / 2f;
        View best = null;
        float bestDist = Float.MAX_VALUE;
        for (int j = 0; j < appSpines.size(); j++) {
            if (appShelf.get(j) != shelf) continue;
            SpineView s = appSpines.get(j);
            float dist = Math.abs(s.getLeft() + s.getWidth() / 2f - cx);
            if (dist < bestDist) {
                bestDist = dist;
                best = s;
            }
        }
        return best;
    }

    private View nearestTag(View from) {
        float cx = from.getLeft() + from.getWidth() / 2f;
        View best = btnInputs;
        float bestDist = Float.MAX_VALUE;
        for (View t : new View[]{btnInputs, btnWifi, btnSettings}) {
            int[] loc = new int[2];
            t.getLocationInWindow(loc);
            float dist = Math.abs(loc[0] + t.getWidth() / 2f - cx);
            if (dist < bestDist) {
                bestDist = dist;
                best = t;
            }
        }
        return best;
    }

    private void focusBook(String key) {
        if (appSpines.isEmpty()) {
            btnSettings.requestFocus();
            return;
        }
        for (SpineView s : appSpines) {
            if (s.book.key().equals(key)) {
                s.requestFocus();
                return;
            }
        }
        appSpines.get(0).requestFocus();
    }

    private void launch(Book b) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(b.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
        } catch (Exception ex) {
            Toast.makeText(this, getString(R.string.cant_open, b.title), Toast.LENGTH_SHORT).show();
        }
    }

    // ---- taking a book down ------------------------------------------------------------------------

    private static final Interpolator EASE_OUT = new DecelerateInterpolator(1.8f);
    private static final Interpolator EASE_IN = new AccelerateInterpolator(1.4f);
    private static final Interpolator EASE = new AccelerateDecelerateInterpolator();

    /**
     * The opening sequence, about two seconds in all:
     * the book slides forward off the shelf, flies to the centre turning to show its front cover, the cover
     * swings open onto the marbled endpaper, a few leaves riffle past, and the title page and plate print in.
     */
    private void takeDown(SpineView spine) {
        if (busy || open) return;
        busy = true;
        openSpine = spine;
        Book b = spine.book;
        lastKey = b.key();
        stage.removeAllViews();
        stage.setVisibility(View.VISIBLE);

        // Warm dark backdrop.
        scrim = new View(this);
        GradientDrawable dim = new GradientDrawable();
        dim.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        dim.setGradientRadius(screenW * 0.7f);
        dim.setColors(new int[]{0xB0201208, 0xF00A0503});
        scrim.setBackground(dim);
        scrim.setAlpha(0f);
        stage.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // The open book's geometry: two pages of PW x PH around a central hinge, boards overhanging by m.
        float ph = Math.min(screenH * 0.86f, 470 * d);
        float pw = ph * 0.84f, m = 10 * d;
        int gw = Math.round(2 * (pw + m)), gh = Math.round(ph + 2 * m);
        float hinge = pw + m;

        shadow = new View(this);
        GradientDrawable sh = new GradientDrawable();
        sh.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        sh.setGradientRadius(gw * 0.5f);
        sh.setColors(new int[]{0xAA000000, 0x00000000});
        shadow.setBackground(sh);
        shadow.setAlpha(0f);
        FrameLayout.LayoutParams shLp = new FrameLayout.LayoutParams(Math.round(gw * 1.1f), Math.round(gh * 0.35f));
        shLp.leftMargin = (screenW - shLp.width) / 2;
        shLp.topMargin = (screenH + gh) / 2 - shLp.height / 2;
        stage.addView(shadow, shLp);

        group = new FrameLayout(this);
        group.setClipChildren(false);
        FrameLayout.LayoutParams gLp = new FrameLayout.LayoutParams(gw, gh);
        gLp.leftMargin = (screenW - gw) / 2;
        gLp.topMargin = (screenH - gh) / 2;
        stage.addView(group, gLp);

        BookPartView board = part(BookPartView.Part.BOARD, b, m, hinge, 0, pw + m, gh);
        BookPartView title = part(BookPartView.Part.TITLE, b, m, hinge, m, pw, ph);
        BookPartView ink = part(BookPartView.Part.TITLE, b, m, hinge, m, pw, ph);   // the printed page over it
        BookPartView endpaper = part(BookPartView.Part.ENDPAPER, b, m, 0, 0, pw + m, gh);
        BookPartView plate = part(BookPartView.Part.PLATE, b, m, m, m, pw, ph);
        List<BookPartView> leaves = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            BookPartView leaf = part(BookPartView.Part.LEAF, b, m, hinge, m, pw, ph);
            leaf.setId(View.generateViewId());
            leaves.add(0, leaf);   // added bottom-up; the last added (top) turns first
        }
        BookPartView cover = part(BookPartView.Part.COVER, b, m, hinge, 0, pw + m, gh);
        title.setReveal(0f);
        ink.setClipBounds(new Rect(0, 0, Math.round(pw), 0));
        // Only the topmost page of the stack is drawn; each one below appears as the page above starts to
        // turn. Drawing hidden pages costs the TV's GPU dearly.
        title.setVisibility(View.INVISIBLE);
        ink.setVisibility(View.INVISIBLE);
        for (int i = 1; i < leaves.size(); i++) leaves.get(i).setVisibility(View.INVISIBLE);
        // Paint every page now, in parallel, while the spine is still in flight.
        List<BookPartView> parts = new ArrayList<>(leaves);
        parts.add(board); parts.add(title); parts.add(ink); parts.add(endpaper); parts.add(plate); parts.add(cover);
        for (BookPartView v : parts) {
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            new Thread(() -> v.prepare(lp.width, lp.height), "bindery").start();
        }
        plate.setAlpha(0f);
        endpaper.setPivotX(pw + m);
        endpaper.setPivotY(gh / 2f);
        endpaper.setRotationY(90f);
        endpaper.setVisibility(View.INVISIBLE);
        cover.setPivotX(0f);
        cover.setPivotY(gh / 2f);
        for (BookPartView leaf : leaves) {
            leaf.setPivotX(0f);
            leaf.setPivotY(ph / 2f);
        }

        // Closed, the book sits centred on screen, side-on, small.
        group.setCameraDistance(9000 * d);
        group.setPivotX(hinge + (pw + m) / 2f);
        group.setPivotY(gh / 2f);
        group.setTranslationX(-(pw + m) / 2f);
        group.setRotationY(90f);
        group.setScaleX(0.62f);
        group.setScaleY(0.62f);
        group.setVisibility(View.INVISIBLE);

        // A copy of the spine flies off the shelf in its place.
        int[] at = new int[2];
        spine.getLocationInWindow(at);
        int[] stageAt = new int[2];
        stage.getLocationInWindow(stageAt);
        flyer = new SpineView(this, b);
        flyer.setFocusable(false);
        flyer.shareBinding(spine);
        FrameLayout.LayoutParams fLp = new FrameLayout.LayoutParams(spine.getWidth(), spine.getHeight());
        fLp.leftMargin = at[0] - stageAt[0];
        fLp.topMargin = at[1] - stageAt[1];
        stage.addView(flyer, fLp);
        flyer.setCameraDistance(9000 * d);
        flyer.setPivotX(spine.getWidth() / 2f);
        flyer.setPivotY(spine.getHeight() / 2f);
        flyer.setScaleX(spine.getScaleX());
        flyer.setScaleY(spine.getScaleY());
        flyer.setTranslationY(spine.getTranslationY());
        spine.setAlpha(0f);     // not INVISIBLE: that would hand focus to the next book
        // While the book is out, the shelves behind are still: draw them as one cached layer, and the
        // backdrop and shadow as layers too, so fading them is just compositing.
        booksLayer.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        scrim.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        shadow.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        for (View v : new View[]{flyer, board, title, ink, endpaper, plate, cover}) v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        for (View v : leaves) v.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // 0-220 ms: the book slides forward out of the row.
        play(scrim, 0, 600, EASE, null, null, pv(View.ALPHA, 0f, 1f));
        play(flyer, 0, 220, EASE_OUT, null, null,
                pv(View.TRANSLATION_Y, spine.getTranslationY(), -16 * d),
                pv(View.SCALE_X, spine.getScaleX(), 1.25f), pv(View.SCALE_Y, spine.getScaleY(), 1.2f));

        // 220-480 ms: it flies to the centre, turning away to bring its front cover round.
        float dx = screenW / 2f - (fLp.leftMargin + spine.getWidth() / 2f);
        float dy = screenH / 2f - (fLp.topMargin + spine.getHeight() / 2f);
        float grow = (ph * 0.62f) / spine.getHeight();
        play(flyer, 220, 260, EASE_IN, null, () -> flyer.setVisibility(View.GONE),
                pv(View.TRANSLATION_X, 0f, dx), pv(View.TRANSLATION_Y, -16 * d, dy),
                pv(View.SCALE_X, 1.25f, grow), pv(View.SCALE_Y, 1.2f, grow), pv(View.ROTATION_Y, 0f, -90f));

        // 480-800 ms: the front cover turns to face us as the book grows to full size.
        play(group, 480, 320, EASE_OUT, () -> group.setVisibility(View.VISIBLE), null,
                pv(View.ROTATION_Y, 90f, 0f), pv(View.SCALE_X, 0.62f, 1f), pv(View.SCALE_Y, 0.62f, 1f));
        play(shadow, 480, 400, EASE, null, null, pv(View.ALPHA, 0f, 1f));

        // 860-1300 ms: the cover swings open onto the marbled endpaper and the spread slides to centre.
        long coverAt = 860;
        play(group, coverAt, 440, EASE, null, null, pv(View.TRANSLATION_X, -(pw + m) / 2f, 0f));
        play(cover, coverAt, 220, EASE_IN, null, () -> cover.setVisibility(View.INVISIBLE), pv(View.ROTATION_Y, 0f, -90f));
        play(endpaper, coverAt + 220, 220, EASE_OUT, () -> endpaper.setVisibility(View.VISIBLE), () -> {
            busy = false;
            open = true;     // OK can open the app from here on
        }, pv(View.ROTATION_Y, 90f, 0f));

        // 1200-1950 ms: the leaves riffle over, each settling onto the left-hand page.
        long riffleAt = 1200;
        for (int i = 0; i < leaves.size(); i++) {
            BookPartView leaf = leaves.get(i);
            boolean last = i == leaves.size() - 1;
            View below = last ? null : leaves.get(i + 1);
            play(leaf, riffleAt + i * 110L, 420, EASE, () -> {
                if (below != null) below.setVisibility(View.VISIBLE);
                else {
                    title.setVisibility(View.VISIBLE);
                    ink.setVisibility(View.VISIBLE);
                }
            }, () -> {
                if (!last) {
                    leaf.setVisibility(View.INVISIBLE);
                    return;
                }
                // The plate prints in over the last leaf: a fade of its cached layer, which costs the GPU
                // almost nothing, rather than repainting the page every frame.
                plate.bringToFront();
                play(plate, 0, 700, EASE_OUT, null, () -> leaf.setVisibility(View.INVISIBLE), pv(View.ALPHA, 0f, 1f));
            }, pv(View.ROTATION_Y, 0f, -180f));
        }
        // The title page prints in, top to bottom, as the last leaf lifts away: the printed page is uncovered
        // by a moving clip, which only recomposites its cached layer.
        int inkH = Math.round(ph), inkW = Math.round(pw);
        ValueAnimator print = ValueAnimator.ofInt(0, inkH);
        print.setStartDelay(riffleAt + (leaves.size() - 1) * 110L + 200);
        print.setDuration(900);
        print.setInterpolator(EASE_OUT);
        print.addUpdateListener(an -> ink.setClipBounds(new Rect(0, 0, inkW, (int) an.getAnimatedValue())));
        running.add(print);
        print.start();
    }

    private int generation;     // bumps on every stop, so end actions of cancelled animations do nothing
    private final List<Animator> running = new ArrayList<>();

    private static PropertyValuesHolder pv(android.util.Property<View, Float> prop, float from, float to) {
        return PropertyValuesHolder.ofFloat(prop, from, to);
    }

    /** Starts one timed animation on a view, with optional actions at its start and its (uncancelled) end. */
    private void play(View v, long delay, long duration, Interpolator in, Runnable onStart, Runnable onEnd,
                      PropertyValuesHolder... values) {
        final int gen = generation;
        ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(v, values);
        a.setStartDelay(delay);
        a.setDuration(duration);
        a.setInterpolator(in);
        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                if (onStart != null && gen == generation) onStart.run();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (onEnd != null && gen == generation) onEnd.run();
            }
        });
        running.add(a);
        a.start();
    }

    private BookPartView part(BookPartView.Part kind, Book b, float m, float x, float y, float w, float h) {
        BookPartView v = new BookPartView(this, kind, b, m);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.round(w), Math.round(h));
        lp.leftMargin = Math.round(x);
        lp.topMargin = Math.round(y);
        group.addView(v, lp);
        return v;
    }

    private void stopAnimations() {
        generation++;
        for (Animator a : new ArrayList<>(running)) a.cancel();
        running.clear();
    }

    /** Back: the book shrinks back to its place on the shelf and the room comes back up. */
    private void putBookBack() {
        if (openSpine == null || group == null) return;
        stopAnimations();
        busy = true;
        open = false;
        int[] at = new int[2];
        openSpine.getLocationInWindow(at);
        float tx = at[0] + openSpine.getWidth() / 2f - screenW / 2f;
        float ty = at[1] + openSpine.getHeight() / 2f - screenH / 2f;
        play(group, 0, 340, EASE_IN, null, null,
                pv(View.TRANSLATION_X, group.getTranslationX(), tx), pv(View.TRANSLATION_Y, 0f, ty),
                pv(View.SCALE_X, group.getScaleX(), 0.12f), pv(View.SCALE_Y, group.getScaleY(), 0.3f),
                pv(View.ROTATION_Y, group.getRotationY(), 60f), pv(View.ALPHA, 1f, 0f));
        play(shadow, 0, 200, EASE, null, null, pv(View.ALPHA, shadow.getAlpha(), 0f));
        play(scrim, 0, 340, EASE, null, this::putBookBackNow, pv(View.ALPHA, scrim.getAlpha(), 0f));
    }

    /** Clears the stage at once (on return from an app, or at the end of putting a book back). */
    private void putBookBackNow() {
        stopAnimations();
        stage.removeAllViews();
        stage.setVisibility(View.GONE);
        group = null;
        booksLayer.setLayerType(View.LAYER_TYPE_NONE, null);
        if (openSpine != null) {
            openSpine.setAlpha(1f);
            openSpine.requestFocus();
        }
        openSpine = null;
        busy = false;
        open = false;
    }
}
