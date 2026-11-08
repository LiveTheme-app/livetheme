package io.hmg.wallpaperapp;

import android.app.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import android.graphics.*;
import android.content.*;

import org.json.*;

import java.io.*;
import java.util.*;
import java.lang.reflect.*;

/**
 * Production classes, fixture-only storage. A failed assertion leaves its name in logcat.
 */
public final class FixChecksActivity extends Activity {
    int checks;

    void check(boolean yes, String name) {
        if (!yes)
            throw new AssertionError(name);
        checks++;
        android.util.Log.i("FixChecks", "PASS " + name);
    }

    void layout(View v, int w, int h) {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, 1073741824), View.MeasureSpec.makeMeasureSpec(h, 1073741824));
        v.layout(0, 0, w, h);
    }

    View find(View root, String desc, String text) {
        if (desc != null && root.getContentDescription() != null && desc.contentEquals(root.getContentDescription()))
            return root;
        if (text != null && root instanceof TextView && text.contentEquals(((TextView) root).getText()))
            return root;
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                View v = find(g.getChildAt(i), desc, text);
                if (v != null)
                    return v;
            }
        }
        return null;
    }

    WidgetBoard board() {
        WidgetBoard b = new WidgetBoard(this, () -> {
        });
        for (String key : WidgetShelf.KEYS) {
            TextView content = new TextView(this);
            content.setText(key);
            content.setMinHeight(key.equals("clock") ? 260 : 180);
            b.addWidget(key, key, content, key.equals("clock"));
            b.showWidget(key, key.equals("clock") || key.equals("memo"));
        }
        return b;
    }

    boolean noOverlap(float[][] boxes, float w, float h, float gap) {
        for (int i = 0; i < boxes.length; i++) {
            float[] r = boxes[i];
            if (r[0] < 0 || r[1] < 0 || r[0] + r[2] > w + .1f || r[1] + r[3] > h + .1f)
                return false;
            for (int j = 0; j < i; j++)
                if (WidgetPlacement.overlaps(r[0], r[1], r[2], r[3], boxes[j], gap - .1f))
                    return false;
        }
        return true;
    }

    AlertDialog studioDialog(ThemeStudio s) throws Exception {
        Field f = ThemeStudio.class.getDeclaredField("studioDialog");
        f.setAccessible(true);
        return (AlertDialog) f.get(s);
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(new TextView(this));
        try {
            VehicleChecks.run(this);
            UxWorkflowChecks.run(this);
            WidgetRemovalChecks.run(this);
            android.content.SharedPreferences placements = getSharedPreferences("widget_layouts", 0);
            ThemeStudio studio = new ThemeStudio(this, () -> {
            }, () -> {
            });
            for (int[] size : new int[][]{{841, 1210}, {1682, 1210}, {2560, 1439}}) {
                placements.edit().clear().commit();
                WidgetBoard b = board();
                layout(b, size[0], size[1]);
                b.showWidget("memo", false);
                layout(b, size[0], size[1]);
                check(b.dropWidget("memo", size[0] * .5f, size[1] * .6f), "add succeeds " + size[0]);
                check("memo".equals(b.selectedKey()), "new widget selected before enable " + size[0]);
                b.showWidget("memo", true);
                layout(b, size[0], size[1]);
                check("memo".equals(b.selectedKey()), "new widget selected after layout " + size[0]);
                studio.config = CreatorTheme.defaults();
                studio.setWidgetStyle(b.selectedKey(), "focus");
                check(studio.config.getJSONObject("widgetStyles").getString("memo").equals("focus") &&
                                studio.config.getJSONObject("widgetStyles").getString("clock").equals("theme"),
                        "only memo style saved " + size[0]);
                check(new ThemeStudio(this, () -> {
                        }, () -> {
                        }).config.getJSONObject("widgetStyles").getString("memo").equals("focus"),
                        "style survives recreation " + size[0]);
                Map<String, ?> unchangedLayout = new HashMap<>(placements.getAll());
                b.selectWidget("memo");
                layout(b, size[0], size[1]);
                check("memo".equals(b.selectedKey()) && unchangedLayout.equals(placements.getAll()),
                        "editing active widget preserves placement " + size[0]);
            }
            placements.edit().clear().putString("full/clock", "0,0,4.213333,5.940367").commit();
            WidgetBoard full = board();
            full.showWidget("memo", false);
            layout(full, 2560, 1439);
            full.setEditing(true);
            Map<String, ?> before = new HashMap<>(placements.getAll());
            String selected = full.selectedKey();
            check(!full.dropWidget("memo", 1000, 800), "no-room add rejected");
            check(Objects.equals(selected, full.selectedKey()) && before.equals(placements.getAll()),
                    "failed add preserves selection and placement");
            check(!full.arrangeForWidget("memo") && before.equals(placements.getAll()),
                    "failed recovery preserves placement");
            placements.edit().clear().putString("split3/clock", "0,0,1,4.8").putString("split3/memo", "0,0,.75,1")
                    .putString("split3/music", "0,0,.75,1").commit();
            WidgetBoard compact = board();
            compact.showWidget("music", true);
            before = new HashMap<>(placements.getAll());
            layout(compact, 841, 1210);
            int height = compact.getChildAt(0).getHeight(), clockTop = compact.getChildAt(0).getTop();
            check(1210 - 144 - height - 24 >= 240, "compact clock reserves visible widget area");
            check(before.equals(placements.getAll()), "compact size cap never overwrites saved sizes");
            check(compact.canScrollVertically(1), "other widgets still scroll");
            compact.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,
                    null);
            layout(compact, 841, 1210);
            check(compact.getChildAt(0).getTop() == clockTop && before.equals(placements.getAll()),
                    "scroll keeps clock and saved positions fixed");
            float[][] sizes = {{800, 800}, {480, 480}, {480, 400}}, packed = WidgetPlacement.pack(1650, 1066, 16,
                    sizes);
            check(packed != null && noOverlap(packed, 1650, 1066, 16), "L shaped arrangement fits");
            for (int i = 0; i < sizes.length; i++)
                check(packed[i][2] == sizes[i][0] && packed[i][3] == sizes[i][1], "packing preserves size " + i);
            placements.edit().clear().commit();
            WidgetBoard recover = board();
            recover.showWidget("memo", false);
            layout(recover, 1682, 1210);
            check(recover.arrangeForWidget("memo"), "recovery includes incoming widget");
            recover.showWidget("memo", true);
            layout(recover, 1682, 1210);
            check("memo".equals(recover.selectedKey()), "recovery selects incoming widget");
            JSONObject theme = CreatorTheme.defaults();
            for (String key : WidgetShelf.KEYS)
                theme.getJSONObject("panel").put(key, true);
            String original = theme.toString();
            JSONObject preview = ThemePreview.previewTheme(theme);
            float[][] boxes = new float[7][4];
            for (int i = 0; i < 7; i++) {
                String key = WidgetShelf.KEYS[i];
                String[] raw = preview.getJSONObject("layouts").getString("full/" + key).split(",");
                float w = (key.equals("clock") ? 600 : 480) * Float.parseFloat(raw[2]), h =
                        218 * Float.parseFloat(raw[3]);
                boxes[i] = new float[]{Float.parseFloat(raw[0]) * (2528 - w), Float.parseFloat(raw[1]) * (1295 - h), w,
                        h};
            }
            check(noOverlap(boxes, 2528, 1295, 15.5f), "layout-free preview has seven distinct boxes");
            check(original.equals(theme.toString()), "preview never changes shared theme or user layouts");
            File font = new File("/system/fonts/Roboto-Regular.ttf");
            Typeface face = font.isFile() ? Typeface.createFromFile(font) : Typeface.SERIF;
            theme.getJSONObject("clock").put("font", "custom");
            WidgetDesign.Preview sample = new WidgetDesign.Preview(this, "clock", "theme", theme, face);
            ThemePreview gallery = new ThemePreview(this, theme, face);
            check(sample.customFont == face && gallery.customFont == face, "both previews receive custom typeface");
            check(WidgetDesign.clockFont(WidgetDesign.clockTheme(theme), sample.customFont) == face,
                    "preview renderer uses custom typeface");
            File library = new File(getFilesDir(), "library-" + UUID.randomUUID());
            library.mkdir();
            Context isolated = new ContextWrapper(this) {
                public File getFilesDir() {
                    return library;
                }
            };
            File assets = WorkshopLibrary.folder(isolated, WorkshopLibrary.IDS[0]);
            assets.mkdirs();
            new File(assets, "scene.json").createNewFile();
            new File(assets, "scene.pkg").createNewFile();
            JSONObject custom = ThemePresets.create(0);
            custom.put("name", "older");
            File old = CreatorTheme.save(isolated, custom, null);
            old.setLastModified(10000);
            custom.put("name", "newest");
            custom.getJSONObject("widgetSkins").put("music", new JSONObject().put("textColor", "#123456"));
            File newest = CreatorTheme.save(isolated, custom, null);
            newest.setLastModified(20000);
            java.util.List<InstalledThemes.Entry> list = InstalledThemes.list(isolated);
            check(list.size() == 1 && list.get(0).folder.equals(newest),
                    "newest user skin replaces preset without duplicates");
            check(new File(old, "live-theme.json").isFile(), "older packages retained on disk");
            studio.config = CreatorTheme.defaults();
            studio.config.getJSONObject("widgetSkins")
                    .put("clock", new JSONObject().put("textColor", "#ABCDEF").put("font", "mono"));
            studio.show();
            AlertDialog d = studioDialog(studio);
            View root = d.getWindow().getDecorView();
            check(((EditText) find(root, "시계 색상 #RRGGBB", null)).getText().toString().equals("#ABCDEF"),
                    "creator form reflects rendered skin");
            String unchanged = studio.config.toString();
            find(root, null, "미리보기 적용").performClick();
            check(unchanged.equals(studio.config.toString()), "opening and applying unedited creator preserves design");
            ((EditText) find(root, "시계 색상 #RRGGBB", null)).setText("#123456");
            ((Spinner) find(root, "시계 글꼴", null)).setSelection(3);
            find(root, null, "미리보기 적용").performClick();
            JSONObject drawing = WidgetDesign.clockTheme(studio.config).getJSONObject("clock");
            check(drawing.getString("color").equals("#123456") && drawing.getString("font").equals("bold"),
                    "creator color and font match actual rendering");
            d.dismiss();
            studio.config = CreatorTheme.defaults();
            studio.config.getJSONObject("widgetStyles").put("clock", "classic");
            studio.show();
            d = studioDialog(studio);
            root = d.getWindow().getDecorView();
            ((Spinner) find(root, "시계 디자인", null)).setSelection(1);
            find(root, null, "미리보기 적용").performClick();
            check(WidgetDesign.clockTheme(studio.config).getJSONObject("clock").getString("design").equals("stacked"),
                    "creator clock shape overrides basic design intentionally");
            d.dismiss();
            WeatherWidget weather = new WeatherWidget(this, () -> {
            });
            weather.start();
            Method vehicleLocation = WeatherWidget.class.getDeclaredMethod("requestVehicleLocation");
            vehicleLocation.setAccessible(true);
            vehicleLocation.invoke(weather);
            check(find(weather, null, "차량 위치 권한 필요 · 눌러서 다시 선택") != null, "vehicle location denied before SDK access");
            getSharedPreferences("weather", 0).edit().putLong("at", System.currentTimeMillis()).commit();
            weather.permissionResult();
            check(!getSharedPreferences("weather", 0).contains("at"),
                    "permission callback invalidates previous-region cache age");
            Method pause = WeatherWidget.class.getDeclaredMethod(
                    "pauseConnection"), reconnect = WeatherWidget.class.getDeclaredMethod("reconnect");
            pause.setAccessible(true);
            reconnect.setAccessible(true);
            Field active = WeatherWidget.class.getDeclaredField("active");
            active.setAccessible(true);
            pause.invoke(weather);
            reconnect.invoke(weather);
            check(active.getBoolean(weather), "weather reconnect restarts visible widget");
            weather.stop();
            reconnect.invoke(weather);
            check(!active.getBoolean(weather), "weather reconnect cannot start background widget");
            android.util.Log.i("FixChecks", "DONE " + checks);
            finish();
        } catch (Throwable e) {
            android.util.Log.e("FixChecks", "FAILED", e);
            finish();
        }
    }
}
