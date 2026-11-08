package io.hmg.wallpaperapp;

import android.content.Context;
import android.view.*;
import android.widget.FrameLayout;

import androidx.dynamicanimation.animation.*;

/**
 * Control-Center style quick settings: drag down from the top band of the app window and the glass sheet follows the finger; a spring settles it open or closed. Swipe up or tap outside to close.
 */
final class PullDownPanel extends FrameLayout {
    interface Content {
        View build(boolean driving);
    }

    /**
     * Start band, in dp below the app window's top edge. The Pleos status bar and its pull-down live outside the app window (split views) or above it (system bar), so this band never overlaps them.
     */
    static final int BAND_DP = 40;
    /**
     * Open when pulled past this share of the sheet, or flung faster than FLING_DP per second.
     */
    static final float OPEN_SHARE = .35f, FLING_DP = 900;

    static boolean startsInBand(float y, float density) {
        return y >= 0 && y <= BAND_DP * density;
    }

    /**
     * Release decision shared with the checks: a fling wins; otherwise the distance decides.
     */
    static boolean shouldOpen(float shown, float height, float velocityDp) {
        if (velocityDp > FLING_DP)
            return true;
        if (velocityDp < -FLING_DP)
            return false;
        return height > 0 && shown >= height * OPEN_SHARE;
    }

    private final Content content;
    private final View scrim;
    private final FrameLayout sheet;
    private final SpringAnimation spring;
    private final int slop;
    private final float density;
    private boolean open, dragging, tracking;
    private float downX, downY, startShown;
    private VelocityTracker velocity;
    private boolean driving;

    PullDownPanel(Context c, Content content) {
        super(c);
        this.content = content;
        density = c.getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        scrim = new View(c);
        scrim.setBackgroundColor(0x66000000);
        scrim.setAlpha(0);
        addView(scrim, new LayoutParams(-1, -1));
        sheet = new FrameLayout(c);
        sheet.setBackground(AppUi.shape(c, AppUi.GLASS_SOLID, 32, AppUi.EDGE));
        sheet.setClipToOutline(true);
        sheet.setElevation(24 * density);
        LayoutParams at = new LayoutParams(-1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        at.topMargin = Math.round(12 * density);
        addView(sheet, at);
        spring = new SpringAnimation(sheet, DynamicAnimation.TRANSLATION_Y);
        spring.addUpdateListener((a, value, v) -> follow());
        spring.addEndListener((a, canceled, value, v) -> {
            if (!canceled && !open)
                setVisibility(GONE);
        });
        setVisibility(GONE);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    boolean isOpen() {
        return open;
    }

    /**
     * Keeps the sheet within 960dp and the window gutter at every window size (841 / 1682 / 2560).
     */
    @Override
    protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        LayoutParams p = (LayoutParams) sheet.getLayoutParams();
        int target = Math.min(width - Math.round(32 * density), Math.round(960 * density));
        if (p.width != target) {
            p.width = target;
            sheet.setLayoutParams(p);
        }
        super.onMeasure(w, h);
    }

    private float hidden() {
        return -(sheet.getHeight() > 0 ? sheet.getHeight() : sheet.getMeasuredHeight()) - 24 * density;
    }

    private void follow() {
        float h = -hidden();
        float shown = Math.max(0, Math.min(1, (sheet.getTranslationY() + h) / Math.max(1, h)));
        scrim.setAlpha(shown);
    }

    private void prepare(boolean driving) {
        this.driving = driving;
        sheet.removeAllViews();
        sheet.addView(content.build(driving), new LayoutParams(-1, -2));
        setVisibility(VISIBLE);
        int width = Math.min(getWidth() - Math.round(32 * density), Math.round(960 * density));
        sheet.measure(MeasureSpec.makeMeasureSpec(Math.max(1, width), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.max(1, getHeight()), MeasureSpec.AT_MOST));
        if (!open)
            sheet.setTranslationY(hidden());
        follow();
    }

    /**
     * Rebuilds the open sheet, e.g. when driving starts or stops.
     */
    void refresh(boolean driving) {
        if (open) {
            prepare(driving);
            sheet.setTranslationY(0);
            follow();
        }
    }

    void show(boolean driving) {
        prepare(driving);
        settle(true);
    }

    void hide() {
        settle(false);
    }

    private void settle(boolean opening) {
        open = opening;
        spring.cancel();
        float target = opening ? 0 : hidden();
        if (UiMotion.reduced) {
            sheet.setTranslationY(target);
            follow();
            if (!opening)
                setVisibility(GONE);
            return;
        }
        spring.setSpring(new SpringForce(target).setDampingRatio(.86f).setStiffness(620));
        spring.start();
        if (opening) {
            sheet.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED);
        }
    }

    /**
     * Called from Activity.dispatchTouchEvent while closed. Returns true once the gesture belongs to the panel (the caller then sends the window a cancel).
     */
    boolean pull(MotionEvent e, boolean allowed, boolean driving) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                tracking = allowed && !open && startsInBand(e.getY(), density);
                dragging = false;
                downX = e.getX();
                downY = e.getY();
                if (tracking) {
                    velocity = VelocityTracker.obtain();
                    velocity.addMovement(e);
                }
                return false;
            case MotionEvent.ACTION_MOVE:
                if (!tracking)
                    return dragging;
                velocity.addMovement(e);
                float dy = e.getY() - downY, dx = e.getX() - downX;
                if (!dragging) {
                    if (dy > slop && dy > Math.abs(dx) * 1.5f) {
                        dragging = true;
                        prepare(driving);
                        startShown = 0;
                    } else if (dy < -slop || Math.abs(dx) > slop * 2) {
                        tracking = false;
                        return false;
                    } else
                        return false;
                }
                spring.cancel();
                sheet.setTranslationY(Math.min(0, hidden() + Math.max(0, dy - slop)));
                follow();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!tracking) {
                    return false;
                }
                tracking = false;
                if (!dragging) {
                    recycle();
                    return false;
                }
                velocity.addMovement(e);
                velocity.computeCurrentVelocity(1000);
                float vy = velocity.getYVelocity() / density;
                recycle();
                dragging = false;
                settle(e.getActionMasked() == MotionEvent.ACTION_UP &&
                        shouldOpen(sheet.getTranslationY() - hidden(), -hidden(), vy));
                return true;
        }
        return dragging;
    }

    private void recycle() {
        if (velocity != null) {
            velocity.recycle();
            velocity = null;
        }
    }

    /**
     * While open: a tap on the scrim closes; an upward drag anywhere on the sheet pulls it back up. Taps on tiles still reach them.
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (!open)
            return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                dragging = false;
                recycle();
                velocity = VelocityTracker.obtain();
                velocity.addMovement(e);
                startShown = sheet.getTranslationY();
                return !inSheet(e);
            case MotionEvent.ACTION_MOVE:
                if (velocity != null)
                    velocity.addMovement(e);
                float dy = e.getY() - downY;
                if (dy < -slop && Math.abs(dy) > Math.abs(e.getX() - downX)) {
                    dragging = true;
                    return true;
                }
                return false;
        }
        return false;
    }

    private boolean inSheet(MotionEvent e) {
        return e.getX() >= sheet.getLeft() && e.getX() <= sheet.getRight() &&
                e.getY() >= sheet.getTop() + sheet.getTranslationY() &&
                e.getY() <= sheet.getBottom() + sheet.getTranslationY();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!open)
            return false;
        if (velocity != null)
            velocity.addMovement(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    spring.cancel();
                    sheet.setTranslationY(Math.max(hidden(), Math.min(0, startShown + e.getY() - downY + slop)));
                    follow();
                } else if (Math.abs(e.getY() - downY) > slop)
                    dragging = true;
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragging) {
                    recycle();
                    hide();
                    return true;
                }
                if (velocity != null) {
                    velocity.computeCurrentVelocity(1000);
                }
                float vy = velocity == null ? 0 : velocity.getYVelocity() / density;
                recycle();
                dragging = false;
                settle(shouldOpen(sheet.getTranslationY() - hidden(), -hidden(), vy));
                return true;
            case MotionEvent.ACTION_CANCEL:
                recycle();
                dragging = false;
                settle(true);
                return true;
        }
        return true;
    }

    /**
     * The grabber pill drawn at the top centre so drivers can see where the pull starts.
     */
    static View grabber(Context c) {
        View v = new View(c);
        v.setBackground(AppUi.shape(c, 0x99ffffff, 3, 0));
        v.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        v.setElevation(2 * c.getResources().getDisplayMetrics().density);
        return v;
    }
}
