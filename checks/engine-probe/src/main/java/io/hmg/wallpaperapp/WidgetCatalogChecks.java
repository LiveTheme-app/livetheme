package io.hmg.wallpaperapp;

import android.content.Context;
import android.view.*;
import android.widget.*;

import java.util.*;

public final class WidgetCatalogChecks {
    private static int passed;

    private static int dp76(View v) {
        return Math.round(76 * v.getResources().getDisplayMetrics().density);
    }

    private static void check(boolean b, String name) {
        if (!b)
            throw new AssertionError(name);
        passed++;
    }

    private static ArrayList<View> all(View v) {
        ArrayList<View> out = new ArrayList<>();
        out.add(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++)
                out.addAll(all(g.getChildAt(i)));
        }
        return out;
    }

    private static long cards(View v) {
        return all(v).stream().filter(x -> x.getContentDescription() != null &&
                x.getContentDescription().toString().endsWith("위젯 끌어 추가")).count();
    }

    private static void layout(View v, int w, int h) {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, v.getMeasuredWidth(), v.getMeasuredHeight());
    }

    public static void run(Context c) {
        try {
            passed = 0;
            check(WidgetShelf.matches(0, " CLOCK ", "추가 가능", false), "trim case insensitive search");
            check(WidgetShelf.matches(1, "달력", "사용 중", true), "Korean synonym");
            check(!WidgetShelf.matches(2, "", "추가 가능", true), "category filter");
            check(!WidgetShelf.matches(2, "", "사용 중", false), "used filter");
            Set<String> enabled = new HashSet<>(Arrays.asList(WidgetShelf.KEYS));
            WidgetShelf s = new WidgetShelf(c, new WidgetShelf.Actions() {
                public boolean visible(String k) {
                    return enabled.contains(k);
                }

                public void add(String k) {
                    enabled.add(k);
                }

                public void remove(String k) {
                    enabled.remove(k);
                }

                public void drag(WidgetShelf.DragItem i, View v) {
                }

                public void close() {
                }

                public void arrange() {
                }
            });
            for (int width : new int[]{500, 809, 1180, 2560}) {
                layout(s, width, 620);
                check(s.getWidth() <= 1180 && s.getWidth() <= width, "bounded sheet width");
                check(cards(s) == WidgetShelf.KEYS.length, "all cards present");
                int cardWidth = -1, cardHeight = -1;
                for (View v : all(s))
                    if (v.getContentDescription() != null &&
                            v.getContentDescription().toString().endsWith("위젯 끌어 추가")) {
                        if (cardHeight < 0)
                            cardHeight = v.getHeight();
                        check(v.getHeight() == cardHeight && cardHeight >= dp76(v) * 3, "uniform card height");
                        if (cardWidth < 0)
                            cardWidth = v.getWidth();
                        check(cardWidth == v.getWidth(), "uniform card width");
                        ViewGroup row = (ViewGroup) v.getParent();
                        check(v.getRight() <= row.getWidth(), "card within row");
                    }
            }
            for (int i = 0; i < WidgetShelf.KEYS.length; i++)
                for (boolean state : new boolean[]{true, false})
                    check(WidgetShelf.matches(i, "", "사용 중", state) != WidgetShelf.matches(i, "", "추가 가능", state),
                            "each widget belongs to exactly one list");
            EditText search = (EditText) all(s).stream().filter(v -> v instanceof EditText).findFirst().get();
            search.setText("weather");
            check(cards(s) == 1, "search and category intersect");
            search.setText("missing");
            check(cards(s) == 0, "empty search result");
            search.setText("");
            for (View v : all(s))
                if ("메모 위젯 숨기기".equals(v.getContentDescription())) {
                    v.performClick();
                    break;
                }
            check(!enabled.contains("memo"), "remove updates in place");
            check(cards(s) == WidgetShelf.KEYS.length, "hiding keeps one catalog entry per widget");
            for (View v : all(s))
                if (v instanceof Button && ((Button) v).getText().toString().equals("사용 중")) {
                    v.performClick();
                    break;
                }
            check(cards(s) == WidgetShelf.KEYS.length - 1, "used list excludes hidden widget");
            for (View v : all(s))
                if (v instanceof Button && ((Button) v).getText().toString().equals("되돌리기")) {
                    v.performClick();
                    break;
                }
            check(enabled.contains("memo") && cards(s) == WidgetShelf.KEYS.length, "undo restores widget in place");
            android.util.Log.i("WidgetCatalogChecks", "PASS " + passed);
        } catch (Throwable e) {
            android.util.Log.e("WidgetCatalogChecks", "FAIL", e);
            throw new IllegalStateException(e);
        }
    }
}
