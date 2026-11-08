package io.hmg.wallpaperapp;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;

import org.json.*;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

/**
 * A no-code authoring surface and a local, portable theme library.
 */
final class ThemeStudio {
    final Activity activity;
    final Runnable changed, editPlacement;
    JSONObject config;
    Typeface typeface;
    File fontFile;
    File backgroundFile;
    private AlertDialog studioDialog;

    ThemeStudio(Activity a, Runnable changed, Runnable editPlacement) {
        activity = a;
        this.changed = changed;
        this.editPlacement = editPlacement;
        try {
            config = CreatorTheme.validate(
                    new JSONObject(store().getString("draft", CreatorTheme.defaults().toString())));
        } catch (Exception e) {
            config = CreatorTheme.defaults();
        }
        String saved = store().getString("font", "");
        try {
            File f = new File(saved);
            if (!saved.isEmpty() && f.getCanonicalPath()
                    .startsWith(new File(a.getFilesDir(), "creator-themes").getCanonicalPath() + File.separator) &&
                    f.isFile()) {
                CreatorTheme.checkFont(f);
                fontFile = f;
            }
        } catch (Exception ignored) {
        }
        String video = store().getString("video", "");
        try {
            File f = new File(video);
            if (!video.isEmpty() && f.getCanonicalPath()
                    .startsWith(new File(a.getFilesDir(), "creator-themes").getCanonicalPath() + File.separator) &&
                    f.isFile())
                backgroundFile = f;
        } catch (IOException ignored) {
        }
        updateFont();
    }

    private SharedPreferences store() {
        return activity.getSharedPreferences("theme_creator", Context.MODE_PRIVATE);
    }

    private void updateFont() {
        String font = config.optJSONObject("clock").optString("font", "light");
        typeface = "custom".equals(font) && fontFile != null ? Typeface.createFromFile(fontFile) : Typeface.create(
                "serif".equals(font) ? "serif" :
                        "mono".equals(font) ? "monospace" : "light".equals(font) ? "sans-serif-light" : "sans-serif",
                "bold".equals(font) ? Typeface.BOLD : Typeface.NORMAL);
    }

    void persist() {
        store().edit().putString("draft", config.toString())
                .putString("font", fontFile == null ? "" : fontFile.getAbsolutePath())
                .putString("video", backgroundFile == null ? "" : backgroundFile.getAbsolutePath()).apply();
        updateFont();
        changed.run();
    }

    void setWidgetStyle(String key, String style) {
        try {
            JSONObject next = new JSONObject(config.toString());
            next.getJSONObject("widgetStyles").put(key, style);
            config = CreatorTheme.validate(next);
            persist();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    void setColor(int color) {
        try {
            config.getJSONObject("clock").put("color", String.format(Locale.ROOT, "#%06X", color & 0xffffff));
            persist();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    void use(File folder) throws Exception {
        JSONObject next = CreatorTheme.forApplication(new JSONObject(
                new String(Files.readAllBytes(new File(folder, "live-theme.json").toPath()),
                        java.nio.charset.StandardCharsets.UTF_8)), config);
        File f = CreatorTheme.font(folder);
        if ("custom".equals(next.getJSONObject("clock").getString("font")))
            CreatorTheme.checkFont(f);
        File video = new File(folder, "background.mp4");
        if ("video".equals(next.getJSONObject("background").getString("type")))
            CreatorTheme.checkVideo(video);
        if ("workshop".equals(next.getJSONObject("background").optString("type")))
            WorkshopLibrary.require(activity, next.getJSONObject("background").getString("id"));
        File webIndex = new File(folder, "web/index.html");
        if ("web".equals(next.getJSONObject("background").optString("type")) && !webIndex.isFile())
            throw new IOException("웹 배경 파일이 없습니다");
        config = next;
        fontFile = f.isFile() ? f : null;
        backgroundFile = "web".equals(next.getJSONObject("background").optString("type")) ? webIndex :
                "workshop".equals(next.getJSONObject("background").optString("type")) ?
                        WorkshopLibrary.video(activity, next.getJSONObject("background").getString("id")) :
                        video.isFile() ? video : null;
        persist();
        if (studioDialog != null)
            studioDialog.dismiss();
    }

    JSONObject portable() throws Exception {
        return CreatorTheme.withoutLayouts(config);
    }

    private int dp(int n) {
        return Math.round(n * activity.getResources().getDisplayMetrics().density);
    }

    private TextView label(String text) {
        TextView v = new TextView(activity);
        v.setText(text);
        v.setTextColor(0xffdeeee8);
        v.setTextSize(15);
        v.setPadding(0, dp(12), 0, dp(5));
        return v;
    }

    private Button button(String text, LinearLayout parent, Runnable action) {
        Button b = new Button(activity);
        b.setText(text);
        b.setOnClickListener(v -> action.run());
        parent.addView(b, new LinearLayout.LayoutParams(-1, dp(AppUi.TOUCH)));
        return b;
    }

    private EditText field(String title, String value, int length, LinearLayout parent) {
        parent.addView(label(title));
        EditText f = AppUi.inline(new EditText(activity));
        f.setText(value);
        f.setSingleLine(true);
        f.setTextColor(Color.WHITE);
        f.setContentDescription(title);
        f.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(length)});
        parent.addView(f, new LinearLayout.LayoutParams(-1, dp(AppUi.TOUCH)));
        return f;
    }

    private Spinner select(String title, String[] labels, String[] values, String current, LinearLayout parent) {
        parent.addView(label(title));
        Spinner s = new Spinner(activity);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(adapter);
        s.setContentDescription(title);
        s.setSelection(Math.max(0, Arrays.asList(values).indexOf(current)));
        parent.addView(s, new LinearLayout.LayoutParams(-1, dp(AppUi.TOUCH)));
        return s;
    }

    private SeekBar slider(String title, int progress, int max, LinearLayout parent) {
        TextView label = label(title + " · " + progress);
        parent.addView(label);
        SeekBar s = new SeekBar(activity);
        s.setContentDescription(title);
        s.setMax(max);
        s.setProgress(progress);
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int p, boolean user) {
                label.setText(title + " · " + p);
            }

            public void onStartTrackingTouch(SeekBar b) {
            }

            public void onStopTrackingTouch(SeekBar b) {
            }
        });
        parent.addView(s, new LinearLayout.LayoutParams(-1, dp(AppUi.TOUCH)));
        return s;
    }

    private Switch toggle(String title, boolean checked, LinearLayout parent) {
        Switch s = new Switch(activity);
        s.setText(title);
        s.setChecked(checked);
        s.setPadding(0, dp(10), 0, dp(10));
        parent.addView(s, new LinearLayout.LayoutParams(-1, dp(AppUi.TOUCH)));
        return s;
    }

    void show() {
        ScrollView scroll = new ScrollView(activity);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(24), dp(8), dp(24), dp(20));
        scroll.addView(body);
        body.addView(label("배경 영상과 위젯·글꼴·효과를 하나의 테마로 만드세요."));
        boolean contest = BundledThemes.present(
                activity);// contest build: no Workshop option and no built-in videos (left out of that APK)
        String[] backgrounds = contest ? new String[]{"keep", "video"} :
                new String[]{"keep", "builtin0", "builtin1", "builtin2", "video", "workshop"}, backgroundNames =
                contest ? new String[]{"현재 배경 유지 (공유 파일에 미포함)", "내 배경 영상"} :
                        new String[]{"현재 배경 유지 (공유 파일에 미포함)", "달빛 연못", "수채화 빗속 창가", "수채화 노을 바다", "내 배경 영상",
                                "연결한 창작마당 원본"};
        JSONObject bg = config.optJSONObject("background");
        String selected =
                "builtin".equals(bg.optString("type")) ? "builtin" + bg.optInt("index") : bg.optString("type");
        Spinner background = select("테마 배경", backgroundNames, backgrounds, selected, body);
        button("배경 영상 가져오기", body, this::pickVideo);
        ThemeClock renderer = new ThemeClock();
        View preview = new View(activity) {
            protected void onDraw(Canvas canvas) {
                canvas.drawColor(0xff17343c);
                JSONObject drawing = WidgetDesign.clockTheme(config);
                renderer.draw(canvas, getWidth(), getHeight(), drawing, WidgetDesign.clockFont(drawing, typeface), true,
                        true, System.currentTimeMillis());
            }
        };
        preview.setContentDescription("시계 디자인 미리보기");
        body.addView(preview, new LinearLayout.LayoutParams(-1, dp(200)));
        JSONObject c = WidgetDesign.clockTheme(config).optJSONObject("clock"), e = config.optJSONObject("effect");
        EditText name = field("테마 이름", config.optString("name"), 60, body), author = field("제작자 이름 (선택)",
                config.optString("author"), 40, body);
        String[] designs = {"digital", "stacked", "analog"}, fonts = {"light", "serif", "mono", "bold",
                "custom"}, effects = {"none", "fireflies", "snow"};
        Spinner design = select("시계 디자인", new String[]{"가로 디지털", "세로 쌓기", "아날로그"}, designs, c.optString("design"),
                body);
        Spinner font = select("시계 글꼴", new String[]{"얇은 산세리프", "세리프", "고정폭", "굵은 글꼴", "내 TTF / OTF"}, fonts,
                c.optString("font"), body);
        Button importFont = button("글꼴 파일 가져오기", body, this::pickFont);
        body.addView(label(fontFile == null ? "사용자 글꼴은 파일 선택 또는 테마 ZIP에 font.ttf / font.otf를 넣어 가져올 수 있어요." :
                "사용자 글꼴이 연결돼 있어요."));
        EditText color = field("시계 색상 #RRGGBB", c.optString("color"), 7, body), caption = field("시계에 넣을 한 줄 문구",
                c.optString("caption"), 60, body);
        SeekBar size = slider("시계 글자 크기 (%)", (int) Math.round(c.optDouble("size", 1) * 100), 130,
                body), shadow = slider("시계 그림자", c.optInt("shadow", 12), 24, body);
        size.setMin(60);
        Switch seconds = toggle("초 표시", c.optBoolean("seconds"), body), hour24 = toggle("24시간 표시",
                c.optBoolean("hour24", true), body);
        Spinner effect = select("배경 위 효과", new String[]{"없음", "반딧불이", "눈 내리기"}, effects, e.optString("type"), body);
        EditText effectColor = field("효과 색상 #RRGGBB", e.optString("color"), 7, body);
        SeekBar amount = slider("입자 개수", e.optInt("amount", 24), 60, body), speed = slider("효과 속도 (%)",
                (int) Math.round(e.optDouble("speed", 1) * 100), 200, body);
        speed.setMin(10);
        JSONObject panelSettings = config.optJSONObject("panel");
        String[] widgetKeys = CreatorTheme.panelKeys();
        String[] widgetLabels = new String[widgetKeys.length];
        for (int i = 0; i < widgetKeys.length; i++)
            widgetLabels[i] = (widgetKeys[i].equals("date") ? "날짜" : WidgetShelf.nameOf(widgetKeys[i])) + " 표시";
        Switch[] widgets = new Switch[widgetKeys.length];
        for (int i = 0; i < widgets.length; i++)
            widgets[i] = toggle(widgetLabels[i], panelSettings.optBoolean(widgetKeys[i],
                            java.util.Arrays.asList("clock", "date", "calendar", "music", "memo").contains(widgetKeys[i])),
                    body);
        EditText panelColor = field("테마 기준색 #RRGGBB", config.optJSONObject("panel").optString("color"), 7, body);
        Runnable apply = () -> {
            try {
                JSONObject next = new JSONObject(config.toString());
                next.put("name", name.getText().toString().trim()).put("author", author.getText().toString().trim());
                String chosen = backgrounds[background.getSelectedItemPosition()];
                next.getJSONObject("background").put("type", chosen.startsWith("builtin") ? "builtin" : chosen)
                        .put("index", chosen.startsWith("builtin") ? Integer.parseInt(chosen.substring(7)) : 0);
                if ("video".equals(chosen) && (backgroundFile == null || !backgroundFile.isFile()))
                    throw new IOException("배경 영상 파일을 먼저 가져와주세요");
                JSONObject clock = next.getJSONObject("clock");
                clock.put("design", designs[design.getSelectedItemPosition()])
                        .put("font", fonts[font.getSelectedItemPosition()])
                        .put("color", color.getText().toString().trim()).put("caption", caption.getText().toString())
                        .put("size", Math.max(60, size.getProgress()) / 100.0).put("shadow", shadow.getProgress())
                        .put("plate", 0).put("seconds", seconds.isChecked()).put("hour24", hour24.isChecked());
                boolean clockEdited = false;
                java.util.Iterator<String> fields = c.keys();
                while (fields.hasNext()) {
                    String key = fields.next();
                    Object before = c.opt(key), after = clock.opt(key);
                    clockEdited |= before instanceof Number && after instanceof Number ?
                            Double.compare(((Number) before).doubleValue(), ((Number) after).doubleValue()) != 0 :
                            !java.util.Objects.equals(before, after);
                }
                if (clockEdited) {
                    next.getJSONObject("widgetStyles").put("clock", "theme");
                    JSONObject skin = next.getJSONObject("widgetSkins").optJSONObject("clock");
                    if (skin != null) {
                        skin.remove("textColor");
                        skin.remove("font");
                    }
                } else
                    next.put("clock", new JSONObject(config.getJSONObject("clock").toString()));
                next.getJSONObject("effect").put("type", effects[effect.getSelectedItemPosition()])
                        .put("color", effectColor.getText().toString().trim()).put("amount", amount.getProgress())
                        .put("speed", Math.max(10, speed.getProgress()) / 100.0);
                if ("custom".equals(clock.getString("font")) && (fontFile == null || !fontFile.isFile()))
                    throw new IOException("사용할 글꼴 파일을 먼저 가져와주세요");
                for (int i = 0; i < widgets.length; i++)
                    next.getJSONObject("panel").put(widgetKeys[i], widgets[i].isChecked());
                next.getJSONObject("panel").put("color", panelColor.getText().toString().trim());
                config = CreatorTheme.validate(next);
                persist();
                preview.invalidate();
            } catch (Exception error) {
                throw new IllegalArgumentException(error.getMessage(), error);
            }
        };
        button("미리보기 적용", body, () -> run(() -> {
            apply.run();
            AppUi.notice(activity, "디자인을 적용했어요");
        }));
        button("화면에서 위젯 위치·크기 편집", body, () -> run(() -> {
            apply.run();
            if (studioDialog != null)
                studioDialog.dismiss();
            editPlacement.run();
        }));
        button("내 테마로 저장", body, () -> run(() -> {
            apply.run();
            CreatorTheme.save(activity, portable(), fontFile, backgroundFile);
            AppUi.notice(activity, "내 테마 라이브러리에 저장했어요");
        }));
        button("테마 ZIP 만들기·공유", body, () -> run(() -> {
            apply.run();
            File file = new File(activity.getCacheDir(), "live-theme-" + UUID.randomUUID() + ".zip");
            CreatorTheme.export(file, portable(), fontFile, backgroundFile);
            Uri uri = Uri.parse("content://io.hmg.wallpaperapp.themes/" + file.getName());
            Intent send = new Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            send.setClipData(ClipData.newRawUri("라이브테마", uri));
            activity.startActivity(Intent.createChooser(send, "테마 ZIP 공유"));
        }));
        body.addView(
                label("배경 영상·시계 디자인·효과·글꼴을 한 ZIP에 담아요. 위젯 배치는 포함하지 않아요. ‘현재 배경 유지’는 배경을 포함하지 않아요. 메모·일정·곡 정보는 보내지 않아요. 공유받은 ZIP을 라이브테마로 열면 적용할 수 있어요."));
        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(AppUi.S5), dp(AppUi.S5), dp(AppUi.S5), dp(AppUi.S4));
        AlertDialog[] self = {null};
        LinearLayout head = AppUi.header(activity, "테마 제작실", "", () -> {
            if (self[0] != null)
                self[0].dismiss();
        });
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.bottomMargin = dp(AppUi.S3);
        shell.addView(head, hp);
        if (scroll.getParent() != null)
            ((android.view.ViewGroup) scroll.getParent()).removeView(scroll);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        AlertDialog dialog = AppUi.dialog(activity).setView(shell).create();
        self[0] = dialog;
        dialog.setOnShowListener(v -> AppUi.prepareDialog(activity, dialog, shell, 760, .94f));
        studioDialog = dialog;
        dialog.show();
    }

    interface Work {
        void run() throws Exception;
    }

    private void run(Work work) {
        try {
            work.run();
        } catch (Exception e) {
            AppUi.dialog(activity).setTitle("설정을 확인해주세요").setMessage(e.getMessage()).setPositiveButton("확인", null)
                    .show();
        }
    }

    void library() {
        java.util.List<InstalledThemes.Entry> entries = InstalledThemes.list(activity);
        if (entries.isEmpty()) {
            AppUi.notice(activity, "설치된 테마가 없어요");
            return;
        }
        String[] names = new String[entries.size()];
        for (int i = 0; i < names.length; i++)
            names[i] = entries.get(i).theme.optString("name");
        AppUi.dialog(activity).setTitle("설치된 테마").setItems(names, (d, i) -> run(() -> {
            InstalledThemes.Entry entry = entries.get(i);
            if (entry.folder == null)
                applyPreset(entry.theme);
            else
                use(entry.folder);
            AppUi.notice(activity, "테마를 적용했어요");
        })).setNegativeButton("닫기", null).show();
    }

    private void pickFont() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        android.content.pm.ResolveInfo handler = activity.getPackageManager()
                .resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        if (handler == null || handler.activityInfo.packageName.equals("com.android.car.frameworkpackagestubs")) {
            AppUi.dialog(activity).setTitle("테마 ZIP으로 글꼴 가져오기").setMessage(
                            "이 에뮬레이터에는 파일 선택기가 없습니다. live-theme.json과 font.ttf 또는 font.otf를 ZIP으로 묶어 라이브테마로 공유해주세요. PC용 예제와 규격을 프로젝트의 creator-kit 폴더에 제공해요.")
                    .setPositiveButton("확인", null).show();
            return;
        }
        if (studioDialog != null)
            studioDialog.dismiss();
        activity.startActivityForResult(intent, 43);
    }

    File exportCurrent() throws Exception {
        File file = new File(activity.getCacheDir(), "live-theme-" + UUID.randomUUID() + ".zip");
        CreatorTheme.export(file, portable(), fontFile, backgroundFile);
        return file;
    }

    void saveCurrent(String name) throws Exception {
        JSONObject copy = portable();
        copy.put("name", name);
        CreatorTheme.save(activity, copy, fontFile, backgroundFile);
    }

    void applyPreset(JSONObject preset) throws Exception {
        JSONObject next = CreatorTheme.forApplication(preset, config);
        String workshop = next.getJSONObject("background").optString("id");
        if ("workshop".equals(next.getJSONObject("background").optString("type")))
            WorkshopLibrary.require(activity, workshop);
        config = next;
        fontFile = null;
        backgroundFile = "workshop".equals(next.getJSONObject("background").optString("type")) ?
                WorkshopLibrary.video(activity, workshop) : null;
        persist();
    }

    void chooseBuiltin(int index) {
        try {
            config.getJSONObject("background").put("type", "builtin").put("index", index);
            persist();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    void setPanels(boolean clock, boolean date, boolean calendar, boolean music, boolean memo, boolean dday,
                   boolean weather, boolean battery) {
        try {
            config.getJSONObject("panel").put("clock", clock).put("date", date).put("calendar", calendar)
                    .put("music", music).put("memo", memo).put("dday", dday).put("weather", weather)
                    .put("battery", battery);
            persist();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private void pickVideo() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("video/mp4")
                .addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        android.content.pm.ResolveInfo handler = activity.getPackageManager()
                .resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        if (handler == null || handler.activityInfo.packageName.equals("com.android.car.frameworkpackagestubs")) {
            AppUi.dialog(activity).setTitle("영상이 포함된 테마 ZIP 가져오기").setMessage(
                            "이 에뮬레이터에는 파일 선택기가 없습니다. live-theme.json과 background.mp4를 ZIP으로 묶어 라이브테마로 공유해주세요. PC용 예제는 creator-kit 폴더에서 확인할 수 있어요. 영상은 64 MiB 이하를 지원해요.")
                    .setPositiveButton("확인", null).show();
            return;
        }
        if (studioDialog != null)
            studioDialog.dismiss();
        activity.startActivityForResult(intent, 44);
    }

    void importVideo(Uri uri) {
        run(() -> {
            if (!"content".equals(uri.getScheme()))
                throw new IOException("파일 앱의 영상을 선택해주세요");
            File folder = new File(activity.getFilesDir(), "creator-themes/" + UUID.randomUUID());
            if (!folder.mkdirs())
                throw new IOException("영상 저장 실패");
            File file = new File(folder, "background.mp4");
            try {
                try (InputStream in = activity.getContentResolver()
                        .openInputStream(uri); OutputStream out = new FileOutputStream(file)) {
                    if (in == null)
                        throw new IOException("영상을 열 수 없습니다");
                    CreatorTheme.copy(in, out, 64L * 1024 * 1024);
                }
                CreatorTheme.checkVideo(file);
                backgroundFile = file;
                config.getJSONObject("background").put("type", "video");
                persist();
                show();
            } catch (Exception e) {
                file.delete();
                folder.delete();
                throw e;
            }
        });
    }

    void importFont(Uri uri) {
        run(() -> {
            if (!"content".equals(uri.getScheme()))
                throw new IOException("공유된 글꼴을 선택해주세요");
            File folder = new File(activity.getFilesDir(), "creator-themes/" + UUID.randomUUID());
            if (!folder.mkdirs())
                throw new IOException("글꼴 저장 실패");
            File file = new File(folder, "font.ttf");
            try {
                try (InputStream in = activity.getContentResolver()
                        .openInputStream(uri); OutputStream out = new FileOutputStream(file)) {
                    if (in == null)
                        throw new IOException("글꼴을 열 수 없습니다");
                    CreatorTheme.copy(in, out, 4L * 1024 * 1024);
                }
                CreatorTheme.checkFont(file);
                fontFile = file;
                config.getJSONObject("clock").put("font", "custom");
                config.getJSONObject("widgetStyles").put("clock", "theme");
                JSONObject skin = config.getJSONObject("widgetSkins").optJSONObject("clock");
                if (skin != null)
                    skin.remove("font");
                persist();
                AppUi.notice(activity, "글꼴 적용 완료 · 제작실을 다시 열면 선택에 반영돼요");
            } catch (Exception e) {
                file.delete();
                folder.delete();
                throw e;
            }
        });
    }
}
