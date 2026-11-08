package io.hmg.wallpaperapp;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/**
 * A temporary drag destination; accessibility removal lives on each widget.
 */
final class WidgetTrash extends View {
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean armed, active;

    WidgetTrash(Context context) {
        super(context);
        setVisibility(GONE);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setElevation(AppUi.dp(context, 24));
    }

    void show(boolean moving, boolean over) {
        boolean changed = armed != over;
        armed = over;
        if (moving) {
            if (!active) {
                active = true;
                setVisibility(VISIBLE);
                UiMotion.enter(this, AppUi.dp(getContext(), 24), .92f);
                bringToFront();
            }
            if (changed)
                UiMotion.follow(this, 0, 0, over ? 1.06f : 1);
            invalidate();
        } else if (active) {
            active = false;
            UiMotion.exit(this, AppUi.dp(getContext(), 28), () -> setVisibility(GONE));
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float d = getResources().getDisplayMetrics().density, w = getWidth(), h = getHeight();
        ink.setStyle(Paint.Style.FILL);
        ink.setColor(armed ? 0xffbc4357 : 0xf2191b22);
        c.drawRoundRect(0, 0, w, h, 28 * d, 28 * d, ink);
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeWidth(d);
        ink.setColor(armed ? 0xffffb8bf : 0xff696473);
        c.drawRoundRect(d, d, w - d, h - d, 27 * d, 27 * d, ink);
        float x = w / 2 - 98 * d, y = h / 2;
        ink.setColor(Color.WHITE);
        ink.setStrokeWidth(2 * d);
        ink.setStrokeCap(Paint.Cap.ROUND);
        c.drawRoundRect(x - 9 * d, y - 7 * d, x + 9 * d, y + 12 * d, 3 * d, 3 * d, ink);
        c.drawLine(x - 12 * d, y - 12 * d, x + 12 * d, y - 12 * d, ink);
        c.drawLine(x - 4 * d, y - 17 * d, x + 4 * d, y - 17 * d, ink);
        c.drawLine(x - 3 * d, y - 2 * d, x - 3 * d, y + 7 * d, ink);
        c.drawLine(x + 3 * d, y - 2 * d, x + 3 * d, y + 7 * d, ink);
        ink.setStyle(Paint.Style.FILL);
        ink.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        ink.setTextSize(18 * getResources().getDisplayMetrics().scaledDensity);
        c.drawText(armed ? "놓으면 위젯 제거" : "여기로 끌어 제거", x + 26 * d, y + 5 * d, ink);
    }
}
