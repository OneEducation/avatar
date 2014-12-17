package org.olpc.avatargen;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.animation.Interpolator;

import com.shamanland.fab.FloatingActionButton;

import java.util.Date;

/**
 * Created by dongseok0 on 16/12/14.
 */
public class FloatingActionButton2 extends FloatingActionButton {
    TransformRunnable runnable = new TransformRunnable(this);

    public FloatingActionButton2(Context context) {
        super(context);
    }

    public FloatingActionButton2(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FloatingActionButton2(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public FloatingActionButton2 setInterpolator(Interpolator interpolator) {
        runnable.setInterpolator(interpolator);
        return this;
    }

    @Override
    protected void drawableStateChanged() {
        //temporary
        //super.drawableStateChanged();

        //Util.debug("drawableStateChanged: "+String.valueOf(getColor()));
    }

    public FloatingActionButton2 setBGColorTransition(int color) {
        runnable.setColor(color);
        return this;
    }

    public FloatingActionButton2 setDrawableTransition(int id, float timing) {
        runnable.setDrawable(id, timing);
        return this;
    }

    public FloatingActionButton2 setDuration(int duration) {
        runnable.setDuration(duration);
        return this;
    }

    public void start() {
        runnable.start();
    }

    class TransformRunnable implements Runnable {
        FloatingActionButton2 v;
        int duration;
        Integer targetDrawableID = null;
        float drawableChangeTiming = 0.4f;
        Integer color = null;
        int r;
        int g;
        int b;
        int rDiff;
        int gDiff;
        int bDiff;
        long startTime;
        Interpolator interpolator;

        TransformRunnable(FloatingActionButton2 v) {
            super();
            this.v = v;
        }

        public void setDuration(int duration) {
            this.duration = duration;
        }

        public void setDrawable(int id, float timing) {
            this.targetDrawableID = id;
            this.drawableChangeTiming = timing;
        }

        public void setInterpolator(Interpolator i) {
            this.interpolator = i;
        }

        public void setColor(int color) {
            this.color = color;
            r = Color.red(getColor());
            g = Color.green(getColor());
            b = Color.blue(getColor());
            rDiff = Color.red(color) - r;
            gDiff = Color.green(color) - g;
            bDiff = Color.blue(color) - b;
        }

        @Override
        public void run() {
            long currentTime = new Date().getTime();
            long playTime = currentTime - startTime;
            float progress = (float)playTime / (float)duration;

            if(playTime < duration) {

                if(targetDrawableID != null && progress > drawableChangeTiming) {
                    v.setImageResource(targetDrawableID);
                }
                float interpolated = interpolator == null ? progress : interpolator.getInterpolation(progress);
                v.setColor(Color.rgb((int) (r+ rDiff * interpolated), (int) (g+gDiff*interpolated), (int) (b+bDiff*interpolated)));
                v.initBackground();
                v.postDelayed(this, 1000 / 40);
            } else {
                v.setColor(color);
                v.initBackground();
            }
        }

        public void start() {
            startTime = new Date().getTime();
            v.postDelayed(this, 1000/40);
        }

    }
}
