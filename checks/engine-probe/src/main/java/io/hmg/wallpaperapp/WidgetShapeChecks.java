package io.hmg.wallpaperapp;

import android.content.*;
import android.view.*;
import android.widget.*;
import android.net.Uri;

import org.json.*;

import java.io.*;
import java.util.*;

public final class WidgetShapeChecks {
    private static int passed;

    private static void check(boolean b, String label) {
        if (!b)
            throw new AssertionError(label);
        passed++;
    }

    private static void layout(WidgetBoard b, int w, int h) {
        b.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        b.layout(0, 0, w, h);
    }

    public static void run(Context c) {
        try {
            passed = 0;
            android.content.SharedPreferences p = c.getSharedPreferences("widget_layouts", 0);
            p.edit().clear().commit();
            String[] keys = WidgetShelf.KEYS;
            WidgetBoard b = new WidgetBoard(c, () -> {
            });
            for (String k : keys) {
                View v;
                if (k.equals("calendar"))
                    v = new CalendarWidget(c, () -> {
                    });
                else {
                    LinearLayout body = new LinearLayout(c);
                    body.setOrientation(LinearLayout.VERTICAL);
                    TextView text = new TextView(c);
                    text.setText(k);
                    text.setMinHeight(k.equals("weather") ? 134 : 110);
                    body.addView(text);
                    v = body;
                }
                b.addWidget(k, k, v, k.equals("clock"));
            }
            layout(b, 1682, 1210);
            Map<String, ?> other = new HashMap<>(p.getAll());
            layout(b, 2560, 1439);
            b.setEditing(true);
            // Squaring never covers a neighbour: it is applied, or refused with the old size kept when no free spot is left.
            boolean[] squared = new boolean[keys.length];
            int squares = 0;
            for (int i = 0; i < keys.length; i++) {
                View v = b.getChildAt(i);
                int bw = v.getWidth(), bh = v.getHeight();
                check(v.performAccessibilityAction(0x0100000b, null), keys[i] + " square action");
                layout(b, 2560, 1439);
                squared[i] = v.getWidth() == v.getHeight();
                check(squared[i] || v.getWidth() == bw && v.getHeight() == bh,
                        keys[i] + " square applied or refused unchanged");
                if (!squared[i])
                    continue;
                squares++;
                check(p.getString("full/" + keys[i], "").split(",").length == 4, keys[i] + " independent size stored");
                View content = ((android.view.ViewGroup) v).getChildAt(0);
                check(content.getScaleX() == content.getScaleY(), keys[i] + " uniform content scale");
            }
            check(squares >= keys.length - 3, "most widgets square while room remains");
            for (int i = 0; i < keys.length; i++)
                for (int j = 0; j < i; j++) {
                    View v = b.getChildAt(i), o = b.getChildAt(j);
                    check(!WidgetPlacement.overlaps(v.getLeft(), v.getTop(), v.getWidth(), v.getHeight(),
                                    new float[]{o.getLeft(), o.getTop(), o.getWidth(), o.getHeight()}, 0),
                            keys[i] + "/" + keys[j] + " square keeps widgets apart");
                }
            int[][] sizes = new int[keys.length][2];
            for (int i = 0; i < keys.length; i++)
                sizes[i] = new int[]{b.getChildAt(i).getWidth(), b.getChildAt(i).getHeight()};
            b.autoArrange();
            layout(b, 2560, 1439);
            for (int i = 0; i < keys.length; i++) {
                View v = b.getChildAt(i);
                check(v.getWidth() == sizes[i][0] && v.getHeight() == sizes[i][1], keys[i] + " auto preserves size");
                for (int j = 0; j < i; j++) {
                    View o = b.getChildAt(j);
                    check(!WidgetPlacement.overlaps(v.getLeft(), v.getTop(), v.getWidth(), v.getHeight(),
                            new float[]{o.getLeft(), o.getTop(), o.getWidth(), o.getHeight()}, 15), "auto no overlap");
                }
            }
            Map<String, ?> saved = new HashMap<>(p.getAll());
            b.reloadPlacement();
            layout(b, 2560, 1439);
            check(saved.equals(p.getAll()), "reload preserves four value layouts");
            for (int i = 0; i < keys.length; i++)
                if (squared[i])
                    check(b.getChildAt(i).getWidth() == b.getChildAt(i).getHeight(), "reload square");
            for (String k : other.keySet())
                check(other.get(k).equals(p.getAll().get(k)), "other screen layout untouched");
            View memo = b.getChildAt(3);
            int oldHeight = memo.getHeight();
            ((TextView) ((ViewGroup) ((ViewGroup) memo).getChildAt(0)).getChildAt(0)).setMinHeight(190);
            layout(b, 2560, 1439);
            check(memo.getHeight() == oldHeight, "content changes do not change saved height");
            int width = memo.getWidth();
            memo.performAccessibilityAction(0x0100000a, null);
            layout(b, 2560, 1439);
            check(memo.getHeight() > oldHeight && memo.getWidth() == width, "height resizes independently");
            JSONObject config = CreatorTheme.defaults(), layouts = config.getJSONObject("layouts");
            for (String k : keys)
                layouts.put("full/" + k, p.getString("full/" + k, ""));
            layouts.put("split2/clock", "0.2,0.3,1");
            JSONObject valid = CreatorTheme.validate(config);
            check(valid.getJSONObject("layouts").length() == keys.length + 1, "new and legacy theme layouts validated");
            File zip = new File(c.getCacheDir(), "theme.zip");
            CreatorTheme.export(zip, valid, null);
            File imported = CreatorTheme.importUri(c, Uri.parse("content://io.hmg.engineprobe.theme/theme.zip"));
            JSONObject restored = new JSONObject(
                    new String(java.nio.file.Files.readAllBytes(new File(imported, "live-theme.json").toPath()),
                            java.nio.charset.StandardCharsets.UTF_8));
            check(restored.getJSONObject("layouts").toString().equals(valid.getJSONObject("layouts").toString()),
                    "square theme ZIP roundtrip");
            JSONObject bad = new JSONObject(config.toString());
            bad.getJSONObject("layouts").put("full/memo", "0,0,1,NaN");
            try {
                CreatorTheme.validate(bad);
                throw new AssertionError("invalid height accepted");
            } catch (Exception expected) {
                passed++;
            }
            b.setEditing(false);
            layout(b, 500, 600);
            Map<String, ?> compactBefore = new HashMap<>(p.getAll());
            b.autoArrange();
            layout(b, 500, 600);
            check(!b.isEditing() && b.compactReading(), "compact auto stays fixed clock reading mode");
            check(compactBefore.equals(p.getAll()), "compact auto does not reset saved shapes");
            for (String k : keys)
                p.edit().putString("split2/" + k, "0.5,0.5,1,2.2").commit();
            b.reloadPlacement();
            layout(b, 1682, 600);
            Map<String, ?> noRoom = new HashMap<>(p.getAll());
            b.autoArrange();
            layout(b, 1682, 600);
            check(noRoom.equals(p.getAll()), "failed auto preserves previous layout");
            for (String design : new String[]{"digital", "stacked", "analog"})
                for (int[] size : new int[][]{{360, 360}, {240, 600}}) {
                    JSONObject theme = CreatorTheme.defaults();
                    theme.getJSONObject("clock").put("design", design);
                    android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(size[0], size[1],
                            android.graphics.Bitmap.Config.ARGB_8888);
                    new ThemeClock().draw(new android.graphics.Canvas(bitmap), size[0], size[1], theme,
                            android.graphics.Typeface.MONOSPACE, true, true, System.currentTimeMillis());
                    int[] pixels = new int[size[0] * size[1]];
                    bitmap.getPixels(pixels, 0, size[0], 0, 0, size[0], size[1]);
                    int painted = 0;
                    for (int color : pixels)
                        if ((color >>> 24) > 0)
                            painted++;
                    check(painted > 100, "clock renders " + design + " " + size[0] + "x" + size[1]);
                    bitmap.recycle();
                }
            android.util.Log.i("WidgetShapeChecks", "PASS " + passed);
        } catch (Throwable e) {
            android.util.Log.e("WidgetShapeChecks", "FAIL", e);
            throw new IllegalStateException(e);
        }
    }
}
