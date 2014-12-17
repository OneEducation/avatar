package org.olpc.avatargen;

import java.util.Random;

import org.olpc.avatargen.AssetDatabase.ConfigPart;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.os.Debug;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;

public class GameTextureView extends TextureView implements TextureView.SurfaceTextureListener {

    // Random number generator
    private static final Random RANDOM = new Random();

    /**
     * The frame rate we will attempt to achieve with the wallpaper
     */
    public static final int FRAME_RATE = 60;

    /**
     * The width of the wallpaper, as a percent of the height of the phone.
     */
    public static final int SCENE_WIDTH = 150;

    /**
     * Duration of animation to slide between screens (in milliseconds).
     */
    public static final long SLIDE_TIME = 100;

    /**
     * The set of background colors
     */
    private static final int[] COLORS =
            {
                    0x59c0ce,
                    0xe684a9,
                    0xfef48b,
                    0x9dcb7a,
                    0xd65143,
            };

    /**
     * Min scale size of android, where 1 means the width of the android's head just fits on the screen (width-wise)
     */
    public static final float MIN_SCALE = 1.0f;

    /**
     * Max scale size of android, where 1 means the width of the android's head just fits on the screen (width-wise)
     */
    public static final float MAX_SCALE = 1.25f;

    /**
     * Min off-centering offset (in units of the diameter of the android's eye)
     */
    public static final float MIN_OFFSET = -1.5f;

    /**
     * Max off-centering offset (in units of the diameter of the android's eye)
     */
    public static final float MAX_OFFSET = 1.5f;

    /**
     * Min rotation angle (degrees)
     */
    public static final int MIN_ROTATION = 5;

    /**
     * Max rotation angle (degrees)
     */
    public static final int MAX_ROTATION = 15;

    /**
     * Drift time in millis.
     */
    public static final long DRIFT_TIME = 15000L;

    /**
     * Drift amount (in units of the diameter of the android's eye).
     */
    public static final float DRIFT_AMOUNT = 5f;

    /**
     * Amount of time before the next scene starts to fades in (make it plus the transition time less than the drift time), in millis.
     */
    public static final long SCENE_TIME = 5000L;

    /**
     * Amount of time fading between scene transitions, in millis.
     */
    public static final long SCENE_TRANSITION = 1500L;
	
	//SurfaceHolder gameHolder;				//gameHolder.unlockCanvasAndPost 
	public static Context gameContext;		//context to use resources
	
	public RenderEngine engine;
	
	public GameTextureView(Context context, AttributeSet attrs) {		
		super(context, attrs);
		
		gameContext = context;
		setSurfaceTextureListener(this);
		setOpaque(false);
		Log.v("main", "GameSurface Constructer finish");
	}
	
	public void initilize(AssetDatabase db) {
		//gameHolder = getHolder();
		//gameHolder.addCallback(this);
		
		engine = new RenderEngine();
		engine.init(this, db);
	}

	public Bitmap LoadingBitmapFromResource(int id){
		Bitmap output;
		Util.debug("Memory  : " + Long.toString(Debug.getNativeHeapAllocatedSize()));
		BitmapDrawable drawableLocal = (BitmapDrawable) getResources().getDrawable(id);
		output = drawableLocal.getBitmap();
		return output;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		engine.handleTouch(event);
		return true;
	}
	
//	@Override
//	public void surfaceCreated(SurfaceHolder holder) {	
//		
//	}
//
//	@Override
//	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
//		//Log.v("main", "surface change"+ "\tThread State : " + gameThread.getState().toString());
//		engine.setScreen(width, height);
//		//engine.postDraw();
//	}
//	
//	@Override
//	public void surfaceDestroyed(SurfaceHolder holder) {
//		engine.destroy();
//	}
	
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

        /**
         * Gets a random background color, ensuring it isn't the same as the last color.
         */
        private int getNextColor() {
            int index;
            do {
                index = RANDOM.nextInt(COLORS.length);
            } while (index == lastColorIndex);
            lastColorIndex = index;
            return COLORS[index];
        }

        private ZoomInfo createRandomZoomInfo() {
            int angle = MIN_ROTATION + RANDOM.nextInt(MAX_ROTATION - MIN_ROTATION);
            if (RANDOM.nextBoolean()) {
                angle = -angle;
            }
            final float offsetX = MIN_OFFSET + RANDOM.nextFloat() * (MAX_OFFSET - MIN_OFFSET);
            final float offsetY = MIN_OFFSET + RANDOM.nextFloat() * (MAX_OFFSET - MIN_OFFSET);
            int driftAngle = (int) (Math.atan2(offsetY, offsetX) * 180 / Math.PI);
            return new ZoomInfo(
                    MIN_SCALE + RANDOM.nextFloat() * (MAX_SCALE - MIN_SCALE),
                    offsetX,
                    offsetY,
                    angle,
                    RANDOM.nextBoolean(),
                    DRIFT_AMOUNT,
                    DRIFT_TIME,
                    driftAngle
            );
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
        	//android.setScreenWidth(width);
        	android.setDimensions(width, height);        	
        }

        public void doRandomAnimation() {
            //Log.d("ANDROIDIFY WALLPAPER", "Screen tapped.");
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
	public void onSurfaceTextureAvailable(SurfaceTexture arg0, int width,
			int height) {
		// TODO Auto-generated method stub
		engine.setScreen(width, height);
		
	}

	@Override
	public boolean onSurfaceTextureDestroyed(SurfaceTexture arg0) {
		// TODO Auto-generated method stub
		engine.destroy();
		return false;
	}

	@Override
	public void onSurfaceTextureSizeChanged(SurfaceTexture arg0, int width,
			int height) {
		// TODO Auto-generated method stub
		engine.setScreen(width, height);
		
	}

	@Override
	public void onSurfaceTextureUpdated(SurfaceTexture arg0) {
		// TODO Auto-generated method stub
		
	}


}
