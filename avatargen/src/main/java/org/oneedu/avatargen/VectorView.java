package org.oneedu.avatargen;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Picture;
import android.graphics.RectF;
import android.graphics.drawable.PictureDrawable;
import android.util.AttributeSet;
import android.view.View;
import com.larvalabs.svgandroid.SVG;

public class VectorView extends View
{

    private float zoom;
    private float scale;
    private float left;
    private float top;
    private float width;
    private float height;
    private Paint paint;
    private SVG vectors[];
    private Picture pictures[];
    private boolean selected;
    private RectF defaultBound;
    private boolean drawBackground;
    private PictureDrawable selectionDrawable;

    public VectorView(Context context)
    {
        super(context);
        zoom = 1.0F;
        scale = -1F;
        left = 0.0F;
        top = 0.0F;
        vectors = null;
        pictures = null;
        defaultBound = null;
        drawBackground = true;
        selectionDrawable = null;
        a(context, null);
    }

    public VectorView(Context context, AttributeSet attributeset)
    {
        super(context, attributeset);
        zoom = 1.0F;
        scale = -1F;
        left = 0.0F;
        top = 0.0F;
        vectors = null;
        pictures = null;
        defaultBound = null;
        drawBackground = true;
        selectionDrawable = null;
        a(context, attributeset);
    }

    public VectorView(Context context, AttributeSet attributeset, int i1)
    {
        super(context, attributeset, i1);
        zoom = 1.0F;
        scale = -1F;
        left = 0.0F;
        top = 0.0F;
        vectors = null;
        pictures = null;
        defaultBound = null;
        drawBackground = true;
        selectionDrawable = null;
        a(context, attributeset);
    }

    private void refresh()
    {
        float margin = 25f;
        boolean flag = false;
        width = 0.0F;
        height = 0.0F;
        left = 0.0F;
        top = 0.0F;
        RectF limit = null;

        for(SVG svg : vectors)
        {
            RectF rectf1 = svg.getBounds();
            limit = svg.getLimits();

            if (rectf1 != null) {
                flag = true;
                left = -rectf1.left;
                top = -rectf1.top;
                width = rectf1.width();
                height = rectf1.height();
            }
        }

        if (!flag) {
            RectF rectf = limit; //new RectF(0,0,500f,500f); //defaultBound;
            //float marginX = (rectf.width() - getWidth())/2;
            //rectf.
            if (rectf != null)
            {
                left = Math.min(left, -rectf.left+margin);
                top = Math.min(top, -rectf.top+margin);
                width = Math.max(width, rectf.width()+margin*2);
                height = Math.max(height, rectf.height()+margin*2);
            }
        }
        if (width == 0.0F || height == 0.0F)
        {
            width = getWidth();
            height = getHeight();
        }
        scale = Math.min((float)getWidth() / width, (float)getHeight() / height);
    }

    private void a(Context context, AttributeSet attributeset)
    {
        paint = new Paint();
    }

    protected void onDraw(Canvas canvas)
    {
        super.onDraw(canvas);
        if (scale == -1F)
        {
            refresh();
        }
        canvas.save();
        canvas.scale(scale, scale);
        canvas.translate(left, top);
        if (drawBackground)
        {
            if (selectionDrawable == null)
            {
                paint.setStyle(android.graphics.Paint.Style.FILL);
                paint.setColor(-1);
                canvas.drawRect(-10000F, -10000F, 10000F, 10000F, paint);
            }
            if (selected && selectionDrawable == null)
            {
                paint.setStyle(android.graphics.Paint.Style.FILL);
                paint.setColor(0xfff3f3f3);
                canvas.drawRect(-10000F, -10000F, 10000F, 10000F, paint);
            }
        }

        for(Picture p : pictures)
        {
            p.draw(canvas);
        }

        if (selected && selectionDrawable != null)
        {
            selectionDrawable.draw(canvas);
        }
        canvas.restore();
    }

    protected void onMeasure(int i1, int j1)
    {
        int k1 = android.view.View.MeasureSpec.getMode(i1);
        int l1 = android.view.View.MeasureSpec.getSize(i1);
        int i2 = android.view.View.MeasureSpec.getMode(j1);
        int j2 = android.view.View.MeasureSpec.getSize(j1);
        if (k1 != 0)
        {
            j2 = l1;
        } else
        if (i2 != 0)
        {
            l1 = j2;
        } else
        {
            int k2 = j2;
            j2 = l1;
            l1 = k2;
        }
        setMeasuredDimension(j2, l1);
    }

    public void setDefaultBounds(RectF rectf)
    {
        defaultBound = rectf;
    }

    public void setDrawBackground(boolean flag)
    {
        drawBackground = flag;
    }

    public void setSelected(boolean flag)
    {
        selected = flag;
        invalidate();
    }

    public void setSelectionVector(PictureDrawable picturedrawable)
    {
        selectionDrawable = picturedrawable;
    }

    public void setVectors(SVG ab[])
    {
    	vectors = ab;
        int length = vectors.length;
        pictures = new Picture[length];
        for (int i = 0; i < length; i++)
        {
        	pictures[i] = vectors[i].getPicture();
        }

        scale = -1F;
    }

    public void setZoom(float f1)
    {
        zoom = f1;
    }
}
