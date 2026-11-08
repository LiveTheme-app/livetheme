package io.hmg.wallpaperapp;

import android.content.SharedPreferences;

/**
 * "분위기" presets: one tap sets seven display values. Changing any of them by hand makes the mood "custom". Theme files and window-size layouts are never touched; these are viewer preferences.
 */
final class Mood {
    static final String CALM = "calm", DEFAULT = "default", VIVID = "vivid", CUSTOM = "custom";
    static final String[] IDS = {CALM, DEFAULT, VIVID};
    static final String[] NAMES = {"차분하게", "기본", "화려하게"};
    static final int DEFAULT_DIM = 15;
    final int dim;
    final boolean nightDim, autoColor, strongText;
    final float effectAmount, effectSpeed, glass;

    private Mood(int dim, boolean nightDim, boolean autoColor, boolean strongText, float effectAmount,
                 float effectSpeed, float glass) {
        this.dim = dim;
        this.nightDim = nightDim;
        this.autoColor = autoColor;
        this.strongText = strongText;
        this.effectAmount = effectAmount;
        this.effectSpeed = effectSpeed;
        this.glass = glass;
    }

    static Mood of(String id) {
        switch (id) {
            case CALM:
                return new Mood(30, true, false, false, 0f, .6f, 1.3f);
            case VIVID:
                return new Mood(0, true, true, true, 1.6f, 1.3f, .75f);
            default:
                return new Mood(DEFAULT_DIM, true, false, false, 1f, 1f, 1f);
        }
    }

    /**
     * Writes the preset's values; "custom" keeps whatever is stored.
     */
    static void apply(SharedPreferences.Editor e, String id) {
        e.putString("mood", id);
        if (CUSTOM.equals(id))
            return;
        Mood m = of(id);
        e.putInt("dim", m.dim).putBoolean("nightDim", m.nightDim).putBoolean("autoColor", m.autoColor)
                .putBoolean("strongText", m.strongText).putFloat("effectAmount", m.effectAmount)
                .putFloat("effectSpeed", m.effectSpeed).putFloat("glass", m.glass);
    }

    /**
     * First start of 1.32: no mood saved yet. A user who already moved the dim slider starts as "custom" and keeps that value; everyone else gets "default", which equals the 1.31 look. Returns the mood to store.
     */
    static String migrate(SharedPreferences p) {
        if (p.contains("mood"))
            return p.getString("mood", DEFAULT);
        return p.getInt("dim", DEFAULT_DIM) == DEFAULT_DIM ? DEFAULT : CUSTOM;
    }

    static int index(String id) {
        for (int i = 0; i < IDS.length; i++)
            if (IDS[i].equals(id))
                return i;
        return -1;
    }
}
