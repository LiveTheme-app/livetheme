package io.hmg.wallpaperapp;

import android.app.Activity;
import android.content.SharedPreferences;
import android.view.*;
import android.widget.*;

import java.util.*;

/**
 * Real gesture dispatch in fixture storage; no real app data or vehicle calls.
 */
final class WidgetRemovalChecks {
    static int passed;

    static void check(boolean ok, String name) {
        if (!ok)
            throw new AssertionError(name);
        passed++;
        android.util.Log.i("WidgetRemovalChecks", "PASS " + name);
    }

    static void touch(View tile, int action, float rawX, float rawY) {
        MotionEvent e = MotionEvent.obtain(1, 1000 + action * 30, action, rawX, rawY, 0);
        e.offsetLocation(-tile.getLeft(), -tile.getTop());
        tile.dispatchTouchEvent(e);
        e.recycle();
    }

    static void run(Activity a) {
        SharedPreferences store = a.getSharedPreferences("widget_layouts", 0);
        for (int[] size : new int[][]{{841, 1210}, {1682, 1210}, {2560, 1439}}) {
            int w = size[0], h = size[1];
            check(WidgetBoard.removalHit(w, h, 1, w / 2f, h - 60), "center arms " + w);
            check(!WidgetBoard.removalHit(w, h, 1, 10, h - 60), "bottom corner cannot remove " + w);
            check(!WidgetBoard.removalHit(w, h, 1, w / 2f, h - 140), "above zone cannot remove " + w);
            check(!WidgetBoard.removalHit(w, h, 1, Float.NaN, h - 60), "nonfinite pointer rejected " + w);
            store.edit().clear().commit();
            WidgetBoard b = new WidgetBoard(a, () -> {
            });
            for (String key : new String[]{"clock", "memo"}) {
                TextView v = new TextView(a);
                v.setText(key);
                v.setMinHeight(180);
                b.addWidget(key, key, v, key.equals("clock"));
            }
            VehicleChecks.layout(b, w, h);
            b.selectWidget("memo");
            VehicleChecks.layout(b, w, h);
            int[] removed = {0};
            boolean[] feedback = {false, false};
            b.setRemovalListener(new WidgetBoard.RemovalListener() {
                public void dragging(boolean active, boolean armed) {
                    feedback[0] = active;
                    feedback[1] = armed;
                }

                public void remove(String key) {
                    check(key.equals("memo"), "removes selected widget " + w);
                    removed[0]++;
                    b.showWidget(key, false);
                }
            });
            View tile = b.getChildAt(1);
            float x = tile.getLeft() + tile.getWidth() / 2f, y = tile.getTop() + tile.getHeight() / 2f;
            Map<String, ?> original = new HashMap<>(store.getAll());
            touch(tile, 0, x, y);
            touch(tile, 2, w / 2f, h - 60);
            check(feedback[0] && feedback[1] && removed[0] == 0, "hover previews without removal " + w);
            check(original.equals(store.getAll()), "drag does not save temporary trash position " + w);
            touch(tile, 3, w / 2f, h - 60);
            VehicleChecks.layout(b, w, h);
            check(!feedback[0] && removed[0] == 0 && original.equals(store.getAll()), "cancel preserves layout " + w);
            touch(tile, 0, x, y);
            touch(tile, 2, w / 2f, h - 60);
            touch(tile, 2, 20, h - 60);
            check(!feedback[1], "leaving trash disarms " + w);
            touch(tile, 3, 20, h - 60);
            VehicleChecks.layout(b, w, h);
            check(removed[0] == 0, "leaving trash never removes " + w);
            touch(tile, 0, x, y);
            touch(tile, 2, w / 2f, h - 60);
            touch(tile, 1, w / 2f, h - 60);
            check(removed[0] == 0, "removal waits for collapse animation " + w);
            b.setEditing(false);
            check(removed[0] == 1, "released removal commits on interruption " + w);
            b.setEditing(false);
            check(removed[0] == 1, "remove callback exactly once " + w);
            check(original.equals(store.getAll()), "all saved sizes and positions retained " + w);
            b.showWidget("memo", true);
            VehicleChecks.layout(b, w, h);
            check(tile.getVisibility() == View.VISIBLE && tile.getAlpha() >= 0 && tile.getScaleX() > .9f,
                    "restore resets collapsed widget " + w);
            b.selectWidget("memo");
            VehicleChecks.layout(b, w, h);
            touch(tile, 0, tile.getRight() - 8, tile.getBottom() - 8);
            touch(tile, 2, w / 2f, h - 60);
            touch(tile, 1, w / 2f, h - 60);
            check(removed[0] == 1 && !feedback[0], "resize never activates removal " + w);
            check(tile.performAccessibilityAction(0x0100000c, null) && removed[0] == 2,
                    "accessible removal action " + w);
        }
        android.util.Log.i("WidgetRemovalChecks", "DONE " + passed);
    }
}
