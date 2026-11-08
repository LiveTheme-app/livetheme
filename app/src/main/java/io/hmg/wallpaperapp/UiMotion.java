package io.hmg.wallpaperapp;

import android.animation.ValueAnimator;
import android.view.View;

import androidx.dynamicanimation.animation.*;

/**
 * Retarget running springs instead of restarting easing curves at zero velocity.
 */
final class UiMotion {
    /**
     * Set while driving: press-down and banner motion are skipped (car distraction rules).
     */
    static boolean reduced;

    private static final class State implements View.OnAttachStateChangeListener {
        final SpringAnimation x, y, sx, sy;
        Runnable finished, discarded;
        float vx, vy;

        State(View v) {
            x = spring(v, DynamicAnimation.TRANSLATION_X);
            y = spring(v, DynamicAnimation.TRANSLATION_Y);
            sx = spring(v, DynamicAnimation.SCALE_X);
            sy = spring(v, DynamicAnimation.SCALE_Y);
            x.addUpdateListener((a, value, velocity) -> vx = velocity);
            y.addUpdateListener((a, value, velocity) -> vy = velocity);
            y.addEndListener((animation, canceled, value, velocity) -> {
                Runnable action = finished;
                finished = null;
                if (!canceled && action != null)
                    action.run();
            });
            sy.addEndListener((a, canceled, value, velocity) -> {
                Runnable action = discarded;
                discarded = null;
                if (!canceled && action != null)
                    action.run();
            });
            v.addOnAttachStateChangeListener(this);
        }

        public void onViewAttachedToWindow(View v) {
        }

        public void onViewDetachedFromWindow(View v) {
            stop();
            v.animate().cancel();
        }

        void stop() {
            finished = null;
            discarded = null;
            x.cancel();
            y.cancel();
            sx.cancel();
            sy.cancel();
            vx = vy = 0;
        }
    }

    private static SpringAnimation spring(View view, DynamicAnimation.ViewProperty property) {
        SpringAnimation a = new SpringAnimation(view, property);
        a.setSpring(new SpringForce(0).setDampingRatio(.84f).setStiffness(480));
        return a;
    }

    private static void target(SpringAnimation a, float value, float damping, float stiffness) {
        a.getSpring().setDampingRatio(damping).setStiffness(stiffness);
        a.animateToFinalPosition(value);
    }

    // Motion owns the tag only on its dedicated panel and tile containers.
    private static State state(View v) {
        State s = v.getTag() instanceof State ? (State) v.getTag() : null;
        if (s == null) {
            s = new State(v);
            v.setTag(s);
        }
        return s;
    }

    static void cancel(View view) {
        State s = view.getTag() instanceof State ? (State) view.getTag() : null;
        if (s != null)
            s.stop();
        view.animate().cancel();
    }

    static void settle(View v) {
        State s = state(v);
        s.finished = null;
        s.discarded = null;
        if (!ValueAnimator.areAnimatorsEnabled()) {
            cancel(v);
            v.setTranslationX(0);
            v.setTranslationY(0);
            v.setScaleX(1);
            v.setScaleY(1);
            v.setAlpha(1);
            return;
        }
        target(s.x, 0, .84f, 480);
        target(s.y, 0, .84f, 480);
        target(s.sx, 1, .74f, 600);
        target(s.sy, 1, .74f, 600);
        v.animate().alpha(1).setDuration(160).start();
    }

    static void follow(View v, float x, float y, float scale) {
        State s = state(v);
        s.finished = null;
        s.discarded = null;
        if (!ValueAnimator.areAnimatorsEnabled()) {
            cancel(v);
            v.setTranslationX(x);
            v.setTranslationY(y);
            v.setScaleX(scale);
            v.setScaleY(scale);
            return;
        }
        target(s.x, x, .92f, 1100);
        target(s.y, y, .92f, 1100);
        target(s.sx, scale, .74f, 600);
        target(s.sy, scale, .74f, 600);
    }

    static void release(View v, float x, float y) {
        State s = state(v);
        float vx = s.vx, vy = s.vy;
        cancel(v);
        v.setTranslationX(x);
        v.setTranslationY(y);
        if (ValueAnimator.areAnimatorsEnabled()) {
            s.x.setStartVelocity(Math.max(-1800, Math.min(1800, vx)));
            s.y.setStartVelocity(Math.max(-1800, Math.min(1800, vy)));
        }
        settle(v);
    }

    static void discard(View v, float x, float y, Runnable done) {
        State s = state(v);
        s.finished = null;
        if (!ValueAnimator.areAnimatorsEnabled()) {
            cancel(v);
            done.run();
            return;
        }
        s.discarded = done;
        target(s.x, x, .9f, 700);
        target(s.y, y, .9f, 700);
        target(s.sx, .12f, .9f, 700);
        target(s.sy, .12f, .9f, 700);
        v.animate().alpha(0).setDuration(180).start();
    }

    static void enter(View v, float offset, float scale) {
        cancel(v);
        if (!ValueAnimator.areAnimatorsEnabled()) {
            settle(v);
            return;
        }
        v.setTranslationY(offset);
        v.setScaleX(scale);
        v.setScaleY(scale);
        v.setAlpha(0);
        settle(v);
    }

    static void exit(View v, float offset, Runnable finished) {
        State s = state(v);
        v.animate().cancel();
        if (!ValueAnimator.areAnimatorsEnabled()) {
            cancel(v);
            finished.run();
            return;
        }
        s.finished = finished;
        s.y.animateToFinalPosition(offset);
        v.animate().alpha(0).setDuration(160).start();
    }
}
