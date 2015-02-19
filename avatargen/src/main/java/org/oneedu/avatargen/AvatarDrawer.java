package org.oneedu.avatargen;

import android.graphics.*;
import android.view.animation.CycleInterpolator;
import android.view.animation.LinearInterpolator;
import com.larvalabs.svgandroid.SVG;
import com.larvalabs.svgandroid.SVGParser;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

import rx.functions.Action1;

import static org.oneedu.avatargen.Constants.*;
import static org.oneedu.avatargen.AssetDatabase.*;

public class AvatarDrawer {

    /**
     * Constant needed for drawing circles (or close approximation thereof) with splines.
     */
    private static final float KAPPA = 0.5522847498f;

    /**
     * An arbitrarily large value used when specifying clipping regions where only one edge of the clip is important.
     */
    private static final int MAX_CLIP = 20000;

    /**
     * Random number generator.
     */
    static final Random RANDOM = new Random();

    /**
     * This is the clipping path for the android body (can be parsed with the SVG library).
     */
    //private static final String BODY_CLIP = "M140.396,175.489v177.916c0,10.566,8.566,19.133,19.135,19.133h2.303h20.331h48.709h37.371h48.708h11.881h10.752c10.569,0,19.137-8.562,19.137-19.133V175.489H140.396z";
    private static final String BODY_CLIP = "M323.582,375.08c-1.005,11.803-11.52,21.46-23.366,21.46h-98.88c-11.846,0-22.346-9.658-23.333-21.463l-13.127-156.901c-0.987-11.805,7.896-21.463,19.742-21.463h132.621c11.846,0,20.715,9.657,19.709,21.46L323.582,375.08z";
    /**
     * The scaling factor when drawing the android.
     */
    private float scaleFactor = 1f;

    /**
     * The currently-running animations, keyed by type (only one animation of each type can run at once).
     */
    private HashMap<String, AndroidAnimation> animations;

    // The clothing vector graphics.
    private Picture shirtBody = null;
    private Picture shirtArm = null;
    private Picture shirtTop = null;
    private Picture pantsLeg = null;
    private Picture pantsSkirt = null;
    private Picture pantsTop = null;
    private Picture hairBack = null;
    private Picture hairFront = null;
    private Picture shoes = null;
    private Picture glasses = null;
    private Picture beard = null;
    private Picture hats = null;
    private Picture rightHandAcc = null;
    private Picture leftHandAcc = null;
    private Picture bodyAccFront = null;
    private Picture faceAcc = null;
    private Picture bodyAccBack;
    private Picture backGround;

    /**
     * The accessories.
     */
    private AccessorySet accessories = new AccessorySet();

    /**
     * The clipping bath for the body (it is drawn within a clip so that the contents of the shirts can scale
     * proportionally without changing the shape of the android).
     */
    private Path bodyClip;

    /**
     * The ambient antennae animation.
     */
    private AntennaAnimation antennaAnimation;

    /**
     * The transform used to draw the android.
     */
    private Matrix transform = null;

    /**
     * The reverse of the transform used to the draw the android. This can be used to convert a touch event back in to
     * the original co-ordinates the android was drawn in.
     */
    public Matrix reverseTransform = null;

    // These variables track various bounds of the android, but aren't interesting for the wallpaper.
    private RectF droidBounds;
    private float legsHeight;
    private float headHeight;
    private PointF droidCenter;

    // The android body parts, including scaling and offset information.
    public Part droidBody;
    public Part droidHead;
    public Part droidArm;
    public Part droidLegs;
    // The remaining android body parts
    private Picture feet;
    private Picture antenna;
    // The path for drawing the arm (this is generated dynamically so as to keep the shoulders and hands round when
    // drawing the arm at different scales.
    private Path armPath;

    // Size information for the view
    private int width, height;

    private float droidWidth;
    private RectF hairBounds = DEFAULT_HAIR_BOUNDS;

    private RectF workRect = new RectF();
    private Paint workPaint;

    private int skinColor = ANDROID_COLOR;
    private int hairColor = HAIR_COLOR_DEFAULT;

    private int backgroundRed = 0XFF;
    private int backgroundGreen = 0XFF;
    private int backgroundBlue = 0XFF;

    private ZoomInfo zoom = null;
    private float driftAngle;

	private String hair;
    private int     hatHeightDiff;
    private int     hairHeightDiff;


    /**
     * Sets the zoom information for this android drawer, which will also reset the drift animation.
     * @param zoom
     */
    public void setZoom(ZoomInfo zoom) {
        this.zoom = zoom;
        setDrift(zoom.driftAmount * (POINT_BOTTOM_OF_LEFT_EYE.y - POINT_TOP_OF_LEFT_EYE.y), zoom.driftAngle, zoom.driftTime);
    }

    public void loadConfig(final AssetDatabase db, AvatarConfig config) {
        feet = config.droidFeet.picture;
        droidBody = config.droidBody;
        droidHead = config.droidHead;
        droidArm = config.droidArm;
        droidLegs = config.droidLegs;

        backGround = getPicture(db.getSVGForAsset("backgrounds", "Beach", null));   // default

        armPath = new Path();
        createArmPath();

        computeArmOffset();
        computeLegsOffset();
        rescale();


        config.setSubscriber(new Action1<JSONObject>() {
            @Override
            public void call(JSONObject jsonObject) {
                Iterator<String> it = jsonObject.keys();
                while (it.hasNext()) {
                    String key = it.next();
                    String v;
                    try {
                        v = jsonObject.getString(key);
                        setConfig(db, ConfigPart.valueOf(key), v);
                    } catch (JSONException e) {
                        Util.debug(e.getMessage());
                    } catch (IllegalArgumentException e) {
                        continue;
                    }
                }
                computeArmOffset();
                computeLegsOffset();
                rescale();
            }
        });
    }
    /**
     * Create a new android drawer.
     */
    public AvatarDrawer() {
        droidBounds = new RectF();
        droidCenter = new PointF();
        // Setup body clip
        bodyClip = SVGParser.parsePath(BODY_CLIP);
        reverseTransform = new Matrix();
        workPaint = new Paint();
        workPaint.setAntiAlias(true);

        animations = new HashMap<String, AndroidAnimation>();
    }

    /**
     * Sets the background color by component.
     * @param red the red value (from 0 to 255).
     * @param green the green value (from 0 to 255).
     * @param blue the blue value (from 0 to 255).
     */
    public void setBackgroundColor(int red, int green, int blue) {
        this.backgroundRed = red;
        this.backgroundGreen = green;
        this.backgroundBlue = blue;
    }

    /**
     * Sets the background color as a packed int. Note that the alpha channel is ignored.
     * @param rgb a packed 24-bit color int.
     */
    public void setBackgroundColor(int rgb) {
        int r = (0xFF0000 & rgb) >> 16;
        int g = (0xFF00 & rgb) >> 8;
        int b = (0xFF & rgb);
        setBackgroundColor(r, g, b);
    }

    /**
     * Convenience method to get a Picture object from an SVG.
     * @param svg the parsed SVG.
     * @return the picture, or null if svg was null.
     */
    private static Picture getPicture(SVG svg) {
        if (svg == null) {
            return null;
        } else {
            return svg.getPicture();
        }
    }

    private void setSkinColor(AssetDatabase db, int color) {
    	skinColor = color;
        droidHead.picture = db.getSVGForResource(R.raw.avatar_head, ANDROID_COLOR, skinColor).getPicture();
        droidBody.picture = db.getSVGForResource(R.raw.avatar_body, ANDROID_COLOR, skinColor).getPicture();
        droidLegs.picture = db.getSVGForResource(R.raw.avatar_legs, ANDROID_COLOR, skinColor).getPicture();
        feet = db.getSVGForResource(R.raw.avatar_feet, ANDROID_COLOR, skinColor).getPicture();
        //antenna = db.getSVGForResource(R.raw.android_antenna, ANDROID_COLOR, skinColor).getPicture();
    }

	private void setHairColor(AssetDatabase db, int color) {
        hairColor = color;
		hairBack = getPicture(db.getSVGForAsset(ASSET_HAIR, this.hair, HAIR_BACK, HAIR_COLOR_DEFAULT, color));
        hairFront = getPicture(db.getSVGForAsset(ASSET_HAIR, this.hair, HAIR_FRONT, HAIR_COLOR_DEFAULT, color));
	}
    
    private void setShoes(AssetDatabase db, String shoeConf) {
    	shoes = getPicture(db.getSVGForAsset(ASSET_SHOES, shoeConf, null));
        if(shoes == null)
            shoes = getPicture(db.getSVGForAsset(ASSET_SHOES, shoeConf, SHOES));
    }
    
    private void setPants(AssetDatabase db, String pants) {
    	pantsLeg = getPicture(db.getSVGForAsset(ASSET_PANTS, pants, PANTS_LEG));
        pantsTop = getPicture(db.getSVGForAsset(ASSET_PANTS, pants, PANTS_TOP));
        pantsSkirt = getPicture(db.getSVGForAsset(ASSET_PANTS, pants, "skirt"));
    }
    
    private void setHair(AssetDatabase db, String hair) {
    	this.hair = hair;
    	hairBack = getPicture(db.getSVGForAsset(ASSET_HAIR, hair, HAIR_BACK, HAIR_COLOR_DEFAULT, hairColor));
        hairFront = getPicture(db.getSVGForAsset(ASSET_HAIR, hair, HAIR_FRONT, HAIR_COLOR_DEFAULT, hairColor));		
	}

	private void setGlasses(AssetDatabase db, String glassesConf) {
    	glasses = getPicture(db.getSVGForAsset(ASSET_GLASSES, glassesConf, null));
	}

	private void setShirt(AssetDatabase db, String shirt) {
		shirtBody = getPicture(db.getSVGForAsset(ASSET_SHIRT, shirt, SHIRT_BODY));
		shirtArm = getPicture(db.getSVGForAsset(ASSET_SHIRT, shirt, SHIRT_ARM));
		shirtTop = getPicture(db.getSVGForAsset(ASSET_SHIRT, shirt, SHIRT_TOP));		
	}

    private void setHats(AssetDatabase db, String item) {
        hats = getPicture(db.getSVGForAsset(ASSET_HATS, item, HATS));
    }

    private void setHandAcc(AssetDatabase db, String item) {
        rightHandAcc = getPicture(db.getSVGForAsset(ASSET_ACCESSORIES, item, "righthand"));
        leftHandAcc = getPicture(db.getSVGForAsset(ASSET_ACCESSORIES, item, "lefthand"));
    }

    private void setBodyAcc(AssetDatabase db, String item) {
        bodyAccFront = getPicture(db.getSVGForAsset(ASSET_BODYACC, item, "front"));
        bodyAccBack = getPicture(db.getSVGForAsset(ASSET_BODYACC, item, "back"));
    }
    
    public void setConfig(AssetDatabase db, ConfigPart part, String item) {
    	switch(part) {
        case hats:
            setHats(db, item);
            break;

    	case handAcc:
            setHandAcc(db, item);
    		break;

        case bodyAcc:
            setBodyAcc(db, item);
            break;
    		
    	case skinColor:
    		setSkinColor(db,Integer.parseInt(item));
    		break;
    		
    	case shoes:
    		setShoes(db, item);
    		break;
    		
    	case pants:
    		setPants(db, item);
    		break;
    		
    	case shirt:
    		setShirt(db, item);
    		break;
    		
    	case glasses:
    		setGlasses(db, item);
    		break;
    		
    	case hair:
    		setHair(db, item);
    		break;
    		
    	case hairColor:
    		setHairColor(db, Integer.parseInt(item));
    		break;

        case beard:
            beard = getPicture(db.getSVGForAsset(ASSET_BEARD, item, null));
            break;

        case face:
            faceAcc = getPicture(db.getSVGForAsset(ASSET_FACE, item, null));
            break;

        case backgrounds:
            backGround = getPicture(db.getSVGForAsset("backgrounds", item, null));
    		break;

		default:
			break;
    	}
    }

    public void computeArmOffset() {
        // Account for body width
        droidArm.offsetX = -(POINT_TOP_OF_BODY.x - POINT_TOP_LEFT_OF_BODY.x) * (droidBody.scaleX - 1);
        // Account for arm width
        droidArm.offsetX -= (POINT_RIGHT_OF_LEFT_SHOULDER.x - POINT_LEFT_OF_LEFT_SHOULDER.x) * (droidArm.scaleX - 1);
    }

    public void computeLegsOffset() {
        // Account for body height
        droidLegs.offsetY = (POINT_BOTTOM_OF_BODY.y - POINT_TOP_OF_BODY.y) * (droidBody.scaleY - 1);
    }

    /**
     * Runs an animation on this android.
     * @param animation the animation to run.
     */
    public void addAnimation(AndroidAnimation animation) {
        animations.put(animation.getType().toString(), animation);
    }

    /**
     * Gets an animation by type.
     * @param type the animation type.
     * @return the animation, or null if no such animation of that type is running.
     */
    public AndroidAnimation getAnimation(AndroidAnimation.Type type) {
        return animations.get(type.toString());
    }

    /**
     * Removes an animation by type.
     * @param type the animation type.
     */
    public void removeAnimation(AndroidAnimation.Type type) {
        animations.remove(type.toString());
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    /**
     * Constructs an arm path based on the arm scaling information in both the X and Y dimensions.
     */
    private void createArmPath() {
        armPath.rewind();
        float width = (POINT_RIGHT_OF_LEFT_SHOULDER.x - POINT_LEFT_OF_LEFT_SHOULDER.x) * droidArm.scaleX;
        float height = (POINT_BOTTOM_OF_LEFT_ARM.y - POINT_TOP_OF_LEFT_ARM.y) * droidArm.scaleY;
        float x = POINT_LEFT_OF_LEFT_SHOULDER.x;
        float y = POINT_TOP_OF_LEFT_ARM.y;
        float r = width / 2;
        float c = r * KAPPA;
        armPath.moveTo(x, y + r);
        armPath.rCubicTo(0, -c, r - c, -r, r, -r);
        armPath.rCubicTo(c, 0, r, r - c, r, r);
        armPath.rLineTo(0, height - width);
        armPath.rCubicTo(0, c, -(r - c), r, -r, r);
        armPath.rCubicTo(-c, 0, -r, -(r - c), -r, -r);
        armPath.rLineTo(0, -(height - width));
        armPath.close();
    }

    /**
     * Gets the current antenna angle from the ambient antennae animation object.
     * @param i the antenna index (0 = left, 1 = right).
     * @return the angle in radians.
     */
    protected float getAmbientAntennaAngle(int i) {
        return antennaAnimation.getAngle(i);
    }

    /**
     * Computes new scaling information based on changes to the android's dimensions, assets or zoom information.
     */
    public void rescale() {
        Util.debug("Rescaling for w: " + getWidth() + ", h: " + getHeight());
        // Compute width of droid
        float bodyWidth = (POINT_TOP_RIGHT_OF_BODY.x - POINT_TOP_LEFT_OF_BODY.x) * droidBody.scaleX;
        float halfBodyWidth = bodyWidth/2;
        // Add in arms
        float armWidth = (POINT_TOP_LEFT_OF_BODY.x - POINT_LEFT_OF_LEFT_SHOULDER.x) * droidArm.scaleX;
        bodyWidth += 2 * armWidth; //(POINT_TOP_LEFT_OF_BODY.x - POINT_LEFT_OF_LEFT_SHOULDER.x) * droidArm.scaleX;
        float headWidth = (POINT_RIGHT_BOTTOM_OF_HEAD.x - POINT_LEFT_BOTTOM_OF_HEAD.x) * droidHead.scaleX;
        float halfHeadWidth = headWidth/2;
        droidWidth = Math.max(bodyWidth, headWidth);

        // Compute height of droid
        float topHeight = Math.min(HEAD_BOUNDS_TOP, hairBounds.top);
        // Also consider a tall hat
        //SVG hat = accessories.getSVGForType(Accessory.TYPE_HEAD);
        hatHeightDiff=0;
        hairHeightDiff=0;

        if (hats != null && hats.getHeight() > 500) {
            hatHeightDiff = hats.getHeight() - 500;
        }

        if (hairFront != null && hairFront.getHeight() > 500) {
            hairHeightDiff = hairFront.getHeight() - 500;
        }
        topHeight -= Math.max(hatHeightDiff, hairHeightDiff);

        float tHeight = topHeight;
        headHeight = (POINT_BOTTOM_OF_HEAD.y - tHeight) * droidHead.scaleY + (POINT_TOP_OF_BODY.y - POINT_BOTTOM_OF_HEAD.y);
        //float topOfHead = POINT_BOTTOM_OF_HEAD.y - (POINT_BOTTOM_OF_HEAD.y - HEAD_BOUNDS_TOP) * droidHead.scaleY + (POINT_TOP_OF_BODY.y - POINT_BOTTOM_OF_HEAD.y);
        //float topOfHair = POINT_BOTTOM_OF_HEAD.y - (POINT_BOTTOM_OF_HEAD.y - hairBounds.top) * droidHead.scaleY + (POINT_TOP_OF_BODY.y - POINT_BOTTOM_OF_HEAD.y);
        float bodyHeight = (POINT_BOTTOM_OF_BODY.y - POINT_TOP_OF_BODY.y) * droidBody.scaleY;
        float halfBodyHeight = bodyHeight/2;

        droidHead.bound.set(CENTER_X - halfHeadWidth, POINT_BOTTOM_OF_HEAD.y - headHeight , CENTER_X + halfHeadWidth, POINT_BOTTOM_OF_HEAD.y);
        droidBody.bound.set(POINT_CENTER_OF_BODY.x - halfBodyWidth, POINT_CENTER_OF_BODY.y - halfBodyHeight,POINT_CENTER_OF_BODY.x + halfBodyWidth, POINT_CENTER_OF_BODY.y + halfBodyHeight);
        
        legsHeight = (POINT_CENTER_OF_LEFT_FOOT.y - POINT_BOTTOM_OF_BODY.y) * droidLegs.scaleY + (POINT_BOTTOM_OF_LEFT_LEG.y - POINT_CENTER_OF_LEFT_FOOT.y) * droidLegs.scaleX;
        droidLegs.bound.set(droidBody.bound.left, droidBody.bound.bottom, droidBody.bound.right, droidBody.bound.bottom+legsHeight);
        droidArm.bound.set(droidBody.bound.left-armWidth, droidBody.bound.top, droidBody.bound.right+armWidth, droidLegs.bound.bottom);
        // It's possible that the hair is longer than anything else
        float hairBottomLength = (hairBounds.bottom - POINT_BOTTOM_OF_HEAD.y) * droidHead.scaleY - bodyHeight;
        if (hairBottomLength > legsHeight) {
            legsHeight = hairBottomLength;
        }
        createArmPath();
        float height = headHeight + bodyHeight + legsHeight;
        droidBounds.set(POINT_CENTER_OF_BODY.x - droidWidth / 2, POINT_TOP_OF_BODY.y - headHeight, POINT_CENTER_OF_BODY.x + droidWidth / 2, POINT_TOP_OF_BODY.y + bodyHeight + legsHeight);
        droidCenter.set((droidBounds.left + droidBounds.right) / 2, (droidBounds.top + droidBounds.bottom) / 2);
        // Add in margins
        droidWidth *= (1 + MARGIN_SIZE);
        height *= (1 + MARGIN_SIZE);
        if (zoom == null) {
            // Use smallest scaling factor over the two dimensions that will fit the droid
            final float viewWidth = getWidth();
            float scaleX = viewWidth / droidWidth;
            float scaleY = getHeight() / height;
            scaleFactor = Math.min(scaleX, scaleY);
            // Revert these so they must be recalculated
            transform = new Matrix();
            transform.preTranslate(viewWidth / 2 - droidCenter.x, getHeight() / 2 - droidCenter.y);
            transform.preScale(scaleFactor, scaleFactor, droidCenter.x, droidCenter.y);
        } else {
            transform = new Matrix();
            float eyeDiameter = (POINT_BOTTOM_OF_LEFT_EYE.y - POINT_TOP_OF_LEFT_EYE.y);
            // Figure out where the center point is of our zoom
            float centerX;
            if (zoom.leftEye) {
                centerX = CENTER_X + (POINT_LEFT_EYE.x - CENTER_X) * droidHead.scaleX;
            } else {
                centerX = CENTER_X + (POINT_RIGHT_EYE.x - CENTER_X) * droidHead.scaleX;
            }
            centerX += zoom.offsetX * eyeDiameter;
            float centerY = POINT_BOTTOM_OF_HEAD.y + (POINT_LEFT_EYE.y - POINT_BOTTOM_OF_HEAD.y) * droidHead.scaleY;
            centerY += zoom.offsetY * eyeDiameter;
            // Center this in the view
            float midX = getWidth()/2;
            float midY = getHeight()/2;
            float tx = midX - centerX;
            float ty = midY - centerY;
            transform.preTranslate(tx, ty);
            // Now scale
            scaleFactor = width * zoom.scale / headWidth;
            transform.preScale(scaleFactor, scaleFactor, centerX, centerY);
            // Finally, rotate
            transform.preRotate(zoom.angle, centerX, centerY);
        }
        //transform.preTranslate(0, -2 * (POINT_BOTTOM_OF_LEFT_LEG.y - POINT_CENTER_OF_LEFT_FOOT.y));
        transform.invert(reverseTransform);
        // Set up scaling info
        float[] topCenter = {POINT_TOP_OF_BODY.x, POINT_TOP_OF_BODY.y};
        transform.mapPoints(topCenter);
        Util.debug("Scale factor: " + scaleFactor);
    }

    /**
     * Advance the animations one frame.
     */
    public void stepAnimations() {
        //antennaAnimation.step();
        final Collection<AndroidAnimation> anims = animations.values();
        for (AndroidAnimation anim : anims) {
            anim.step();
        }
    }

    private void setDrift(float distance, int angle, long time) {
        // Remove any old drifts
        removeAnimation(AndroidAnimation.Type.DRIFT);
        driftAngle = (float)(angle * Math.PI / 180);
        AndroidAnimation drift = new AndroidAnimation(AndroidAnimation.Type.DRIFT, time);
        drift.setInterpolator(new LinearInterpolator(), 0f, distance);
        addAnimation(drift);
    }

    public void draw(Canvas canvas) {
        draw(canvas, true);
    }
    /**
     * Actually draw the android to a canvas.
     * @param canvas the canvas on which to draw the android.
     */
    public void draw(Canvas canvas, boolean drawBackground) {
        if(drawBackground) {
            backGround.draw(canvas);
            //canvas.drawARGB(0xFF, backgroundRed, backgroundGreen, backgroundBlue);
        }

        int startCount = canvas.getSaveCount();
        canvas.save();
        // Only needed once for first draw
        if (scaleFactor == -1f) {
            rescale();
        }
        boolean animationsActive = !animations.isEmpty();
        if (animationsActive) {
            Iterator<AndroidAnimation> anims = animations.values().iterator();
            while (anims.hasNext()) {

                AndroidAnimation animation = anims.next();
                if (animation.step()) {
                    anims.remove();
                }
            }
        }
        AndroidAnimation headTilt = getAnimation(AndroidAnimation.Type.HEAD_TILT);
        AndroidAnimation nod = getAnimation(AndroidAnimation.Type.NOD);
        // Do drift animation (if present)
        AndroidAnimation drift = getAnimation(AndroidAnimation.Type.DRIFT);
        if (drift != null) {
            canvas.translate((float)(Math.cos(driftAngle) * drift.getValue()), (float)(Math.sin(driftAngle) * drift.getValue()));
        }
        
        AndroidAnimation hover = getAnimation(AndroidAnimation.Type.HOVERING);
        if (hover != null) {
            canvas.translate(0f, (float)(-hover.getValue()));
        }
        
        
        // Do regular transform
        canvas.concat(transform);
        // Draw hair (behind)
        {
            canvas.save();
            if (headTilt != null) {
                canvas.rotate(headTilt.getValue(), POINT_BOTTOM_OF_HEAD.x, POINT_BOTTOM_OF_HEAD.y);
            }
            if (nod != null) {
                canvas.translate(0f, nod.getValue());
            }
            canvas.scale(droidHead.scaleX, droidHead.scaleY, POINT_BOTTOM_OF_HEAD.x, POINT_BOTTOM_OF_HEAD.y);
            if (hairBack != null) {
                hairBack.draw(canvas);
            }
            canvas.restore();
        }
        // Draw legs
        {
            canvas.save();
            canvas.translate(droidLegs.offsetX, droidLegs.offsetY);
            canvas.scale(droidLegs.scaleX, droidLegs.scaleY, POINT_BOTTOM_OF_BODY.x, POINT_BOTTOM_OF_BODY.y);
            droidLegs.picture.draw(canvas);
            canvas.restore();
        }
        {
            canvas.save();
            canvas.translate(droidLegs.offsetX, droidLegs.offsetY);
            canvas.scale(droidLegs.scaleX, droidLegs.scaleY, POINT_BOTTOM_OF_BODY.x, POINT_BOTTOM_OF_BODY.y);
            // Make feet and shoes proportional (fixed on the width of the legs)
            // Draw feet
            if(feet != null)
            {
                canvas.save();
                canvas.scale(1f, droidLegs.scaleX / droidLegs.scaleY, CENTER_X, POINT_CENTER_OF_LEFT_FOOT.y);
                feet.draw(canvas);
                canvas.restore();
            }
            // Draw pants
            {
                for (int i = 0; i < 2; i++) {
                    canvas.save();
                    if (i == 1) {
                        canvas.scale(-1f, 1f, CENTER_X, TOP_Y);
                    }
                    if (pantsLeg != null) {
                        pantsLeg.draw(canvas);
                    }
                    {
                        canvas.save();
                        canvas.scale(1f, droidLegs.scaleX / droidLegs.scaleY, (i == 0 ? POINT_TOP_OF_LEFT_LEG_CENTER.x : POINT_TOP_OF_RIGHT_LEG_CENTER.x), POINT_BOTTOM_OF_BODY.y);
                        if (pantsTop != null) {
                            pantsTop.draw(canvas);
                        }
                        canvas.restore();
                    }
                    canvas.restore();

                }
            }
            canvas.restore();

            // Draw shoes
            {
                canvas.save();
                canvas.translate(droidLegs.offsetX, droidLegs.offsetY);
                canvas.scale(droidLegs.scaleX, droidLegs.scaleY, POINT_BOTTOM_OF_BODY.x, POINT_BOTTOM_OF_BODY.y);
                // Draw shoes
                {
                    canvas.scale(1f, droidLegs.scaleX / droidLegs.scaleY, CENTER_X, POINT_CENTER_OF_LEFT_FOOT.y);
                    if (shoes != null) {
                        shoes.draw(canvas);
                        canvas.save();
                        canvas.scale(-1f, 1f, CENTER_X, TOP_Y);
                        shoes.draw(canvas);
                        canvas.restore();
                    }
                }
                canvas.restore();
            }

            if (pantsSkirt != null) {
                canvas.save();
                canvas.translate(droidLegs.offsetX, droidLegs.offsetY);
                canvas.scale(droidLegs.scaleX, 1, POINT_BOTTOM_OF_BODY.x, POINT_BOTTOM_OF_BODY.y);
                pantsSkirt.draw(canvas);
                canvas.restore();
            }
        }

        // Draw body
        {
            canvas.save();
            canvas.save(Canvas.MATRIX_SAVE_FLAG);
            canvas.scale(droidBody.scaleX, droidBody.scaleY, POINT_TOP_OF_BODY.x, POINT_TOP_OF_BODY.y);
            if (bodyAccBack != null) {
                canvas.save();
                bodyAccBack.draw(canvas);
                canvas.restore();
            }
            canvas.clipPath(bodyClip);
            droidBody.picture.draw(canvas);
            canvas.restore();
            if (shirtBody != null) {
                float scaleX = droidBody.scaleX < 1.3f ? droidBody.scaleX / 1.3f : 1.0f;
                float scaleY = droidBody.scaleY < 1.2f ? droidBody.scaleY / 1.2f : 1.0f;
                canvas.scale(scaleX, scaleY, POINT_TOP_OF_BODY.x, POINT_TOP_OF_BODY.y);
                shirtBody.draw(canvas);
            }
            canvas.restore();
        }
        // Draw arms
        {
            // If zooming, do special considerations here
            float armOffsetX = droidArm.offsetX;
            float armScaleX = droidArm.scaleX;
            Picture arm = shirtArm;
            for (int i = 0; i < 2; i++) {
                canvas.save();
                AndroidAnimation shrug = getAnimation(AndroidAnimation.Type.SHRUG);
                if (shrug != null) {
                    canvas.translate(0f, shrug.getValue());
                }
                if (i == 1) {
                    // Flip to right hand
                    canvas.scale(-1f, 1f, CENTER_X, TOP_Y);
                }
                canvas.translate(armOffsetX, droidArm.offsetY);
                if (i == 0) {
                    AndroidAnimation animation = getAnimation(AndroidAnimation.Type.WAVE);
                    if (animation != null) {
                        canvas.rotate(animation.getValue(), POINT_TOP_OF_LEFT_ARM.x, POINT_TOP_OF_LEFT_ARM.y);
                    }
                }
                workPaint.setColor(skinColor);
                canvas.drawPath(armPath, workPaint);
                //Picture handAccessory = accessories.getPictureForType(i == 0 ? Accessory.TYPE_ON_LEFT_HAND : Accessory.TYPE_ON_RIGHT_HAND);
                Picture handAccessory = i == 0 ? leftHandAcc : rightHandAcc; //temporary
                Picture gloveAccessory = accessories.getPictureForType(Accessory.TYPE_ON_BOTH_HANDS);
                // Draw shirt
                if (arm != null || handAccessory != null || gloveAccessory != null) {
                    //// DRAW SHOULDER
                    {
                        canvas.save();
                        // Scale proportionally
                        canvas.translate((POINT_LEFT_SHOULDER.x - POINT_LEFT_OF_LEFT_SHOULDER.x) * (armScaleX - 1), 0f);
                        canvas.scale(armScaleX, armScaleX, POINT_LEFT_SHOULDER.x, POINT_TOP_OF_LEFT_ARM.y);
                        workRect.set(-MAX_CLIP, -MAX_CLIP, MAX_CLIP, POINT_LEFT_SHOULDER.y);
                        canvas.save();
                        canvas.clipRect(workRect);
                        if (arm != null) {
                            arm.draw(canvas);
                        }
                        canvas.restore();
                        canvas.restore();
                    }
                    //// DRAW MAIN PART OF ARM
                    // This is the excess length from the shoulder
                    float extraLength = (POINT_LEFT_SHOULDER.y - POINT_TOP_OF_LEFT_ARM.y) * (armScaleX - droidArm.scaleY);
                    // This is the actual arm length
                    float armLength = (POINT_LEFT_HAND.y - POINT_LEFT_SHOULDER.y) * droidArm.scaleY;
                    // This is the extra scale ratio needed
                    float goalLength = armLength - 2 * extraLength;
                    float newScale = goalLength / (POINT_LEFT_HAND.y - POINT_LEFT_SHOULDER.y);
                    {
                        canvas.save();
                        canvas.translate((POINT_LEFT_SHOULDER.x - POINT_LEFT_OF_LEFT_SHOULDER.x) * (armScaleX - 1), 0f);
                        canvas.scale(armScaleX, droidArm.scaleY, POINT_TOP_OF_LEFT_ARM.x, POINT_TOP_OF_LEFT_ARM.y);
                        canvas.scale(1f, newScale / droidArm.scaleY, POINT_CENTER_OF_LEFT_ARM.x, POINT_CENTER_OF_LEFT_ARM.y);
                        workRect.set(-MAX_CLIP, POINT_LEFT_SHOULDER.y, MAX_CLIP, POINT_LEFT_HAND.y);
                        canvas.clipRect(workRect);
                        if (arm != null) {
                            arm.draw(canvas);
                        }
                        canvas.restore();
                    }
                    //// DRAW HAND
                    {
                        canvas.save();
                        //canvas.translate(-droidArm.offsetX, -droidArm.offsetY);
                        // Sale proportionally
                        canvas.translate((POINT_LEFT_SHOULDER.x - POINT_LEFT_OF_LEFT_SHOULDER.x) * (armScaleX - 1), 0f);
                        canvas.scale(armScaleX, droidArm.scaleY, POINT_LEFT_SHOULDER.x, POINT_TOP_OF_LEFT_ARM.y);
                        canvas.scale(1f, armScaleX / droidArm.scaleY, POINT_BOTTOM_OF_LEFT_ARM.x, POINT_BOTTOM_OF_LEFT_ARM.y);
                        workRect.set(-MAX_CLIP, POINT_LEFT_HAND.y, MAX_CLIP, MAX_CLIP);
                        canvas.clipRect(workRect);
                        if (arm != null) {
                            arm.draw(canvas);
                        }
                        canvas.restore();
                    }
                    //// Draw hand accessories
                    if (handAccessory != null || gloveAccessory != null) {
                        canvas.save();
                        canvas.translate((POINT_LEFT_SHOULDER.x - POINT_LEFT_OF_LEFT_SHOULDER.x) * (armScaleX - 1), 0f);
                        canvas.translate(0f, (POINT_BOTTOM_OF_LEFT_ARM.y - POINT_TOP_OF_LEFT_ARM.y) * (droidArm.scaleY - 1));
                        canvas.scale(armScaleX, armScaleX, POINT_BOTTOM_OF_LEFT_ARM.x, POINT_BOTTOM_OF_LEFT_ARM.y);
                        if (gloveAccessory != null) {
                            gloveAccessory.draw(canvas);
                        }
                        if (handAccessory != null) {
                            if (i == 1) {
                                // revert back to order //Flip to right hand
                                canvas.scale(-1f, 1f, CENTER_X, TOP_Y);
                            }
                            handAccessory.draw(canvas);
                        }
                        canvas.restore();
                    }

                }
                canvas.restore();
            }
        }

        // Draw head
        {
            //faceAcc = accessories.getPictureForType(Accessory.TYPE_FACE);
            canvas.save();
            if (headTilt != null) {
                canvas.rotate(headTilt.getValue(), POINT_BOTTOM_OF_HEAD.x, POINT_BOTTOM_OF_HEAD.y);
            }
            if (nod != null) {
                canvas.translate(0f, nod.getValue());
            }
            canvas.scale(droidHead.scaleX, droidHead.scaleY, POINT_BOTTOM_OF_HEAD.x, POINT_BOTTOM_OF_HEAD.y);
            droidHead.picture.draw(canvas);

            // Draw face accessory
            if (faceAcc != null) {
                faceAcc.draw(canvas);
            }

            canvas.restore();
        }

        // Draw shirt top and body accessory
        {
            Picture accessory = bodyAccFront; //accessories.getPictureForType(Accessory.TYPE_BODY);
            // Now shirt top (scaled to body directly)g
            if (shirtTop != null || accessory != null) {
                canvas.save();
                canvas.scale(droidBody.scaleX, droidBody.scaleY, POINT_TOP_OF_BODY.x, POINT_TOP_OF_BODY.y);
                if (shirtTop != null) {
                    shirtTop.draw(canvas);
                }
                if (accessory != null) {
                    accessory.draw(canvas);
                }
                canvas.restore();
            }
        }
        // Draw beard and hair in front, then glasses, then head accessory
        {
            //Picture accessory = accessories.getPictureForType(Accessory.TYPE_HEAD);
            Picture mouthAccessory = accessories.getPictureForType(Accessory.TYPE_MOUTH);
            canvas.save();
            if (headTilt != null) {
                canvas.rotate(headTilt.getValue(), POINT_BOTTOM_OF_HEAD.x, POINT_BOTTOM_OF_HEAD.y);
            }
            if (nod != null) {
                canvas.translate(0f, nod.getValue());
            }
            canvas.scale(droidHead.scaleX, droidHead.scaleY, POINT_BOTTOM_OF_HEAD.x, POINT_BOTTOM_OF_HEAD.y);
            if (beard != null) {
                canvas.save();
                beard.draw(canvas);
                canvas.restore();
            }
            if (hairFront != null) {
                canvas.save();
                canvas.translate(0, -hairHeightDiff);
                hairFront.draw(canvas);
                canvas.restore();
            }
            // Draw glasses
            if (glasses != null) {
                // Scale proportionately based on X
                canvas.save();
                canvas.scale(1f, droidHead.scaleX / droidHead.scaleY, POINT_BOTTOM_OF_HEAD.x, POINT_LEFT_EYE.y);
                glasses.draw(canvas);
                canvas.restore();
            }
            if (mouthAccessory != null) {
                mouthAccessory.draw(canvas);
            }
            if (hats != null) {
                canvas.save();
                canvas.translate(0, -hatHeightDiff);
                hats.draw(canvas);
                canvas.restore();
            }
            canvas.restore();
        }
        // Draw shoulder accessory
        for (int i = 0; i < 2; i++) {
            canvas.save();
            if (i == 1) {
                // Flip to right hand
                canvas.scale(-1f, 1f, CENTER_X, TOP_Y);
            }
            canvas.translate(droidArm.offsetX, droidArm.offsetY);
            Picture shoulderAccessory = accessories.getPictureForType(i == 0 ? Accessory.TYPE_LEFT_SHOULDER : Accessory.TYPE_RIGHT_SHOULDER);
            if (shoulderAccessory != null) {
                canvas.save();
                // Scale proportionally
                canvas.translate((POINT_LEFT_SHOULDER.x - POINT_LEFT_OF_LEFT_SHOULDER.x) * (droidArm.scaleX - 1), 0f);
                canvas.scale(droidArm.scaleX, droidArm.scaleX, POINT_LEFT_SHOULDER.x, POINT_TOP_OF_LEFT_ARM.y);
                shoulderAccessory.draw(canvas);
                canvas.restore();
            }
            canvas.restore();
        }

        canvas.restore();
    }

    /**
     * Set the dimensions for this drawer (used when rescaling).
     * @param width the width of the view rectangle.
     * @param height the height of the view rectangle.
     */
    public void setDimensions(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        this.width = width;
        this.height = height;
        rescale();
    }

    /**
     * Start a random animation to the android.
     * @param allowWaving true if waving or other arm-only animations are allowed.
     */
    public void addRandomAnimation(boolean allowWaving) {
        // Total number of animation types
        int n = 6;
        int k;
        if (allowWaving) {
            k = RANDOM.nextInt(n);
        } else {
            // No shrugging allowed either, just face stuff
            k = RANDOM.nextInt(n - 2) + 1;
        }
        switch (k) {
            case 0:
                addWaveAnimation();
                break;
            case 1:
                addBlinkAnimation();
                break;
            case 2:
                addAntennaTwitchAnimation();
                break;
            case 3:
                addHeadTiltAnimation();
                break;
            case 4:
                addNodAnimation();
                break;
            case 5:
                addShrugAnimation();
                break;
            default:
                // no-op
        }
    }

    /**
     * Make the anroid shrug.
     */
    public void addShrugAnimation() {
        AndroidAnimation animation = new AndroidAnimation(AndroidAnimation.Type.SHRUG, 1000);
        KeyFrameInterpolator keyFramer = new KeyFrameInterpolator(0, 0);
        keyFramer.addKeyFrame(2f / 10f, 1f);
        keyFramer.addKeyFrame(8f / 10f, 1f);
        keyFramer.addKeyFrame(1f, 0f);
        animation.setInterpolator(keyFramer, 0, Constants.POINT_BOTTOM_OF_HEAD.y - Constants.POINT_TOP_OF_BODY.y);
        addAnimation(animation);

    }

    /**
     * Make the anroid nod.
     */
    public void addNodAnimation() {
        AndroidAnimation animation = new AndroidAnimation(AndroidAnimation.Type.NOD, 600);
        KeyFrameInterpolator keyFramer = new KeyFrameInterpolator(0, 0);
        keyFramer.addKeyFrame(2.25f / 10f, 1f);
        keyFramer.addKeyFrame(4.5f / 10f, 0f);
        keyFramer.addKeyFrame(5.5f / 10f, 0f);
        keyFramer.addKeyFrame(7.75f / 10f, 1f);
        keyFramer.addKeyFrame(1f, 0f);
        animation.setInterpolator(keyFramer, 0, Constants.POINT_TOP_OF_BODY.y - Constants.POINT_BOTTOM_OF_HEAD.y);
        addAnimation(animation);
    }

    /**
     * Make the anroid tilt its head.
     */
    public void addHeadTiltAnimation() {
        AndroidAnimation animation = new AndroidAnimation(AndroidAnimation.Type.HEAD_TILT, 1000);
        KeyFrameInterpolator keyFramer = new KeyFrameInterpolator(0, 0);
        keyFramer.addKeyFrame(0.20f, 1f);
        keyFramer.addKeyFrame(0.80f, 1f);
        animation.setInterpolator(keyFramer, 0, RANDOM.nextBoolean() ? 8 : -8);
        addAnimation(animation);
    }

    /**
     * Make the anroid twitch its antennae.
     */
    public void addAntennaTwitchAnimation() {
        AndroidAnimation animation = new AndroidAnimation(AndroidAnimation.Type.ANTENNA_TWITCH);
        KeyFrameInterpolator keyFramer = new KeyFrameInterpolator(0, 0);
        keyFramer.addKeyFrame(0.25f, 1f);
        keyFramer.addKeyFrame(0.5f, 0f);
        keyFramer.addKeyFrame(0.75f, 1f);
        animation.setInterpolator(keyFramer, 0, 40);
        addAnimation(animation);
    }

    /**
     * Make the anroid blink.
     */
    public void addBlinkAnimation() {
        addAnimation(new AndroidAnimation(AndroidAnimation.Type.BLINK));
    }

    /**
     * Make the android wave.
     * 
     * @return duration of animation in millis
     */
    public long addWaveAnimation() {
        int duration = 1200;
        AndroidAnimation animation = new AndroidAnimation(AndroidAnimation.Type.WAVE, duration);
        KeyFrameInterpolator keyFramer = new KeyFrameInterpolator(0, 0);
        keyFramer.addKeyFrame(0.35f, 1f);
        keyFramer.addKeyFrame(0.5f, 0.8f);
        keyFramer.addKeyFrame(0.65f, 1f);
        animation.setInterpolator(keyFramer, 0f, 160f);
        addAnimation(animation);
        return duration;
    }
    
    public long addHoverAnimation() {
    	int duration = 3000;
        AndroidAnimation animation = new AndroidAnimation(AndroidAnimation.Type.HOVERING, duration, true);
        CycleInterpolator keyFramer = new CycleInterpolator(1);
        animation.setInterpolator(keyFramer, 0f, 10f);
        addAnimation(animation);
        return duration;
    }

    public void drawHead(Canvas canvas, int width, int height) {
    	float scaleX = width / droidWidth;
        float scaleY = height / headHeight;
        float scaleFactor = Math.min(scaleX, scaleY);
    	Matrix transform = new Matrix();
        transform.preTranslate(width/2 - POINT_BOTTOM_OF_HEAD.x, (height - POINT_BOTTOM_OF_HEAD.y + Math.max(hatHeightDiff, hairHeightDiff))/2);
        transform.preScale(scaleFactor, scaleFactor, POINT_BOTTOM_OF_HEAD.x, POINT_BOTTOM_OF_HEAD.y);
        
    	canvas.drawARGB(0xFF, backgroundRed, backgroundGreen, backgroundBlue);
        //faceAcc = accessories.getPictureForType(Accessory.TYPE_FACE);
        canvas.save();
        canvas.concat(transform);
        
        // Draw hair (behind)
        {
            canvas.save();
            canvas.scale(droidHead.scaleX, droidHead.scaleY, POINT_BOTTOM_OF_HEAD.x, POINT_BOTTOM_OF_HEAD.y);
            if (hairBack != null) {
                hairBack.draw(canvas);
            }
            canvas.restore();
        }
        
        {
	        canvas.save();
	        canvas.scale(droidHead.scaleX, droidHead.scaleY, POINT_BOTTOM_OF_HEAD.x, POINT_BOTTOM_OF_HEAD.y);
	        droidHead.picture.draw(canvas);

        }
        // Draw face accessory
        if (faceAcc != null) {
            faceAcc.draw(canvas);
        }
        
        // Draw beard and hair in front, then glasses, then head accessory
        {
            //Picture accessory = accessories.getPictureForType(Accessory.TYPE_HEAD);
            Picture mouthAccessory = accessories.getPictureForType(Accessory.TYPE_MOUTH);
            canvas.save();
 
            canvas.scale(droidHead.scaleX, droidHead.scaleY, POINT_BOTTOM_OF_HEAD.x, POINT_BOTTOM_OF_HEAD.y);
            if (beard != null) {
                canvas.save();
                beard.draw(canvas);
                canvas.restore();
            }
            if (hairFront != null) {
                canvas.save();
                canvas.translate(0, -hairHeightDiff);
                hairFront.draw(canvas);
                canvas.restore();
            }
            // Draw glasses
            if (glasses != null) {
                // Scale proportionately based on X
                canvas.save();
                canvas.scale(1f, droidHead.scaleX / droidHead.scaleY, POINT_BOTTOM_OF_HEAD.x, POINT_LEFT_EYE.y);
                glasses.draw(canvas);
                canvas.restore();
            }
            if (mouthAccessory != null) {
                mouthAccessory.draw(canvas);
            }
            if (hats != null) {
                canvas.save();
                canvas.translate(0, -hatHeightDiff);
                hats.draw(canvas);
                canvas.restore();
            }
            canvas.restore();
        }
        
        canvas.restore();
    
    }
}
