package io.hmg.wallpaperapp;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.view.*;
import android.widget.*;

import org.json.JSONObject;

/**
 * Parked-use theme app with local video/scene backgrounds and a configurable mini panel.
 */
public final class MainActivity extends Activity {
    private boolean playing = true;
    private int palette;
    private int themeIndex;
    private static final String[] THEME_NAMES = {"달빛 연못", "수채화 빗속 창가", "수채화 노을 바다"};
    private static final String[] THEME_FILES = {"background.mp4", "themes/cafe.mp4", "themes/coast.mp4"};
    private TextView themeLabel, memoText, brandLabel;
    private LinearLayout memoCard;
    private boolean showClock, showDate, showMusic, showMemo, showCalendar, showDDay, showWeather;
    private WidgetBoard widgetBoard;
    private LinearLayout placementTools;
    private TextView placementLabel;
    private View headingChrome, editChrome;
    private View backgroundShade;
    private WidgetTrash widgetTrash;
    private LinearLayout removalUndo;
    private String removedWidget;
    private boolean removedClock, removedDate;
    private final Runnable dismissRemovalUndo = this::hideRemovalUndo;
    private CalendarWidget calendarWidget;
    private DDayWidget ddayWidget;
    private WeatherWidget weatherWidget;
    private BatteryWidget batteryWidget;
    private boolean showBattery;
    private VehicleWidgets vehicleWidgets;
    private NavigationWidget navigationWidget;
    private int dim, clockPosition;
    /**
     * 분위기 (Mood) values; theme files are never changed by them. autoClockColor/autoEffect are sampled from the background when autoColor is on.
     */
    private String mood = Mood.DEFAULT;
    private boolean nightDim = true, autoColor, strongText;
    private float effectAmount = 1, effectSpeed = 1, glass = 1;
    private Integer autoClockColor;
    private final Runnable colorTask = this::matchColors;
    private String memo;
    private int accent = 0xfff3f7ef;
    private VideoBackground video;
    private java.io.File externalVideo;
    private SceneBackground sceneView;
    private boolean sceneMode;
    private String sceneFolder = "";
    private android.app.AlertDialog editorDialog;
    private boolean importing;
    private ClockFace clock;
    private ThemeStudio studio;
    private ThemeEffects themeEffects;
    private NowPlayingMonitor songs;
    private LinearLayout songCard;
    private TextView songTitle, songArtist, songState;
    private android.widget.ImageButton songPrevious, songPlay, songNext;
    private boolean songHasArt;
    private ImageView songArt;
    private LinearLayout songRow, songWords, songControls;
    private FrameLayout root;
    private View shelfScrim;
    private WidgetShelf widgetShelf;
    private WidgetShelf.DragItem widgetDrag;
    private boolean wasEditing;
    private String failedWidgetDrop;
    private boolean foreground;
    private DriveRestriction drive;
    private boolean driveRestricted;
    /**
     * Control-Center style quick settings pulled down from the top band (PullDownPanel).
     */
    private PullDownPanel pullDown;
    private boolean pullCancelled;
    /**
     * "무드등을 배경 색에 맞추기": off by default, asked once, then applied on theme/background changes while parked, at most every 15 s.
     */
    private final Runnable ambientTask = this::matchAmbient;
    private boolean ambientSync, ambientPending;
    private long lastAmbientSync;
    private int lastAmbientColor;
    /**
     * The scene view shows a creator web theme (background type "web") rather than a Workshop scene.
     */
    private boolean sceneWeb;
    private long lastThemeRequest;
    private android.app.AlertDialog themeRequestDialog;
    private long trackStarted;
    private float themeVolume = -1;
    private long lastMediaRequest;
    /**
     * STREAM_MUSIC volume 0..1 for web themes, read without any permission; polled only while a web theme is showing.
     */
    private final Runnable volumeTask = new Runnable() {
        public void run() {
            if (!foreground)
                return;
            android.media.AudioManager audio = getSystemService(android.media.AudioManager.class);
            if (sceneWeb && audio != null) {
                int max = Math.max(1, audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC));
                float v = audio.isVolumeFixed() ? -1 : audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) /
                        (float) max;// AAOS with car volume groups keeps the stream fixed at max and needs a system permission for the real level: report "unknown" (null) instead of a false 100%.
                if (Math.abs(v - themeVolume) > .001f) {
                    themeVolume = v;
                    Log.i("LiveThemeVolume", v < 0 ? "unknown (fixed car volume)" : String.valueOf(v));
                    pushThemeData();
                }
            }
            root.postDelayed(this, 500);
        }
    };
    private String songTitleText = "", songArtistText = "";
    private long lastTouchSent;
    private final android.content.BroadcastReceiver accessReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, android.content.Intent intent) {
            songs.stop();
            updateSongVisibility();
        }
    };
    private final String instance = String.valueOf(SystemClock.elapsedRealtimeNanos());

    @Override
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        playing = getPreferences(MODE_PRIVATE).getBoolean("playing", true);
        ambientSync = getPreferences(MODE_PRIVATE).getBoolean("ambientSync", false);
        palette = getPreferences(MODE_PRIVATE).getInt("palette", 0);
        themeIndex = getPreferences(MODE_PRIVATE).getInt("theme", 0);
        if (themeIndex < 0 || themeIndex >= THEME_FILES.length)
            themeIndex = 0;
        if (saved != null) {
            playing = saved.getBoolean("playing", playing);
            palette = saved.getInt("palette", palette);
        }
        showClock = getPreferences(MODE_PRIVATE).getBoolean("showClock", true);
        showDate = getPreferences(MODE_PRIVATE).getBoolean("showDate", true);
        showMusic = getPreferences(MODE_PRIVATE).getBoolean("showMusic", true);
        showMemo = getPreferences(MODE_PRIVATE).getBoolean("showMemo", true);
        showCalendar = getPreferences(MODE_PRIVATE).getBoolean("showCalendar", true);
        showDDay = getPreferences(MODE_PRIVATE).getBoolean("showDDay", false);
        showWeather = getPreferences(MODE_PRIVATE).getBoolean("showWeather", false);
        memo = getPreferences(MODE_PRIVATE).getString("memo", "");
        // 1.32 migration: an untouched 1.31 setup becomes "기본" (same values); a moved dim slider becomes "사용자 지정" and is kept.
        if (!getPreferences(MODE_PRIVATE).contains("mood")) {
            String migrated = Mood.migrate(getPreferences(MODE_PRIVATE));
            android.content.SharedPreferences.Editor e = getPreferences(MODE_PRIVATE).edit();
            Mood.apply(e, migrated);
            e.apply();
        }
        loadMood();
        clockPosition = getPreferences(MODE_PRIVATE).getInt("clockPosition", 0) == 1 ? 1 : 0;
        accent = getPreferences(MODE_PRIVATE).getInt("accent", 0xfff3f7ef);
        root = new FrameLayout(this);
        root.setBackgroundColor(0xff071b19);
        BundledThemes.firstRun(this);
        studio = new ThemeStudio(this, () -> {
            applyCreatorTheme();
            if (clock != null)
                clock.invalidate();
            if (themeEffects != null)
                themeEffects.invalidate();
        }, () -> widgetBoard.setEditing(true));
        if (!getSharedPreferences("theme_creator", MODE_PRIVATE).contains("draft"))
            studio.setPanels(showClock, showDate, showCalendar, showMusic, showMemo, showDDay, showWeather,
                    showBattery);
        if ("video".equals(studio.config.optJSONObject("background").optString("type")) ||
                "workshop".equals(studio.config.optJSONObject("background").optString("type")))
            externalVideo = studio.backgroundFile;
        video = new VideoBackground();
        if (saved != null)
            video.position = saved.getInt("videoPosition", 0);
        root.addView(video, new FrameLayout.LayoutParams(-1, -1));
        sceneView = new SceneBackground(this);
        root.addView(sceneView, new FrameLayout.LayoutParams(-1, -1));
        sceneView.setVisibility(View.GONE);
        sceneView.setOnReplaced(next -> sceneView = next);
        sceneView.setRequests(this::onThemeRequest);
        drive = new DriveRestriction(this, this::onDriveRestriction);
        sceneFolder = getPreferences(MODE_PRIVATE).getString("sceneFolder", "");
        sceneMode = getPreferences(MODE_PRIVATE).getBoolean("sceneMode", false) &&
                sceneFolder.matches("scene-[a-f0-9-]{36}") &&
                new java.io.File(getFilesDir(), sceneFolder + "/scene.pkg").isFile();
        if ("workshop".equals(studio.config.optJSONObject("background").optString("type"))) {
            String id = studio.config.optJSONObject("background").optString("id");
            java.io.File workshop = WorkshopLibrary.scene(this, id);
            sceneMode = workshop != null;
            if (sceneMode)
                sceneFolder = "creator-themes/workshop-" + id;
        }
        if ("web".equals(studio.config.optJSONObject("background").optString("type"))) {
            sceneWeb = studio.backgroundFile != null && studio.backgroundFile.isFile();
            sceneMode = sceneWeb;
        }
        if (externalVideo != null)
            sceneMode = false;
        if (sceneMode) {
            sceneView.setVisibility(View.VISIBLE);
            if (sceneWeb)
                sceneView.loadWeb(studio.backgroundFile.getParentFile());
            else
                sceneView.load(new java.io.File(getFilesDir(), sceneFolder));
        }
        backgroundShade = new View(this) {
            private final Paint paint = new Paint();

            @Override
            protected void onDraw(Canvas canvas) {
                int w = getWidth(), h = getHeight();
                paint.setShader(
                        new LinearGradient(0, 0, 0, h, new int[]{0x60051a18, 0x30051a18, 0x08051a18, 0x70051a18},
                                new float[]{0, .35f, .65f, 1}, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, paint);
                paint.setShader(null);
                paint.setColor(Color.argb(Math.round(dim * 2.55f), 5, 14, 22));
                canvas.drawRect(0, 0, w, h, paint);
            }
        };
        root.addView(backgroundShade, new FrameLayout.LayoutParams(-1, -1));
        themeEffects = new ThemeEffects(this, studio);
        root.addView(themeEffects, new FrameLayout.LayoutParams(-1, -1));
        widgetBoard = new WidgetBoard(this, this::updatePlacementTools);
        root.addView(widgetBoard, new FrameLayout.LayoutParams(-1, -1));
        widgetBoard.setDesignAction(this::showWidgetDesign);
        widgetBoard.setRemovalListener(new WidgetBoard.RemovalListener() {
            public void dragging(boolean active, boolean armed) {
                if (widgetTrash != null)
                    widgetTrash.show(active, armed);
            }

            public void remove(String key) {
                removePlacedWidget(key);
            }
        });

        widgetBoard.setOnLongClickListener(v -> {
            showWidgetShelf();
            return true;
        });
        widgetBoard.setContentDescription("배경을 길게 눌러 위젯 목록 열기");
        widgetBoard.setOnDragListener((v, event) -> {
            if (widgetDrag == null || event.getLocalState() != widgetDrag)
                return false;
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case DragEvent.ACTION_DRAG_LOCATION:
                    widgetBoard.previewDrop(widgetDrag.key, event.getX(), event.getY());
                    return true;
                case DragEvent.ACTION_DROP:
                    if (widgetBoard.dropWidget(widgetDrag.key, event.getX(), event.getY())) {
                        setWidgetVisible(widgetDrag.key, true);
                        return true;
                    }
                    failedWidgetDrop = widgetDrag.key;
                    return false;
                case DragEvent.ACTION_DRAG_EXITED:
                    widgetBoard.clearDrop();
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    widgetBoard.clearDrop();
                    widgetDrag = null;
                    String failed = failedWidgetDrop;
                    failedWidgetDrop = null;
                    if (event.getResult()) {
                        if (foreground)
                            root.post(this::showWidgetDesign);
                    } else {
                        widgetBoard.setEditing(wasEditing);
                        if (foreground)
                            root.post(() -> {
                                showWidgetShelf();
                                if (failed != null)
                                    showAddRecovery(failed);
                            });
                    }
                    return true;
                default:
                    return true;
            }
        });
        clock = new ClockFace();
        clock.setKeepScreenOn(true);
        widgetBoard.addWidget("clock", "시계", clock, true);
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        brandLabel = label("라이브테마", 17);
        heading.addView(brandLabel);
        themeLabel = label(sceneMode ? "가져온 원본 장면 · 실험" : THEME_NAMES[themeIndex], 12);
        heading.addView(themeLabel);
        FrameLayout.LayoutParams top = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        top.setMargins(dp(32), dp(28), dp(24), 0);
        root.addView(heading, top);
        headingChrome = heading;
        Button edit = button("꾸미기");
        edit.setContentDescription("테마·내 테마·커뮤니티 열기");
        edit.setOnClickListener(v -> showEditor());
        LinearLayout shortcuts = new LinearLayout(this);
        shortcuts.setGravity(Gravity.CENTER_VERTICAL);
        Button addWidget = button("위젯");
        AppUi.icon(addWidget, AppUi.res(this, "ic_plus"));
        AppUi.icon(edit, AppUi.res(this, "ic_sliders"));
        for (Button glass : new Button[]{addWidget, edit})
            glass.setBackground(AppUi.shape(this, 0x8c1c1c1e, 38, AppUi.EDGE));
        addWidget.setContentDescription("위젯 바로 추가");
        addWidget.setOnClickListener(v -> showWidgetShelf());
        shortcuts.addView(addWidget, new LinearLayout.LayoutParams(dp(136), dp(AppUi.TOUCH)));
        LinearLayout.LayoutParams editButton = new LinearLayout.LayoutParams(dp(152), dp(AppUi.TOUCH));
        editButton.leftMargin = dp(16);
        shortcuts.addView(edit, editButton);
        editChrome = shortcuts;
        FrameLayout.LayoutParams editBounds = new FrameLayout.LayoutParams(-2, dp(AppUi.TOUCH),
                Gravity.TOP | Gravity.END);
        editBounds.setMargins(0, dp(20), dp(24), 0);
        root.addView(shortcuts, editBounds);
        placementTools = new LinearLayout(this) {
            @Override
            protected void onMeasure(int ws, int hs) {
                super.onMeasure(
                        MeasureSpec.makeMeasureSpec(Math.min(MeasureSpec.getSize(ws), dp(960)), MeasureSpec.EXACTLY),
                        hs);
            }
        };
        placementTools.setGravity(Gravity.CENTER_VERTICAL);
        placementTools.setPadding(dp(12), 0, dp(12), 0);
        placementTools.setBackground(AppUi.shape(this, AppUi.GLASS_SOLID, 28, AppUi.EDGE));
        placementTools.setVisibility(View.GONE);
        placementLabel = AppUi.text(this, "", 15, AppUi.INK);
        placementLabel.setMaxLines(2);
        placementTools.addView(placementLabel, new LinearLayout.LayoutParams(0, -2, 1));
        Button design = AppUi.button(this, "디자인", true, this::showWidgetDesign);
        design.setContentDescription("선택한 위젯 디자인 변경");
        placementTools.addView(design, new LinearLayout.LayoutParams(dp(112), dp(AppUi.TOUCH)));
        Button size = AppUi.button(this, "크기", false, () -> showSizeOptions());
        size.setContentDescription("선택한 위젯 크기 변경");
        LinearLayout.LayoutParams sizeBounds = new LinearLayout.LayoutParams(dp(96), dp(AppUi.TOUCH));
        sizeBounds.leftMargin = dp(8);
        placementTools.addView(size, sizeBounds);
        Button more = AppUi.button(this, "더보기", false, null);
        more.setContentDescription("위젯 배치 도구 더보기");
        more.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(this, v);
            String[] names = {"위젯 추가", "선택 위젯 숨기기", "자동 정렬", "배치 초기화"};
            for (int i = 0; i < names.length; i++)
                menu.getMenu().add(0, i, i, names[i]);
            menu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 0)
                    showWidgetShelf();
                else if (item.getItemId() == 1) {
                    String key = widgetBoard.selectedKey();
                    if (key != null)
                        removePlacedWidget(key);
                } else
                    confirmArrange(item.getItemId() == 3);
                return true;
            });
            menu.show();
        });
        LinearLayout.LayoutParams moreBounds = new LinearLayout.LayoutParams(dp(112), dp(AppUi.TOUCH));
        moreBounds.leftMargin = dp(8);
        placementTools.addView(more, moreBounds);
        FrameLayout.LayoutParams toolsBounds = new FrameLayout.LayoutParams(-1, dp(AppUi.TOUCH + 16),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        toolsBounds.setMargins(dp(16), dp(12), dp(16), 0);
        root.addView(placementTools, toolsBounds);
        widgetTrash = new WidgetTrash(this);
        FrameLayout.LayoutParams trashBounds = new FrameLayout.LayoutParams(dp(328), dp(92),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        trashBounds.bottomMargin = dp(20);
        root.addView(widgetTrash, trashBounds);
        memoCard = new LinearLayout(this) {
            @Override
            protected void onMeasure(int w, int h) {
                boolean tall = View.MeasureSpec.getMode(h) != View.MeasureSpec.UNSPECIFIED &&
                        View.MeasureSpec.getSize(h) > View.MeasureSpec.getSize(w) * .8f;
                if (memoText != null) {
                    memoText.setTextSize(tall ? 26 : 22);
                    if (memoText.getMaxLines() != (tall ? 7 : 3))
                        memoText.setMaxLines(tall ? 7 : 3);
                    if (memoText.getLineSpacingExtra() != dp(tall ? 5 : 0))
                        memoText.setLineSpacing(dp(tall ? 5 : 0), 1);
                }
                super.onMeasure(w, h);
            }
        };
        memoCard.setOrientation(LinearLayout.VERTICAL);
        styleCard(memoCard);
        TextView memoHeading = label("MY NOTE", 12);
        memoHeading.setLetterSpacing(.18f);
        memoHeading.setTextColor(0xffa8dcd0);
        memoHeading.setPadding(0, 0, 0, dp(10));
        memoCard.addView(memoHeading);
        memoText = label("", 22);
        memoText.setMaxLines(4);
        memoText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        memoCard.addView(memoText);
        memoCard.setOnClickListener(v -> editMemo());
        memoCard.setContentDescription("메모 편집");
        addSongCard();
        calendarWidget = new CalendarWidget(this, () -> {
            layoutWidgets();
            report("calendar_changed");
        });
        widgetBoard.addWidget("calendar", "캘린더", calendarWidget, false);
        widgetBoard.addWidget("music", "음악", songCard, false);
        widgetBoard.addWidget("memo", "메모", memoCard, false);
        ddayWidget = new DDayWidget(this, this::layoutWidgets);
        weatherWidget = new WeatherWidget(this, this::layoutWidgets);
        styleCard(ddayWidget);
        styleCard(weatherWidget);
        widgetBoard.addWidget("dday", "D-day", ddayWidget, false);
        widgetBoard.addWidget("weather", "날씨·강수", weatherWidget, false);
        batteryWidget = new BatteryWidget(this, this::layoutWidgets);
        styleCard(batteryWidget);
        widgetBoard.addWidget("battery", "차량 배터리", batteryWidget, false);
        vehicleWidgets = new VehicleWidgets(this, () -> root.post(this::layoutWidgets));
        batteryWidget.onState = (level, charging) -> {
            themeEffects.links.level = level;
            themeEffects.links.charging = charging;
            themeEffects.invalidate();
            pushThemeData();
        };
        vehicleWidgets.ambientState = (color, brightness) -> {
            themeEffects.links.ambientColor = color;
            themeEffects.links.ambientBrightness = brightness;
            themeEffects.invalidate();
            pushThemeData();
        };
        for (VehicleWidgets.Card card : vehicleWidgets.cards.values())
            widgetBoard.addWidget(card.key, card.name, card, false);
        navigationWidget = new NavigationWidget(this, () -> root.post(this::layoutWidgets));
        widgetBoard.addWidget("navigation", "내비 연동", navigationWidget, false);
        songs = new NowPlayingMonitor(this, (allowed, title, artist, art, isPlaying, canPrevious, canNext) -> {
            songPrevious.setEnabled(canPrevious);
            songNext.setEnabled(canNext);
            songPrevious.setAlpha(canPrevious ? 1 : .35f);
            songNext.setAlpha(canNext ? 1 : .35f);
            boolean canPlay = songs != null && songs.canToggle();
            songPlay.setEnabled(canPlay);
            songPlay.setAlpha(canPlay ? 1 : .35f);
            songPlay.setImageResource(isPlaying ? AppUi.res(this, "ic_pause") : AppUi.res(this, "ic_play"));
            songPlay.setContentDescription(isPlaying ? "일시정지" : "재생");
            songState.setText(!allowed ? "곡 정보 연결" : title.isEmpty() ? "음악" : isPlaying ? "지금 재생 중" : "일시정지");
            songTitle.setText(!allowed ? "현재 곡을 연결해 주세요" : title.isEmpty() ? "재생 중인 음악이 없어요" : title);
            songArtist.setText(!allowed ? "알림 접근을 허용하면 곡 정보를 표시해요" : artist);
            themeEffects.links.playing = allowed ? isPlaying : null;
            themeEffects.links.albumColor = ThemeLinks.averageColor(art);
            themeEffects.invalidate();
            String nextTitle = allowed ? title : "";
            if (!nextTitle.equals(songTitleText))
                trackStarted = System.currentTimeMillis();
            songTitleText = nextTitle;
            songArtistText = allowed ? artist : "";
            pushThemeData();
            songHasArt = art != null;
            songArt.setImageBitmap(art);
            songArt.invalidate();
            songArt.setVisibility(View.VISIBLE);
            songArt.setContentDescription(art == null ? "음악 앨범 자리 표시" : "앨범 이미지");
            root.post(this::layoutWidgets);
        });
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l == or - ol && b - t == ob - ot)
                return;
            if (widgetShelf != null) {
                FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) widgetShelf.getLayoutParams();
                int h = Math.min(dp(760), Math.round(root.getHeight() * .86f));
                if (p.height != h) {
                    p.height = h;
                    widgetShelf.setLayoutParams(p);
                }
            }
            root.post(this::updateSongVisibility);
        });
        pullDown = new PullDownPanel(this, this::quickPanel);
        root.addView(pullDown, new FrameLayout.LayoutParams(-1, -1));
        View grab = PullDownPanel.grabber(this);
        FrameLayout.LayoutParams grabBounds = new FrameLayout.LayoutParams(dp(56), dp(5),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        grabBounds.topMargin = dp(8);
        root.addView(grab, grabBounds);
        setContentView(root);
        applyCreatorTheme();
        layoutWidgets();
        report("created");
        debugDrive(getIntent());
        handleSharedScene(getIntent());
    }

    private void addSongCard() {
        songCard = new LinearLayout(this) {
            @Override
            protected void onMeasure(int w, int h) {
                boolean tall = View.MeasureSpec.getMode(h) != View.MeasureSpec.UNSPECIFIED &&
                        View.MeasureSpec.getSize(h) > View.MeasureSpec.getSize(w) * .8f;
                // A short box (e.g. the split3 stack) cannot fit the controls as their own row: put them beside the title instead of letting the box clip the song.
                boolean shortBox = !tall && View.MeasureSpec.getMode(h) == View.MeasureSpec.EXACTLY &&
                        View.MeasureSpec.getSize(h) < dp(236);
                if (songControls != null) {
                    ViewGroup want = shortBox ? songRow : this;
                    if (songControls.getParent() != want) {
                        ((ViewGroup) songControls.getParent()).removeView(songControls);
                        want.addView(songControls, new LinearLayout.LayoutParams(-2, dp(AppUi.TOUCH)));
                    }
                    songArt.setVisibility(shortBox ? View.GONE : View.VISIBLE);
                    int skips = shortBox && View.MeasureSpec.getSize(w) < dp(560) ? View.GONE : View.VISIBLE;
                    songPrevious.setVisibility(skips);
                    songNext.setVisibility(skips);
                }
                if (songRow != null) {
                    songRow.setOrientation(tall ? VERTICAL : HORIZONTAL);
                    songRow.setGravity(tall ? Gravity.START : Gravity.CENTER_VERTICAL);
                    LinearLayout.LayoutParams art = (LinearLayout.LayoutParams) songArt.getLayoutParams();
                    int side =
                            tall ? Math.min(dp(96), Math.round(View.MeasureSpec.getSize(h) * .18f)) : dp(64), gap = dp(
                            tall ? 8 : 0), right = dp(tall ? 0 : 16);
                    if (art.width != side || art.topMargin != gap || art.rightMargin != right) {
                        art.width = art.height = side;
                        art.setMargins(0, gap, right, gap);
                        songArt.setLayoutParams(art);
                    }
                    LinearLayout.LayoutParams wordBounds = (LinearLayout.LayoutParams) songWords.getLayoutParams();
                    if (wordBounds.width != (tall ? -1 : 0) || wordBounds.weight != (tall ? 0 : 1))
                        songWords.setLayoutParams(new LinearLayout.LayoutParams(tall ? -1 : 0, -2, tall ? 0 : 1));
                    songTitle.setTextSize(tall ? 22 : 20);
                    if (songTitle.getMaxLines() != (tall ? 2 : 1))
                        songTitle.setMaxLines(tall ? 2 : 1);
                    songArtist.setTextSize(tall ? 13 : 14);
                    if (songArtist.getMaxLines() != 1)
                        songArtist.setMaxLines(1);
                }
                super.onMeasure(w, h);
            }
        };
        songCard.setOrientation(LinearLayout.VERTICAL);
        songCard.setPadding(dp(22), dp(16), dp(22), dp(14));
        styleCard(songCard);
        songState = label("지금 재생 중", 12);
        songState.setLetterSpacing(.12f);
        songState.setMaxLines(1);
        songState.setTextColor(0xffa8dcd0);
        songCard.addView(songState);
        LinearLayout row = new LinearLayout(this);
        songRow = row;
        row.setGravity(Gravity.CENTER_VERTICAL);
        songArt = new ImageView(this) {
            private final Paint paint = new Paint(3);

            @Override
            protected void onDraw(Canvas c) {
                if (songHasArt) {
                    super.onDraw(c);
                    return;
                }
                float x = getWidth() / 2f, y = getHeight() / 2f, r = Math.min(x, y);
                int musicAccent = Color.parseColor(
                        WidgetDesign.panelFor(studio.config, "music").optString("accentColor", "#A8DCD0"));
                paint.setColor(musicAccent);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                c.drawCircle(x, y, r * .58f, paint);
                c.drawCircle(x, y, r * .19f, paint);
                paint.setStyle(Paint.Style.FILL);
            }
        };
        songArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        songArt.setContentDescription("앨범 이미지");
        LinearLayout.LayoutParams artBounds = new LinearLayout.LayoutParams(dp(80), dp(80));
        artBounds.setMargins(0, 0, dp(16), 0);
        row.addView(songArt, artBounds);
        LinearLayout words = new LinearLayout(this);
        songWords = words;
        words.setOrientation(LinearLayout.VERTICAL);
        songTitle = label("현재 곡을 연결해 주세요", 23);
        songTitle.setMaxLines(2);
        songTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        songTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        songArtist = label("", 15);
        songArtist.setTextColor(0xffbdd0d2);
        songArtist.setPadding(0, dp(8), 0, 0);
        songArtist.setMaxLines(2);
        songArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        words.addView(songTitle);
        words.addView(songArtist);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout controls = new LinearLayout(this);
        songControls = controls;
        controls.setGravity(
                Gravity.CENTER_VERTICAL);// Round 76dp transport controls with monoline icons; play is the filled white one, as in iOS Now Playing.
        songPrevious = transport(AppUi.res(this, "ic_prev"), "이전 곡", false);
        songPlay = transport(AppUi.res(this, "ic_play"), "재생", true);
        songNext = transport(AppUi.res(this, "ic_next"), "다음 곡", false);
        for (android.widget.ImageButton control : new android.widget.ImageButton[]{songPrevious, songPlay, songNext}) {
            control.setEnabled(false);
            control.setAlpha(.35f);
            LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(dp(AppUi.TOUCH), dp(AppUi.TOUCH));
            at.rightMargin = dp(8);
            controls.addView(control, at);
        }
        songPrevious.setOnClickListener(v -> skipSong(false));
        songPlay.setOnClickListener(v -> {
            if (!songs.toggle())
                AppUi.notice(this, "음악 앱에서 재생·일시정지를 지원하지 않아요");
        });
        songNext.setOnClickListener(v -> skipSong(true));
        songCard.addView(row);
        songCard.addView(controls, new LinearLayout.LayoutParams(-2,
                dp(AppUi.TOUCH)));// Own row under the song: three 76dp controls beside the title would squeeze it to a few letters.
        songCard.setOnClickListener(v -> openSongSettings());
        songCard.setContentDescription("음악 정보 및 연결 설정");
    }

    private void updateSongVisibility() {
        if (songs == null || root.getWidth() == 0)
            return;
        songCard.setVisibility(showMusic ? View.VISIBLE : View.GONE);
        memoCard.setVisibility(showMemo ? View.VISIBLE : View.GONE);
        calendarWidget.setVisibility(showCalendar ? View.VISIBLE : View.GONE);
        ddayWidget.setVisibility(showDDay ? View.VISIBLE : View.GONE);
        weatherWidget.setVisibility(showWeather ? View.VISIBLE : View.GONE);
        if (showWeather && foreground)
            weatherWidget.start();
        else
            weatherWidget.stop();
        memoText.setText(memo.isEmpty() ? "메모를 남겨보세요" : memo);
        if ((showMusic || linked("music.")) && foreground)
            songs.start();
        else
            songs.stop();
        layoutWidgets();
        clock.invalidate();
    }

    /**
     * A theme data link, or a web theme (which may read every value), needs its source running even when the matching widget is hidden.
     */
    private boolean linked(String prefix) {
        return studio != null && (ThemeLinks.uses(studio.config.optJSONObject("links"), prefix) ||
                "web".equals(studio.config.optJSONObject("background").optString("type")));
    }

    private static Object orNull(Object value) {
        return value == null ? JSONObject.NULL : value;
    }

    private static Object hex(Integer color) {
        return color == null ? JSONObject.NULL : String.format(java.util.Locale.ROOT, "#%06X", color & 0xffffff);
    }

    /**
     * Read-only values for a web theme: same sources as the data links, plus the song text.
     */
    private void pushThemeData() {
        if (!sceneWeb || sceneView == null || !sceneView.isWeb())
            return;
        ThemeLinks l = themeEffects.links;
        try {
            JSONObject data = new JSONObject();
            data.put("battery", new JSONObject().put("level", orNull(l.level)).put("charging", orNull(l.charging)));
            data.put("ambient",
                    new JSONObject().put("color", hex(l.ambientColor)).put("brightness", orNull(l.ambientBrightness)));
            data.put("music", new JSONObject().put("title", songTitleText).put("artist", songArtistText)
                    .put("playing", orNull(l.playing)).put("color", hex(l.albumColor)));
            data.put("volume", themeVolume < 0 ? JSONObject.NULL : Math.round(themeVolume * 1000) / 1000.0);
            // Estimated rhythm: tempo derived from the track, not from the sound (no microphone or audio capture). Themes must treat it as decoration.
            data.put("rhythm", new JSONObject().put("estimated", true)
                    .put("bpm", CreatorTheme.estimatedBpm(songTitleText, songArtistText))
                    .put("playing", Boolean.TRUE.equals(l.playing)).put("since", trackStarted));
            data.put("night", l.night).put("paused", l.paused);
            sceneView.sendData(data);
        } catch (org.json.JSONException ignored) {
        }
    }

    /**
     * Web theme requests: declared in the theme, never while driving, one per 10 s, and only applied after the user confirms.
     */
    private void onThemeRequest(JSONObject message, java.util.function.Consumer<JSONObject> reply) {
        boolean[] answered = {false};
        java.util.function.BiConsumer<Boolean, String> answer = (ok, reason) -> {
            if (answered[0])
                return;
            answered[0] = true;
            Log.i("LiveThemeRequest", message.optString("action") + " ok=" + ok + " reason=" + reason);
            try {
                reply.accept(new JSONObject().put("ok", ok).put("reason", reason == null ? JSONObject.NULL : reason));
            } catch (org.json.JSONException ignored) {
            }
        };
        String action = message.optString("action");
        org.json.JSONArray declared = studio.config.optJSONObject("background").optJSONArray("requests");
        boolean listed = false;
        if (declared != null)
            for (int i = 0; i < declared.length(); i++)
                listed |= action.equals(declared.optString(i));
        if (!sceneWeb || !listed || !CreatorTheme.REQUESTS.contains(action)) {
            answer.accept(false, "not-declared");
            return;
        }
        // Music controls: no dialog, at most two per second; while driving only play/pause, the same single tap the music widget allows.
        if (CreatorTheme.mediaRequest(action)) {
            if (driveRestricted && !"media.playPause".equals(action)) {
                answer.accept(false, "driving");
                return;
            }
            long at = SystemClock.elapsedRealtime();
            if (at - lastMediaRequest < 500) {
                answer.accept(false, "rate-limited");
                return;
            }
            lastMediaRequest = at;
            boolean done = songs != null &&
                    ("media.playPause".equals(action) ? songs.toggle() : songs.skip("media.next".equals(action)));
            answer.accept(done, done ? null : "unsupported");
            return;
        }
        if (driveRestricted) {
            answer.accept(false, "driving");
            return;
        }
        if (themeRequestDialog != null && themeRequestDialog.isShowing()) {
            answer.accept(false, "busy");
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (lastThemeRequest != 0 && now - lastThemeRequest < 10_000) {
            answer.accept(false, "rate-limited");
            return;
        }
        lastThemeRequest = now;
        double value;
        String what;
        if ("ambient.color".equals(action)) {
            String color = message.optString("value");
            if (!color.matches("#[0-9a-fA-F]{6}")) {
                answer.accept(false, "invalid");
                return;
            }
            value = Color.parseColor(color) | 0xff000000;
            what = "실내 무드등 색을 " + color.toUpperCase(java.util.Locale.ROOT) + "(으)로";
        } else {
            value = message.optDouble("value", Double.NaN);
            if (!(value >= 0 && value <= 100)) {
                answer.accept(false, "invalid");
                return;
            }
            value = Math.round(value);
            what = "실내 무드등 밝기를 " + (int) value + "(으)로";
        }
        final double target = value;
        themeRequestDialog = AppUi.dialog(this).setTitle("테마가 차량 설정을 바꾸려고 해요")
                .setMessage("‘" + studio.config.optString("name") + "’ 테마가 " + what + " 바꾸려고 해요.\n적용할까요?")
                .setNegativeButton("거절", (d, w) -> answer.accept(false, "declined"))
                .setPositiveButton("적용", (d, w) -> {
                    if (driveRestricted) {
                        answer.accept(false, "driving");
                        return;
                    }
                    boolean sent = vehicleWidgets != null && vehicleWidgets.requestAmbient(action, target);
                    answer.accept(sent, sent ? null : "unsupported");
                })
                .setOnCancelListener(d -> answer.accept(false, driveRestricted ? "driving" : "declined")).show();
    }

    /**
     * Taps on the background reach a web theme as 0..1 coordinates (throttled, not while driving).
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // A downward drag from the top band belongs to the quick-settings panel; everything under it gets one cancel so taps, long-presses and widget drags never fire half-way.
        if (pullDown != null && !pullDown.isOpen()) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN)
                pullCancelled = false;
            boolean allowed = widgetShelf == null && !widgetBoard.isEditing() && widgetDrag == null;
            if (pullDown.pull(event, allowed, driveRestricted)) {
                if (!pullCancelled) {
                    MotionEvent cancel = MotionEvent.obtain(event);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(cancel);
                    cancel.recycle();
                    pullCancelled = true;
                }
                return true;
            }
        }
        // While driving only the touch-down reaches the theme (a single tap, e.g. play/pause); drags and swipes stay off.
        if (sceneWeb && sceneView != null && sceneView.isWeb() && sceneView.getWidth() > 0 && widgetShelf == null &&
                !widgetBoard.claims(event.getRawX(), event.getRawY())) {
            int action = event.getActionMasked();
            long now = SystemClock.uptimeMillis();
            if (action == MotionEvent.ACTION_DOWN ||
                    !driveRestricted && action == MotionEvent.ACTION_MOVE && now - lastTouchSent >= 33) {
                lastTouchSent = now;
                int[] at = new int[2];
                sceneView.getLocationOnScreen(at);
                float x = (event.getRawX() - at[0]) / sceneView.getWidth(), y =
                        (event.getRawY() - at[1]) / sceneView.getHeight();
                if (x >= 0 && x <= 1 && y >= 0 && y <= 1)
                    sceneView.sendTouch(x, y);
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private JSONObject vehicleConfig() {
        if ((!linked("ambient.") && !ambientSync) || studio.config.optJSONObject("panel").optBoolean("ambient"))
            return studio.config;
        try {
            JSONObject config = new JSONObject(studio.config.toString());
            config.getJSONObject("panel").put("ambient", true);
            return config;
        } catch (org.json.JSONException e) {
            return studio.config;
        }
    }

    private void layoutWidgets() {
        if (calendarWidget == null)
            return;
        widgetBoard.showWidget("clock", shown("clock"));
        widgetBoard.showWidget("battery", shown("battery"));
        if (batteryWidget != null) {
            if ((showBattery || linked("battery.")) && foreground)
                batteryWidget.start();
            else
                batteryWidget.stop();
        }
        widgetBoard.showWidget("dday", shown("dday"));
        widgetBoard.showWidget("weather", shown("weather"));
        if (vehicleWidgets != null) {
            for (String key : VehicleWidgets.KEYS)
                widgetBoard.showWidget(key, widgetVisible(key));
            vehicleWidgets.sync(vehicleConfig(), foreground);
        }
        if (navigationWidget != null) {
            boolean show = widgetVisible("navigation");
            widgetBoard.showWidget("navigation", show);
            if (show && foreground)
                navigationWidget.start();
            else
                navigationWidget.stop();
        }
        widgetBoard.showWidget("calendar", shown("calendar"));
        widgetBoard.showWidget("music", shown("music"));
        widgetBoard.showWidget("memo", showMemo);
        widgetBoard.requestLayout();
        clock.invalidate();
        backgroundShade.invalidate();
    }

    /**
     * Widgets the current web theme draws itself and the user agreed to hide. Keyed to the theme, so another theme brings them back. Layouts are never changed.
     */
    private java.util.Set<String> themeHides() {
        String saved = getPreferences(MODE_PRIVATE).getString("themeHides", "");
        int bar = saved.indexOf('|');
        if (bar < 0 || !saved.substring(0, bar).equals(themeSignature()))
            return java.util.Collections.emptySet();
        return new java.util.HashSet<>(java.util.Arrays.asList(saved.substring(bar + 1).split(",")));
    }

    private String themeSignature() {
        return studio.config.optString("name") + "/" + studio.config.optString("author") + "/" +
                studio.config.optJSONObject("background").optString("type");
    }

    private boolean shown(String key) {
        return widgetVisible(key) && !themeHides().contains(key);
    }

    /**
     * Asks once per theme whether to hide the widgets it replaces.
     */
    private void offerThemeHides() {
        org.json.JSONArray replaces = studio.config.optJSONArray("replacesWidgets");
        String sig = themeSignature();
        if (replaces == null || replaces.length() == 0 || driveRestricted ||
                getPreferences(MODE_PRIVATE).getString("themeHidesAsked", "").equals(sig))
            return;
        getPreferences(MODE_PRIVATE).edit().putString("themeHidesAsked", sig).apply();
        StringBuilder keys = new StringBuilder(), names = new StringBuilder();
        for (int i = 0; i < replaces.length(); i++) {
            String k = replaces.optString(i);
            if (keys.length() > 0) {
                keys.append(",");
                names.append("·");
            }
            keys.append(k);
            names.append(WidgetShelf.nameOf(k));
        }
        AppUi.dialog(this).setTitle("배경이 " + names + "을(를) 보여줘요")
                .setMessage("이 테마는 배경 안에 " + names + " 기능을 담고 있어요. 같은 위젯을 숨길까요?\n위젯 배치는 그대로 남고, 다른 테마로 바꾸면 다시 나타나요.")
                .setNegativeButton("그대로 두기", null).setPositiveButton("숨기기", (d, w) -> {
                    getPreferences(MODE_PRIVATE).edit().putString("themeHides", sig + "|" + keys).apply();
                    layoutWidgets();
                    updateSongVisibility();
                }).show();
    }

    private boolean widgetVisible(String key) {
        switch (key) {
            case "clock":
                return showClock || showDate;
            case "calendar":
                return showCalendar;
            case "music":
                return showMusic;
            case "memo":
                return showMemo;
            case "dday":
                return showDDay;
            case "battery":
                return showBattery;
            case "weather":
                return showWeather;
            default:
                return java.util.Arrays.asList(WidgetShelf.KEYS).contains(key) &&
                        studio.config.optJSONObject("panel").optBoolean(key, false);
        }
    }

    private void setWidgetVisible(String key, boolean visible) {
        switch (key) {
            case "clock":
                showClock = visible;
                showDate = visible;
                break;
            case "calendar":
                showCalendar = visible;
                break;
            case "music":
                showMusic = visible;
                break;
            case "memo":
                showMemo = visible;
                break;
            case "dday":
                showDDay = visible;
                break;
            case "battery":
                showBattery = visible;
                break;
            case "weather":
                showWeather = visible;
                break;
            default:
                if (!java.util.Arrays.asList(WidgetShelf.KEYS).contains(key))
                    return;
                try {
                    studio.config.getJSONObject("panel").put(key, visible);
                } catch (org.json.JSONException e) {
                    throw new IllegalStateException(e);
                }
                break;
        }
        savePanel();
    }

    private void hideRemovalUndo() {
        root.removeCallbacks(dismissRemovalUndo);
        if (removalUndo != null) {
            root.removeView(removalUndo);
            removalUndo = null;
        }
        removedWidget = null;
    }

    private void removePlacedWidget(String key) {
        if (!widgetVisible(key))
            return;
        hideRemovalUndo();
        removedWidget = key;
        removedClock = showClock;
        removedDate = showDate;
        setWidgetVisible(key, false);
        widgetBoard.setEditing(false);
        if (!foreground)
            return;
        removalUndo = new LinearLayout(this);
        removalUndo.setGravity(Gravity.CENTER_VERTICAL);
        removalUndo.setPadding(dp(20), dp(8), dp(8), dp(8));
        removalUndo.setBackground(AppUi.shape(this, AppUi.GLASS_SOLID, AppUi.R_SHEET, AppUi.EDGE));
        removalUndo.setElevation(dp(28));
        removalUndo.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        TextView message = AppUi.text(this, WidgetShelf.nameOf(key) + " 위젯을 숨겼어요", AppUi.T_CALLOUT, AppUi.INK);
        message.setMaxLines(2);
        removalUndo.addView(message, new LinearLayout.LayoutParams(0, -2, 1));
        Button undo = AppUi.plain(this, "되돌리기", () -> {
            String removed = removedWidget;
            if (removed == null)
                return;
            if (removed.equals("clock")) {
                showClock = removedClock;
                showDate = removedDate;
                savePanel();
            } else
                setWidgetVisible(removed, true);
            hideRemovalUndo();
        });
        undo.setContentDescription("제거한 위젯 되돌리기");
        removalUndo.addView(undo, new LinearLayout.LayoutParams(dp(132), dp(AppUi.TOUCH)));
        FrameLayout.LayoutParams bounds = new FrameLayout.LayoutParams(
                Math.min(dp(560), Math.max(dp(280), root.getWidth() - dp(32))), dp(80),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bounds.bottomMargin = dp(20);
        root.addView(removalUndo, bounds);
        UiMotion.enter(removalUndo, dp(20), .97f);
        root.postDelayed(dismissRemovalUndo, 10000);
    }

    private void hideWidgetShelf() {
        if (widgetShelf == null)
            return;
        WidgetShelf old = widgetShelf;
        widgetShelf = null;
        ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(
                old.getWindowToken(), 0);
        if (shelfScrim != null) {
            root.removeView(shelfScrim);
            shelfScrim = null;
        }
        old.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        UiMotion.exit(old, dp(70), () -> root.removeView(old));
    }

    /**
     * Entry gate for screens that need a long look or typing: refused with a notice while driving.
     */
    private boolean blocked() {
        if (!driveRestricted)
            return false;
        AppUi.notice(this, AppUi.DRIVE_NOTICE);
        return true;
    }

    private void showWidgetShelf() {
        if (blocked())
            return;
        if (widgetShelf != null || widgetDrag != null || isFinishing())
            return;
        shelfScrim = new View(this);
        shelfScrim.setBackgroundColor(0x6610151d);
        shelfScrim.setContentDescription("배경을 눌러 위젯 보관함 닫기");
        shelfScrim.setOnClickListener(v -> hideWidgetShelf());
        root.addView(shelfScrim, new FrameLayout.LayoutParams(-1, -1));
        widgetShelf = new WidgetShelf(this, new WidgetShelf.Actions() {
            public boolean visible(String key) {
                return widgetVisible(key);
            }

            public void add(String key) {
                if (widgetVisible(key)) {
                    widgetBoard.selectWidget(key);
                    hideWidgetShelf();
                    root.post(MainActivity.this::showWidgetDesign);
                } else if (widgetBoard.dropWidget(key, root.getWidth() * .5f, root.getHeight() * .45f))
                    completeWidgetAdd(key);
                else
                    showAddRecovery(key);
            }

            public void remove(String key) {
                setWidgetVisible(key, false);
                if (key.equals(widgetBoard.selectedKey()))
                    widgetBoard.setEditing(false);
            }

            public void restore(String key) {
                setWidgetVisible(key, true);
            }

            public void close() {
                hideWidgetShelf();
            }

            public org.json.JSONObject theme() {
                return studio.config;
            }

            public void arrange() {
                confirmArrange(false);
            }

            public void drag(WidgetShelf.DragItem item, View view) {
                wasEditing = widgetBoard.isEditing();
                widgetDrag = item;
                failedWidgetDrop = null;
                if (view.startDragAndDrop(android.content.ClipData.newPlainText("라이브테마 위젯", item.key),
                        WidgetShelf.shadow(view), item, 0)) {
                    if (!widgetBoard.compactReading())
                        widgetBoard.setEditing(true);
                    hideWidgetShelf();
                } else
                    widgetDrag = null;
            }
        });
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1,
                Math.min(dp(1040), Math.round(root.getHeight() * .9f)), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.setMargins(dp(16), 0, dp(16), dp(16));
        root.addView(widgetShelf, params);
        widgetShelf.bringToFront();
        widgetShelf.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        UiMotion.enter(widgetShelf, dp(72), 1);
    }

    private void completeWidgetAdd(String key) {
        setWidgetVisible(key, true);
        hideWidgetShelf();
        root.post(this::showWidgetDesign);
    }

    private void showAddRecovery(String key) {
        AppUi.dialog(this).setTitle("위젯을 놓을 공간이 부족해요")
                .setMessage("현재 배치는 유지했어요. 위젯 크기를 유지하며 다시 정렬해서 추가할 수 있어요.")
                .setNegativeButton("현재 배치 유지", null).setPositiveButton("자동 정렬 후 추가", (d, w) -> {
                    if (widgetBoard.arrangeForWidget(key))
                        completeWidgetAdd(key);
                    else
                        AppUi.dialog(this).setTitle("현재 크기로는 모두 넣기 어려워요")
                                .setMessage("위젯 크기를 줄이거나 일부를 숨긴 뒤 다시 추가해주세요. 기존 배치는 유지돼요.").setPositiveButton("확인", null)
                                .show();
                }).show();
    }

    private void updatePlacementTools() {
        if (placementTools == null)
            return;
        boolean editing = widgetBoard.isEditing();
        placementTools.setVisibility(editing ? View.VISIBLE : View.GONE);
        if (headingChrome != null)
            headingChrome.setVisibility(editing ? View.GONE : View.VISIBLE);
        if (editChrome != null)
            editChrome.setVisibility(editing ? View.GONE : View.VISIBLE);
        if (placementLabel != null)
            placementLabel.setText(
                    widgetBoard.selectedName() + " · " + widgetBoard.profileName() + "\n배경을 눌러 완료 · 아래로 끌어 제거");
    }

    private void showSizeOptions() {
        AppUi.menu(this, widgetBoard.selectedName() + " 크기", new String[]{"조금 작게", "조금 크게", "정사각형"}, -1, i -> {
            if (i == 2)
                widgetBoard.squareSelected();
            else
                widgetBoard.resizeSelected(i == 0 ? .85f : 1.15f);
        });
    }

    private void confirmArrange(boolean reset) {
        AppUi.dialog(this).setTitle(reset ? "이 화면의 배치를 초기화할까요?" : "이 화면의 위젯을 정렬할까요?")
                .setMessage("현재 창 크기의 위젯 위치와 크기가 바뀌어요. 다른 화면 크기의 배치와 메모·일정·디자인은 유지돼요.").setNegativeButton("취소", null)
                .setPositiveButton(reset ? "초기화" : "정렬", (d, w) -> {
                    hideWidgetShelf();
                    if (reset)
                        widgetBoard.resetPlacement();
                    else
                        widgetBoard.autoArrange();
                }).show();
    }

    private void showWidgetDesign() {
        if (blocked())
            return;
        String key = widgetBoard.selectedKey();
        if (key == null) {
            AppUi.notice(this, "먼저 위젯을 선택해주세요");
            return;
        }
        String[] ids = WidgetDesign.IDS;
        String current = WidgetDesign.design(studio.config, key);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(AppUi.S5), dp(AppUi.S5), dp(AppUi.S5), dp(AppUi.S5));
        android.app.AlertDialog[] self = {null};
        LinearLayout head = AppUi.header(this, widgetBoard.selectedName() + " 디자인", "고르면 바로 바뀌어요", () -> {
            if (self[0] != null)
                self[0].dismiss();
        });
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.bottomMargin = dp(AppUi.S4);
        shell.addView(head, hp);
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        android.app.AlertDialog dialog = AppUi.dialog(this).setView(shell).create();
        self[0] = dialog;
        Button[] choices = new Button[ids.length];
        LinearLayout[] cards = new LinearLayout[ids.length];
        String[] descriptions = {"배경과 어울리는 색감과 글꼴", "가볍고 또렷한 디자인", "중요한 정보를 굵게", "차분한 세리프 감성"};
        int columns = root.getWidth() < dp(1000) ? 1 : 2;
        LinearLayout row = null;
        for (int i = 0; i < ids.length; i++) {
            final int n = i;
            if (i % columns == 0) {
                row = new LinearLayout(this);
                LinearLayout.LayoutParams rb = new LinearLayout.LayoutParams(-1, -2);
                rb.bottomMargin = dp(AppUi.S4);
                body.addView(row, rb);
            }
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(AppUi.S3), dp(AppUi.S3), dp(AppUi.S3), dp(AppUi.S3));
            card.setBackground(AppUi.card(this, ids[i].equals(current)));
            cards[i] = card;
            View sample = new WidgetDesign.Preview(this, key, ids[i], studio.config, studio.typeface);
            sample.setContentDescription(WidgetDesign.NAMES[i] + " " + widgetBoard.selectedName() + " 미리보기");
            sample.setBackground(AppUi.shape(this, AppUi.CARD, AppUi.R_CARD - 6, 0));
            sample.setClipToOutline(true);
            card.addView(sample, new LinearLayout.LayoutParams(-1, dp(124)));
            TextView description = AppUi.text(this, descriptions[i], AppUi.T_CAPTION, AppUi.MUTED);
            description.setPadding(dp(AppUi.S1), dp(AppUi.S3), dp(AppUi.S1), dp(AppUi.S3));
            description.setMaxLines(2);
            card.addView(description);
            Button choice = AppUi.button(this, WidgetDesign.NAMES[i], ids[i].equals(current), null);
            choice.setContentDescription(WidgetDesign.NAMES[i] + " 디자인 적용");
            choice.setSelected(ids[i].equals(current));
            choices[i] = choice;
            card.addView(choice, new LinearLayout.LayoutParams(-1, dp(AppUi.TOUCH)));
            choice.setOnClickListener(v -> {
                studio.setWidgetStyle(key, ids[n]);
                for (int j = 0; j < ids.length; j++) {
                    choices[j].setSelected(j == n);
                    AppUi.styleButton(choices[j], j == n);
                    cards[j].setBackground(AppUi.card(this, j == n));
                }
            });
            sample.setOnClickListener(v -> choice.performClick());
            LinearLayout.LayoutParams cb = new LinearLayout.LayoutParams(0, -2, 1);
            if (i % columns != 0)
                cb.leftMargin = dp(AppUi.S4);
            row.addView(card, cb);
        }
        Button done = AppUi.button(this, "배치로 돌아가기", true, dialog::dismiss);
        done.setContentDescription("위젯 디자인 선택 완료");
        shell.addView(done, new LinearLayout.LayoutParams(-1, dp(AppUi.TOUCH)));
        dialog.setOnShowListener(v -> AppUi.prepareDialog(this, dialog, shell, 840, .86f));
        dialog.show();
    }

    private void styleCard(LinearLayout card) {
        card.setPadding(dp(24), dp(20), dp(24), dp(18));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0x99142129);
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), 0x24ffffff);
        card.setBackground(bg);
    }

    private void loadMood() {
        android.content.SharedPreferences p = getPreferences(MODE_PRIVATE);
        mood = p.getString("mood", Mood.DEFAULT);
        dim = Math.max(0, Math.min(70, p.getInt("dim", Mood.DEFAULT_DIM)));
        nightDim = p.getBoolean("nightDim", true);
        autoColor = p.getBoolean("autoColor", false);
        strongText = p.getBoolean("strongText", false);
        effectAmount = p.getFloat("effectAmount", 1);
        effectSpeed = p.getFloat("effectSpeed", 1);
        glass = p.getFloat("glass", 1);
    }

    private void pushMood() {
        WidgetDesign.strongText = strongText;
        if (themeEffects != null) {
            themeEffects.nightDim = nightDim;
            themeEffects.amountScale = effectAmount;
            themeEffects.speedScale = effectSpeed;
            if (!autoColor)
                themeEffects.autoColor = null;
            themeEffects.invalidate();
        }
        if (!autoColor)
            autoClockColor = null;
        if (widgetBoard != null)
            widgetBoard.setGlassScale(glass);
        if (backgroundShade != null)
            backgroundShade.invalidate();
        if (clock != null)
            clock.invalidate();
    }

    /**
     * A hand-made change in 세부 조정: store it and mark the mood as 사용자 지정.
     */
    private void customMood() {
        mood = Mood.CUSTOM;
        getPreferences(MODE_PRIVATE).edit().putString("mood", mood).putInt("dim", dim).putBoolean("nightDim", nightDim)
                .putBoolean("autoColor", autoColor).putBoolean("strongText", strongText)
                .putFloat("effectAmount", effectAmount).putFloat("effectSpeed", effectSpeed).putFloat("glass", glass)
                .apply();
        applyCreatorTheme();
    }

    private void chooseMood(String id) {
        android.content.SharedPreferences.Editor e = getPreferences(MODE_PRIVATE).edit();
        Mood.apply(e, id);
        e.apply();
        loadMood();
        applyCreatorTheme();
    }

    private void savePanel() {
        studio.setPanels(showClock, showDate, showCalendar, showMusic, showMemo, showDDay, showWeather, showBattery);
        getPreferences(MODE_PRIVATE).edit().putBoolean("showClock", showClock).putBoolean("showDate", showDate)
                .putBoolean("showMusic", showMusic).putBoolean("showMemo", showMemo)
                .putBoolean("showCalendar", showCalendar).putBoolean("showDDay", showDDay)
                .putBoolean("showWeather", showWeather).putString("memo", memo)
                .putInt("dim", dim).putInt("clockPosition", clockPosition).putInt("accent", accent).apply();
        updateSongVisibility();
        report("panel_saved");
    }

    private void editMemo() {
        EditText field = AppUi.input(this, memo, "기억해 둘 일을 적어주세요", 4, 280);
        field.setContentDescription("메모 내용");
        AppUi.form(this, "나의 메모", "저장하면 이 기기에만 보관돼요. 최대 280자까지 적을 수 있어요.", field, "저장", () -> {
            memo = field.getText().toString().trim();
            savePanel();
        });
    }

    private void skipSong(boolean next) {
        if (!songs.skip(next))
            AppUi.notice(this, "음악 앱에서 이 곡의 전환을 지원하지 않아요");
    }

    private void openSongSettings() {
        boolean allowed = songs.allowed();
        if (allowed && songs.openPlayer())
            return;
        AppUi.dialog(this).setTitle(allowed ? "음악 연결" : "곡 정보 연결")
                .setMessage(allowed ?
                        "곡 정보 접근이 허용되어 있어요. 음악 앱에서 재생하면 제목·가수와 이전·다음 곡 조작이 표시돼요. 현재 음악 앱이 바로 열기를 제공하지 않으면 이 안내가 나타나요." :
                        "Android 알림 접근을 허용하면 음악 앱이 제공하는 곡 정보를 표시해요. 알림 본문은 읽거나 저장하지 않아요.")
                .setNegativeButton("닫기", null).setPositiveButton("설정 열기", (d, w) -> {
                    try {
                        startActivity(
                                new android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                    } catch (android.content.ActivityNotFoundException e) {
                        AppUi.notice(this, "연결 설정을 열 수 없습니다");
                    }
                }).show();
    }

    private void showEditor() {
        if (blocked())
            return;
        ThemeHub.show(this, studio, this::showWidgetShelf, () -> widgetBoard.setEditing(true), this::showAdvancedEditor,
                this::quickControls);
    }

    /**
     * Hub row under the theme tabs: 분위기 presets and the mood-light switch, one tap each.
     */
    private View quickControls() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = AppUi.text(this, "분위기", AppUi.T_CALLOUT, AppUi.INK);
        title.setTypeface(AppUi.SEMIBOLD);
        row.addView(title, new LinearLayout.LayoutParams(dp(96), -2));
        TextView custom = AppUi.text(this, "사용자 지정", AppUi.T_CAPTION, AppUi.MUTED);
        LinearLayout track = AppUi.segments(this);
        Button[] choices = new Button[Mood.IDS.length];
        Runnable mark = () -> {
            for (int i = 0; i < choices.length; i++)
                AppUi.segment(choices[i], Mood.IDS[i].equals(mood));
            custom.setVisibility(Mood.CUSTOM.equals(mood) ? View.VISIBLE : View.GONE);
        };
        for (int i = 0; i < Mood.IDS.length; i++) {
            final String id = Mood.IDS[i];
            choices[i] = AppUi.addSegment(track, Mood.NAMES[i], null);
            choices[i].setContentDescription("분위기 " + Mood.NAMES[i]);
            choices[i].setOnClickListener(v -> {
                chooseMood(id);
                mark.run();
            });
        }
        row.addView(track, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(row);
        custom.setPadding(dp(96), dp(AppUi.S1), 0, 0);
        box.addView(custom);
        mark.run();
        addAmbientSyncToggle(box);
        return box;
    }

    /**
     * Pull-down sheet: 분위기, 무드등, and four large tiles. While driving, the two that need a long look are dimmed and explain why.
     */
    private View quickPanel(boolean driving) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(AppUi.S5), dp(AppUi.S5), dp(AppUi.S5), dp(AppUi.S5));
        LinearLayout head = AppUi.header(this, "빠른 설정", "", () -> pullDown.hide());
        LinearLayout.LayoutParams hb = new LinearLayout.LayoutParams(-1, -2);
        hb.bottomMargin = dp(AppUi.S4);
        c.addView(head, hb);
        c.addView(quickControls());
        String[] names = {"테마 바꾸기", "위젯 보관함", "배치 편집", "세부 조정"};
        String[] icons = {"ic_theme", "ic_widgets", "ic_layout", "ic_sliders"};
        boolean[] longLook = {true, true, true, true};
        Runnable[] actions = {this::showEditor, this::showWidgetShelf, () -> widgetBoard.setEditing(true),
                this::showAdvancedEditor};
        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(this);
            for (int col = 0; col < 2; col++) {
                final int i = row * 2 + col;
                boolean blocked = driving && longLook[i];
                Button tile = AppUi.button(this, names[i], false, () -> {
                    if (blocked) {
                        AppUi.notice(this, AppUi.DRIVE_NOTICE);
                        return;
                    }
                    pullDown.hide();
                    actions[i].run();
                });
                AppUi.tile(tile, icons[i]);
                tile.setContentDescription(names[i] + (blocked ? " · " + AppUi.DRIVE_NOTICE : ""));
                AppUi.lockForDriving(tile, blocked);
                LinearLayout.LayoutParams tb = new LinearLayout.LayoutParams(0, dp(112), 1);
                if (col > 0)
                    tb.leftMargin = dp(AppUi.S3);
                line.addView(tile, tb);
            }
            LinearLayout.LayoutParams lb = new LinearLayout.LayoutParams(-1, -2);
            lb.topMargin = dp(AppUi.S3);
            c.addView(line, lb);
        }
        if (driving) {
            TextView note = AppUi.text(this, "주행 중 · 잠긴 항목은 " + AppUi.DRIVE_NOTICE, AppUi.T_CALLOUT, AppUi.WARN);
            note.setPadding(dp(AppUi.S1), dp(AppUi.S4), 0, 0);
            c.addView(note);
        }
        return c;
    }

    private Switch carSwitch(String text, boolean on) {
        return AppUi.toggle(this, text, on);
    }

    /**
     * 세부 조정: the individual values behind the 분위기 presets. Widget on/off lives in the widget shelf, widget content behind a tap on the widget itself.
     */
    private void showAdvancedEditor() {
        if (blocked())
            return;
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(AppUi.S5), dp(AppUi.S5), dp(AppUi.S5), dp(AppUi.S5));
        android.app.AlertDialog[] self = {null};
        LinearLayout head = AppUi.header(this, "세부 조정", "바꾸면 분위기가 ‘사용자 지정’이 돼요", () -> {
            savePanel();
            if (self[0] != null)
                self[0].dismiss();
        });
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.bottomMargin = dp(AppUi.S4);
        shell.addView(head, hp);
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView shadeLabel = AppUi.text(this, "배경 어둡기  " + dim + "%", AppUi.T_CALLOUT, AppUi.INK);
        shadeLabel.setPadding(dp(AppUi.S4), dp(AppUi.S3), 0, 0);
        LinearLayout looks = AppUi.group(this);
        LinearLayout shadeCell = new LinearLayout(this);
        shadeCell.setOrientation(LinearLayout.VERTICAL);
        shadeCell.addView(shadeLabel);
        SeekBar shade = new SeekBar(this);
        shade.setMax(70);
        shade.setProgress(dim);
        shade.setContentDescription("배경 어둡기");
        shade.setProgressTintList(ColorStateList.valueOf(AppUi.ACCENT));
        shade.setThumbTintList(ColorStateList.valueOf(AppUi.ACCENT));
        shade.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int n, boolean user) {
                if (user) {
                    dim = n;
                    shadeLabel.setText("배경 어둡기  " + n + "%");
                    backgroundShade.invalidate();
                }
            }

            public void onStartTrackingTouch(SeekBar b) {
            }

            public void onStopTrackingTouch(SeekBar b) {
                customMood();
            }
        });
        shadeCell.addView(shade, new LinearLayout.LayoutParams(-1, dp(AppUi.TOUCH)));
        AppUi.addRow(looks, shadeCell);
        content.addView(looks, new LinearLayout.LayoutParams(-1, -2));
        Switch night = carSwitch("밤에는 자동으로 조금 더 어둡게", nightDim);
        night.setOnCheckedChangeListener((b, on) -> {
            nightDim = on;
            customMood();
        });
        AppUi.addRow(looks, night);
        Switch colors = carSwitch("시계·효과 색을 배경에 맞추기", autoColor);
        colors.setOnCheckedChangeListener((b, on) -> {
            autoColor = on;
            customMood();
        });
        AppUi.addRow(looks, colors);
        Switch strong = carSwitch("위젯 숫자 굵게", strongText);
        strong.setOnCheckedChangeListener((b, on) -> {
            strongText = on;
            customMood();
        });
        AppUi.addRow(looks, strong);
        TextView effectLabel = AppUi.text(this, "배경 효과", AppUi.T_CALLOUT, AppUi.INK);
        effectLabel.setTypeface(AppUi.SEMIBOLD);
        effectLabel.setPadding(dp(AppUi.S1), dp(AppUi.S5), 0, dp(AppUi.S2));
        content.addView(effectLabel);
        LinearLayout effects = AppUi.segments(this);
        String[] effectNames = {"끄기", "보통", "많이"};
        float[][] effectValues = {{0, .6f}, {1, 1}, {1.6f, 1.3f}};
        Button[] effectButtons = new Button[3];
        Runnable markEffect = () -> {
            for (int i = 0; i < 3; i++)
                AppUi.segment(effectButtons[i], Math.abs(effectAmount - effectValues[i][0]) < .01f);
        };
        for (int i = 0; i < 3; i++) {
            final int n = i;
            effectButtons[i] = AppUi.addSegment(effects, effectNames[i], () -> {
                effectAmount = effectValues[n][0];
                effectSpeed = effectValues[n][1];
                markEffect.run();
                customMood();
            });
            effectButtons[i].setContentDescription("배경 효과 " + effectNames[i]);
        }
        markEffect.run();
        content.addView(effects);
        Switch still = carSwitch("배경 움직임 멈추기", !playing);
        still.setOnCheckedChangeListener((b, on) -> {
            playing = !on;
            themeEffects.playing = playing && !driveRestricted;
            themeEffects.invalidate();
            getPreferences(MODE_PRIVATE).edit().putBoolean("playing", playing).apply();
            if (sceneMode)
                sceneView.playback(playing);
            else
                video.updatePlayback();
        });
        LinearLayout motion = AppUi.group(this);
        AppUi.addRow(motion, still);
        LinearLayout.LayoutParams sb = new LinearLayout.LayoutParams(-1, -2);
        sb.topMargin = dp(AppUi.S4);
        content.addView(motion, sb);
        if (ThemeHub.devMode(this)) {
            AppUi.section(content, "개발자", "시험용 기능이에요. 꾸미기 제목을 2초 눌러 끌 수 있어요.");
            Button scene = button("원본 장면 가져오기 · 실험");
            scene.setOnClickListener(v -> pickScene());
            content.addView(scene, new LinearLayout.LayoutParams(-1, dp(AppUi.TOUCH)));
        }
        android.app.AlertDialog dialog = AppUi.dialog(this).setView(shell).create();
        self[0] = dialog;
        Button done = AppUi.button(this, "완료", true, () -> {
            savePanel();
            dialog.dismiss();
        });
        LinearLayout.LayoutParams doneBounds = new LinearLayout.LayoutParams(-1, dp(AppUi.TOUCH));
        doneBounds.topMargin = dp(AppUi.S4);
        shell.addView(done, doneBounds);
        editorDialog = dialog;
        dialog.setOnShowListener(v -> AppUi.prepareDialog(this, dialog, shell, 760, .94f));
        dialog.show();
    }

    private void pickScene() {
        if (importing)
            return;
        android.content.Intent picker = new android.content.Intent(
                android.content.Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        android.content.pm.ResolveInfo handler = getPackageManager().resolveActivity(picker,
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        if (handler == null || handler.activityInfo.packageName.equals("com.android.car.frameworkpackagestubs")) {
            AppUi.dialog(this).setTitle("ZIP 공유로 테마 가져오기")
                    .setMessage(
                            "이 환경에는 폴더 선택기가 없습니다. 파일을 공유할 수 있는 앱에서 테마 ZIP의 공유 버튼을 누르고 ‘라이브테마’를 선택해주세요. ZIP 최상위에 project.json·scene.pkg와 부속 파일이 있어야 합니다.")
                    .setPositiveButton("확인", null).show();
            return;
        }
        try {
            startActivityForResult(picker, 41);
        } catch (android.content.ActivityNotFoundException e) {
            AppUi.notice(this, "이 환경에는 폴더 선택기가 없습니다");
        }
    }

    @Override
    protected void onActivityResult(int request, int result, android.content.Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 44 && result == RESULT_OK && data != null && data.getData() != null) {
            studio.importVideo(data.getData());
            return;
        }
        if (request == 43 && result == RESULT_OK && data != null && data.getData() != null) {
            studio.importFont(data.getData());
            return;
        }
        if (request != 41 || result != RESULT_OK || data == null || data.getData() == null)
            return;
        importScene(data.getData(), false);
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        debugDrive(intent);
        handleSharedScene(intent);
    }

    /**
     * Debug builds only: am start --es debugDrive restricted|parked|auto, --es debugRenderer crash.
     */
    private void debugDrive(android.content.Intent intent) {
        if (!BuildConfig.DEBUG || intent == null)
            return;
        String mode = intent.getStringExtra("debugDrive");
        if (mode != null)
            drive.force("restricted".equals(mode) ? Boolean.TRUE : "parked".equals(mode) ? Boolean.FALSE : null);
        if ("crash".equals(intent.getStringExtra("debugRenderer")))
            sceneView.debugCrashRenderer();
    }

    private void onDriveRestriction(boolean restricted) {
        driveRestricted = restricted;
        AppUi.driving = restricted;
        if (restricted) {
            int closed = AppUi.closeAll();
            if (widgetShelf != null) {
                hideWidgetShelf();
                closed++;
            }
            if (widgetBoard.isEditing())
                closed++;
            if (closed > 0)
                AppUi.notice(this, "주행 중이라 열린 화면을 닫았어요 · " + AppUi.DRIVE_NOTICE);
        }
        if (pullDown != null)
            pullDown.refresh(restricted);
        if (!restricted && ambientPending)
            root.postDelayed(ambientTask, 1000);
        UiMotion.reduced = restricted;
        widgetBoard.setDriving(restricted);
        themeEffects.links.paused = restricted;
        if (restricted && themeRequestDialog != null && themeRequestDialog.isShowing())
            themeRequestDialog.cancel();
        pushThemeData();
        sceneView.setRestricted(restricted);
        video.updatePlayback();
        themeEffects.playing = playing && !restricted;
        themeEffects.invalidate();
        report(restricted ? "drive_restricted" : "drive_clear");
    }

    private void handleSharedScene(android.content.Intent intent) {
        if (!android.content.Intent.ACTION_SEND.equals(intent.getAction()))
            return;
        android.net.Uri uri = intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM);
        if (uri == null || !"content".equals(uri.getScheme()))
            return;
        intent.removeExtra(android.content.Intent.EXTRA_STREAM);
        AppUi.dialog(this).setTitle("공유받은 테마 가져오기")
                .setMessage(
                        "공유받은 테마 디자인을 저장하고 적용합니다. 라이브테마 ZIP은 배경 영상·시계·효과를 적용하며 위젯 배치는 유지하고 메모와 일정은 유지됩니다. 원본 Scene ZIP은 실험용으로 가져옵니다.")
                .setNegativeButton("취소", null).setPositiveButton("가져오기", (d, w) -> importScene(uri, true)).show();
    }

    private void importScene(android.net.Uri tree, boolean zip) {
        if (importing)
            return;
        importing = true;
        AppUi.notice(this, "원본 장면을 가져오고 있어요");
        new Thread(() -> {
            SceneImporter importer = new SceneImporter(getApplicationContext());
            try {
                java.io.File creator = zip ? CreatorTheme.importUri(getApplicationContext(), tree) : null;
                if (creator != null) {
                    runOnUiThread(() -> {
                        importing = false;
                        if (isDestroyed())
                            return;
                        try {
                            studio.use(creator);
                            if (editorDialog != null)
                                editorDialog.dismiss();
                            AppUi.notice(this, "공유 테마 적용 완료");
                            report("creator_imported");
                        } catch (Exception e) {
                            AppUi.dialog(this).setTitle("테마 적용 실패").setMessage(e.getMessage())
                                    .setPositiveButton("확인", null).show();
                        }
                    });
                    return;
                }
                java.io.File folder = zip ? importer.loadZip(tree) : importer.load(tree);
                runOnUiThread(() -> {
                    importing = false;
                    if (isDestroyed()) {
                        importer.discard(folder);
                        return;
                    }
                    String old = sceneFolder;
                    useScene(folder);
                    if (editorDialog != null)
                        editorDialog.dismiss();
                    report("scene_imported");
                    if (old.matches("scene-[a-f0-9-]{36}") && !old.equals(sceneFolder))
                        importer.discard(new java.io.File(getFilesDir(), old));
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    importing = false;
                    if (!isDestroyed())
                        AppUi.dialog(this).setTitle("장면을 가져올 수 없어요").setMessage(error.getMessage())
                                .setPositiveButton("확인", null).show();
                });
            }
        }, "scene-import").start();
    }

    private void useScene(java.io.File folder) {
        externalVideo = null;
        try {
            studio.config.getJSONObject("background").put("type", "keep");
        } catch (org.json.JSONException e) {
            throw new IllegalStateException(e);
        }
        sceneFolder = folder.getName();
        sceneMode = true;
        sceneWeb = false;
        getPreferences(MODE_PRIVATE).edit().putString("sceneFolder", sceneFolder).putBoolean("sceneMode", true).apply();
        video.active = false;
        video.releasePlayer();
        sceneView.setVisibility(View.VISIBLE);
        sceneView.playback(foreground && playing);
        sceneView.load(folder);
        themeLabel.setText("가져온 원본 장면 · 실험");
        studio.persist();
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private TextView label(String text, int size) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(0xffe0eee5);
        v.setShadowLayer(Math.min(dp(12), Math.max(dp(4), v.getTextSize() * .4f)), 0, dp(1), 0xf0091716);
        v.setPadding(0, 0, 0, dp(8));
        return v;
    }

    private void addAmbientSyncToggle(LinearLayout content) {
        Switch toggle = AppUi.toggle(this, "무드등을 배경 색에 맞추기", ambientSync);
        Boolean supported = vehicleWidgets == null ? Boolean.FALSE : vehicleWidgets.ambientSupported();
        TextView note = AppUi.text(this,
                Boolean.FALSE.equals(supported) ? "이 차량에서는 지원하지 않아요" : "테마를 바꾸면 무드등도 배경 색으로 바뀌어요", AppUi.T_CAPTION,
                Boolean.FALSE.equals(supported) ? AppUi.WARN : AppUi.MUTED);
        note.setPadding(dp(AppUi.S4), 0, dp(AppUi.S4), dp(AppUi.S3));
        toggle.setOnCheckedChangeListener((b, on) -> {
            if (!on) {
                setAmbientSync(false);
                return;
            }
            if (getPreferences(MODE_PRIVATE).getBoolean("ambientSyncConsent", false)) {
                setAmbientSync(true);
                return;
            }
            AppUi.dialog(this).setTitle("무드등을 배경 색에 맞출까요?").setMessage(
                            "테마나 배경이 바뀔 때마다 배경의 대표 색으로 실내 무드등 색을 자동으로 바꿔요.\n주행 중에는 바꾸지 않고, 정차하면 적용해요. 15초에 한 번까지만 바꿔요.\n언제든 이 설정에서 끌 수 있어요.")
                    .setNegativeButton("취소", (d, w) -> b.setChecked(false))
                    .setOnCancelListener(d -> b.setChecked(false))
                    .setPositiveButton("켜기", (d, w) -> {
                        getPreferences(MODE_PRIVATE).edit().putBoolean("ambientSyncConsent", true).apply();
                        setAmbientSync(true);
                    }).show();
        });
        LinearLayout group = AppUi.group(this);
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.addView(toggle, new LinearLayout.LayoutParams(-1, -2));
        cell.addView(note);
        AppUi.addRow(group, cell);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, -2);
        gp.topMargin = dp(AppUi.S3);
        content.addView(group, gp);
    }

    private void setAmbientSync(boolean on) {
        ambientSync = on;
        getPreferences(MODE_PRIVATE).edit().putBoolean("ambientSync", on).apply();
        lastAmbientColor = 0;
        if (vehicleWidgets != null)
            vehicleWidgets.sync(vehicleConfig(), foreground);
        if (on)
            root.postDelayed(ambientTask, 1500);
    }

    /**
     * 시계·효과 색을 배경에 맞추기: the background's vibrant colour for effects, a pale tint of it for the clock so the time stays readable. View-only; the theme is not changed.
     */
    private void matchColors() {
        if (!autoColor || !foreground || isFinishing())
            return;
        Bitmap sample = Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888);
        try {
            android.view.PixelCopy.request(getWindow(), sample, result -> {
                if (result != android.view.PixelCopy.SUCCESS || !autoColor)
                    return;
                int[] pixels = new int[64 * 36];
                sample.getPixels(pixels, 0, 64, 0, 0, 64, 36);
                int color = AmbientMatch.pick(pixels);
                float[] hsv = new float[3];
                Color.colorToHSV(color, hsv);
                autoClockColor = Color.HSVToColor(new float[]{hsv[0], Math.min(hsv[1], .18f), 1f});
                themeEffects.autoColor = color;
                themeEffects.invalidate();
                clock.invalidate();
            }, new android.os.Handler(getMainLooper()));
        } catch (IllegalArgumentException ignored) {
        }
    }

    /**
     * Samples what is on screen (video frame, picture or web theme) and sends its vibrant colour to the mood light.
     */
    private void matchAmbient() {
        if (!ambientSync || !foreground || vehicleWidgets == null || isFinishing())
            return;
        if (driveRestricted) {
            ambientPending = true;
            return;
        }
        long wait = lastAmbientSync == 0 ? 0 :
                AmbientMatch.MIN_INTERVAL_MS - (SystemClock.elapsedRealtime() - lastAmbientSync);
        if (wait > 0) {
            root.removeCallbacks(ambientTask);
            root.postDelayed(ambientTask, wait);
            return;
        }
        Bitmap sample = Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888);
        try {
            android.view.PixelCopy.request(getWindow(), sample, result -> {
                if (result != android.view.PixelCopy.SUCCESS || !ambientSync || driveRestricted) {
                    ambientPending = driveRestricted;
                    return;
                }
                int[] pixels = new int[64 * 36];
                sample.getPixels(pixels, 0, 64, 0, 0, 64, 36);
                int color = AmbientMatch.pick(pixels);
                ambientPending = false;
                if (color == lastAmbientColor)
                    return;
                lastAmbientSync = SystemClock.elapsedRealtime();
                boolean sent = vehicleWidgets.requestAmbient("ambient.color", color | 0xff000000);
                Log.i("LiveAmbientSync", String.format(java.util.Locale.ROOT, "#%06X sent=%s", color & 0xffffff, sent));
                if (sent)
                    lastAmbientColor = color;
            }, new android.os.Handler(getMainLooper()));
        } catch (IllegalArgumentException e) {
            ambientPending = false;
        }
    }

    private Button button(String text) {
        return AppUi.button(this, text, false, null);
    }

    private android.widget.ImageButton transport(int icon, String name, boolean primary) {
        android.widget.ImageButton b = new android.widget.ImageButton(this);
        b.setImageResource(icon);
        b.setContentDescription(name);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        b.setPadding(dp(24), dp(24), dp(24), dp(24));
        b.setBackground(
                new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33ffffff),
                        AppUi.shape(this, primary ? 0xffffffff : 0x33ffffff, 38, 0), null));
        if (!primary)
            b.setImageTintList(android.content.res.ColorStateList.valueOf(0xffffffff));
        AppUi.pressable(b);
        return b;
    }

    @Override
    protected void onResume() {
        super.onResume();
        themeEffects.links.night =
                (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES;
        pushThemeData();
        video.active = !sceneMode;
        video.openIfNeeded();
        video.updatePlayback();
        sceneView.onResume();
        themeEffects.active = true;
        themeEffects.playing = playing && !driveRestricted;
        themeEffects.invalidate();
        if (sceneMode) {
            sceneView.applyFps();
            sceneView.playback(playing);
        }
        clock.active = true;
        clock.invalidate();
        foreground = true;
        if (android.os.Build.VERSION.SDK_INT >= 33)
            registerReceiver(accessReceiver, new android.content.IntentFilter(SongAccessService.ACCESS_CHANGED),
                    android.content.Context.RECEIVER_NOT_EXPORTED);
        else
            registerReceiver(accessReceiver, new android.content.IntentFilter(SongAccessService.ACCESS_CHANGED),
                    android.content.Context.RECEIVER_NOT_EXPORTED);
        updateSongVisibility();
        root.removeCallbacks(volumeTask);
        root.post(volumeTask);
        report("resumed");
    }

    @Override
    protected void onPause() {
        foreground = false;
        widgetBoard.setEditing(false);
        hideRemovalUndo();
        hideWidgetShelf();
        weatherWidget.stop();
        batteryWidget.stop();
        if (vehicleWidgets != null)
            vehicleWidgets.stop();
        if (navigationWidget != null)
            navigationWidget.stop();
        themeEffects.active = false;
        themeEffects.invalidate();
        songs.stop();
        unregisterReceiver(accessReceiver);
        video.active = false;
        video.updatePlayback();
        sceneView.playback(false);
        sceneView.onPause();
        clock.active = false;
        report("paused");
        super.onPause();
    }

    @Override
    protected void onStart() {
        super.onStart();
        drive.start();
    }

    @Override
    protected void onStop() {
        drive.stop();
        video.releasePlayer();
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        if (pullDown != null && pullDown.isOpen()) {
            pullDown.hide();
            return;
        }
        if (widgetDrag != null) {
            widgetBoard.cancelDragAndDrop();
            return;
        }
        if (widgetShelf != null) {
            hideWidgetShelf();
            return;
        }
        if (widgetBoard.isEditing()) {
            widgetBoard.setEditing(false);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        video.releasePlayer();
        sceneView.destroy();
        if (batteryWidget != null)
            batteryWidget.close();
        if (vehicleWidgets != null)
            vehicleWidgets.stop();
        if (navigationWidget != null)
            navigationWidget.stop();
        weatherWidget.close();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle saved) {
        saved.putBoolean("playing", playing);
        saved.putInt("palette", palette);
        saved.putInt("videoPosition", video.currentPosition());
        super.onSaveInstanceState(saved);
    }

    private void report(String event) {
        try {
            JSONObject row = new JSONObject();
            row.put("event", event).put("instance", instance).put("taskId", getTaskId())
                    .put("uid", Process.myUid()).put("systemApp",
                            (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0)
                    .put("width", video == null ? 0 : video.getWidth())
                    .put("height", video == null ? 0 : video.getHeight())
                    .put("playing", playing).put("palette", palette).put("theme", themeIndex)
                    .put("videoPosition", video == null ? 0 : video.currentPosition())
                    .put("videoReady", video != null && video.ready)
                    .put("videoFrames", video == null ? 0 : video.frames)
                    .put("creatorName", studio == null ? "" : studio.config.optString("name"))
                    .put("clockDesign", studio == null ? "" : studio.config.optJSONObject("clock").optString("design"))
                    .put("sceneMode", sceneMode).put("showClock", showClock).put("showMusic", showMusic)
                    .put("showMemo", showMemo).put("showCalendar", showCalendar)
                    .put("layoutEditing", widgetBoard != null && widgetBoard.isEditing())
                    .put("layoutProfile", widgetBoard == null ? "" : widgetBoard.profileName())
                    .put("memoLength", memo == null ? 0 : memo.length()).put("dim", dim)
                    .put("clockPosition", clockPosition)
                    .put("videoDuration", video != null && video.ready ? video.player.getDuration() : 0);
            Log.i("PleosWallpaperApp", row.toString());
        } catch (org.json.JSONException error) {
            Log.e("PleosWallpaperApp", "State report failed", error);
        }
    }

    private final class VideoBackground extends TextureView implements TextureView.SurfaceTextureListener {
        private MediaPlayer player;
        private Surface surface;
        private boolean ready, active;
        private int position, videoWidth = 1, videoHeight = 1, frames;

        VideoBackground() {
            super(MainActivity.this);
            setSurfaceTextureListener(this);
            setContentDescription("라이브테마 배경 영상");
        }

        private int currentPosition() {
            if (ready && player != null)
                position = player.getCurrentPosition();
            return position;
        }

        private void openIfNeeded() {
            if (!active || surface == null || player != null)
                return;
            MediaPlayer candidate = new MediaPlayer();
            player = candidate;
            candidate.setOnPreparedListener(mp -> {
                if (player != mp)
                    return;
                ready = true;
                videoWidth = mp.getVideoWidth();
                videoHeight = mp.getVideoHeight();
                fitVideo();
                mp.seekTo(position, MediaPlayer.SEEK_CLOSEST);
                report("video_prepared");
            });
            candidate.setOnSeekCompleteListener(mp -> {
                if (player == mp) {
                    updatePlayback();
                    report("video_seek_complete");
                }
            });
            candidate.setOnVideoSizeChangedListener((mp, w, h) -> {
                videoWidth = w;
                videoHeight = h;
                fitVideo();
            });
            candidate.setOnErrorListener((mp, what, extra) -> {
                if (player != mp)
                    return true;
                ready = false;
                Log.e("PleosWallpaperApp", "Video decoder error " + what + " / " + extra);
                releasePlayer();
                AppUi.notice(MainActivity.this, "영상을 재생할 수 없습니다");
                return true;
            });
            try {
                if (externalVideo != null)
                    candidate.setDataSource(externalVideo.getAbsolutePath());
                else
                    try (AssetFileDescriptor file = getAssets().openFd(THEME_FILES[themeIndex])) {
                        candidate.setDataSource(file.getFileDescriptor(), file.getStartOffset(), file.getLength());
                    }
                candidate.setSurface(surface);
                candidate.setLooping(true);
                candidate.setVolume(0f, 0f);
                candidate.prepareAsync();
            } catch (java.io.IOException | IllegalArgumentException error) {
                Log.e("PleosWallpaperApp", "Unable to open background video", error);
                releasePlayer();
                AppUi.notice(MainActivity.this, "배경 영상을 열 수 없습니다");
            }
        }

        private void updatePlayback() {
            if (!ready || player == null)
                return;
            if (active && playing && !driveRestricted) {
                if (!player.isPlaying())
                    player.start();
            } else if (player.isPlaying()) {
                player.pause();
                position = player.getCurrentPosition();
            }
        }

        private void releasePlayer() {
            if (player == null)
                return;
            currentPosition();
            MediaPlayer previous = player;
            player = null;
            ready = false;
            previous.release();
        }

        private void fitVideo() {
            int w = getWidth(), h = getHeight();
            if (w == 0 || h == 0 || videoWidth == 0 || videoHeight == 0)
                return;
            float scale = Math.max(w / (float) videoWidth, h / (float) videoHeight);
            Matrix matrix = new Matrix();
            matrix.setScale(videoWidth * scale / w, videoHeight * scale / h, w * .5f, h * .5f);
            setTransform(matrix);
        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int w, int h) {
            surface = new Surface(texture);
            fitVideo();
            openIfNeeded();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int w, int h) {
            fitVideo();
            report("size");
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            releasePlayer();
            if (surface != null)
                surface.release();
            surface = null;
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
            if (++frames % 60 == 0 || frames == 1)
                report("video_frame");
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            super.onSizeChanged(w, h, oldW, oldH);
            fitVideo();
            report("size");
        }
    }

    private void applyCreatorTheme() {
        if (video == null)
            return;
        pushMood();
        if (sceneView != null) {
            org.json.JSONArray rw = studio.config.optJSONArray("replacesWidgets");
            sceneView.setInfoWhileDriving(rw != null && rw.length() > 0);
        }
        root.post(this::offerThemeHides);
        if (autoColor) {
            root.removeCallbacks(colorTask);
            root.postDelayed(colorTask, 2500);
        }
        JSONObject panel = studio.config.optJSONObject("panel");
        int headingColor = Color.parseColor(studio.config.optJSONObject("clock").optString("color", "#F3F7EF"));
        if (brandLabel != null)
            brandLabel.setTextColor(headingColor);
        if (themeLabel != null)
            themeLabel.setTextColor(headingColor);
        showClock = panel.optBoolean("clock", true);
        showDate = panel.optBoolean("date", true);
        showCalendar = panel.optBoolean("calendar", true);
        showMusic = panel.optBoolean("music", true);
        showMemo = panel.optBoolean("memo", true);
        showDDay = panel.optBoolean("dday", false);
        showWeather = panel.optBoolean("weather", false);
        showBattery = panel.optBoolean("battery", false);
        getPreferences(MODE_PRIVATE).edit().putBoolean("showClock", showClock).putBoolean("showDate", showDate)
                .putBoolean("showCalendar", showCalendar).putBoolean("showMusic", showMusic)
                .putBoolean("showMemo", showMemo).putBoolean("showDDay", showDDay)
                .putBoolean("showWeather", showWeather).apply();
        String[] keys = {"clock", "memo", "music", "calendar", "dday", "weather", "battery"};
        View[] cards = {clock, memoCard, songCard, calendarWidget, ddayWidget, weatherWidget, batteryWidget};
        for (int i = 0; i < keys.length; i++)
            if (cards[i] != null)
                WidgetDesign.applySkin(cards[i], keys[i], studio.config);
        if (vehicleWidgets != null)
            for (VehicleWidgets.Card card : vehicleWidgets.cards.values())
                WidgetDesign.applySkin(card, card.key, studio.config);
        if (navigationWidget != null)
            WidgetDesign.applySkin(navigationWidget, "navigation", studio.config);
        if (songArt != null)
            songArt.invalidate();
        if (ambientSync) {
            lastAmbientColor = 0;
            root.postDelayed(ambientTask, 2500);
        }
        widgetBoard.setGlass(Color.luminance(Color.parseColor(panel.optString("textColor", "#E0EEE5"))) < .4f);
        JSONObject background = studio.config.optJSONObject("background");
        String type = background.optString("type", "keep");
        if ("web".equals(type) && studio.backgroundFile != null && studio.backgroundFile.isFile()) {
            java.io.File dir = studio.backgroundFile.getParentFile();
            if (!sceneMode || !sceneWeb || !dir.getAbsolutePath().equals(sceneFolder)) {
                video.releasePlayer();
                externalVideo = null;
                sceneMode = true;
                sceneWeb = true;
                sceneFolder = dir.getAbsolutePath();
                sceneView.setVisibility(View.VISIBLE);
                sceneView.loadWeb(dir);
                if (foreground)
                    sceneView.playback(playing);
            }
            if (themeLabel != null)
                themeLabel.setText(studio.config.optString("name"));
            updateSongVisibility();
            pushThemeData();
            return;
        }
        if ("workshop".equals(type)) {
            String id = background.optString("id");
            java.io.File workshop = WorkshopLibrary.scene(this, id);
            if (workshop != null) {
                String folder = "creator-themes/workshop-" + id;
                if (!sceneMode || sceneWeb || !folder.equals(sceneFolder)) {
                    video.releasePlayer();
                    externalVideo = null;
                    sceneMode = true;
                    sceneWeb = false;
                    sceneFolder = folder;
                    sceneView.setVisibility(View.VISIBLE);
                    sceneView.load(workshop);
                    getPreferences(MODE_PRIVATE).edit().putBoolean("sceneMode", true).putString("sceneFolder", folder)
                            .apply();
                    if (foreground)
                        sceneView.playback(playing);
                }
                if (themeLabel != null)
                    themeLabel.setText(studio.config.optString("name"));
                updateSongVisibility();
                return;
            }
        }
        java.io.File next = ("video".equals(type) || "workshop".equals(type)) ? studio.backgroundFile : null;
        int index = background.optInt("index", 0);
        if (!"keep".equals(type) && (sceneMode || !java.util.Objects.equals(next, externalVideo) ||
                ("builtin".equals(type) && themeIndex != index))) {
            sceneMode = false;
            sceneWeb = false;
            getPreferences(MODE_PRIVATE).edit().putBoolean("sceneMode", false)
                    .putInt("theme", "builtin".equals(type) ? index : themeIndex).apply();
            sceneView.playback(false);
            sceneView.setVisibility(View.GONE);
            sceneView.loadUrl("about:blank");
            video.releasePlayer();
            externalVideo = next;
            if ("builtin".equals(type))
                themeIndex = index;
            video.position = 0;
            video.frames = 0;
            video.active = foreground;
            video.openIfNeeded();
        }
        if (themeLabel != null && !"keep".equals(type))
            themeLabel.setText(("video".equals(type) || "workshop".equals(type)) ? studio.config.optString("name") :
                    THEME_NAMES[themeIndex]);
        updateSongVisibility();
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if ((request == 55 || request == 57) && weatherWidget != null)
            weatherWidget.permissionResult();
        if (request == 56 && batteryWidget != null)
            batteryWidget.permissionResult();
    }

    private final class ClockFace extends View {
        private final ThemeClock renderer = new ThemeClock();
        private boolean active;

        ClockFace() {
            super(MainActivity.this);
        }

        @Override
        protected void onDraw(Canvas c) {
            long now = System.currentTimeMillis();
            JSONObject drawing = WidgetDesign.clockTheme(studio.config);
            if (autoClockColor != null)
                try {
                    drawing.getJSONObject("clock")
                            .put("color", String.format(java.util.Locale.ROOT, "#%06X", autoClockColor & 0xffffff));
                } catch (org.json.JSONException ignored) {
                }
            Typeface face = WidgetDesign.clockFont(drawing, studio.typeface);
            renderer.draw(c, getWidth(), getHeight(), drawing, face, showClock, showDate, now);
            setContentDescription("현재 시각 " +
                    new java.text.SimpleDateFormat("HH:mm", java.util.Locale.KOREA).format(new java.util.Date(now)));
            if (active)
                postInvalidateDelayed(1000);
        }
    }
}
