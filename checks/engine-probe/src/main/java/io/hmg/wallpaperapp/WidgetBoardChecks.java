package io.hmg.wallpaperapp;

import android.content.*;
import android.view.*;
import android.widget.*;

import java.util.*;

/**
 * Runs in the fixture application's private store, never the real user's layouts.
 */
public final class WidgetBoardChecks {
    private static int passed;

    private static void check(boolean yes, String name) {
        if (!yes)
            throw new AssertionError(name);
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
            SharedPreferences p = c.getSharedPreferences("widget_layouts", 0);
            p.edit().clear().commit();
            WidgetBoard b = new WidgetBoard(c, () -> {
            });
            String[] keys = {"clock", "calendar", "music", "memo", "dday", "weather"};
            for (String k : keys) {
                View content;
                if (k.equals("calendar"))
                    content = new CalendarWidget(c, () -> {
                    });
                else {
                    TextView v = new TextView(c);
                    v.setText(k);
                    v.setMinHeight(k.equals("weather") ? 180 : 140);
                    content = v;
                }
                b.addWidget(k, k, content, k.equals("clock"));
            }
            for (int[] size : new int[][]{{841, 1210}, {1682, 1210}, {2560, 1439}}) {
                layout(b, size[0], size[1]);
                String profile = b.profileKey();
                for (String k : keys)
                    check(p.contains(profile + k), profile + k + " saved");
                for (int i = 0; i < b.getChildCount(); i++) {
                    View a = b.getChildAt(i);
                    check(a.getLeft() >= 0 && a.getTop() >= 0 && a.getRight() <= size[0] && a.getBottom() <= size[1],
                            "bounds");
                    for (int j = 0; j < i; j++) {
                        View d = b.getChildAt(j);
                        check(!WidgetPlacement.overlaps(a.getLeft(), a.getTop(), a.getWidth(), a.getHeight(),
                                new float[]{d.getLeft(), d.getTop(), d.getWidth(), d.getHeight()}, 0), "no overlap");
                    }
                }
            }
            String full = p.getString("full/clock", null);
            check(full != null, "full profile selected");
            p.edit().putString("full/clock", "0.7,0.3,0.8").commit();
            b.reloadPlacement();
            layout(b, 2560, 1439);
            Map<String, ?> before = new HashMap<>(p.getAll());
            layout(b, 841, 1210);
            layout(b, 1682, 1210);
            layout(b, 2560, 1439);
            check(before.equals(p.getAll()), "resize does not overwrite manual layouts");
            b.showWidget("memo", false);
            layout(b, 2560, 1439);
            String old = p.getString("full/memo", "");
            check(b.dropWidget("memo", 1700, 700), "hidden widget can drop");
            b.showWidget("memo", true);
            layout(b, 2560, 1439);
            check("memo".equals(b.selectedKey()), "newly added widget stays selected");
            check(!old.equals(p.getString("full/memo", "")), "drop saved");
            p.edit().remove("full/weather").commit();
            Map<String, ?> imported = new HashMap<>(p.getAll());
            b.reloadPlacement();
            layout(b, 2560, 1439);
            check(p.contains("full/weather"), "partial imported layout fills missing widget");
            for (String k : imported.keySet())
                check(imported.get(k).equals(p.getAll().get(k)), "partial import keeps provided placements");
            Map<String, ?> prior = new HashMap<>(p.getAll());
            b.autoArrange();
            layout(b, 2560, 1439);
            for (String k : prior.keySet())
                if (k.startsWith("split"))
                    check(prior.get(k).equals(p.getAll().get(k)), "other profile preserved");
            b.setEditing(false);
            layout(b, 500, 600);
            check(b.canScrollVertically(1), "small viewport can scroll all widgets");
            boolean hiddenAccessible = false;
            for (int i = 1; i < b.getChildCount(); i++)
                hiddenAccessible |= b.getChildAt(i).getImportantForAccessibility() ==
                        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS;
            check(hiddenAccessible, "offscreen compact widgets cannot steal accessibility focus");
            int clockTop = b.getChildAt(0).getTop(), otherTop = b.getChildAt(1).getTop();
            Map<String, ?> scrollBefore = new HashMap<>(p.getAll());
            check(b.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,
                    null), "accessible scroll");
            layout(b, 500, 600);
            check(clockTop == b.getChildAt(0).getTop(), "clock stays fixed");
            check(b.getChildAt(1).getTop() < otherTop, "other widgets scroll");
            check(scrollBefore.equals(p.getAll()), "scroll never writes manual placements");
            for (int i = 0; i < b.getChildCount(); i++)
                check(b.getChildAt(i).getVisibility() == View.VISIBLE, "overflow widget remains available");
            b.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, null);
            layout(b, 500, 600);
            android.view.MotionEvent.PointerProperties pp = new android.view.MotionEvent.PointerProperties();
            pp.id = 0;
            pp.toolType = android.view.MotionEvent.TOOL_TYPE_MOUSE;
            android.view.MotionEvent.PointerCoords pc = new android.view.MotionEvent.PointerCoords();
            pc.setAxisValue(android.view.MotionEvent.AXIS_VSCROLL, -2);
            android.view.MotionEvent wheel = android.view.MotionEvent.obtain(0, 0,
                    android.view.MotionEvent.ACTION_SCROLL, 1, new android.view.MotionEvent.PointerProperties[]{pp},
                    new android.view.MotionEvent.PointerCoords[]{pc}, 0, 0, 1, 1, 0, 0,
                    android.view.InputDevice.SOURCE_MOUSE, 0);
            check(b.onGenericMotionEvent(wheel), "mouse wheel handled");
            wheel.recycle();
            layout(b, 500, 600);
            check(clockTop == b.getChildAt(0).getTop(), "mouse wheel also keeps clock fixed");
            Map<String, ?> dropBefore = new HashMap<>(p.getAll());
            b.showWidget("memo", false);
            layout(b, 500, 600);
            check(b.dropWidget("memo", 250, 450), "compact list accepts drop without vacant rectangle");
            b.showWidget("memo", true);
            layout(b, 500, 600);
            check(!b.isEditing(), "compact drop retains scroll reading mode");
            check(dropBefore.equals(p.getAll()), "compact drop preserves manual positions");
            layout(b, 2560, 1439);
            b.setEditing(true);
            layout(b, 2560, 1439);
            final int[] designs = {0};
            b.setDesignAction(() -> designs[0]++);
            View first = b.getChildAt(0), second = b.getChildAt(1);
            first.performAccessibilityAction(0x01000001, null);
            layout(b, 2560, 1439);
            View firstTab = ((ViewGroup) first).getChildAt(1), secondTab = ((ViewGroup) second).getChildAt(1);
            check(firstTab.getVisibility() == View.VISIBLE, "design tab on selected widget");
            check(secondTab.getVisibility() == View.GONE, "no tab on unselected widget");
            firstTab.performClick();
            check(designs[0] == 1, "design callback");
            second.performAccessibilityAction(0x01000001, null);
            layout(b, 2560, 1439);
            check(secondTab.getVisibility() == View.VISIBLE && firstTab.getVisibility() == View.GONE,
                    "tab follows edited widget");
            check(secondTab.getRight() <= second.getWidth() && secondTab.getTop() >= 0, "tab lies inside widget");
            Map<String, ?> ending = new HashMap<>(p.getAll());
            android.view.MotionEvent down = android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_DOWN,
                    8, 500, 0), up = android.view.MotionEvent.obtain(0, 20, android.view.MotionEvent.ACTION_UP, 8, 500,
                    0);
            b.onTouchEvent(down);
            b.onTouchEvent(up);
            down.recycle();
            up.recycle();
            check(!b.isEditing(), "background tap finishes placement");
            check(ending.equals(p.getAll()), "finish retains layout");
            check(secondTab.getVisibility() == View.GONE, "finished hides design tab");
            b.setEditing(true);
            down = android.view.MotionEvent.obtain(0, 0, 0, 8, 500, 0);
            android.view.MotionEvent move = android.view.MotionEvent.obtain(0, 10, 2, 100, 600, 0);
            up = android.view.MotionEvent.obtain(0, 20, 1, 100, 600, 0);
            b.onTouchEvent(down);
            b.onTouchEvent(move);
            b.onTouchEvent(up);
            down.recycle();
            move.recycle();
            up.recycle();
            check(b.isEditing(), "background swipe does not finish");
            b.setEditing(false);
            p.edit().putString("full/music", "0.4,0.3,0.8,2.2").commit();
            final int[] measures = {0, 0};
            View responsive = new View(c) {
                protected void onMeasure(int ws, int hs) {
                    measures[MeasureSpec.getMode(hs) == MeasureSpec.EXACTLY ? 1 : 0]++;
                    setMeasuredDimension(MeasureSpec.getSize(ws),
                            MeasureSpec.getMode(hs) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(hs) : 140);
                }
            };
            WidgetBoard exact = new WidgetBoard(c, () -> {
            });
            exact.addWidget("music", "music", responsive, false);
            layout(exact, 2560, 1439);
            check(measures[0] == 0, "saved box never measures responsive content at natural size");
            check(measures[1] > 0, "saved box measures content at exact size");
            check(WidgetBoard.profileFor(2560, 2560).equals("full/") &&
                    WidgetBoard.profileFor(1682, 2560).equals("split2/") &&
                    WidgetBoard.profileFor(841, 2560).equals("split3/"), "window class by width (2560x1210 is full)");
            android.util.Log.i("WidgetBoardChecks", "PASS " + passed);
        } catch (Throwable e) {
            android.util.Log.e("WidgetBoardChecks", "FAIL", e);
            throw new IllegalStateException(e);
        }
    }
}
