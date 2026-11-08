package io.hmg.wallpaperapp;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/**
 * 1.38 design system: one set of tokens and components for every app screen (browsing, editing, sheets, dialogs, banners).
 * Widgets on the home screen and theme artwork keep the theme's own palette; everything around them uses these.
 * - Colour: dark glass with iOS-like system colours. Labels are graded INK > MUTED > FAINT; FILL is the grey control fill.
 * - Type (sp, read at car glance distance): TITLE 28, HEADLINE 22, BODY 19, CALLOUT 17, CAPTION 15; 13 only inside badges.
 * - Space: 4/8 grid (S1..S6 = 4, 8, 12, 16, 24, 32). Radius: sheet 32, card 22, control 18, chip 12.
 * - Touch: every control is at least TOUCH (76dp) tall; sheets close with the same ✕ at the top right.
 * - States: pressed (96% scale), selected (segment pill or accent outline), disabled (40%), locked while driving (lock glyph + DRIVE_NOTICE).
 */
final class AppUi {
    static final int SURFACE = 0xff1c1c1e, CARD = 0xff2a2a2d, CARD_HI = 0xff3a3a3f, INK = 0xfff5f5f7, MUTED = 0xffa1a1a8, FAINT = 0xff74747a, ACCENT = 0xff0a84ff, LINE = 0xff3a3a3c;
    static final int SUCCESS = 0xff30d158, WARN = 0xffff9f0a, DANGER = 0xffff453a, ACCENT_SOFT = 0x2e0a84ff, SEPARATOR = 0x1affffff;
    /**
     * Glass fills: translucent where the window behind is blurred, nearly opaque where the device cannot blur. FILL is the grey control fill.
     */
    static final int GLASS = 0xd61c1c1e, GLASS_SOLID = 0xf51c1c1e, EDGE = 0x26ffffff, FILL = 0x3d767680;
    static final Typeface SEMIBOLD = Typeface.create(Typeface.DEFAULT, 600, false), MEDIUM = Typeface.create(
            Typeface.DEFAULT, 500, false);
    static final int T_TITLE = 28, T_HEADLINE = 22, T_BODY = 19, T_CALLOUT = 17, T_CAPTION = 15, T_BADGE = 13;
    static final int S1 = 4, S2 = 8, S3 = 12, S4 = 16, S5 = 24, S6 = 32;
    static final int R_SHEET = 32, R_CARD = 22, R_CONTROL = 18, R_CHIP = 12;
    /**
     * Android for Cars minimum touch target (76dp), also used as the minimum list row height.
     */
    static final int TOUCH = 76;

    /**
     * Drawable by name: AppUi is also compiled into the test probe, whose R class lives in another package.
     */
    static int res(Context c, String name) {
        return c.getResources().getIdentifier(name, "drawable", c.getPackageName());
    }

    static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable shape(Context c, int fill, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, radius));
        if (stroke != 0)
            d.setStroke(dp(c, 1), stroke);
        return d;
    }

    static TextView text(Context c, String value, int size, int color) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setFontFeatureSettings("kern");
        return t;
    }

    static TextView title(Context c, String value) {
        TextView t = text(c, value, T_TITLE, INK);
        t.setTypeface(SEMIBOLD);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return t;
    }

    static TextView headline(Context c, String value) {
        TextView t = text(c, value, T_HEADLINE, INK);
        t.setTypeface(SEMIBOLD);
        return t;
    }

    static Button button(Context c, String title, boolean primary, Runnable action) {
        Button b = new Button(c);
        b.setText(title);
        b.setAllCaps(false);
        b.setTextSize(T_BODY);
        b.setTypeface(SEMIBOLD);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(dp(c, TOUCH));
        b.setPadding(dp(c, 20), 0, dp(c, 20), 0);
        b.setStateListAnimator(null);
        styleButton(b, primary);
        pressable(b);
        if (action != null)
            b.setOnClickListener(v -> action.run());
        return b;
    }

    /**
     * Primary = filled accent (one per screen), otherwise the grey control fill.
     */
    static void styleButton(Button b, boolean primary) {
        Context c = b.getContext();
        b.setTextColor(INK);
        b.setBackgroundTintList(null);
        b.setBackground(
                new RippleDrawable(ColorStateList.valueOf(0x22ffffff), shape(c, primary ? ACCENT : FILL, R_CONTROL, 0),
                        null));
    }

    /**
     * Text-only action in accent colour (e.g. 되돌리기).
     */
    static Button plain(Context c, String title, Runnable action) {
        Button b = button(c, title, false, action);
        b.setTextColor(ACCENT);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22ffffff), shape(c, 0x00000000, R_CONTROL, 0),
                shape(c, 0xffffffff, R_CONTROL, 0)));
        return b;
    }

    /**
     * A round icon-only control (76dp): ✕ to close, ··· for more. The description is what TalkBack and the checks read.
     */
    static Button iconButton(Context c, String iconName, String description, Runnable action) {
        Button b = button(c, "", false, action);
        b.setPadding(0, 0, 0, 0);
        b.setContentDescription(description);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22ffffff), shape(c, FILL, TOUCH / 2, 0), null));
        int r = res(c, iconName);
        Drawable d = r == 0 ? null : c.getDrawable(r);
        if (d != null) {
            int s = dp(c, 28);
            int pad = (dp(c, TOUCH) - s) / 2;
            b.setCompoundDrawablesRelative(null, null, null, null);
            b.setForeground(new InsetDrawable(d, pad));
        }
        return b;
    }

    /**
     * Segmented control item: the selected one is a raised pill, the others are plain labels on the shared track (segments()).
     */
    static void segment(Button b, boolean selected) {
        Context c = b.getContext();
        b.setSelected(selected);
        b.setTextColor(selected ? INK : MUTED);
        b.setBackgroundTintList(null);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x1affffff),
                shape(c, selected ? CARD_HI : 0x00000000, R_CONTROL - 4, 0), shape(c, 0xffffffff, R_CONTROL - 4, 0)));
    }

    /**
     * The track that holds segment() buttons.
     */
    static LinearLayout segments(Context c) {
        LinearLayout track = new LinearLayout(c);
        track.setGravity(Gravity.CENTER_VERTICAL);
        int p = dp(c, S1);
        track.setPadding(p, p, p, p);
        track.setBackground(shape(c, 0x26767680, R_CONTROL, 0));
        return track;
    }

    /**
     * Adds one segment to a track; height is the touch minimum minus the track padding, so the whole control stays 76dp + 8.
     */
    static Button addSegment(LinearLayout track, String name, Runnable action) {
        Button b = button(track.getContext(), name, false, action);
        b.setTextSize(T_CALLOUT);
        b.setPadding(dp(track.getContext(), 8), 0, dp(track.getContext(), 8), 0);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(track.getContext(), TOUCH), 1);
        track.addView(b, p);
        segment(b, false);
        return b;
    }

    /**
     * Sheet header used by every sheet and dialog: title (and an optional one-line subtitle) on the left, extra actions, then ✕ at the top right.
     */
    static LinearLayout header(Context c, String title, String subtitle, Runnable close, View... trailing) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(title(c, title));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = text(c, subtitle, T_CAPTION, MUTED);
            s.setSingleLine(true);
            s.setEllipsize(android.text.TextUtils.TruncateAt.END);
            words.addView(s);
        }
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        for (View v : trailing) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    v instanceof Button && ((Button) v).getText().length() == 0 ? dp(c, TOUCH) : -2, dp(c, TOUCH));
            p.leftMargin = dp(c, S3);
            row.addView(v, p);
        }
        if (close != null) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(c, TOUCH), dp(c, TOUCH));
            p.leftMargin = dp(c, S3);
            row.addView(iconButton(c, "ic_close", "닫기", close), p);
        }
        return row;
    }

    /**
     * Card surface (lists, tiles, previews).
     */
    static GradientDrawable card(Context c, boolean selected) {
        return shape(c, CARD, R_CARD, selected ? ACCENT : EDGE);
    }

    /**
     * Small status pill, e.g. 배치됨 / 사용 중.
     */
    static TextView badge(Context c, String value, int fill) {
        TextView t = text(c, value, T_BADGE, INK);
        t.setTypeface(SEMIBOLD);
        t.setPadding(dp(c, 10), dp(c, 5), dp(c, 10), dp(c, 5));
        t.setBackground(shape(c, fill, R_CHIP, 0));
        return t;
    }

    /**
     * Empty or error state: a centred headline and one short line.
     */
    static LinearLayout empty(Context c, String title, String message) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(c, S5), dp(c, S6), dp(c, S5), dp(c, S6));
        TextView h = headline(c, title);
        h.setGravity(Gravity.CENTER);
        box.addView(h);
        if (message != null && !message.isEmpty()) {
            TextView m = text(c, message, T_CALLOUT, MUTED);
            m.setGravity(Gravity.CENTER);
            m.setPadding(0, dp(c, S2), 0, 0);
            box.addView(m);
        }
        return box;
    }

    /**
     * Grouped list (iOS inset style): rows on one card, thin separators between them.
     */
    static LinearLayout group(Context c) {
        LinearLayout g = new LinearLayout(c);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setBackground(shape(c, CARD, R_CARD, 0));
        g.setClipToOutline(true);
        return g;
    }

    static void addRow(LinearLayout group, View row) {
        Context c = group.getContext();
        if (group.getChildCount() > 0) {
            View line = new View(c);
            line.setBackgroundColor(SEPARATOR);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, dp(c, 1)));
            lp.leftMargin = dp(c, S4);
            group.addView(line, lp);
        }
        group.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    /**
     * Locked while driving: dimmed, a lock glyph, and the shared notice when touched.
     */
    static void lockForDriving(TextView v, boolean locked) {
        v.setAlpha(locked ? .42f : 1f);
        if (locked) {
            int r = res(v.getContext(), "ic_lock");
            Drawable d = r == 0 ? null : v.getContext().getDrawable(r);
            if (d != null) {
                int s = dp(v.getContext(), 22);
                d.setBounds(0, 0, s, s);
                Drawable[] cur = v.getCompoundDrawablesRelative();
                v.setCompoundDrawablesRelative(cur[0], null, d, null);
                v.setCompoundDrawablePadding(dp(v.getContext(), 10));
            }
        }
    }

    /**
     * Action menu sheet (더보기, 저장·공유, …): ✕ header and one grouped list; a tap runs the item and closes. The checked index, if any, shows a check mark.
     */
    static AlertDialog menu(Activity a, String title, String[] items, int checked,
                            java.util.function.IntConsumer pick) {
        LinearLayout shell = new LinearLayout(a);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(a, S5), dp(a, S5), dp(a, S5), dp(a, S5));
        AlertDialog[] self = {null};
        shell.addView(header(a, title, "", () -> {
            if (self[0] != null)
                self[0].dismiss();
        }));
        LinearLayout list = group(a);
        for (int i = 0; i < items.length; i++) {
            final int n = i;
            TextView row = text(a, items[i], T_BODY, INK);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinHeight(dp(a, TOUCH));
            row.setPadding(dp(a, S5), 0, dp(a, S5), 0);
            row.setBackground(
                    new RippleDrawable(ColorStateList.valueOf(0x1affffff), null, new ColorDrawable(0xffffffff)));
            row.setClickable(true);
            row.setFocusable(true);
            if (i == checked) {
                int r = res(a, "ic_check");
                Drawable d = r == 0 ? null : a.getDrawable(r).mutate();
                if (d != null) {
                    d.setTint(ACCENT);
                    int s = dp(a, 24);
                    d.setBounds(0, 0, s, s);
                    row.setCompoundDrawablesRelative(null, null, d, null);
                }
            }
            row.setOnClickListener(v -> {
                if (self[0] != null)
                    self[0].dismiss();
                pick.accept(n);
            });
            addRow(list, row);
        }
        ScrollView scroll = new ScrollView(a);
        scroll.addView(list);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(a, S4);
        shell.addView(scroll, sp);
        AlertDialog dialog = dialog(a).setView(shell).create();
        self[0] = dialog;
        dialog.setOnShowListener(v -> {
            prepareDialog(a, dialog, shell, 720, .9f);
            Window w = dialog.getWindow();
            if (w != null)
                w.setLayout(w.getAttributes().width, WindowManager.LayoutParams.WRAP_CONTENT);
        });
        dialog.show();
        return dialog;
    }

    /**
     * The one switch style: a 76dp row, label at body size, green when on.
     */
    static Switch toggle(Context c, String text, boolean on) {
        Switch t = new Switch(c);
        t.setText(text);
        t.setTextSize(T_CALLOUT + 1);
        t.setTextColor(INK);
        t.setPadding(dp(c, S4), dp(c, S2), dp(c, S4), dp(c, S2));
        t.setMinHeight(dp(c, TOUCH));
        t.setChecked(on);
        t.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{0xffffffff, 0xffd1d1d6}));
        t.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{SUCCESS, 0x66787880}));
        return t;
    }

    /**
     * Control-centre tile: icon above the label on a card.
     */
    static void tile(Button b, String iconName) {
        Context c = b.getContext();
        b.setTextSize(T_CALLOUT + 1);
        b.setGravity(Gravity.CENTER);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22ffffff), shape(c, CARD, R_CARD, EDGE), null));
        int r = res(c, iconName);
        Drawable d = r == 0 ? null : c.getDrawable(r);
        if (d != null) {
            int s = dp(c, 30);
            d.setBounds(0, 0, s, s);
            b.setCompoundDrawablesRelative(null, d, null, null);
            b.setCompoundDrawablePadding(dp(c, S2));
        }
        b.setPadding(dp(c, S3), dp(c, S4), dp(c, S3), dp(c, S3));
    }

    /**
     * A monoline icon (res/drawable ic_*) before a button's label.
     */
    static void icon(TextView v, int res) {
        Drawable d = v.getContext().getDrawable(res);
        if (d == null)
            return;
        int s = dp(v.getContext(), 26);
        d.setBounds(0, 0, s, s);
        v.setCompoundDrawablesRelative(d, null, null, null);
        v.setCompoundDrawablePadding(dp(v.getContext(), 10));
    }

    static void section(LinearLayout parent, String title, String subtitle) {
        Context c = parent.getContext();
        TextView h = headline(c, title);
        h.setPadding(dp(c, S1), dp(c, S2), 0, dp(c, S2));
        parent.addView(h);
        if (!subtitle.isEmpty()) {
            TextView s = text(c, subtitle, T_CAPTION, MUTED);
            s.setPadding(dp(c, S1), 0, 0, dp(c, S4));
            parent.addView(s);
        }
    }

    static EditText input(Context c, String value, String hint, int lines, int limit) {
        EditText field = new EditText(c);
        field.setText(value);
        field.setHint(hint);
        field.setTextSize(T_BODY - 1);
        field.setTextColor(INK);
        field.setHintTextColor(MUTED);
        field.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        field.setBackground(shape(c, FILL, R_CONTROL - 4, 0));
        field.setMinLines(lines);
        field.setMaxLines(Math.max(lines, 4));
        field.setSingleLine(lines == 1);
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                (lines == 1 ? 0 : android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE));
        field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(limit)});
        return inline(field);
    }

    /**
     * Car landscape screens otherwise open a full-screen extract editor that hides the form and driving panel.
     */
    static EditText inline(EditText field) {
        field.setImeOptions(field.getImeOptions() | android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI |
                android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN);
        return field;
    }

    static AlertDialog form(Activity a, String title, String subtitle, View content, String action, Runnable save) {
        LinearLayout shell = new LinearLayout(a);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(a, S5), dp(a, S5), dp(a, S5), dp(a, S5));
        AlertDialog[] self = {null};
        shell.addView(header(a, title, subtitle, () -> {
            if (self[0] != null)
                self[0].dismiss();
        }));
        ScrollView scroll = new ScrollView(a);
        scroll.setFillViewport(false);
        scroll.addView(content);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, 0, 1);
        sp.topMargin = dp(a, S4);
        shell.addView(scroll, sp);
        AlertDialog dialog = AppUi.dialog(a).setView(shell).create();
        self[0] = dialog;
        LinearLayout footer = new LinearLayout(a);
        footer.addView(button(a, action, true, () -> {
            save.run();
            dialog.dismiss();
        }), new LinearLayout.LayoutParams(-1, dp(a, TOUCH)));
        LinearLayout.LayoutParams fb = new LinearLayout.LayoutParams(-1, -2);
        fb.topMargin = dp(a, 16);
        shell.addView(footer, fb);
        dialog.setOnShowListener(v -> {
            prepareDialog(a, dialog, shell, 840, .84f);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        });
        dialog.show();
        return dialog;
    }

    static void prepareDialog(Activity a, Dialog dialog, View panel, int maxWidth, float height) {
        if (refuse(dialog))
            return;
        Window w = dialog.getWindow();
        if (w == null)
            return;
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        w.setDimAmount(.46f);
        View owner = a.getWindow().getDecorView();
        Runnable size = () -> {
            int width = Math.max(1, owner.getWidth() - dp(a, 32)), available = Math.max(1,
                    owner.getHeight() - dp(a, 32));
            w.setLayout(Math.min(width, dp(a, maxWidth)), Math.min(available, Math.round(available * height)));
        };
        size.run();
        View.OnLayoutChangeListener resize = (v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot)
                size.run();
        };
        owner.addOnLayoutChangeListener(resize);
        panel.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) {
            }

            public void onViewDetachedFromWindow(View v) {
                owner.removeOnLayoutChangeListener(resize);
            }
        });
        panel.setBackground(shape(a, blurBehind(w) ? GLASS : GLASS_SOLID, R_SHEET, EDGE));
        panel.setClipToOutline(true);
        UiMotion.enter(panel, dp(a, 28), .985f);
    }

    /**
     * iOS-style sheet backdrop: blur and lightly dim what is behind the window (Android 12+ cross-window blur). False when the device has blur off; the caller then uses a solid panel.
     */
    static boolean blurBehind(Window w) {
        if (w == null)
            return false;
        WindowManager wm = w.getContext().getSystemService(WindowManager.class);
        if (wm == null || !wm.isCrossWindowBlurEnabled())
            return false;
        w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        WindowManager.LayoutParams p = w.getAttributes();
        p.setBlurBehindRadius(dp(w.getContext(), 28));
        w.setAttributes(p);
        w.setDimAmount(.28f);
        return true;
    }

    /**
     * A small press-down: 96% while held, back on release. Off while driving (UiMotion.reduced).
     */
    static void pressable(View v) {
        v.setOnTouchListener((view, e) -> {
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN && !UiMotion.reduced)
                view.animate().scaleX(.96f).scaleY(.96f).setDuration(90).start();
            else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                view.animate().scaleX(1).scaleY(1).setDuration(UiMotion.reduced ? 0 : 180).start();
            return false;
        });
    }
    /** Builder for every plain dialog: the car framework's alert layout uses phone-sized text and full-width windows, so each one is resized and raised to car sizes when it shows. */
    /**
     * Driving lock: every app dialog (and every keyboard input, which only lives in dialogs) is refused while driving and closed when driving starts.
     */
    static boolean driving;
    static final String DRIVE_NOTICE = "정차 후 이용할 수 있어요";
    private static final java.util.Set<Dialog> openDialogs = java.util.Collections.newSetFromMap(
            new java.util.WeakHashMap<>());

    /**
     * True (and the dialog is closed with a notice) when it tried to open while driving.
     */
    static boolean refuse(Dialog d) {
        openDialogs.add(d);
        if (!driving)
            return false;
        d.dismiss();
        notice(d.getContext() instanceof Activity ? d.getContext() : d.getOwnerActivity(), DRIVE_NOTICE);
        return true;
    }

    /**
     * Closes every open app dialog; returns how many were open.
     */
    static int closeAll() {
        int closed = 0;
        for (Dialog d : new java.util.ArrayList<>(openDialogs))
            if (d.isShowing()) {
                d.dismiss();
                closed++;
            }
        openDialogs.clear();
        return closed;
    }

    static AlertDialog.Builder dialog(Context c) {
        return new AlertDialog.Builder(c) {
            @Override
            public AlertDialog create() {
                AlertDialog d = super.create();
                d.setOnShowListener(v -> carSize(d));
                return d;
            }
        };
    }

    static void carSize(AlertDialog d) {
        if (refuse(d))
            return;
        Context c = d.getContext();
        Window w = d.getWindow();
        int screen = dp(c, c.getResources().getConfiguration().screenWidthDp);
        if (w != null) {
            w.setLayout(Math.min(screen - dp(c, 32), dp(c, 760)), WindowManager.LayoutParams.WRAP_CONTENT);
            if (!blurBehind(w)) {
                int solid = res(c, "dialog_surface_solid");
                if (solid != 0)
                    w.setBackgroundDrawableResource(solid);
            }
        }
        int title = c.getResources().getIdentifier("alertTitle", "id", "android");
        TextView heading = title == 0 ? null : d.findViewById(title);
        if (heading != null) {
            heading.setTextSize(T_HEADLINE + 2);
            heading.setTextColor(INK);
            heading.setTypeface(SEMIBOLD);
        }
        TextView message = d.findViewById(android.R.id.message);
        if (message != null) {
            message.setTextSize(T_BODY);
            message.setTextColor(0xffc7c7cc);
            message.setLineSpacing(0, 1.15f);
        }
        for (int which : new int[]{DialogInterface.BUTTON_POSITIVE, DialogInterface.BUTTON_NEGATIVE,
                DialogInterface.BUTTON_NEUTRAL}) {
            Button b = d.getButton(which);
            if (b != null) {
                b.setTextSize(T_BODY);
                b.setAllCaps(false);
                b.setMinHeight(dp(c, TOUCH));
                b.setMinimumHeight(dp(c, TOUCH));
                b.setMinWidth(dp(c, 120));
                b.setTypeface(SEMIBOLD);
                b.setTextColor(which == DialogInterface.BUTTON_POSITIVE ? ACCENT : 0xffd1d1d6);
                pressable(b);
            }
        }
        ListView list = d.getListView();
        if (list != null) {
            list.setDivider(new ColorDrawable(SEPARATOR));
            list.setDividerHeight(1);
            for (int i = 0; i < list.getChildCount(); i++)
                row(list.getChildAt(i));
            list.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
                public void onChildViewAdded(View parent, View child) {
                    row(child);
                }

                public void onChildViewRemoved(View parent, View child) {
                }
            });
        }
    }

    private static void row(View v) {
        if (!(v instanceof TextView))
            return;
        TextView t = (TextView) v;
        t.setTextSize(T_BODY);
        t.setTextColor(INK);
        t.setMinHeight(dp(t.getContext(), TOUCH));
        t.setMinimumHeight(dp(t.getContext(), TOUCH));
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(t.getContext(), S5), 0, dp(t.getContext(), S5), 0);
    }

    private static View notice;

    /**
     * Pleos never shows Toasts. This banner is its own window of the activity, so it also appears above open dialogs and never takes touches.
     */
    static void notice(Context c, CharSequence message) {
        if (!(c instanceof Activity))
            return;
        Activity a = (Activity) c;
        a.runOnUiThread(() -> {
            if (a.isFinishing() || a.isDestroyed())
                return;
            WindowManager wm = a.getWindowManager();
            if (notice != null) {
                try {
                    wm.removeViewImmediate(notice);
                } catch (RuntimeException ignored) {
                }
                notice = null;
            }
            // iOS notification look: glass capsule, a tinted info glyph, one semibold line.
            boolean locked = DRIVE_NOTICE.contentEquals(message);
            TextView t = text(a, message.toString(), T_BODY, INK);
            t.setTypeface(SEMIBOLD);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setMaxWidth(dp(a, 760));
            t.setPadding(dp(a, 16), dp(a, 14), dp(a, 28), dp(a, 14));
            t.setBackground(shape(a, 0xeb2c2c2e, R_SHEET, EDGE));
            t.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            int glyph = res(a, locked ? "ic_notice_lock" : "ic_notice");
            Drawable icon = glyph == 0 ? null : a.getDrawable(glyph);
            if (icon != null) {
                icon.setBounds(0, 0, dp(a, 40), dp(a, 40));
                t.setCompoundDrawablesRelative(icon, null, null, null);
                t.setCompoundDrawablePadding(dp(a, 16));
            }
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(-2, -2,
                    WindowManager.LayoutParams.TYPE_APPLICATION,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT);
            p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            p.y = dp(a, 28);
            p.setTitle("LiveThemeNotice");
            try {
                wm.addView(t, p);
            } catch (RuntimeException e) {
                return;
            }
            notice = t;
            t.setAlpha(0);
            t.setTranslationY(UiMotion.reduced ? 0 : -dp(a, 24));
            t.animate().alpha(1).translationY(0).setDuration(UiMotion.reduced ? 0 : 240)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(2f));
            t.postDelayed(() -> {
                if (notice == t)
                    notice = null;
                try {
                    wm.removeViewImmediate(t);
                } catch (RuntimeException ignored) {
                }
            }, 4000);
        });
    }
}
