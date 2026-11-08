package io.hmg.wallpaperapp;

import android.app.*;
import android.view.*;
import android.widget.*;

import ai.pleos.playground.navi.helper.NaviHelper;
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener;
import ai.pleos.playground.navi.data.*;
import ai.pleos.playground.navi.constants.NaviErrorCode;
import kotlin.Unit;

/**
 * Links to the built-in navigation app; does not embed or recolor its map.
 */
final class NavigationWidget extends LinearLayout {
    private final Activity activity;
    private final Runnable changed;
    private final TextView destination, detail, status;
    private final Button open, search;
    private NaviHelper helper;
    private NaviHelperEventListener listener;
    private boolean active, ready;
    private int generation;
    private long received;
    private final Runnable poll = new Runnable() {
        public void run() {
            if (!active)
                return;
            if (ready)
                try {
                    helper.getDestinationInfo();
                } catch (RuntimeException e) {
                    status.setText("내비 정보를 받지 못했어요");
                }
            postDelayed(this, 15000);
        }
    };

    NavigationWidget(Activity activity, Runnable changed) {
        super(activity);
        this.activity = activity;
        this.changed = changed;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(24), dp(20), dp(24), dp(18));
        text("내비게이션", 13);
        destination = text("기본 내비와 연결", 29);
        detail = text("지도는 기본 내비 화면에서 열려요", 15);
        status = text("연결 대기", 12);
        LinearLayout actions = new LinearLayout(activity);
        open = AppUi.button(activity, "내비 열기", false, null);
        search = AppUi.button(activity, "목적지 찾기", false, null);
        open.setEnabled(false);
        search.setEnabled(false);
        open.setContentDescription("기본 내비 화면 열기");
        search.setContentDescription("기본 내비 목적지 검색");
        open.setOnClickListener(v -> request(NavigationScreenRequest.Foreground.INSTANCE));
        search.setOnClickListener(v -> showSearch());
        actions.addView(open, new LinearLayout.LayoutParams(0, dp(AppUi.TOUCH), 1));
        actions.addView(search, new LinearLayout.LayoutParams(0, dp(AppUi.TOUCH), 1));
        addView(actions);
        setContentDescription("내비 연동 차량 위젯");
        setOnClickListener(v -> showOptions());
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView text(String s, int size) {
        TextView t = new TextView(activity);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(0xffe0eee5);
        t.setMaxLines(2);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        addView(t);
        return t;
    }

    void start() {
        if (active)
            return;
        active = true;
        ready = false;
        int token = ++generation;
        destination.setText("기본 내비와 연결");
        detail.setText("지도는 기본 내비 화면에서 열려요");
        status.setText("내비 연결 확인 중…");
        received = 0;
        try {
            helper = new NaviHelper(activity);
            listener = new NaviHelperEventListener() {
                @Override
                public void onDestinationInfo(DestinationInfo info) {
                    post(() -> {
                        if (!active || token != generation)
                            return;
                        received = android.os.SystemClock.elapsedRealtime();
                        destination.setText(info == null || info.getName() == null || info.getName().trim().isEmpty() ?
                                "안내 중인 목적지 없음" : info.getName());
                        detail.setText("기본 내비의 목적지 · 눌러서 열기");
                        status.setText("차량 기본 내비 연동");
                        changed.run();
                    });
                }

                @Override
                public void onRouteCancelled() {
                    post(() -> {
                        if (!active || token != generation)
                            return;
                        destination.setText("안내 중인 목적지 없음");
                        detail.setText("새 목적지를 선택해보세요");
                        changed.run();
                    });
                }

                @Override
                public void onError(NaviErrorCode error) {
                    post(() -> {
                        if (active && token == generation)
                            status.setText("내비 요청 처리 불가 · 기본 내비 상태 확인");
                    });
                }
            };
            helper.initialize(() -> {
                post(() -> {
                    if (!active || token != generation)
                        return;
                    ready = true;
                    open.setEnabled(true);
                    search.setEnabled(true);
                    helper.addListener(listener);
                    removeCallbacks(poll);
                    post(poll);
                    status.setText("연결됨 · 목적지 응답 대기");
                    postDelayed(() -> {
                        if (active && token == generation && received == 0)
                            status.setText("목적지 정보 미수신 · 내비 화면은 열 수 있어요");
                    }, 6000);
                });
                return Unit.INSTANCE;
            }, error -> {
                post(() -> {
                    if (active && token == generation) {
                        ready = false;
                        open.setEnabled(false);
                        search.setEnabled(false);
                        status.setText("이 환경에서 내비 연결을 확인할 수 없어요");
                    }
                });
                return Unit.INSTANCE;
            });
        } catch (RuntimeException e) {
            status.setText("기본 내비 서비스에 연결할 수 없어요");
        }
    }

    void stop() {
        active = false;
        ready = false;
        generation++;
        removeCallbacks(poll);
        open.setEnabled(false);
        search.setEnabled(false);
        if (helper != null) {
            try {
                if (listener != null)
                    helper.removeListener(listener);
                helper.release();
            } catch (RuntimeException ignored) {
            }
            helper = null;
            listener = null;
        }
    }

    private void request(NavigationScreenRequest screen) {
        if (!active || !ready || helper == null) {
            AppUi.notice(activity, "기본 내비 연결을 먼저 확인해주세요");
            return;
        }
        try {
            helper.requestNavigationScreen(screen);
            status.setText("기본 내비에 화면 열기를 요청했어요");
        } catch (RuntimeException e) {
            status.setText("화면 요청을 처리할 수 없어요");
        }
    }

    private void showSearch() {
        EditText input = AppUi.input(activity, "", "장소 이름 또는 주소", 1, 100);
        input.setContentDescription("기본 내비 검색어");
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(VERTICAL);
        form.setPadding(dp(16), dp(8), dp(16), dp(8));
        form.addView(input);
        TextView error = AppUi.text(activity, "장소 이름이나 주소를 입력해주세요", 16, 0xffffb4ab);
        error.setPadding(0, dp(8), 0, 0);
        error.setVisibility(GONE);
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        form.addView(error);
        AlertDialog dialog = AppUi.dialog(activity).setTitle("기본 내비에서 목적지 찾기").setView(form)
                .setNegativeButton("취소", null).setPositiveButton("검색", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String q = input.getText().toString().trim();
            if (q.isEmpty()) {
                input.requestFocus();
                error.setVisibility(VISIBLE);
                return;
            }
            request(new NavigationScreenRequest.SearchPlace(q));
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void showOptions() {
        AppUi.dialog(activity).setTitle("기본 내비 연결")
                .setItems(new String[]{"내비 화면 열기", "목적지 찾기", "주변 충전소 찾기", "연결 다시 확인"}, (d, i) -> {
                    if (i == 0)
                        request(NavigationScreenRequest.Foreground.INSTANCE);
                    else if (i == 1)
                        showSearch();
                    else if (i == 2)
                        request(NavigationScreenRequest.NearbyChargingStations.INSTANCE);
                    else {
                        stop();
                        start();
                    }
                }).setNegativeButton("닫기", null).show();
    }
}
