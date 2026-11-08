package io.hmg.wallpaperapp;

import android.content.Context;
import android.graphics.*;
import android.view.View;

import org.json.JSONObject;

/**
 * Offline package preview. Example labels never include personal widget contents.
 */
final class ThemePreview extends View {
    private final JSONObject theme;
    private final Paint paint = new Paint(3);
    private final ThemeClock clock = new ThemeClock();
    private Bitmap background;
    final Typeface customFont;

    ThemePreview(Context c, JSONObject theme) {
        this(c, theme, null);
    }

    ThemePreview(Context c, JSONObject theme, Typeface customFont) {
        super(c);
        this.theme = previewTheme(theme);
        this.customFont = customFont == null ? Typeface.DEFAULT : customFont;
        int index = theme.optJSONObject("background").optInt("index", 0);
        if ("workshop".equals(theme.optJSONObject("background").optString("type")))
            try {
                background = BitmapFactory.decodeFile(
                        new java.io.File(WorkshopLibrary.folder(c, theme.optJSONObject("background").optString("id")),
                                "preview.jpg").getAbsolutePath());
            } catch (RuntimeException ignored) {
            }
        if ("builtin".equals(theme.optJSONObject("background").optString("type")))
            try (java.io.InputStream stream = c.getAssets().open("themes/preview-" + index + ".jpg")) {
                background = BitmapFactory.decodeStream(stream);
            } catch (java.io.IOException ignored) {
            }
        if (background == null)
            background = BundledThemes.preview(c, theme);
        setContentDescription(theme.optString("name") + " 배경과 위젯 미리보기");
    }

    static JSONObject previewTheme(JSONObject source) {
        try {
            JSONObject copy = new JSONObject(source.toString()), layouts = copy.getJSONObject(
                    "layouts"), panel = copy.getJSONObject("panel");
            java.util.ArrayList<String> keys = new java.util.ArrayList<>();
            boolean complete = true;
            for (String key : WidgetShelf.KEYS)
                if (panel.optBoolean(key)) {
                    keys.add(key);
                    complete &= layouts.has("full/" + key);
                }
            if (complete)
                return copy;
            float[][] sizes = new float[keys.size()][];
            for (int i = 0; i < keys.size(); i++) {
                String key = keys.get(i);
                sizes[i] = key.equals("clock") ? new float[]{600, 260, 360, 160} :
                        key.equals("calendar") ? new float[]{480, 480, 400, 218} : new float[]{480, 218, 288, 131};
            }
            float[][] boxes = WidgetPlacement.grid(2528, 1295, 16, sizes);
            if (boxes == null)
                throw new IllegalArgumentException("위젯 미리보기 배치를 만들 수 없습니다");
            for (int i = 0; i < keys.size(); i++) {
                float[] r = boxes[i];
                layouts.put("full/" + keys.get(i),
                        String.format(java.util.Locale.ROOT, "%.6f,%.6f,%.6f,%.6f", r[0] / Math.max(1, 2528 - r[2]),
                                r[1] / Math.max(1, 1295 - r[3]), r[2] / (keys.get(i).equals("clock") ? 600 : 480),
                                r[3] / 218));
            }
            return copy;
        } catch (org.json.JSONException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w), height =
                MeasureSpec.getMode(h) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(h) : Math.round(width * 9f / 16);
        if (MeasureSpec.getMode(h) == MeasureSpec.AT_MOST)
            height = Math.min(height, MeasureSpec.getSize(h));
        setMeasuredDimension(width, height);
    }

    private int color(JSONObject panel, String key, String fallback) {
        return Color.parseColor(panel.optString(key, fallback));
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (background != null)
            c.drawBitmap(background, null, new Rect(0, 0, getWidth(), getHeight()), paint);
        else
            c.drawColor(0xff233b49);
        paint.setColor(0x44071319);
        c.drawRect(0, 0, getWidth(), getHeight(), paint);
        c.save();
        c.scale(getWidth() / 2560f, getHeight() / 1439f);
        JSONObject globalPanel = theme.optJSONObject("panel"), layouts = theme.optJSONObject(
                "layouts"), styles = theme.optJSONObject("widgetStyles");
        for (String key : WidgetShelf.KEYS) {
            if (!globalPanel.optBoolean(key, false))
                continue;
            JSONObject panel = WidgetDesign.panelFor(theme, key);
            String[] raw = layouts.optString("full/" + key).split(",");
            if (raw.length < 3)
                continue;
            float width = (key.equals("clock") ? 600 : 480) * Float.parseFloat(raw[2]), height =
                    raw.length > 3 ? 218 * Float.parseFloat(raw[3]) :
                            (key.equals("clock") ? 260 : 218) * Float.parseFloat(raw[2]), x =
                    16 + Float.parseFloat(raw[0]) * (2560 - 32 - width), y =
                    96 + Float.parseFloat(raw[1]) * (1439 - 144 - height);
            c.save();
            c.translate(x, y);
            c.clipRect(0, 0, width, height);
            String font = panel.optString("font");
            Typeface face = Typeface.create(
                    "serif".equals(font) ? "serif" : "mono".equals(font) ? "monospace" : "sans-serif", Typeface.NORMAL);
            if (key.equals("clock")) {
                JSONObject drawing = WidgetDesign.clockTheme(theme);
                clock.draw(c, Math.round(width), Math.round(height), drawing,
                        WidgetDesign.clockFont(drawing, customFont), true, true, System.currentTimeMillis());
                c.restore();
                continue;
            }
            paint.setTypeface(face);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setColor(color(panel, "accentColor", "#A8DCD0"));
            paint.setTextSize(20);
            String heading = key.equals("calendar") ? "CALENDAR" : key.equals("music") ? "MUSIC" :
                    key.equals("memo") ? "MY NOTE" : key.equals("battery") ? "EV BATTERY" :
                            key.equals("weather") ? "WEATHER" :
                                    WidgetShelf.vehicleKey(key) ? WidgetShelf.nameOf(key) : "D-DAY";
            c.drawText(heading, 26, 42, paint);
            paint.setColor(color(panel, "textColor", "#E0EEE5"));
            paint.setTextSize(key.equals("battery") ? 64 : 30);
            String sample = key.equals("battery") ? "—%" : key.equals("music") ? "나의 플레이리스트" :
                    key.equals("memo") ? "기억하고 싶은 한 줄" : key.equals("weather") ? "날씨 · 강수" :
                            key.equals("calendar") ? java.time.LocalDate.now().getMonthValue() + "월" :
                                    WidgetShelf.vehicleKey(key) ? WidgetShelf.sample(key) : "기다리는 날";
            c.drawText(sample, 26, Math.min(height - 28, 104), paint);
            if (key.equals("calendar")) {
                paint.setTextSize(20);
                for (int i = 1; i <= 28; i++)
                    c.drawText("" + i, 30 + ((i - 1) % 7) * (width - 60) / 7, 160 + ((i - 1) / 7) * 54, paint);
            } else if (key.equals("battery")) {
                paint.setTextSize(22);
                c.drawText("배터리와 충전 상태", 26, Math.min(height - 25, 150), paint);
            }
            c.restore();
        }
        c.restore();
    }
}
