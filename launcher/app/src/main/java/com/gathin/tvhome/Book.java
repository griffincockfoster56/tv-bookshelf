package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** One volume on the bookshelf: an app (focusable, opens), a classic, or a volume of a matching set. */
final class Book {

    enum Kind { APP, CLASSIC, SET }

    final Kind kind;
    final String title;
    final String author;        // classics: the author; sets: the volume numeral; apps: null
    final int leather;          // spine and cover colour
    final int label;            // title label colour
    final boolean vellum;       // pale binding with dark lettering
    final float widthDp, heightDp;
    final long seed;            // stable per-book randomness (bands, wear)

    // Apps only.
    final String pkg;
    final ComponentName component;
    final Drawable banner, icon;

    private Book(Kind kind, String title, String author, int leather, int label, boolean vellum,
                 float widthDp, float heightDp, String pkg, ComponentName component, Drawable banner, Drawable icon) {
        this.kind = kind;
        this.title = title;
        this.author = author;
        this.leather = leather;
        this.label = label;
        this.vellum = vellum;
        this.widthDp = widthDp;
        this.heightDp = heightDp;
        this.seed = title.hashCode() * 31L + (author == null ? 0 : author.hashCode());
        this.pkg = pkg;
        this.component = component;
        this.banner = banner;
        this.icon = icon;
    }

    boolean isApp() {
        return kind == Kind.APP;
    }

    String key() {
        return component != null ? component.flattenToShortString() : kind + ":" + title + ":" + author;
    }

    // ---- the library ---------------------------------------------------------------------------

    private static final int OXBLOOD = 0xFF5A1C17, FOREST = 0xFF1F3A29, NAVY = 0xFF1C2A42, EBONY = 0xFF1C1612,
            TAN = 0xFF8A5B30, CHOCOLATE = 0xFF3D2416, BURGUNDY = 0xFF4A1726, OLIVE = 0xFF3B3E21, VELLUM = 0xFFCDBB90;
    private static final int LABEL_BLACK = 0xFF16100C, LABEL_RED = 0xFF7A2017, LABEL_GREEN = 0xFF1D3A25;

    /** Thirty classics, alternating women and men. {title, author, leather, label}. */
    private static final Object[][] CLASSICS = {
            {"Pride and Prejudice", "Austen", TAN, LABEL_RED},
            {"War and Peace", "Tolstoy", OXBLOOD, LABEL_BLACK},
            {"Middlemarch", "George Eliot", FOREST, LABEL_RED},
            {"De Officiis", "Cicero", VELLUM, LABEL_RED},
            {"To the Lighthouse", "Woolf", NAVY, LABEL_BLACK},
            {"The Republic", "Plato", CHOCOLATE, LABEL_RED},
            {"Frankenstein", "Mary Shelley", EBONY, LABEL_RED},
            {"Nicomachean Ethics", "Aristotle", BURGUNDY, LABEL_BLACK},
            {"Wuthering Heights", "Emily Brontë", OLIVE, LABEL_BLACK},
            {"Infinite Jest", "Foster Wallace", NAVY, LABEL_RED},
            {"Jane Eyre", "Charlotte Brontë", OXBLOOD, LABEL_BLACK},
            {"The Odyssey", "Homer", TAN, LABEL_BLACK},
            {"Beloved", "Toni Morrison", CHOCOLATE, LABEL_BLACK},
            {"Moby-Dick", "Melville", FOREST, LABEL_BLACK},
            {"The Age of Innocence", "Wharton", BURGUNDY, LABEL_BLACK},
            {"The Brothers Karamazov", "Dostoevsky", EBONY, LABEL_RED},
            {"Poems", "Emily Dickinson", VELLUM, LABEL_GREEN},
            {"The Divine Comedy", "Dante", OXBLOOD, LABEL_BLACK},
            {"Fragments", "Sappho", NAVY, LABEL_RED},
            {"The Great Gatsby", "Fitzgerald", FOREST, LABEL_RED},
            {"My Ántonia", "Willa Cather", TAN, LABEL_GREEN},
            {"Meditations", "Marcus Aurelius", CHOCOLATE, LABEL_RED},
            {"The Tale of Genji", "Murasaki Shikibu", BURGUNDY, LABEL_BLACK},
            {"Hamlet", "Shakespeare", EBONY, LABEL_RED},
            {"Rights of Woman", "Wollstonecraft", OLIVE, LABEL_RED},
            {"Don Quixote", "Cervantes", OXBLOOD, LABEL_BLACK},
            {"Their Eyes Were Watching God", "Zora Neale Hurston", NAVY, LABEL_BLACK},
            {"Essays", "Montaigne", VELLUM, LABEL_RED},
            {"The Second Sex", "de Beauvoir", FOREST, LABEL_BLACK},
            {"Leaves of Grass", "Whitman", TAN, LABEL_RED},
    };

    private static final String[] NUMERALS = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
            "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX"};

    static List<Book> classics() {
        List<Book> out = new ArrayList<>();
        for (Object[] c : CLASSICS) {
            String title = (String) c[0];
            Random r = new Random(title.hashCode());
            int leather = (int) c[2];
            out.add(new Book(Kind.CLASSIC, title, (String) c[1], leather, (int) c[3], leather == VELLUM,
                    30 + r.nextInt(14), 150 + r.nextInt(32), null, null, null, null));
        }
        return out;
    }

    /** The next volume of the gilt green Encyclopaedia Britannica that fills spare shelf space. */
    static Book setVolume(int n) {
        return new Book(Kind.SET, "Encyclopaedia Britannica", NUMERALS[n % NUMERALS.length], 0xFF233F2C, LABEL_RED,
                false, 32, 172, null, null, null, null);
    }

    /**
     * The installed apps as volumes: dock apps first in config order, then the rest alphabetically, then the
     * shelf-end apps in config order.
     */
    static List<Book> apps(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        HashSet<String> hidden = new HashSet<>(Arrays.asList(ctx.getResources().getStringArray(R.array.hidden_apps)));
        List<String> favourites = Arrays.asList(ctx.getResources().getStringArray(R.array.dock_apps));
        List<String> atEnd = Arrays.asList(ctx.getResources().getStringArray(R.array.shelf_end_apps));
        List<ResolveInfo> found = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        List<Book> out = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || hidden.contains(pkg) || !seen.add(pkg)) continue;
            Drawable banner = ri.activityInfo.loadBanner(pm);
            if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
            Drawable icon = ri.loadIcon(pm);
            String label = String.valueOf(ri.loadLabel(pm));
            int leather = leatherFrom(AppAdapter.vividColor(banner != null ? banner : icon));
            int labelColor = isReddish(leather) ? LABEL_BLACK : LABEL_RED;
            Random r = new Random(pkg.hashCode());
            out.add(new Book(Kind.APP, label, null, leather, labelColor, false, 48 + r.nextInt(8), 176 + r.nextInt(10),
                    pkg, new ComponentName(pkg, ri.activityInfo.name), banner, icon));
        }
        Collections.sort(out, (a, b) -> {
            int fa = favourites.indexOf(a.pkg), fb = favourites.indexOf(b.pkg);
            if (fa >= 0 || fb >= 0) {
                if (fa < 0) return 1;
                if (fb < 0) return -1;
                return Integer.compare(fa, fb);
            }
            int ea = atEnd.indexOf(a.pkg), eb = atEnd.indexOf(b.pkg);
            if (ea >= 0 || eb >= 0) {
                if (ea < 0) return -1;
                if (eb < 0) return 1;
                return Integer.compare(ea, eb);
            }
            return a.title.compareToIgnoreCase(b.title);
        });
        return out;
    }

    /** Number of apps pinned to the top (eye-level) shelf. */
    static int dockCount(Context ctx, List<Book> apps) {
        List<String> favourites = Arrays.asList(ctx.getResources().getStringArray(R.array.dock_apps));
        int n = 0;
        for (Book b : apps) if (favourites.contains(b.pkg)) n++;
        return n;
    }

    /** Turns an app's brand colour into a believable dyed leather: less saturated, much darker. */
    private static int leatherFrom(int vivid) {
        float[] hsv = new float[3];
        Color.colorToHSV(vivid, hsv);
        if (hsv[1] < 0.2f) return TAN;               // white or grey brands get natural tan calf
        hsv[1] = Math.min(hsv[1], 0.85f) * 0.8f;
        hsv[2] = 0.4f;
        return Color.HSVToColor(hsv);
    }

    private static boolean isReddish(int c) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        return hsv[1] > 0.3f && (hsv[0] < 25 || hsv[0] > 330);
    }

    // ---- title page wording ----------------------------------------------------------------------

    private static final Map<String, String> TAGLINES = new HashMap<>();

    static {
        TAGLINES.put("com.netflix.ninja", "Being a Compendium of Films & Serials");
        TAGLINES.put("com.wbd.stream", "Prestige Dramas, Collected & Bound");
        TAGLINES.put("com.google.android.youtube.tv", "An Encyclopaedia of Every Thing");
        TAGLINES.put("com.apple.atve.sony.appletv", "Original Productions, Finely Pressed");
        TAGLINES.put("com.amazon.amazonvideo.livingroom", "A Catalogue of Pictures, Delivered");
        TAGLINES.put("com.spotify.tv.android", "A Gramophone for the Modern Age");
        TAGLINES.put("com.google.android.youtube.tvunplugged", "Live Broadcasts in Daily Editions");
        TAGLINES.put("com.android.vending", "A Directory of Further Volumes");
        TAGLINES.put("com.sony.dtv.smartmediaapp", "Personal Recordings & Photographs");
        TAGLINES.put("com.sony.dtv.timers", "On the Keeping of Time");
        TAGLINES.put("com.peacocktv.peacockandroid", "Comedies, Contests & the Evening News");
        TAGLINES.put("com.cbs.ott", "From the Mountain: Pictures & Programmes");
        TAGLINES.put("com.hulu.livingroomplus", "The Week's Broadcasts, Gathered");
        TAGLINES.put("com.disney.disneyplus", "Fables, Fairy Tales & Far Galaxies");
        TAGLINES.put("com.gotv.nflgamecenter.us.lite", "A Chronicle of the Gridiron");
    }

    String tagline() {
        String t = pkg != null ? TAGLINES.get(pkg) : null;
        return t != null ? t : "A Volume of the Household Library";
    }

    static String roman(int n) {
        int[] v = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] s = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            while (n >= v[i]) {
                n -= v[i];
                sb.append(s[i]);
            }
        }
        return sb.toString();
    }
}
