package org.olpc.avatargen;

import org.olpc.avatargen.AssetDatabase.ConfigPart;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;

public class AvatarTextureView extends TextureView implements TextureView.SurfaceTextureListener {
    /**
     * The frame rate we will attempt to achieve with the wallpaper
     */
    public static final int FRAME_RATE = 40;

    /**
     * Amount of time before the next scene starts to fades in (make it plus the transition time less than the drift time), in millis.
     */
    public static final long SCENE_TIME = 5000L;
	private Context context;		//context to use resources
	private RenderEngine engine;
	
	public AvatarTextureView(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		this.context = context;
		setSurfaceTextureListener(this);
		setOpaque(false);
	}
	
	public void initialize(AssetDatabase db) {
		engine = new RenderEngine();
		engine.init(this, db);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		engine.handleTouch(event);
		return true;
	}
	
	public void setConfig(ConfigPart part, String item) {
		engine.setConfig(part, item);
	}
	
    @Override
	protected void onVisibilityChanged(View changedView, int visibility) {
		engine.setVisibility(visibility == View.VISIBLE);
		super.onVisibilityChanged(changedView, visibility);
	}
    
    public Bitmap takeScreenShot(boolean head, int w, int h) {
    	return head ? engine.takeHeadScreenshot(w,h) : engine.takeScreenShot(w,h);
    }


	/**
     * The engine responsible for rendering.
     */
    private class RenderEngine {

        private final Handler mHandler = new Handler();
        private boolean mVisible;
        private AndroidDrawer android; //, nextAndroid;
        private AssetDatabase assetDatabase;
        private int width, height;
        private int lastColorIndex = -1;
        private long sceneTime = 0L;
        
        private TextureView holder;
        
        private float[] matrixTouch = {0,0};
        private float touchX;
        private float touchY;
        
        private int multiFactor = 1;
        final private int NONE = -1;
        final private int HEAD = 0;
        final private int BODY = 1;
        final private int LEG = 2;
        final private int ARM = 3;
        private int touchDownRegion = NONE;
        
        Paint paint = new Paint();

        private final Runnable drawer = new Runnable() {
            public void run() {
                drawFrame();
            }
        };

        /**
         * Gets a randomly-generated android.
         */
        private AndroidConfig getNextConfig() {
            // Return a random config
            return assetDatabase.getRandomConfig();
        }

        public void init(TextureView surfaceHolder, AssetDatabase db) {
        	holder = surfaceHolder;
            assetDatabase = db;
            android = new AndroidDrawer(assetDatabase);
            //android.setAndroidConfig(getNextConfig(), assetDatabase);
            sceneTime = System.currentTimeMillis();
            
        	paint.setAntiAlias(true);
        	paint.setTextSize(20);
        	paint.setColor(Color.RED);
        }
        
        public void setConfig(ConfigPart part, String item) {
        	android.setConfig(assetDatabase, part, item);
        }
        
        public void handleTouch(MotionEvent e) {
        	float x = e.getX();
        	float y = e.getY();
        	
        	switch(e.getAction()) {
        	case MotionEvent.ACTION_DOWN:        		
        		matrixTouch[0] = x;
        		matrixTouch[1] = y;
        		if(android.reverseTransform!=null)
            		android.reverseTransform.mapPoints(matrixTouch);
        		
        		if(android.droidHead.contains(matrixTouch[0], matrixTouch[1])) {
        			touchDownRegion = HEAD;
        		} else if(android.droidBody.contains(matrixTouch[0], matrixTouch[1])) {
        			touchDownRegion = BODY;
        		} else if(android.droidLegs.contains(matrixTouch[0], matrixTouch[1])) {
        			touchDownRegion = LEG;
        		} else if(android.droidArm.contains(matrixTouch[0], matrixTouch[1])) {
        			touchDownRegion = ARM;
        		}
        		
        		if(matrixTouch[0] < Constants.CENTER_X)
        			multiFactor = -1;
        		else
        			multiFactor = 1;
        		
        		break;
        	case MotionEvent.ACTION_MOVE:
        		
        		if(touchDownRegion != -1) {
        			float diffY = e.getY() - touchY;
        			float diffX = e.getX() - touchX;
        			diffX *= multiFactor;
        			
        			switch(touchDownRegion) {
        			case HEAD:
        				android.droidHead.scale(diffX, diffY);
            			break;
        			case BODY:
        				android.droidBody.scale(diffX, diffY);
            			break;
        			case LEG:
        				android.droidLegs.scale(diffX, diffY);
            			break;
        			case ARM:
        				android.droidArm.scale(diffX, diffY);
            			break;
        			}
        			
        			//Log.d("Mouse", android.droidArm.scaleY+"");
        			android.computeArmOffset();
        	        android.computeLegsOffset();
            		android.rescale();
        		}        		
        		break;
        	case MotionEvent.ACTION_UP:
        		if(touchDownRegion == NONE) {
        			//android.setAndroidConfig(getNextConfig(), assetDatabase);
        		}
        		touchDownRegion = NONE;
        		break;
        	}
        	touchX = x;
        	touchY = y;
        }

        public void destroy() {
            mHandler.removeCallbacks(drawer);
        }

        public void setVisibility(boolean visible) {
            mVisible = visible;
            if (visible) {
                postDraw();
            } else {
                mHandler.removeCallbacks(drawer);
            }
        }
        
        public void setScreen(int width, int height) {
        	this.width = width;
        	this.height = height;
        	android.setDimensions(width, height);
        }

        public void doRandomAnimation() {
            android.addRandomAnimation(true);
        }

        /*
         * Draw one frame of the animation. This method gets called repeatedly
         * by posting a delayed Runnable. You can do any drawing you want in
         * here. This example draws a wireframe cube.
         */
        private void drawFrame() {
            // Figure out if we are in the middle of a scene change
            long time = System.currentTimeMillis() - sceneTime;
            if (time > SCENE_TIME) {
            	doRandomAnimation();
        		sceneTime = time + sceneTime;
            }
            android.stepAnimations();
            Canvas c = null;
            try {
                c = holder.lockCanvas(null);
                if (c != null) {
                    c.save();

                    android.draw(c);
                    
                    if(Util.debugMode) {
	                	c.drawText("Mouse Point : X="+matrixTouch[0]+", Y="+matrixTouch[1], 0, 450, paint);
	                	c.drawText("Head bound : "+ android.droidHead.bound.toShortString() + " / " + android.droidHead.contains(matrixTouch[0], matrixTouch[1]), 0, 500, paint);
	                	c.drawText("Body bound : "+ android.droidBody.bound.toShortString() + " / " + android.droidBody.contains(matrixTouch[0], matrixTouch[1]), 0, 550, paint);
	                	c.drawText("Leg bound : "+ android.droidLegs.bound.toShortString() + " / " + android.droidLegs.contains(matrixTouch[0], matrixTouch[1]), 0, 600, paint);
	                	c.drawText("Arm bound : "+ android.droidArm.bound.toShortString() + " / " + android.droidArm.contains(matrixTouch[0], matrixTouch[1]), 0, 650, paint);
                    }

                    c.restore();
                }
            } finally {
                if (c != null) {
                    holder.unlockCanvasAndPost(c);
                }
            }

            postDraw();

        }

        /**
         * Posts a draw event to the handler.
         */
        public void postDraw() {
            mHandler.removeCallbacks(drawer);
            if (mVisible) {
                mHandler.postDelayed(drawer, 1000 / FRAME_RATE);
            }
        }
        
        public Bitmap takeScreenShot(int w, int h) {
            mHandler.removeCallbacks(drawer);
            android.setDimensions(w, h);
    		Bitmap  bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    		Canvas canvas = new Canvas(bitmap);
    		android.draw(canvas, true);
    		setScreen(width, height);
    		postDraw();
    		return bitmap;
        }
        
        public Bitmap takeHeadScreenshot(int w, int h) {
        	Bitmap  bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    		Canvas canvas = new Canvas(bitmap);
    		android.drawHead(canvas, w, h);
    		return bitmap;
        }

    }


	@Override
	public void onSurfaceTextureAvailable(SurfaceTexture arg0, int width, int height) {
		engine.setScreen(width, height);
		
	}

	@Override
	public boolean onSurfaceTextureDestroyed(SurfaceTexture arg0) {
		engine.destroy();
		return false;
	}

	@Override
	public void onSurfaceTextureSizeChanged(SurfaceTexture arg0, int width,	int height) {
		engine.setScreen(width, height);
		
	}

	@Override
	public void onSurfaceTextureUpdated(SurfaceTexture arg0) {

	}


}
