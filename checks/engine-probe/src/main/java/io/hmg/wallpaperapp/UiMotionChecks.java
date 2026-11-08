package io.hmg.wallpaperapp;

import android.app.Activity;
import android.view.*;
import android.widget.*;

/**
 * Exercises retargeting and detach cleanup on real choreographer frames.
 */
public final class UiMotionChecks {
    private static int passed;

    private static void check(boolean ok, String message) {
        if (!ok)
            throw new AssertionError(message);
        passed++;
    }

    public static void run(Activity a) {
        passed = 0;
        FrameLayout host = new FrameLayout(a);
        a.setContentView(host);
        View tile = new View(a);
        host.addView(tile, new FrameLayout.LayoutParams(100, 100));
        Button button = AppUi.button(a, "테마 적용", true, () -> {
        });
        check(button.getMinHeight() >= AppUi.dp(a, 48), "minimum touch height");
        host.post(() -> {
            UiMotion.enter(tile, 80, .96f);
            host.postDelayed(() -> {
                check(tile.getTranslationY() > 0 && tile.getTranslationY() < 80, "spring advances between frames");
                final boolean[] removed = {false};
                UiMotion.exit(tile, 120, () -> removed[0] = true);
                float current = tile.getTranslationY();
                UiMotion.settle(tile);
                check(Math.abs(tile.getTranslationY() - current) < .01f, "retarget does not reset position");
                host.postDelayed(() -> {
                    check(!removed[0], "retarget cancels obsolete exit action");
                    check(Math.abs(tile.getTranslationY()) < .6f, "spring reaches target");
                    check(Math.abs(tile.getScaleX() - 1) < .01f, "scale settles");
                    check(tile.getAlpha() > .99f, "opacity settles");
                    UiMotion.exit(tile, 90, () -> {
                        removed[0] = true;
                        host.removeView(tile);
                    });
                    host.postDelayed(() -> {
                        check(removed[0] && tile.getParent() == null, "exit completion removes view");
                        View detached = new View(a);
                        host.addView(detached, new FrameLayout.LayoutParams(100, 100));
                        UiMotion.enter(detached, 90, 1);
                        host.removeView(detached);
                        float atDetach = detached.getTranslationY();
                        host.postDelayed(() -> {
                            check(detached.getTranslationY() == atDetach, "detached spring stops");
                            dragChecks(a, host);
                        }, 150);
                    }, 700);
                }, 700);
            }, 80);
        });
    }

    private static void dragChecks(Activity a, FrameLayout host) {
        View v = new View(a);
        host.addView(v, new FrameLayout.LayoutParams(100, 100));
        host.post(() -> {
            UiMotion.follow(v, 80, 40, 1.035f);
            host.postDelayed(() -> {
                check(v.getTranslationX() > 0 && v.getScaleX() > 1, "drag has spring lift");
                float before = v.getTranslationX();
                UiMotion.follow(v, -30, 0, .88f);
                check(v.getTranslationX() == before, "drag retarget preserves position");
                host.postDelayed(() -> {
                    check(Math.abs(v.getTranslationX() + 30) < .7f && Math.abs(v.getScaleX() - .88f) < .01f,
                            "drag and hover settle");
                    float visual = v.getLeft() + v.getTranslationX();
                    v.layout(40, 0, 140, 100);
                    UiMotion.release(v, visual - 40, v.getTranslationY());
                    check(Math.abs(v.getLeft() + v.getTranslationX() - visual) < .01f,
                            "release rebase preserves visual position");
                    host.postDelayed(() -> {
                        check(Math.abs(v.getTranslationX()) < .7f && Math.abs(v.getScaleX() - 1) < .01f,
                                "release returns to resting transform");
                        int[] discarded = {0};
                        UiMotion.discard(v, 80, 80, () -> discarded[0]++);
                        host.postDelayed(() -> {
                            check(discarded[0] == 1, "discard completion once");
                            check(v.getAlpha() < .01f && Math.abs(v.getScaleX() - .12f) < .01f,
                                    "discard shrinks and fades");
                            longPressChecks(a, host);
                        }, 750);
                    }, 750);
                }, 750);
            }, 80);
        });
    }

    private static void longPressChecks(Activity a, FrameLayout host) {
        host.removeAllViews();
        a.getSharedPreferences("widget_layouts", 0).edit().clear().commit();
        WidgetBoard board = new WidgetBoard(a, () -> {
        });
        TextView memo = new TextView(a);
        memo.setMinHeight(180);
        int[] clicks = {0}, removed = {0};
        boolean[] armed = {false};
        memo.setOnClickListener(v -> clicks[0]++);
        board.addWidget("memo", "memo", memo, false);
        board.setRemovalListener(new WidgetBoard.RemovalListener() {
            public void dragging(boolean active, boolean over) {
                armed[0] = active && over;
            }

            public void remove(String key) {
                removed[0]++;
                board.showWidget(key, false);
            }
        });
        host.addView(board, new FrameLayout.LayoutParams(-1, -1));
        host.post(() -> {
            View tile = board.getChildAt(0);
            long down = android.os.SystemClock.uptimeMillis();
            send(board, 0, tile.getLeft() + tile.getWidth() / 2f, tile.getTop() + tile.getHeight() / 2f, down);
            host.postDelayed(() -> {
                check(board.isEditing(), "long press begins placement");
                send(board, 2, board.getWidth() / 2f, board.getHeight() - 66, down);
                check(armed[0], "first move reaches trash without a lost touch event");
                send(board, 1, board.getWidth() / 2f, board.getHeight() - 66, down);
                host.postDelayed(() -> {
                    check(removed[0] == 1, "first release removes once");
                    check(clicks[0] == 0, "long press cancels underlying widget click");
                    android.util.Log.i("UiMotionChecks", "PASS " + passed);
                    a.finish();
                }, 750);
            }, android.view.ViewConfiguration.getLongPressTimeout() + 120);
        });
    }

    private static void send(WidgetBoard board, int action, float x, float y, long down) {
        int[] location = new int[2];
        board.getLocationOnScreen(location);
        MotionEvent event = MotionEvent.obtain(down, android.os.SystemClock.uptimeMillis(), action, x + location[0],
                y + location[1], 0);
        event.offsetLocation(-location[0], -location[1]);
        board.dispatchTouchEvent(event);
        event.recycle();
    }

}
