package io.hmg.wallpaperapp;

import android.app.Activity;
import android.car.Car;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.car.hardware.property.EvChargeState;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.Gravity;
import android.widget.*;

import ai.pleos.playground.vehicle.Vehicle;
import ai.pleos.playground.vehicle.api.EvBattery;
import ai.pleos.playground.vehicle.api.model.capability.AccessType;
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener;
import kotlin.Unit;

/**
 * Vehicle data only. A charging switch never substitutes for actual charging state.
 */
final class BatteryWidget extends LinearLayout {
    private final Activity activity;
    private final Runnable changed;
    private final TextView value, state, source;
    private final ProgressBar bar;
    private Vehicle vehicle;
    private EvBattery ev;
    private boolean active, registered, checking;
    private int generation;
    private Integer percent, chargeState;
    private Boolean chargeSwitch, socReadable;
    private String problem = "", last = "";
    private Car car;
    private CarPropertyManager properties;
    private HandlerThread carThread;
    private EvBatteryStateOfChargeListener socListener;
    private CarPropertyManager.CarPropertyEventCallback chargeListener;
    private final Runnable tick = new Runnable() {
        public void run() {
            if (!active)
                return;
            read();
            postDelayed(this, 15000);
        }
    };

    BatteryWidget(Activity a, Runnable changed) {
        super(a);
        activity = a;
        this.changed = changed;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = text("EV BATTERY", 12);
        heading.setLetterSpacing(.15f);
        value = text("—%", 48);
        state = text("차량 연결 대기", 16);
        bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        LinearLayout.LayoutParams bounds = new LinearLayout.LayoutParams(-1, dp(7));
        bounds.setMargins(0, dp(14), 0, dp(12));
        addView(bar, bounds);
        source = text("Pleos 차량 데이터", 11);
        source.setMaxLines(2);
        setContentDescription("차량 배터리와 충전 상태");
        setOnClickListener(v -> {
            if (a.checkSelfPermission(Car.PERMISSION_ENERGY) != PackageManager.PERMISSION_GRANTED)
                a.requestPermissions(new String[]{Car.PERMISSION_ENERGY}, 56);
            else {
                problem = "";
                socReadable = null;
                read();
            }
        });
    }

    static Integer validPercent(Integer n) {
        return n != null && n >= 0 && n <= 100 ? n : null;
    }

    static String chargeLabel(Integer state) {
        if (state == null)
            return "충전 상태 확인 불가";
        switch (state) {
            case EvChargeState.STATE_CHARGING:
                return "충전 중";
            case EvChargeState.STATE_FULLY_CHARGED:
                return "목표 충전량 도달";
            case EvChargeState.STATE_NOT_CHARGING:
                return "충전 중 아님";
            case EvChargeState.STATE_ERROR:
                return "충전 오류";
            default:
                return "충전 상태 확인 불가";
        }
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private TextView text(String title, int size) {
        TextView t = new TextView(activity);
        t.setText(title);
        t.setTextSize(size);
        t.setTextColor(0xffe0eee5);
        t.setMaxLines(1);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        addView(t);
        return t;
    }

    void start() {
        if (active)
            return;
        active = true;
        final int listenerToken = ++generation;
        socListener = new EvBatteryStateOfChargeListener() {
            public void onEvBatteryStateOfChargeUpdated(Integer n) {
                post(() -> {
                    if (active && listenerToken == generation) {
                        percent = validPercent(n);
                        render();
                    }
                });
            }

            public void onFailed(Exception error) {
                failure(error, listenerToken);
            }
        };
        percent = null;
        chargeState = null;
        chargeSwitch = null;
        socReadable = null;
        problem = "";
        try {
            if (vehicle == null) {
                vehicle = new Vehicle(activity);
                vehicle.initialize();
                ev = vehicle.getEvBattery();
            }
            connectCharge();
            post(tick);
        } catch (RuntimeException error) {
            problem = "차량 서비스 연결 불가";
            render();
        }
    }

    void stop() {
        if (!active)
            return;
        active = false;
        generation++;
        checking = false;
        removeCallbacks(tick);
        if (registered && ev != null)
            try {
                ev.unregisterEvBatteryStateOfCharge(socListener);
            } catch (RuntimeException ignored) {
            }
        registered = false;
        if (properties != null)
            try {
                properties.unregisterCallback(chargeListener);
            } catch (RuntimeException ignored) {
            }
        properties = null;
        if (car != null) {
            car.disconnect();
            car = null;
        }
        if (carThread != null) {
            carThread.quitSafely();
            carThread = null;
        }
    }

    void close() {
        stop();
        if (vehicle != null) {
            vehicle.release();
            vehicle = null;
            ev = null;
        }
    }

    void permissionResult() {
        if (!active)
            return;
        connectCharge();
        render();
    }

    private void connectCharge() {
        if (activity.checkSelfPermission(Car.PERMISSION_ENERGY) != PackageManager.PERMISSION_GRANTED || car != null)
            return;
        final int token = generation;
        chargeListener = new CarPropertyManager.CarPropertyEventCallback() {
            public void onChangeEvent(CarPropertyValue v) {
                if (v.getPropertyId() == VehiclePropertyIds.EV_CHARGE_STATE) {
                    Integer state =
                            v.getStatus() == CarPropertyValue.STATUS_AVAILABLE && v.getValue() instanceof Integer ?
                                    (Integer) v.getValue() : null;
                    deliverCharge(state, token);
                }
            }

            public void onErrorEvent(int id, int area) {
                deliverCharge(null, token);
            }
        };
        carThread = new HandlerThread("live-theme-charge");
        carThread.start();
        Handler handler = new Handler(carThread.getLooper());
        try {
            car = Car.createCar(activity, handler, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT, (connected, ready) -> {
                if (!active || token != generation)
                    return;
                if (!ready) {
                    deliverCharge(null, token);
                    return;
                }
                try {
                    properties = (CarPropertyManager) connected.getCarManager(Car.PROPERTY_SERVICE);
                    if (properties.getCarPropertyConfig(VehiclePropertyIds.EV_CHARGE_STATE) == null) {
                        deliverCharge(null, token);
                        return;
                    }
                    properties.registerCallback(chargeListener, VehiclePropertyIds.EV_CHARGE_STATE,
                            CarPropertyManager.SENSOR_RATE_ONCHANGE);
                    deliverCharge(properties.getIntProperty(VehiclePropertyIds.EV_CHARGE_STATE, 0), token);
                } catch (RuntimeException error) {
                    deliverCharge(null, token);
                }
            });
        } catch (RuntimeException error) {
            deliverCharge(null, token);
        }
    }

    private void deliverCharge(Integer state, int token) {
        post(() -> {
            if (active && token == generation) {
                chargeState = state;
                render();
            }
        });
    }

    // The capability answer is asked once per session; the registered listener pushes changes, so the poll is only a slow safety net.
    private void read() {
        if (!active || ev == null || checking)
            return;
        checking = true;
        final int token = generation;
        postDelayed(() -> {
            if (token == generation)
                checking = false;
        }, 2500);
        try {
            if (Boolean.TRUE.equals(socReadable))
                readLevel(token);
            else if (socReadable == null)
                ev.checkEvBatteryStateOfChargeCapabilities(config -> {
                    post(() -> {
                        if (!active || token != generation)
                            return;
                        AccessType access = config.getAccessType();
                        socReadable = access == AccessType.READ || access == AccessType.READ_WRITE;
                        if (!socReadable) {
                            percent = null;
                            problem = "이 차량은 EV 배터리 정보를 제공하지 않아요";
                            checking = false;
                            render();
                            return;
                        }
                        readLevel(token);
                    });
                    return Unit.INSTANCE;
                }, error -> {
                    failure(error, token);
                    return Unit.INSTANCE;
                });
            else
                checking = false;
            ev.getEvChargeSwitchState(on -> {
                post(() -> {
                    if (active && token == generation) {
                        chargeSwitch = on;
                        render();
                    }
                });
                return Unit.INSTANCE;
            }, error -> {
                post(() -> {
                    if (active && token == generation) {
                        chargeSwitch = null;
                        render();
                    }
                });
                return Unit.INSTANCE;
            });
        } catch (RuntimeException error) {
            failure(error, token);
        }
    }

    private void readLevel(int token) {
        ev.getEvBatteryStateOfCharge(n -> {
            post(() -> {
                if (active && token == generation) {
                    checking = false;
                    percent = validPercent(n);
                    problem = percent == null ? "배터리 정보 확인 불가" : "";
                    render();
                }
            });
            return Unit.INSTANCE;
        }, error -> {
            failure(error, token);
            return Unit.INSTANCE;
        });
        if (!registered) {
            registered = true;
            ev.registerEvBatteryStateOfCharge(socListener);
        }
    }

    private void failure(Exception error, int token) {
        post(() -> {
            if (!active || token != generation)
                return;
            checking = false;
            percent = null;
            problem = "차량 데이터 연결 확인 중";
            android.util.Log.w("LiveBattery", error.getClass().getSimpleName() + ": " + error.getMessage());
            render();
        });
    }

    /**
     * Charge level and charging state for theme data links.
     */
    java.util.function.BiConsumer<Integer, Boolean> onState;

    private void render() {
        if (onState != null)
            onState.accept(percent, chargeState == null ? null : chargeState == EvChargeState.STATE_CHARGING);
        String footer = !problem.isEmpty() ? problem :
                activity.checkSelfPermission(Car.PERMISSION_ENERGY) != PackageManager.PERMISSION_GRANTED ?
                        "눌러서 충전 상태 읽기 허용" : chargeState == null && chargeSwitch != null ?
                        (chargeSwitch ? "충전 설정 켜짐 · 실제 상태 미확인" : "충전 설정 꺼짐 · 실제 상태 미확인") : "Pleos SDK · 차량 충전 상태";
        String text = (percent == null ? "—" : percent) + "%", label = chargeLabel(chargeState), signature =
                text + label + footer;
        if (signature.equals(last))
            return;
        last = signature;
        value.setText(text);
        state.setText(label);
        source.setText(footer);
        source.setVisibility(footer.equals("Pleos SDK · 차량 충전 상태") ? GONE : VISIBLE);
        bar.setProgress(percent == null ? 0 : percent);
        bar.setAlpha(percent == null ? .25f : 1);
        changed.run();
    }

    @Override
    protected void onMeasure(int w, int h) {
        value.setTextSize(MeasureSpec.getSize(w) < dp(360) ? 38 : 48);
        super.onMeasure(w, h);
    }
}
