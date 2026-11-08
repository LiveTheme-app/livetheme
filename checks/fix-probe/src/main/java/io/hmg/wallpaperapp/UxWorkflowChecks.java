package io.hmg.wallpaperapp;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import java.lang.reflect.*;
import java.util.*;

/**
 * Isolated storage and simulated callbacks; never sends vehicle commands.
 */
final class UxWorkflowChecks {
    static int passed;

    static void check(boolean ok, String name) {
        if (!ok)
            throw new AssertionError(name);
        passed++;
        android.util.Log.i("UxWorkflowChecks", "PASS " + name);
    }

    static ArrayList<View> all(View v) {
        ArrayList<View> out = new ArrayList<>();
        out.add(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++)
                out.addAll(all(g.getChildAt(i)));
        }
        return out;
    }

    static View text(View root, String value) {
        for (View v : all(root))
            if (v instanceof TextView && value.contentEquals(((TextView) v).getText()))
                return v;
        throw new AssertionError(value);
    }

    static View desc(View root, String value) {
        for (View v : all(root))
            if (value.contentEquals(v.getContentDescription() == null ? "" : v.getContentDescription()))
                return v;
        throw new AssertionError(value);
    }

    static long cards(View root) {
        return all(root).stream().filter(v -> v.getContentDescription() != null &&
                v.getContentDescription().toString().endsWith("위젯 끌어 추가")).count();
    }

    static void run(Activity a) throws Exception {
        Set<String> enabled = new HashSet<>(Arrays.asList("clock", "memo"));
        int[] restores = {0}, adds = {0};
        WidgetShelf shelf = new WidgetShelf(a, new WidgetShelf.Actions() {
            public boolean visible(String k) {
                return enabled.contains(k);
            }

            public void add(String k) {
                adds[0]++;
            }

            public void remove(String k) {
                enabled.remove(k);
            }

            public void restore(String k) {
                enabled.add(k);
                restores[0]++;
            }

            public void drag(WidgetShelf.DragItem d, View v) {
            }

            public void close() {
            }

            public void arrange() {
            }
        });
        for (int w : new int[]{500, 841, 1180, 2560}) {
            VehicleChecks.layout(shelf, w, 900);
            check(cards(shelf) == 10, "all ten unique cards " + w);
            for (View v : all(shelf))
                if (v.getContentDescription() != null && v.getContentDescription().toString().endsWith("위젯 끌어 추가"))
                    check(v.getRight() <= ((View) v.getParent()).getWidth(), "catalog card stays within width " + w);
        }
        text(shelf, "차량").performClick();
        check(cards(shelf) == 4, "four vehicle cards without duplicate tags");
        text(shelf, "시간").performClick();
        check(cards(shelf) == 3, "three time cards");
        text(shelf, "음악").performClick();
        check(cards(shelf) == 1, "one music card");
        text(shelf, "기록").performClick();
        check(cards(shelf) == 2, "two record cards");
        check(WidgetShelf.matches(3, "", "일상", false) && !WidgetShelf.matches(6, "", "일상", false),
                "legacy 일상 filter still works");
        text(shelf, "사용 중").performClick();
        check(cards(shelf) == 2, "used filter only active widgets");
        text(shelf, "전체").performClick();
        desc(shelf, "메모 위젯 숨기기").performClick();
        check(!enabled.contains("memo") && cards(shelf) == 10, "hide preserves catalog discovery");
        text(shelf, "되돌리기").performClick();
        check(enabled.contains("memo") && restores[0] == 1 && adds[0] == 0,
                "undo restores without opening design flow");
        desc(shelf, "메모 위젯 편집").performClick();
        check(adds[0] == 1, "used primary action opens edit");
        EditText search = (EditText) all(shelf).stream().filter(v -> v instanceof EditText).findFirst().get();
        search.setText("배터리");
        check(cards(shelf) == 1, "search finds one matching widget");
        search.setText("no-such-widget");
        check(cards(shelf) == 0, "empty search handled");
        SharedPreferences layouts = a.getSharedPreferences("widget_layouts", 0);
        layouts.edit().clear().commit();
        WidgetBoard board = new WidgetBoard(a, () -> {
        });
        board.addWidget("clock", "시계", new TextView(a), true);
        board.addWidget("memo", "메모", new TextView(a), false);
        VehicleChecks.layout(board, 2560, 1439);
        board.selectWidget("memo");
        VehicleChecks.layout(board, 2560, 1439);
        Map<String, ?> before = new HashMap<>(layouts.getAll());
        board.resizeSelected(.85f);
        VehicleChecks.layout(board, 2560, 1439);
        check(Objects.equals(before.get("full/clock"), layouts.getString("full/clock", "")),
                "resize only selected widget");
        Map<String, ?> sized = new HashMap<>(layouts.getAll());
        board.resizeSelected(Float.NaN);
        check(sized.equals(layouts.getAll()), "invalid size never corrupts layout");
        VehicleWidgets host = new VehicleWidgets(a, () -> {
        });
        VehicleChecks.set(host, "theme", CreatorTheme.defaults());
        VehicleChecks.set(host, "active", true);
        VehicleWidgets.Card card = host.cards.get("ambient");
        card.visible = true;
        Object metric = card.fields.get(1);
        VehicleChecks.set(metric, "value", 22d);
        VehicleChecks.set(metric, "readable", true);
        VehicleChecks.set(metric, "writable", true);
        VehicleChecks.set(metric, "min", 16d);
        VehicleChecks.set(metric, "max", 30d);
        VehicleChecks.set(metric, "state", "조회 완료");
        Method open = VehicleWidgets.class.getDeclaredMethod("showControls", VehicleWidgets.Card.class);
        open.setAccessible(true);
        open.invoke(host, card);
        AlertDialog dialog = (AlertDialog) VehicleChecks.field(host, "controls").get(host);
        View root = dialog.getWindow().getDecorView();
        boolean noApplyButton;
        try {
            desc(root, "실내 무드등 밝기 적용");
            noApplyButton = false;
        } catch (AssertionError expected) {
            noApplyButton = true;
        }
        check(noApplyButton && !((Boolean) VehicleChecks.field(metric, "busy").get(metric)),
                "opening controls sends nothing; values apply on release");
        VehicleChecks.set(metric, "value", 25d);
        VehicleChecks.set(metric, "state", "새 값 수신");
        card.render();
        check(((TextView) desc(root, "실내 무드등 밝기 현재값")).getText().toString().contains("25"),
                "open dialog updates actual value");
        check(text(root, "새 값 수신") != null, "open dialog updates state");
        text(root, "다시 확인").performClick();
        check(dialog.isShowing(), "refresh stays in controls");
        host.stop();
        check(!dialog.isShowing(), "background closes controls");
        NowPlayingMonitor music = new NowPlayingMonitor(a, (allowed, title, artist, art, playing, previous, next) -> {
        });
        check(!music.openPlayer(), "missing music session offers connection fallback");
        int[] saved = {0};
        AlertDialog form = AppUi.form(a, "test", "test", AppUi.input(a, "original", "hint", 2, 280), "저장",
                () -> saved[0]++);
        desc(form.getWindow().getDecorView(), "닫기").performClick();
        check(saved[0] == 0, "cancel never commits form");
        form = AppUi.form(a, "test", "test", AppUi.input(a, "original", "hint", 2, 280), "저장", () -> saved[0]++);
        text(form.getWindow().getDecorView(), "저장").performClick();
        check(saved[0] == 1, "explicit save commits once");
        android.util.Log.i("UxWorkflowChecks", "DONE " + passed);
    }
}
