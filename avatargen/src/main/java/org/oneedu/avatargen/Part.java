package org.oneedu.avatargen;

import android.graphics.Picture;
import android.graphics.RectF;

import org.json.JSONObject;

public class Part {
    public Picture picture;
    public float scaleX;
    public float scaleY;
    public float offsetX;
    public float offsetY;
    public RectF bound = new RectF();
    
    private float minScaleX;
    private float maxScaleX;
    private float minScaleY;
    private float maxScaleY;
    private float scaleFactor = 500f;
    
    public void scale(float diffX, float diffY) {
    	if(diffX < 0) {
			scaleX = Math.max( scaleX + (diffX/scaleFactor), minScaleX);
		} else {
			scaleX = Math.min( scaleX + (diffX/scaleFactor), maxScaleX);
		}
		if(diffY < 0) {
			scaleY = Math.max( scaleY + (diffY/scaleFactor), minScaleY);
		} else {
			scaleY = Math.min( scaleY + (diffY/scaleFactor), maxScaleY);
		}
    }

    public void setScale(float scaleX, float scaleY) {
        this.scaleX = scaleX;
        this.scaleY = scaleY;
    }

    public Part(Picture picture) {
        this.picture = picture;
        scaleX = 1f;
        scaleY = 1f;
    }
    
    public Part(Picture picture, float minScaleX, float minScaleY, float maxScaleX, float maxScaleY) {
    	this(picture);
    	this.minScaleX = minScaleX;
    	this.minScaleY = minScaleY;
    	this.maxScaleX = maxScaleX;
    	this.maxScaleY = maxScaleY;
    }
    
    public boolean contains(float x, float y) { 	
    	return bound.contains(x, y);
    }

    public JSONObject getJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("scaleX", scaleX);
            json.put("scaleY", scaleY);
        } catch (Exception e) {
            Util.debug(e.getMessage());
        }
        return json;
    }
}
