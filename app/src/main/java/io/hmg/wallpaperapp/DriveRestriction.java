package io.hmg.wallpaperapp;

import android.car.Car;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.content.Context;
import android.util.Log;

/**
 * Driving restriction from the car UX restriction service. The Vehicle SDK exposes no speed or gear, so this is the only signal.
 */
final class DriveRestriction {
    interface Listener {
        void changed(boolean restricted);
    }

    private final Context context;
    private final Listener listener;
    private Car car;
    private CarUxRestrictionsManager manager;
    private boolean system;
    private Boolean forced;
    private boolean restricted;

    DriveRestriction(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    boolean restricted() {
        return restricted;
    }

    void start() {
        if (car != null)
            return;
        try {
            car = Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT, (connected, ready) -> {
                if (!ready) {
                    manager = null;
                    system = false;
                    publish();
                    return;
                }
                try {
                    manager = (CarUxRestrictionsManager) connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE);
                    if (manager == null)
                        return;
                    manager.registerListener(this::accept);
                    accept(manager.getCurrentCarUxRestrictions());
                } catch (RuntimeException error) {
                    Log.w("LiveDrive", "UX restrictions unavailable: " + error);
                }
            });
        } catch (RuntimeException error) {
            Log.w("LiveDrive", "Car service unavailable: " + error);
        }
    }

    void stop() {
        if (manager != null)
            try {
                manager.unregisterListener();
            } catch (RuntimeException ignored) {
            }
        manager = null;
        if (car != null) {
            car.disconnect();
            car = null;
        }
    }

    private void accept(CarUxRestrictions now) {
        system = now != null && now.isRequiresDistractionOptimization();
        publish();
    }

    /**
     * Debug builds only: lets the emulator, whose user build refuses VHAL injection, exercise the driving path. null follows the car.
     */
    void force(Boolean value) {
        if (BuildConfig.DEBUG) {
            forced = value;
            publish();
        }
    }

    private void publish() {
        boolean next = forced != null ? forced : system;
        if (next == restricted)
            return;
        restricted = next;
        Log.i("LiveDrive", "restricted=" + next + (forced != null ? " (forced)" : ""));
        listener.changed(next);
    }
}
