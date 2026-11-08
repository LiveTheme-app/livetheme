package io.hmg.wallpaperapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import java.util.ArrayList;

/**
 * In-app widget placement; coordinates are relative to the remaining usable space.
 */
final class WidgetBoard extends FrameLayout {
    private final SharedPreferences store;
    private final ArrayList<Tile> tiles = new ArrayList<>();
    private Tile selected, held, pendingRemoval;
    private final int[] screenLocation = new int[2];

    interface RemovalListener {
        void dragging(boolean active, boolean armed);

        void remove(String key);
    }

    private RemovalListener removal;

    void setRemovalListener(RemovalListener listener) {
        removal = listener;
    }

    static boolean removalHit(float width, float height, float density, float x, float y) {
        float half = Math.min(164 * density, (width - 32 * density) / 2);
        return density > 0 && half > 0 && Float.isFinite(x) && Float.isFinite(y) && x >= width / 2 - half &&
                x <= width / 2 + half && y >= height - 112 * density && y <= height - 20 * density;
    }

    private void feedback(boolean active, boolean armed) {
        if (removal != null)
            removal.dragging(active, armed);
    }

    private void commitRemoval() {
        Tile tile = pendingRemoval;
        if (tile == null)
            return;
        pendingRemoval = null;
        tile.discarding = false;
        tile.resetMotion();
        feedback(false, false);
        if (removal != null)
            removal.remove(tile.key);
    }

    private void cancelHold() {
        if (held != null) {
            Tile tile = held;
            held = null;
            tile.holding = false;
            tile.dragging = false;
            tile.armed = false;
            tile.releasing = true;
            clearGuides();
            feedback(false, false);
            requestLayout();
        }
    }

    private final Runnable editingChanged;
    private boolean editing;
    private Runnable designRequested = () -> {
    };
    private float backgroundX, backgroundY;
    private boolean backgroundMoved;

    void setDesignAction(Runnable action) {
        designRequested = action;
    }

    private void updateDesignTabs() {
        for (Tile tile : tiles)
            tile.design.setVisibility(editing && tile == selected && tile.requested ? VISIBLE : GONE);
    }

    private String profile = "";
    private boolean profileChanged, autoRequested, arrangePending;
    private String pendingKey;
    private float pendingLeft, pendingTop;
    private RectF dropPreview;
    private String appearKey;
    private boolean compactStack, scrollGesture, scrollingTouch, compactReflow;
    private float compactTop, compactOffset, compactRange;
    private final GestureDetector scrollDetector;
    private final android.widget.OverScroller scroller;
    private float guideX = Float.NaN, guideY = Float.NaN;
    private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private void clearGuides() {
        guideX = guideY = Float.NaN;
        invalidate();
    }

    private float[] targets(Tile moving, boolean horizontal, boolean sizes) {
        float[] values = new float[tiles.size() * 6 + 4];
        int count = 0;
        if (sizes) {
            values[count++] = horizontal ? moving.getHeight() : moving.getWidth();
            values[count++] = dp(horizontal ? 480 : 218);
            if (!horizontal && moving.calendar) {
                values[count++] = dp(480);
            }
        } else {
            values[count++] = horizontal ? dp(16) : top();
            values[count++] = horizontal ? getWidth() * .5f : (top() + bottom()) * .5f;
            values[count++] = horizontal ? getWidth() - dp(16) : bottom();
        }
        for (Tile tile : tiles)
            if (tile != moving && tile.getVisibility() != GONE) {
                if (sizes) {
                    values[count++] = horizontal ? tile.getWidth() : tile.getHeight();
                    continue;
                }
                float start = horizontal ? tile.getLeft() : tile.getTop(), end =
                        horizontal ? tile.getRight() : tile.getBottom();
                values[count++] = start;
                values[count++] = (start + end) * .5f;
                values[count++] = end;
                values[count++] = start - dp(16);
                values[count++] = end + dp(16);
            }
        return java.util.Arrays.copyOf(values, count);
    }

    private float snapSize(Tile tile, float size, boolean horizontal) {
        float[] values = targets(tile, horizontal, true);
        float[] match = SnapMath.align(size, 0, values, values.length, dp(12));
        if (horizontal)
            guideX = Float.isNaN(match[1]) ? Float.NaN : tile.getLeft() + match[0];
        else
            guideY = Float.isNaN(match[1]) ? Float.NaN : tile.getTop() + match[0];
        return match[0];
    }

    private void snapMove(Tile tile, float left, float upper) {
        float width = tile.plannedWidth(), height = tile.plannedHeight();
        left = fit(left, dp(16), getWidth() - dp(16) - width);
        upper = fit(upper, top(), bottom() - height);
        float[] tx = targets(tile, true, false), ty = targets(tile, false, false);
        float[] x = SnapMath.align(left, width, tx, tx.length, dp(12)), y = SnapMath.align(upper, height, ty, ty.length,
                dp(12));
        guideX = x[1];
        guideY = y[1];
        tile.dragLeft = x[0];
        tile.dragTop = y[0];
        UiMotion.follow(tile, x[0] - tile.getLeft(), y[0] - tile.getTop(), tile.armed ? .88f : 1.035f);
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (compactStack && compactRange > 0) {
            float height = bottom() - compactTop, thumb = Math.max(dp(30),
                    height * height / (height + compactRange)), y =
                    compactTop + (height - thumb) * compactOffset / compactRange;
            guidePaint.setColor(0xaaa8ded4);
            canvas.drawRoundRect(getWidth() - dp(7), y, getWidth() - dp(3), y + thumb, dp(2), dp(2), guidePaint);
        }
        if (dropPreview != null) {
            guidePaint.setColor(0x5524bdaa);
            canvas.drawRoundRect(dropPreview, dp(18), dp(18), guidePaint);
            guidePaint.setColor(0xffa8ded4);
            guidePaint.setStyle(Paint.Style.STROKE);
            guidePaint.setStrokeWidth(dp(2));
            canvas.drawRoundRect(dropPreview, dp(18), dp(18), guidePaint);
            guidePaint.setStyle(Paint.Style.FILL);
        }
        if (!editing)
            return;
        guidePaint.setStrokeWidth(dp(1));
        guidePaint.setColor(0x348ed4c8);
        canvas.drawLine(getWidth() * .5f, top(), getWidth() * .5f, bottom(), guidePaint);
        canvas.drawLine(dp(16), (top() + bottom()) * .5f, getWidth() - dp(16), (top() + bottom()) * .5f, guidePaint);
        guidePaint.setColor(0xffa8ded4);
        guidePaint.setStrokeWidth(dp(2));
        if (!Float.isNaN(guideX))
            canvas.drawLine(guideX, top(), guideX, bottom(), guidePaint);
        if (!Float.isNaN(guideY))
            canvas.drawLine(dp(16), guideY, getWidth() - dp(16), guideY, guidePaint);
    }

    WidgetBoard(Context context, Runnable editingChanged) {
        super(context);
        this.editingChanged = editingChanged;
        store = context.getSharedPreferences("widget_layouts", Context.MODE_PRIVATE);
        setClipChildren(true);
        scroller = new android.widget.OverScroller(context);
        scrollDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                scroller.abortAnimation();
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent a, MotionEvent b, float dx, float dy) {
                if (!compactStack || editing || Math.abs(dx) > Math.abs(dy) && !scrollingTouch)
                    return false;
                scrollingTouch = true;
                cancelLongPress();
                if (getParent() != null)
                    getParent().requestDisallowInterceptTouchEvent(true);
                scrollCompact(dy);
                return true;
            }

            @Override
            public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (!scrollingTouch || editing)
                    return false;
                scroller.fling(0, Math.round(compactOffset), 0, Math.round(-vy), 0, 0, 0, Math.round(compactRange));
                postInvalidateOnAnimation();
                return true;
            }
        });
        scrollDetector.setIsLongpressEnabled(false);
    }

    private void scrollCompact(float by) {
        compactOffset = fit(compactOffset + by, 0, compactRange);
        requestLayout();
        invalidate();
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset() && compactStack && !editing) {
            compactOffset = scroller.getCurrY();
            requestLayout();
            postInvalidateOnAnimation();
        }
    }

    @Override
    public boolean canScrollVertically(int direction) {
        return compactStack && compactRange > 0 && (direction < 0 ? compactOffset > 0 : compactOffset < compactRange);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            scrollGesture = compactStack && !editing && e.getY() >= compactTop && e.getY() <= bottom();
            scrollingTouch = false;
        }
        if (scrollGesture)
            scrollDetector.onTouchEvent(e);
        boolean handled = super.dispatchTouchEvent(e);
        if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            scrollGesture = false;
            scrollingTouch = false;
        }
        return handled;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        return scrollingTouch || super.onInterceptTouchEvent(e);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (editing) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    backgroundX = e.getX();
                    backgroundY = e.getY();
                    backgroundMoved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    backgroundMoved |= Math.hypot(e.getX() - backgroundX, e.getY() - backgroundY) >
                            ViewConfiguration.get(getContext()).getScaledTouchSlop();
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!backgroundMoved) {
                        performClick();
                        setEditing(false);
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    return true;
            }
        }
        return scrollingTouch || super.onTouchEvent(e);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent e) {
        float wheel = e.getAxisValue(MotionEvent.AXIS_VSCROLL);
        if (compactStack && !editing && e.getActionMasked() == MotionEvent.ACTION_SCROLL && wheel != 0) {
            scroller.abortAnimation();
            scrollCompact(-wheel * dp(48));
            return true;
        }
        return super.onGenericMotionEvent(e);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long time) {
        if (compactStack && child instanceof Tile && !((Tile) child).clock) {
            int save = canvas.save();
            canvas.clipRect(0, compactTop, getWidth(), bottom());
            boolean drawn = super.drawChild(canvas, child, time);
            canvas.restoreToCount(save);
            return drawn;
        }
        return super.drawChild(canvas, child, time);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (compactStack && compactRange > 0) {
            info.setScrollable(true);
            if (canScrollVertically(1))
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
            if (canScrollVertically(-1))
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        }
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle args) {
        if (compactStack && (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ||
                action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
            scrollCompact(
                    (bottom() - compactTop) * .8f * (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? 1 : -1));
            return true;
        }
        return super.performAccessibilityAction(action, args);
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    boolean isEditing() {
        return editing;
    }

    boolean compactReading() {
        return compactStack;
    }

    String profileName() {
        return profile.equals("full/") ? "전체화면" : profile.equals("split2/") ? "2분할" : "3분할";
    }

    private float defaultScale(Tile tile) {
        return profile.equals("split3/") ? (tile.clock ? .95f : 1.1f) :
                profile.equals("full/") && tile.clock ? 1.25f : 1f;
    }

    /**
     * While driving only the glanceable values stay: captions (13sp and smaller, e.g. hourly rain, "tap to adjust") fade out and return when parked. Alpha keeps every layout in place.
     */
    private final java.util.Map<android.widget.TextView, Float> drivingHidden = new java.util.HashMap<>();
    /**
     * Driving: every widget stays where the user put it (1.35); editing is closed and long-press editing is off. Widget taps that open settings are refused by the dialog lock (AppUi.refuse).
     */
    private boolean driving;

    boolean isDriving() {
        return driving;
    }

    void setDriving(boolean driving) {
        this.driving = driving;
        if (driving && editing)
            setEditing(false);
        if (driving) {
            for (Tile t : tiles)
                hideCaptions(t.content);
            return;
        }
        for (java.util.Map.Entry<android.widget.TextView, Float> e : drivingHidden.entrySet())
            e.getKey().setAlpha(e.getValue());
        drivingHidden.clear();
    }

    private void hideCaptions(View v) {
        if (v instanceof android.widget.TextView && !(v instanceof android.widget.Button) &&
                !"calendar-day".equals(v.getTag())) {
            android.widget.TextView t = (android.widget.TextView) v;
            if (t.getTextSize() / t.getResources().getDisplayMetrics().scaledDensity <= 13.5f &&
                    !drivingHidden.containsKey(t)) {
                drivingHidden.put(t, t.getAlpha());
                t.setAlpha(0);
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++)
                hideCaptions(g.getChildAt(i));
        }
    }

    void setEditing(boolean value) {
        if (!value) {
            cancelHold();
            commitRemoval();
        }
        editing = value;
        if (value && (selected == null || !selected.requested)) {
            selected = null;
            for (Tile t : tiles)
                if (t.requested) {
                    selected = t;
                    break;
                }
        }
        scroller.abortAnimation();
        clearGuides();
        if (value && !compactStack)
            pinDefaults();
        for (Tile tile : tiles) {
            tile.setContentDescription(value ? tile.name + " 위치와 크기 조절" : null);
            tile.invalidate();
        }
        updateDesignTabs();
        requestLayout();
        editingChanged.run();
    }

    private void pinDefaults() {
        for (Tile tile : tiles)
            if (tile.getVisibility() != GONE && !tile.saved) {
                tile.move(tile.getLeft(), tile.getTop());
                tile.remember();
            }
    }

    void addWidget(String key, String name, View content, boolean clock) {
        Tile tile = new Tile(key, name, content, clock);
        tiles.add(tile);
        addView(tile);
    }

    void showWidget(String key, boolean visible) {
        for (Tile tile : tiles)
            if (tile.key.equals(key)) {
                boolean changed = tile.requested != visible;
                if (changed) {
                    compactReflow = true;
                    if (visible)
                        appearKey = key;
                }
                if (visible && changed)
                    arrangePending = true;
                tile.requested = visible;
                if (!visible)
                    tile.overflow = false;
                tile.setVisibility(visible && !tile.overflow ? VISIBLE : GONE);
                if (changed)
                    updateDesignTabs();
            }
    }

    void reloadPlacement() {
        arrangePending = true;
        for (Tile tile : tiles) {
            tile.overflow = false;
            tile.setVisibility(tile.requested ? VISIBLE : GONE);
            tile.load();
        }
        requestLayout();
    }

    void resetPlacement() {
        SharedPreferences.Editor e = store.edit();
        for (Tile t : tiles)
            e.remove(profile + t.key);
        e.apply();
        autoRequested = false;
        arrangePending = true;
        for (Tile t : tiles) {
            t.overflow = false;
            t.setVisibility(t.requested ? VISIBLE : GONE);
            t.load();
        }
        requestLayout();
    }

    void autoArrange() {
        if (compactStack) {
            scroller.abortAnimation();
            compactOffset = 0;
            requestLayout();
            return;
        }
        autoRequested = true;
        arrangePending = true;
        for (Tile tile : tiles) {
            tile.overflow = false;
            tile.setVisibility(tile.requested ? VISIBLE : GONE);
        }
        requestLayout();
    }

    String selectedKey() {
        return selected == null ? null : selected.key;
    }

    String selectedName() {
        return selected == null ? "위젯" : selected.name;
    }

    void selectWidget(String key) {
        for (Tile tile : tiles)
            if (tile.key.equals(key) && tile.requested) {
                setEditing(true);
                selected = tile;
                updateDesignTabs();
                editingChanged.run();
                requestLayout();
                return;
            }
    }

    void resizeSelected(float factor) {
        if (selected == null || !selected.requested || !Float.isFinite(factor) || factor <= 0)
            return;
        Tile t = selected;
        float scale = t.scale, height = t.heightScale;
        boolean free = t.freeSize;
        t.sizeTo(t.getWidth() * factor, t.getHeight() * factor);
        keepClear(t, scale, height, free);
    }

    /**
     * Menu resizes grow in place only when nothing is covered; otherwise the widget moves to the nearest free spot, or the old size is kept.
     */
    private void keepClear(Tile t, float scale, float heightScale, boolean freeSize) {
        float w = t.plannedWidth(), h = t.plannedHeight(), width = getMeasuredWidth() - dp(32), height =
                bottom() - top();
        float x = fit(t.getLeft() - dp(16), 0, Math.max(0, width - w)), y = fit(t.getTop() - top(), 0,
                Math.max(0, height - h));
        java.util.List<float[]> others = occupied(t);
        boolean clear = true;
        for (float[] r : others)
            if (WidgetPlacement.overlaps(x, y, w, h, r, 0)) {
                clear = false;
                break;
            }
        float[] at = clear ? new float[]{x, y} : WidgetPlacement.vacant(width, height, w, h, x, y, others, dp(12));
        if (at == null) {
            t.scale = scale;
            t.heightScale = heightScale;
            t.freeSize = freeSize;
            requestLayout();// A dialog, not a toast: toasts from this multi-window app are not drawn on the Pleos emulator.
            AppUi.dialog(getContext()).setTitle("크기를 그대로 두었어요")
                    .setMessage("다른 위젯과 겹치지 않을 자리가 없어요. 주변 위젯을 옮기거나 작게 만든 뒤 다시 시도해주세요.").setPositiveButton("확인", null)
                    .show();
            return;
        }
        t.move(at[0] + dp(16), at[1] + top());
        t.remember();
        requestLayout();
        invalidate();
    }

    void squareSelected() {
        if (selected == null || !selected.requested) {
            AppUi.notice(getContext(), "먼저 위젯을 선택해주세요");
            return;
        }
        Tile t = selected;
        float scale = t.scale, height = t.heightScale;
        boolean free = t.freeSize;
        float side = Math.min(t.calendar ? dp(480) : dp(360), Math.min(t.getWidthAvailable(), t.getHeightAvailable()));
        side = Math.max(Math.min(t.minWidth(), t.getWidthAvailable()), side);
        t.sizeTo(side, side);
        keepClear(t, scale, height, free);
    }

    String profileKey() {
        return profile;
    }

    /**
     * True when a screen point is on a visible widget (or the board is being edited): such touches belong to the widget, not to a web background.
     */
    boolean claims(float rawX, float rawY) {
        if (editing)
            return true;
        android.graphics.Rect r = new android.graphics.Rect();
        for (Tile t : tiles)
            if (t.getVisibility() == VISIBLE && t.getGlobalVisibleRect(r) &&
                    r.contains(Math.round(rawX), Math.round(rawY)))
                return true;
        return false;
    }

    /**
     * Width decides the window class: a full-width window with system bars (2560x1210) is still the full screen, not the 1682px two-way split.
     */
    static String profileFor(int width, int maximumWidth) {
        return width >= maximumWidth * .92f ? "full/" : width >= maximumWidth * .5f ? "split2/" : "split3/";
    }

    private int top() {
        return dp(96);
    }

    private int bottom() {
        return getMeasuredHeight() - dp(48);
    }

    private float fit(float value, float lo, float hi) {
        return Math.max(lo, Math.min(hi, value));
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec), h = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(w, h);
        Rect maximum = getContext().getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
        String next = profileFor(w, maximum.width());
        if (!next.equals(profile)) {
            compactOffset = 0;
            scroller.abortAnimation();
            profileChanged = true;
            arrangePending = true;
            pendingKey = null;
            clearDrop();
            profile = next;
            if (editing)
                setEditing(false);
            for (Tile tile : tiles) {
                tile.overflow = false;
                tile.setVisibility(tile.requested ? VISIBLE : GONE);
                tile.load();
            }
            android.util.Log.i("WidgetLayout", "profile=" + profile + " size=" + w + "x" + h + " maximum=" + maximum);
        }

        for (Tile tile : tiles)
            tile.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(h, MeasureSpec.AT_MOST));
        if (arrangePending && pendingKey == null && w > 0 && h > dp(144)) {
            prepareProfile(w, h);
            arrangePending = false;
        }
        float clockHeight = 0, others = 0;
        int count = 0;
        for (Tile tile : tiles)
            if (tile.requested) {
                if (tile.clock)
                    clockHeight = tile.getMeasuredHeight();
                else {
                    others += tile.getMeasuredHeight();
                    count++;
                }
            }
        compactTop = top() + (clockHeight > 0 ? clockHeight + dp(24) : 0);
        compactRange = Math.max(0, others + dp(16) * Math.max(0, count - 1) - (bottom() - compactTop));
        compactStack = profile.equals("split3/") && !editing;
        compactOffset = fit(compactOffset, 0, compactRange);
        if (compactStack)
            for (Tile tile : tiles)
                tile.setVisibility(tile.requested ? VISIBLE : GONE);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (compactStack) {
            float y = compactTop - compactOffset;
            for (Tile tile : tiles)
                if (tile.requested) {
                    int w = tile.getMeasuredWidth(), h = tile.getMeasuredHeight(), left =
                            (getWidth() - w) / 2, upper = Math.round(tile.clock ? top() : y);
                    float oldX = tile.getLeft() + tile.getTranslationX(), oldY = tile.getTop() + tile.getTranslationY();
                    boolean laid = tile.isLaidOut();
                    tile.layout(left, upper, left + w, upper + h);
                    if (compactReflow && laid) {
                        if (tile.key.equals(appearKey))
                            UiMotion.enter(tile, dp(16), .94f);
                        else
                            UiMotion.release(tile, oldX - left, oldY - upper);
                    } else
                        tile.resetMotion();
                    tile.setImportantForAccessibility(tile.clock || upper + h > compactTop && upper < bottom() ?
                            IMPORTANT_FOR_ACCESSIBILITY_AUTO : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                    if (!tile.clock)
                        y += h + dp(16);
                }
            compactReflow = false;
            profileChanged = false;
            appearKey = null;
            return;
        }
        int w = getWidth(), h = getHeight(), total = 0, count = 0, memoHeight = 0;
        for (Tile tile : tiles)
            if (!tile.clock && tile.getVisibility() != GONE) {
                total += tile.getMeasuredHeight();
                count++;
                if (tile.key.equals("memo"))
                    memoHeight = tile.getMeasuredHeight();
            }
        total += dp(16) * Math.max(0, count - 1);
        int offset = 0;
        for (Tile tile : tiles) {
            if (tile.getVisibility() == GONE)
                continue;
            tile.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            int tw = tile.getMeasuredWidth(), th = tile.getMeasuredHeight();
            float x, y;
            if (tile.key.equals(pendingKey)) {
                tile.move(pendingLeft, pendingTop);
                tile.remember();
                appearKey = tile.key;
                pendingKey = null;
            }
            if (tile.saved) {
                x = dp(16) + tile.x * Math.max(0, w - dp(32) - tw);
                y = top() + tile.y * Math.max(0, bottom() - top() - th);
            } else if (tile.clock) {
                x = profile.equals("split3/") ? (w - tw) / 2f : dp(profile.equals("full/") ? 112 : 80);
                y = top() + dp(profile.equals("split3/") ? 28 : 64);
            } else if ((tile.key.equals("weather") || tile.key.equals("dday") || tile.key.equals("battery")) &&
                    !profile.equals("split3/")) {
                x = profile.equals("full/") ? w - dp(112) - tw : dp(80);
                y = top() + dp(profile.equals("full/") ? 48 : 380) + (tile.key.equals("dday") ? dp(230) : 0);
            } else if (profile.equals("full/")) {
                x = tile.key.equals("calendar") ? dp(112) : w - dp(112) - tw;
                y = h - dp(96) - th - (tile.key.equals("music") && memoHeight > 0 ? memoHeight + dp(16) : 0);
            } else {
                x = profile.equals("split3/") ? (w - tw) / 2f : w - dp(80) - tw;
                y = h - dp(88) - total + offset;
            }
            if (!tile.clock)
                offset += th + dp(16);
            int left = Math.round(fit(x, dp(16), Math.max(dp(16), w - dp(16) - tw)));
            int upper = Math.round(fit(y, top(), Math.max(top(), bottom() - th)));
            if (tile.holding) {
                left = Math.round(tile.startX);
                upper = Math.round(tile.startY);
            }
            float oldX = tile.getLeft() + tile.getTranslationX(), oldY = tile.getTop() + tile.getTranslationY();
            boolean laid = tile.isLaidOut();
            tile.layout(left, upper, left + tw, upper + th);
            if (tile.releasing) {
                tile.releasing = false;
                tile.setTranslationZ(0);
                UiMotion.release(tile, oldX - left, oldY - upper);
            } else if (!tile.holding && !tile.discarding && ((profileChanged && laid) || tile.key.equals(appearKey))) {
                UiMotion.cancel(tile);
                if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
                    if (tile.key.equals(appearKey)) {
                        tile.setAlpha(.3f);
                        tile.setScaleX(.94f);
                        tile.setScaleY(.94f);
                    } else {
                        tile.setTranslationX(oldX - left);
                        tile.setTranslationY(oldY - upper);
                    }
                    UiMotion.settle(tile);
                } else {
                    tile.setTranslationX(0);
                    tile.setTranslationY(0);
                    tile.setAlpha(1);
                    tile.setScaleX(1);
                    tile.setScaleY(1);
                }
            }
        }
        profileChanged = false;
        appearKey = null;
        if (editing)
            pinDefaults();
    }

    private java.util.List<float[]> occupied(Tile except) {
        java.util.ArrayList<float[]> out = new java.util.ArrayList<>();
        for (Tile tile : tiles)
            if (tile != except && tile.requested && !tile.overflow && tile.saved) {
                float tw = tile.getMeasuredWidth(), th = tile.getMeasuredHeight();
                out.add(new float[]{tile.x * Math.max(0, getMeasuredWidth() - dp(32) - tw),
                        tile.y * Math.max(0, bottom() - top() - th), tw, th});
            }
        return out;
    }

    private float[] free(Tile tile, float x, float y) {
        return WidgetPlacement.vacant(getMeasuredWidth() - dp(32), bottom() - top(), tile.getMeasuredWidth(),
                tile.getMeasuredHeight(), x - dp(16) - tile.getMeasuredWidth() / 2f,
                y - top() - tile.getMeasuredHeight() / 2f, occupied(tile), dp(12));
    }

    void previewDrop(String key, float x, float y) {
        for (Tile tile : tiles)
            if (tile.key.equals(key)) {
                float[] at = free(tile, x, y);
                if (at == null) {
                    clearDrop();
                    return;
                }
                float left = at[0] + dp(16), upper = at[1] + top();
                dropPreview = new RectF(left, upper, left + tile.getMeasuredWidth(), upper + tile.getMeasuredHeight());
                invalidate();
                return;
            }
    }

    void clearDrop() {
        dropPreview = null;
        invalidate();
    }

    boolean dropWidget(String key, float x, float y) {
        for (Tile tile : tiles)
            if (tile.key.equals(key)) {
                if (compactStack) {
                    selected = tile;
                    tile.overflow = false;
                    arrangePending = true;
                    clearDrop();
                    requestLayout();
                    return true;
                }
                float[] at = free(tile, x, y);
                if (at == null)
                    at = freeAtDefaultSize(tile, x, y);
                if (at == null)
                    return false;
                pendingKey = key;
                pendingLeft = at[0] + dp(16);
                pendingTop = at[1] + top();
                tile.overflow = false;
                clearDrop();
                setEditing(true);
                selected = tile;
                updateDesignTabs();
                editingChanged.run();
                requestLayout();
                return true;
            }
        return false;
    }

    /**
     * A widget re-added after hiding keeps its saved size when it fits; otherwise it comes back at the default size instead of failing.
     */
    private float[] freeAtDefaultSize(Tile tile, float x, float y) {
        float scale = tile.scale, height = tile.heightScale;
        boolean wasFree = tile.freeSize;
        tile.freeSize = tile.calendar;
        tile.scale = defaultScale(tile);
        tile.heightScale = tile.scale;
        measureTile(tile);
        float[] at = free(tile, x, y);
        if (at == null) {
            tile.scale = scale;
            tile.heightScale = height;
            tile.freeSize = wasFree;
            measureTile(tile);
        }
        return at;
    }

    private void measureTile(Tile tile) {
        tile.measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.AT_MOST));
    }

    boolean arrangeForWidget(String key) {
        Tile target = null;
        java.util.ArrayList<Tile> visible = new java.util.ArrayList<>();
        for (Tile t : tiles) {
            if (t.key.equals(key))
                target = t;
            if (t.requested || t.key.equals(key))
                visible.add(t);
        }
        if (target == null || !arrange(visible, true))
            return false;
        setEditing(true);
        selected = target;
        updateDesignTabs();
        editingChanged.run();
        arrangePending = false;
        requestLayout();
        return true;
    }

    private boolean arrange(java.util.ArrayList<Tile> visible, boolean preserveSize) {
        int w = getMeasuredWidth(), h = getMeasuredHeight();
        float density = getResources().getDisplayMetrics().density;
        float[][] sizes = new float[visible.size()][];
        for (int i = 0; i < visible.size(); i++) {
            Tile t = visible.get(i);
            sizes[i] = preserveSize ? new float[]{t.getMeasuredWidth() / density, t.getMeasuredHeight() / density} :
                    new float[]{t.clock ? 600 : 480, t.calendar ? 218 : t.baseHeight / density,
                            t.calendar ? 400 : t.clock ? 360 : 288, t.calendar ? 218 : t.baseHeight / density * .6f};
        }
        float width = (w - dp(32)) / density, height = (h - dp(144)) / density;
        float[][] layout = preserveSize ? WidgetPlacement.pack(width, height, 16, sizes) :
                WidgetPlacement.grid(width, height, 16, sizes);
        if (layout == null)
            return false;
        SharedPreferences.Editor edit = store.edit();
        for (int i = 0; i < visible.size(); i++) {
            Tile t = visible.get(i);
            float[] r = layout[i];
            if (!preserveSize) {
                t.scale = r[2] / (t.clock ? 600 : 480);
                t.heightScale = t.calendar ? r[3] / 218 : t.scale;
            }
            t.overflow = false;
            t.setVisibility(VISIBLE);
            t.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(h, MeasureSpec.AT_MOST));
            t.x = fit(r[0] * density / Math.max(1, w - dp(32) - t.getMeasuredWidth()), 0, 1);
            t.y = fit(r[1] * density / Math.max(1, h - dp(144) - t.getMeasuredHeight()), 0, 1);
            t.saved = true;
            edit.putString(profile + t.key, t.encoded());
        }
        edit.apply();
        profileChanged = true;
        android.util.Log.i("WidgetLayout",
                "arranged " + profile + " preserveSize=" + preserveSize + " widgets=" + visible.size());
        return true;
    }

    private void prepareProfile(int w, int h) {
        boolean any = false;
        for (Tile tile : tiles)
            if (store.contains(profile + tile.key)) {
                any = true;
                break;
            }
        if (autoRequested || !any) {
            java.util.ArrayList<Tile> visible = new java.util.ArrayList<>();
            for (Tile tile : tiles)
                if (tile.requested)
                    visible.add(tile);
            if (!arrange(visible, autoRequested) && !profile.equals("split3/"))
                AppUi.notice(getContext(), "현재 크기로는 모두 배치할 수 없어요. 위젯 크기를 줄이거나 일부를 숨겨주세요. 기존 배치는 유지됩니다.");
        } else if (!profile.equals("split3/") || editing) {
            boolean overflow = false;
            for (Tile t : tiles)
                if (t.requested && !t.saved) {
                    float[] at = free(t, w * .5f, top() + t.getMeasuredHeight() / 2f);
                    if (at == null) {
                        t.overflow = true;
                        t.setVisibility(GONE);
                        overflow = true;
                    } else {
                        t.move(at[0] + dp(16), at[1] + top());
                        t.remember();
                    }
                }
            if (overflow)
                AppUi.notice(getContext(), "기존 배치는 유지했어요. 공간이 부족한 위젯은 목록의 자동 정렬로 배치할 수 있어요.");
        }
        autoRequested = false;
    }

    @Override
    protected void onDetachedFromWindow() {
        setEditing(false);
        super.onDetachedFromWindow();
    }

    /**
     * iOS-style widget glass: a translucent rounded plate behind every widget except the clock. Themes with dark text get a light plate.
     */
    private boolean lightGlass;
    private float glassScale = 1;

    void setGlass(boolean darkText) {
        lightGlass = darkText;
        for (Tile t : tiles)
            t.plate();
    }

    /**
     * 차분하게 makes plates more solid (calmer, easier to read), 화려하게 lets more of the background through.
     */
    void setGlassScale(float scale) {
        glassScale = Math.max(.3f, Math.min(1.8f, scale));
        for (Tile t : tiles)
            t.plate();
    }

    private final class Tile extends ViewGroup {
        final String key, name;
        final View content;
        final boolean clock, calendar;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final GestureDetector detector;
        final android.widget.Button design;
        private boolean designTouch;
        float x, y, scale = 1, heightScale = 1, startX, startY, startScale, startWidth, startHeight, downX, downY;
        boolean saved, resizing, moved, requested = true, overflow, holding, dragging, armed, releasing, discarding;
        float dragLeft, dragTop;
        int baseWidth, baseHeight, contentWidth, contentHeight;
        boolean freeSize;

        Tile(String key, String name, View content, boolean clock) {
            super(WidgetBoard.this.getContext());
            this.key = key;
            this.name = name;
            this.content = content;
            this.clock = clock;
            this.calendar = content instanceof CalendarWidget;
            this.freeSize = calendar;
            setClipChildren(true);
            addView(content);
            plate();
            content.setPivotX(0);
            content.setPivotY(0);
            setFocusable(true);
            design = new android.widget.Button(getContext());
            design.setText("디자인");
            design.setTextSize(17);
            design.setTextColor(0xffe5fff4);
            design.setAllCaps(false);
            design.setPadding(0, 0, 0, 0);
            design.setMinWidth(0);
            design.setMinimumWidth(0);
            design.setMinHeight(0);
            design.setMinimumHeight(0);
            android.graphics.drawable.GradientDrawable tab = new android.graphics.drawable.GradientDrawable();
            tab.setColor(AppUi.SURFACE);
            tab.setCornerRadius(dp(12));
            tab.setStroke(dp(1), AppUi.ACCENT);
            design.setBackground(tab);
            design.setContentDescription(name + " 디자인 변경");
            design.setVisibility(GONE);
            design.setOnClickListener(v -> {
                selected = Tile.this;
                designRequested.run();
            });
            addView(design);
            detector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(MotionEvent event) {
                    return true;
                }

                @Override
                public void onLongPress(MotionEvent event) {
                    if (!editing && !driving) {
                        cancelContentTouch(event);
                        selected = Tile.this;
                        startX = getLeft();
                        startY = getTop();
                        holding = true;
                        held = Tile.this;
                        setEditing(true);
                        UiMotion.follow(Tile.this, 0, 0, 1.035f);
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        bringToFront();
                    }
                }

            });
        }

        void load() {
            saved = false;
            freeSize = calendar;
            scale = defaultScale(this);
            heightScale = scale;
            requestLayout();
            String raw = store.getString(profile + key, "");
            if (raw.isEmpty())
                return;
            try {
                String[] parts = raw.split(",");
                if (parts.length != 3 && parts.length != 4)
                    return;
                x = Float.parseFloat(parts[0]);
                y = Float.parseFloat(parts[1]);
                scale = Float.parseFloat(parts[2]);
                if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(scale))
                    throw new NumberFormatException();
                x = fit(x, 0, 1);
                y = fit(y, 0, 1);
                freeSize = calendar || parts.length == 4;
                scale = calendar ? Math.max(400f / 480, scale) : fit(scale, parts.length == 4 ? .1f : .6f, 8f);
                heightScale = parts.length == 4 ? Float.parseFloat(parts[3]) : scale;
                if (!Float.isFinite(heightScale))
                    throw new NumberFormatException();
                heightScale = calendar ? Math.max(1, heightScale) : fit(heightScale, .1f, 8);
                saved = true;
            } catch (NumberFormatException error) {
                saved = false;
                freeSize = calendar;
                scale = defaultScale(this);
                heightScale = scale;
            }
        }

        String encoded() {
            return x + "," + y + "," + scale + (freeSize ? "," + heightScale : "");
        }

        void remember() {
            if (saved)
                store.edit().putString(profile + key, encoded()).apply();
        }

        void move(float left, float upper) {
            float roomX = Math.max(0, getWidthAvailable() - plannedWidth()), roomY = Math.max(0,
                    bottom() - top() - plannedHeight());
            x = roomX == 0 ? 0 : fit((left - dp(16)) / roomX, 0, 1);
            y = roomY == 0 ? 0 : fit((upper - top()) / roomY, 0, 1);
            saved = true;
            requestLayout();
        }

        private int getWidthAvailable() {
            return Math.max(1, WidgetBoard.this.getMeasuredWidth() - dp(32));
        }

        private int getHeightAvailable() {
            int available = Math.max(1, WidgetBoard.this.getMeasuredHeight() - dp(144));
            if (clock && profile.equals("split3/") && !editing) {
                for (Tile t : tiles)
                    if (!t.clock && t.requested)
                        return Math.max(1, available - dp(24) - Math.min(dp(240), available / 2));
            }
            return available;
        }

        private int minWidth() {
            return dp(calendar ? 400 : 240);
        }

        private int minHeight() {
            return dp(calendar ? 218 : clock ? 160 : 112);
        }

        private float plannedWidth() {
            return freeSize ?
                    fit(dp(clock ? 600 : 480) * scale, Math.min(minWidth(), getWidthAvailable()), getWidthAvailable()) :
                    baseWidth * Math.min(scale, maxScale());
        }

        private float plannedHeight() {
            return freeSize ?
                    fit(dp(218) * heightScale, Math.min(minHeight(), getHeightAvailable()), getHeightAvailable()) :
                    baseHeight * Math.min(scale, maxScale());
        }

        private float maxScale() {
            return Math.max(.1f, Math.min(2.4f, Math.min(getWidthAvailable() / (float) baseWidth,
                    getHeightAvailable() / (float) Math.max(1, baseHeight))));
        }

        void sizeTo(float width, float height) {
            freeSize = true;
            width = fit(width, Math.min(minWidth(), getWidthAvailable()), getWidthAvailable());
            height = fit(height, Math.min(minHeight(), getHeightAvailable()), getHeightAvailable());
            scale = width / dp(clock ? 600 : 480);
            heightScale = height / dp(218);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            design.measure(MeasureSpec.makeMeasureSpec(dp(112), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(dp(AppUi.TOUCH), MeasureSpec.EXACTLY));
            baseWidth = dp(clock ? 600 : 480);
            if (calendar) {
                contentWidth = Math.round(plannedWidth());
                contentHeight = Math.round(plannedHeight());
                ((CalendarWidget) content).prepareSize(contentWidth, contentHeight);
                content.setScaleX(1);
                content.setScaleY(1);
                content.measure(MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(contentHeight, MeasureSpec.EXACTLY));
                baseHeight = dp(218);
                setMeasuredDimension(contentWidth, contentHeight);
                return;
            }
            // A saved box has exact bounds; a preliminary natural-size pass would flip responsive content twice.
            if (!freeSize) {
                content.measure(MeasureSpec.makeMeasureSpec(baseWidth, MeasureSpec.EXACTLY),
                        clock ? MeasureSpec.makeMeasureSpec(dp(260), MeasureSpec.EXACTLY) :
                                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                baseHeight = Math.max(1, content.getMeasuredHeight());
                float actual = Math.min(scale, maxScale());
                contentWidth = baseWidth;
                contentHeight = baseHeight;
                content.setScaleX(actual);
                content.setScaleY(actual);
                setMeasuredDimension(Math.round(baseWidth * actual), Math.round(baseHeight * actual));
                return;
            }
            int width = Math.round(plannedWidth()), height = Math.round(plannedHeight());
            contentWidth = width;
            contentHeight = height;
            if (content instanceof android.widget.LinearLayout)
                ((android.widget.LinearLayout) content).setGravity(Gravity.CENTER_VERTICAL);
            content.setScaleX(1);
            content.setScaleY(1);
            content.measure(MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(contentHeight, MeasureSpec.EXACTLY));
            setMeasuredDimension(width, height);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            content.layout(0, 0, contentWidth, contentHeight);
            design.layout(Math.max(0, r - l - dp(120)), dp(8), r - l - dp(8), dp(8 + AppUi.TOUCH));
        }

        private void cancelContentTouch(MotionEvent down) {
            MotionEvent cancel = MotionEvent.obtain(down);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(cancel);
            cancel.recycle();
        }

        void resetMotion() {
            UiMotion.cancel(this);
            setTranslationX(0);
            setTranslationY(0);
            setScaleX(1);
            setScaleY(1);
            setAlpha(1);
            setTranslationZ(0);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            if (discarding)
                return true;
            if (compactStack && !clock && event.getActionMasked() == MotionEvent.ACTION_DOWN &&
                    (getTop() + event.getY() < compactTop || getTop() + event.getY() > bottom()))
                return false;
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                designTouch =
                        editing && selected == this && event.getX() >= getWidth() - dp(120) && event.getY() >= dp(8) &&
                                event.getY() <= dp(8 + AppUi.TOUCH);
                if (editing) {
                    selected = this;
                    updateDesignTabs();
                    editingChanged.run();
                    WidgetBoard.this.invalidate();
                }
                resetMotion();
                moved = false;
                downX = event.getRawX();
                downY = event.getRawY();
                startX = getLeft();
                startY = getTop();
                startScale = getWidth() / (float) Math.max(1, baseWidth);
                startWidth = getWidth();
                startHeight = getHeight();
                resizing = editing && event.getX() >= getWidth() - dp(AppUi.TOUCH) &&
                        event.getY() >= getHeight() - dp(AppUi.TOUCH);
            }
            detector.onTouchEvent(event);
            return super.dispatchTouchEvent(event);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            return editing && !designTouch;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!editing)
                return true;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    bringToFront();
                    return true;
                case MotionEvent.ACTION_MOVE:
                case MotionEvent.ACTION_UP:
                    float dx = event.getRawX() - downX, dy = event.getRawY() - downY;
                    if (!moved && Math.hypot(dx, dy) <= ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
                        if (event.getActionMasked() == MotionEvent.ACTION_UP && holding) {
                            holding = false;
                            held = null;
                            releasing = true;
                            requestLayout();
                        }
                        return true;
                    }
                    moved = true;
                    clearGuides();
                    if (resizing) {
                        float width = snapSize(this, startWidth + dx, true), height = snapSize(this, startHeight + dy,
                                false);
                        if (Math.abs(width - height) <= dp(16)) {
                            float side = Math.min(width, height);
                            width = height = side;
                            guideX = startX + side;
                            guideY = startY + side;
                        }
                        sizeTo(width, height);
                        move(startX, startY);
                        WidgetBoard.this.invalidate();
                        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                            remember();
                            clearGuides();
                        }
                        return true;
                    }
                    if (!dragging) {
                        dragging = true;
                        holding = true;
                        held = this;
                        setTranslationZ(dp(12));
                    }
                    WidgetBoard.this.getLocationOnScreen(screenLocation);
                    float pointerX = event.getRawX() - screenLocation[0], pointerY =
                            event.getRawY() - screenLocation[1];
                    boolean next = removal != null &&
                            removalHit(WidgetBoard.this.getWidth(), WidgetBoard.this.getHeight(),
                                    getResources().getDisplayMetrics().density, pointerX, pointerY);
                    if (next != armed) {
                        armed = next;
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    }
                    feedback(true, armed);
                    snapMove(this, startX + dx, startY + dy);
                    if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                        holding = false;
                        dragging = false;
                        held = null;
                        clearGuides();
                        if (armed) {
                            armed = false;
                            discarding = true;
                            pendingRemoval = this;
                            UiMotion.discard(this, WidgetBoard.this.getWidth() / 2f - getLeft() - getWidth() / 2f,
                                    WidgetBoard.this.getHeight() - dp(66) - getTop() - getHeight() / 2f,
                                    WidgetBoard.this::commitRemoval);
                        } else {
                            feedback(false, false);
                            releasing = true;
                            move(dragLeft, dragTop);
                            remember();
                            requestLayout();
                        }
                        performClick();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    cancelHold();
                    if (resizing) {
                        remember();
                        clearGuides();
                    }
                    return true;
            }
            return true;
        }

        void plate() {
            if (clock)
                return;
            int fill = lightGlass ? 0x99ffffff : 0x52101114, alpha = Math.min(255,
                    Math.round((fill >>> 24) * glassScale));
            setBackground(AppUi.shape(getContext(), (fill & 0xffffff) | (alpha << 24), 28,
                    lightGlass ? 0x80ffffff : 0x2effffff));
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (!editing)
                return;
            String sizeLabel = (selected == this ? "선택 · " : "") + name + " " +
                    Math.round(getWidth() / getResources().getDisplayMetrics().density) + "×" +
                    Math.round(getHeight() / getResources().getDisplayMetrics().density) +
                    (Math.abs(getWidth() - getHeight()) <= 1 ? " · 1:1" : "") +
                    (calendar ? (((CalendarWidget) content).isMonthView() ? " · 한 달" : " · 세로로 늘리면 월간") : "");
            // Bottom-left, clear of the resize handle: at the top it covered the widget title.
            int labelSave = canvas.save();
            float base = getHeight() - dp(30);
            canvas.clipRect(0, base - dp(4), Math.max(dp(24), getWidth() - dp(AppUi.TOUCH + 4)), getHeight());
            paint.setTextSize(dp(14));
            paint.setColor(0xee183b3a);
            canvas.drawRoundRect(dp(6), base, Math.min(getWidth() - dp(8), dp(22) + paint.measureText(sizeLabel)),
                    base + dp(26), dp(6), dp(6), paint);
            paint.setColor(0xffd5f1e9);
            canvas.drawText(sizeLabel, dp(12), base + dp(19), paint);
            canvas.restoreToCount(labelSave);
            paint.setColor(selected == this ? 0xffe9fff7 : 0xffa8ded4);
            paint.setStrokeWidth(dp(selected == this ? 3 : 1));
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawRoundRect(1, 1, getWidth() - 1, getHeight() - 1, dp(28), dp(28), paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(getWidth() - dp(48), getHeight() - dp(48), getWidth(), getHeight(), dp(12), dp(12),
                    paint);
            paint.setColor(0xff102a2b);
            paint.setStrokeWidth(dp(2));
            for (int i = 0; i < 3; i++) {
                float d = dp(12 + i * 7);
                canvas.drawLine(getWidth() - d, getHeight() - dp(9), getWidth() - dp(9), getHeight() - d, paint);
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            if (!editing)
                return;
            String[] actions = {"왼쪽 이동", "오른쪽 이동", "위로 이동", "아래로 이동", "크기 줄이기", "크기 키우기", "가로 줄이기", "가로 키우기", "세로 줄이기",
                    "세로 키우기", "정사각형으로 변경", "위젯 숨기기"};
            for (int i = 0; i < actions.length; i++)
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(0x01000001 + i, actions[i]));
        }

        @Override
        public boolean performAccessibilityAction(int action, Bundle arguments) {
            if (editing && action == 0x0100000c && requested && removal != null) {
                removal.remove(key);
                return true;
            }
            if (editing && action >= 0x01000001 && action <= 0x0100000b) {
                selected = this;
                updateDesignTabs();
                editingChanged.run();
                int index = action - 0x01000001;
                if (index == 10) {
                    squareSelected();
                    return true;
                }
                if (index < 4)
                    move(getLeft() + (index == 0 ? -dp(24) : index == 1 ? dp(24) : 0),
                            getTop() + (index == 2 ? -dp(24) : index == 3 ? dp(24) : 0));
                else {
                    float width = getWidth(), height = getHeight();
                    if (index < 6) {
                        float factor = index == 4 ? .9f : 1.1f;
                        width *= factor;
                        height *= factor;
                    } else if (index < 8)
                        width += index == 6 ? -dp(24) : dp(24);
                    else
                        height += index == 8 ? -dp(24) : dp(24);
                    sizeTo(width, height);
                    move(getLeft(), getTop());
                }
                remember();
                return true;
            }
            return super.performAccessibilityAction(action, arguments);
        }
    }
}
