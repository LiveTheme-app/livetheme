package io.hmg.wallpaperapp;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import android.widget.*;

import java.io.*;
import java.net.*;
import java.util.*;

import org.json.*;

import ai.pleos.playground.location.fused.FusedLocation;
import ai.pleos.playground.location.fused.listener.FusedLocationListener;
import ai.pleos.playground.location.fused.model.LocationData;

/**
 * Foreground-only weather. Coordinates/forecast cache never enter a shared theme.
 */
final class WeatherWidget extends LinearLayout {
    private final Activity activity;
    private final SharedPreferences store;
    private final TextView place, summary, condition, rain, status;
    private final Runnable changed;
    private String forecast = "", temperature = "";
    private boolean active, busy, started;
    private int generation;
    private CancellationSignal locationRequest;
    private FusedLocation vehicleLocation;
    private final Runnable tick = () -> refresh(false);
    private static final String[] CITIES = {"서울", "부산", "대전", "광주", "제주"};
    private static final double[][] POINTS = {{37.57, 126.98}, {35.18, 129.08}, {36.35, 127.38}, {35.16, 126.85},
            {33.50, 126.53}};

    WeatherWidget(Activity a, Runnable changed) {
        super(a);
        activity = a;
        this.changed = changed;
        store = a.getSharedPreferences("weather", Context.MODE_PRIVATE);
        setOrientation(VERTICAL);
        setPadding(24, 16, 24, 16);
        place = text("날씨 · 강수", 14);
        summary = text("지역을 선택해주세요", 25);
        condition = text("", 16);
        rain = text("앞으로 6시간의 강수확률·강수량", 13);
        status = text("눌러서 위치 또는 지역 설정", 12);
        setOnClickListener(v -> settings());
        setContentDescription("날씨와 강수 지역 설정");
        showCache();
    }

    private TextView text(String s, int size) {
        TextView v = new TextView(activity);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(0xffe0eee5);
        v.setMaxLines(2);
        addView(v);
        return v;
    }

    @Override
    protected void onMeasure(int w, int h) {
        boolean tall = android.view.View.MeasureSpec.getMode(h) != android.view.View.MeasureSpec.UNSPECIFIED &&
                android.view.View.MeasureSpec.getSize(h) > android.view.View.MeasureSpec.getSize(w) * .8f;
        summary.setTextSize(summary.getText().length() > 10 ? 22 : tall ? 48 : 25);
        if (!temperature.isEmpty())
            summary.setText(tall ? temperature : temperature + "  " + condition.getText());
        condition.setVisibility(tall ? VISIBLE : GONE);
        if (!forecast.isEmpty()) {
            String[] hours = forecast.split("   |\n");
            StringBuilder lines = new StringBuilder();
            for (int i = 0; i < hours.length; i++) {
                if (i > 0)
                    lines.append(i % (tall ? 2 : 3) == 0 ? "\n" : "   ");
                lines.append(hours[i]);
            }
            rain.setText(lines.toString());
        }
        rain.setMaxLines(tall ? 6 : 2);
        rain.setLineSpacing(tall ? 4 : 0, 1);
        super.onMeasure(w, h);
    }

    void start() {
        started = true;
        if (active)
            return;
        active = true;
        refresh(false);
    }

    void stop() {
        started = false;
        pauseConnection();
    }

    private void reconnect() {
        if (started) {
            active = true;
            refresh(true);
        }
    }

    private void pauseConnection() {
        active = false;
        generation++;
        busy = false;
        removeCallbacks(tick);
        if (locationRequest != null) {
            locationRequest.cancel();
            locationRequest = null;
        }
        close();
    }

    void close() {
        if (vehicleLocation != null) {
            try {
                vehicleLocation.release();
            } catch (RuntimeException ignored) {
            }
            vehicleLocation = null;
        }
    }

    private void useVehicleLocation() {
        AppUi.dialog(activity).setTitle("차량 위치로 날씨 조회").setMessage(
                        "차량이 제공한 위치를 소수 둘째 자리로 반올림해 Open-Meteo에 전송해요. 위치는 공유 테마에 포함하지 않으며 앱이 보일 때만 조회해요. 미지원 차량에서는 도시를 직접 선택할 수 있어요.")
                .setNegativeButton("취소", null).setPositiveButton("사용", (d, w) -> {
                    store.edit().putString("mode", "vehicle").remove("at").apply();
                    if (activity.checkSelfPermission("pleos.car.permission.FUSED_LOCATION") !=
                            PackageManager.PERMISSION_GRANTED)
                        activity.requestPermissions(new String[]{"pleos.car.permission.FUSED_LOCATION"}, 57);
                    else
                        reconnect();
                }).show();
    }

    private void requestVehicleLocation() {
        if (activity.checkSelfPermission("pleos.car.permission.FUSED_LOCATION") != PackageManager.PERMISSION_GRANTED) {
            status.setText("차량 위치 권한 필요 · 눌러서 다시 선택");
            return;
        }
        busy = true;
        final int token = generation;
        status.setText("차량 위치 확인 중…");
        try {
            if (vehicleLocation == null) {
                vehicleLocation = new FusedLocation(activity);
                if (!vehicleLocation.isSupported()) {
                    busy = false;
                    status.setText("차량 위치 미지원 · 도시를 선택해주세요");
                    return;
                }
                vehicleLocation.initialize();
            } else if (!vehicleLocation.isSupported()) {
                busy = false;
                status.setText("차량 위치 미지원 · 도시를 선택해주세요");
                return;
            }
            vehicleLocation.getLastKnownLocation(new FusedLocationListener() {
                public void onChanged(LocationData location) {
                    post(() -> {
                        if (!active || token != generation)
                            return;
                        busy = false;
                        long age = location == null ? Long.MAX_VALUE :
                                System.currentTimeMillis() - location.getTimestampMillis();
                        if (location == null || age < -60000 || age > 300000) {
                            status.setText("최근 차량 위치 없음 · 도시를 선택해주세요");
                            return;
                        }
                        fetch(location.getLatitude(), location.getLongitude(), "차량 현재 위치");
                    });
                }

                public void onError(Exception error) {
                    post(() -> {
                        if (active && token == generation) {
                            busy = false;
                            status.setText("차량 위치 접근 불가 · 도시 선택");
                        }
                    });
                }
            });
            postDelayed(() -> {
                if (active && token == generation && busy && status.getText().toString().equals("차량 위치 확인 중…")) {
                    generation++;
                    busy = false;
                    status.setText("차량 위치 응답 대기 · 도시 선택");
                }
            }, 10000);
        } catch (RuntimeException error) {
            android.util.Log.w("LiveVehicleLocation", error.getClass().getSimpleName() + ": " + error.getMessage());
            busy = false;
            close();
            status.setText("차량 위치 연결 불가 · 도시 선택");
        }
    }

    private void showCache() {
        try {
            WeatherData d = WeatherData.parse(new JSONObject(store.getString("cache", "")));
            render(d, store.getString("cachePlace", ""));
            if (System.currentTimeMillis() - store.getLong("at", 0) > 1800000)
                status.setText("이전 날씨 · " + d.updated + " · Open-Meteo");
        } catch (Exception ignored) {
        }
    }

    private void render(WeatherData d, String label) {
        place.setText(label + " · 날씨/강수");
        temperature = d.temperature;
        forecast = d.rain;
        summary.setText(d.temperature);
        condition.setText(d.condition);
        rain.setText(d.rain);
        status.setText(d.updated + " · Open-Meteo");
        changed.run();
    }

    void settings() {
        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(VERTICAL);
        shell.setPadding(AppUi.dp(activity, 24), AppUi.dp(activity, 20), AppUi.dp(activity, 24),
                AppUi.dp(activity, 20));
        AppUi.section(shell, "날씨 · 강수", "내가 선택한 지역의 기온과 앞으로 6시간의 비 소식");
        ScrollView scroll = new ScrollView(activity);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(VERTICAL);
        scroll.addView(body);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView current = AppUi.text(activity, "현재: " + place.getText(), 18, AppUi.INK);
        current.setPadding(0, 0, 0, AppUi.dp(activity, 16));
        body.addView(current);
        TextView disclosure = AppUi.text(activity,
                "조회할 지역 좌표를 Open-Meteo에 전송해요. 위치는 소수 둘째 자리까지만 사용하며 공유 테마에 넣지 않아요. 위치 조회는 앱이 보일 때만 동작해요.", 15,
                AppUi.MUTED);
        body.addView(disclosure);
        AlertDialog dialog = AppUi.dialog(activity).setView(shell).create();
        LinearLayout sources = new LinearLayout(activity);
        Button car = AppUi.button(activity, "차량 위치", false, () -> {
            dialog.dismiss();
            useVehicleLocation();
        }), device = AppUi.button(activity, "기기 위치", false, () -> {
            dialog.dismiss();
            AppUi.dialog(activity).setTitle("현재 위치로 날씨 조회")
                    .setMessage("위치 권한을 허용하면 대략적인 위치를 날씨 서버에 전송해요. 도시를 직접 선택해도 날씨를 볼 수 있어요.")
                    .setNegativeButton("취소", null).setPositiveButton("사용", (x, y) -> {
                        store.edit().putString("mode", "location").remove("at").apply();
                        if (activity.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) !=
                                PackageManager.PERMISSION_GRANTED)
                            activity.requestPermissions(new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION}, 55);
                        else
                            reconnect();
                    }).show();
        });
        sources.addView(car, new LinearLayout.LayoutParams(0, AppUi.dp(activity, AppUi.TOUCH), 1));
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, AppUi.dp(activity, AppUi.TOUCH), 1);
        right.leftMargin = AppUi.dp(activity, 8);
        sources.addView(device, right);
        LinearLayout.LayoutParams sb = new LinearLayout.LayoutParams(-1, -2);
        sb.setMargins(0, AppUi.dp(activity, 16), 0, AppUi.dp(activity, 16));
        body.addView(sources, sb);
        body.addView(AppUi.text(activity, "도시를 직접 선택", 18, AppUi.INK));
        for (int i = 0; i < CITIES.length; i++) {
            final int city = i;
            Button pick = AppUi.button(activity, CITIES[i],
                    store.getString("mode", "").equals("city") && store.getInt("city", 0) == i, () -> {
                        store.edit().putString("mode", "city").putInt("city", city).remove("at").apply();
                        reconnect();
                        dialog.dismiss();
                    });
            LinearLayout.LayoutParams bounds = new LinearLayout.LayoutParams(-1, AppUi.dp(activity, AppUi.TOUCH));
            bounds.topMargin = AppUi.dp(activity, 8);
            body.addView(pick, bounds);
        }
        Button off = AppUi.button(activity, "날씨 연결 끄기", false, () -> {
            pauseConnection();
            store.edit().clear().apply();
            place.setText("날씨 · 강수");
            summary.setText("날씨 연결 꺼짐");
            condition.setText("");
            temperature = "";
            forecast = "";
            rain.setText("지역을 선택해 다시 연결하세요");
            status.setText("외부 조회 중지");
            dialog.dismiss();
        });
        LinearLayout.LayoutParams ob = new LinearLayout.LayoutParams(-1, AppUi.dp(activity, AppUi.TOUCH));
        ob.topMargin = AppUi.dp(activity, 16);
        body.addView(off, ob);
        LinearLayout footer = new LinearLayout(activity);
        footer.addView(AppUi.button(activity, "새로고침", false, () -> {
            reconnect();
            dialog.dismiss();
        }), new LinearLayout.LayoutParams(0, AppUi.dp(activity, AppUi.TOUCH), 1));
        LinearLayout.LayoutParams close = new LinearLayout.LayoutParams(0, AppUi.dp(activity, AppUi.TOUCH), 1);
        close.leftMargin = AppUi.dp(activity, 8);
        footer.addView(AppUi.button(activity, "닫기", true, dialog::dismiss), close);
        LinearLayout.LayoutParams fb = new LinearLayout.LayoutParams(-1, -2);
        fb.topMargin = AppUi.dp(activity, 16);
        shell.addView(footer, fb);
        dialog.setOnShowListener(v -> AppUi.prepareDialog(activity, dialog, shell, 720, .88f));
        dialog.show();
    }

    void permissionResult() {
        store.edit().remove("at").apply();
        reconnect();
    }

    private void refresh(boolean force) {
        if (!active)
            return;
        if (force) {
            generation++;
            busy = false;
            if (locationRequest != null) {
                locationRequest.cancel();
                locationRequest = null;
            }
        }
        if (busy)
            return;
        removeCallbacks(tick);
        postDelayed(tick, 15 * 60 * 1000);
        String mode = store.getString("mode", "");
        if (mode.isEmpty())
            return;
        if (!force && System.currentTimeMillis() - store.getLong("at", 0) < 15 * 60 * 1000)
            return;
        if ("city".equals(mode)) {
            int i = Math.max(0, Math.min(4, store.getInt("city", 0)));
            fetch(POINTS[i][0], POINTS[i][1], CITIES[i] + " · 직접 선택");
            return;
        }
        if ("vehicle".equals(mode)) {
            requestVehicleLocation();
            return;
        }
        if (activity.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            status.setText("위치 권한 필요 · 눌러서 지역 선택");
            return;
        }
        LocationManager lm = activity.getSystemService(LocationManager.class);
        if (lm == null) {
            status.setText("위치 서비스 없음 · 도시를 선택해주세요");
            return;
        }
        try {
            String provider = lm.getProviders(true).contains("fused") ? "fused" :
                    lm.getProviders(true).contains("network") ? "network" : null;
            if (provider == null) {
                status.setText("사용 가능한 대략적 위치 없음 · 도시 선택");
                return;
            }
            Location cached = lm.getLastKnownLocation(provider);
            if (cached != null &&
                    SystemClock.elapsedRealtimeNanos() - cached.getElapsedRealtimeNanos() < 300000000000L) {
                fetch(cached.getLatitude(), cached.getLongitude(), "현재 위치");
                return;
            }
            status.setText("위치 확인 중…");
            locationRequest = new CancellationSignal();
            final int token = generation;
            lm.getCurrentLocation(provider, locationRequest, activity.getMainExecutor(), location -> {
                if (!active || token != generation)
                    return;
                if (location == null) {
                    status.setText("위치를 받지 못했어요 · 도시 선택");
                    return;
                }
                fetch(location.getLatitude(), location.getLongitude(), "현재 위치");
            });
            postDelayed(() -> {
                if (locationRequest != null) {
                    locationRequest.cancel();
                    locationRequest = null;
                    if (status.getText().toString().equals("위치 확인 중…"))
                        status.setText("위치 대기 시간 초과 · 도시 선택");
                }
            }, 10000);
        } catch (RuntimeException e) {
            status.setText("위치 접근 불가 · 도시를 선택해주세요");
        }
    }

    private void fetch(double lat, double lon, String label) {
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) {
            status.setText("잘못된 위치");
            return;
        }
        busy = true;
        final int token = ++generation;
        status.setText("날씨 불러오는 중…");
        String query = String.format(Locale.ROOT,
                "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m,weather_code&hourly=precipitation_probability,precipitation&forecast_days=2&timezone=auto",
                lat, lon);
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(query).openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);
                if (connection.getResponseCode() != 200)
                    throw new IOException("HTTP " + connection.getResponseCode());
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream in = connection.getInputStream()) {
                    CreatorTheme.copy(in, bytes, 256 * 1024);
                }
                String raw = bytes.toString("UTF-8");
                WeatherData data = WeatherData.parse(new JSONObject(raw));
                post(() -> {
                    if (!active || token != generation)
                        return;
                    busy = false;
                    store.edit().putString("cache", raw).putString("cachePlace", label)
                            .putLong("at", System.currentTimeMillis()).apply();
                    render(data, label);
                });
            } catch (Exception e) {
                post(() -> {
                    if (!active || token != generation)
                        return;
                    busy = false;
                    showCache();
                    status.setText("갱신 실패 · 이전 자료 또는 지역 설정 확인 · Open-Meteo");
                });
            } finally {
                if (connection != null)
                    connection.disconnect();
            }
        }, "weather-fetch").start();
    }
}
