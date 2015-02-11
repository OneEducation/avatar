package org.oneedu.avatargen;

import android.view.animation.Interpolator;

public class AndroidAnimation {

    public enum Type {
        BLINK,
        WAVE,
        ANTENNA_TWITCH,
        HEAD_TILT,
        NOD,
        SHRUG,
        SHRINK_DOWN,
        SHRINK_UP,
        SHRINK_LEFT,
        SHRINK_RIGHT,
        BOUNCE_ELEMENT,
        ZOOM_ELEMENT,
        ADJUST_SIZE,
        DRIFT,
        HOVERING
    }

    private Type type;
    private float progress;
    // In millis
    private long startTime;
    // In millis
    private long duration;

    private Interpolator interpolator;
    private float start, end;
    
    private boolean repeat = false;
    
    public AndroidAnimation(Type type, long duration, boolean repeat) {
    	this(type, duration);
    	this.repeat = repeat;
    }

    public AndroidAnimation(Type type, long duration) {
        this.type = type;
        startTime = System.currentTimeMillis();
        this.duration = duration;
        progress = 0f;
    }

    public AndroidAnimation(Type type) {
        this(type, 500);
    }

    /**
     * Steps the animation
     * @return true if animation is done.
     */
    public boolean step() {
        progress = (System.currentTimeMillis() - startTime) / (float) duration;
        if (progress >= 1f && repeat) {
        	startTime = System.currentTimeMillis();
        	progress = 0f;
        	return false;
        }
        return (progress >= 1f);
    }

    public Type getType() {
        return type;
    }

    public float getProgress() {
        return progress;
    }

    public void setInterpolator(Interpolator interpolator, float start, float end) {
        this.interpolator = interpolator;
        this.start = start;
        this.end = end;
    }

    public float getValue() {
        if (interpolator == null) {
            return progress;
        } else {
            return interpolator.getInterpolation(progress) * (end - start) + start;
        }
    }

    public float getInterpolatorValue() {
        if (interpolator == null) {
            return progress;
        } else {
            return interpolator.getInterpolation(progress);
        }
    }
}
