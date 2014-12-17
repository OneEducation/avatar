package org.olpc.avatargen;

import static org.olpc.avatargen.AssetDatabase.ASSET_HAIR;
import static org.olpc.avatargen.AssetDatabase.HAIR_FRONT;
import static org.olpc.avatargen.Constants.ANDROID_COLOR;
import static org.olpc.avatargen.Constants.HAIR_COLOR_DEFAULT;
import static org.olpc.avatargen.Constants.SKIN_COLORS;
import static org.olpc.avatargen.Constants.HAIR_COLORS;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import org.olpc.avatargen.AssetDatabase.ConfigPart;

import com.daimajia.androidanimations.library.Techniques;
import com.daimajia.androidanimations.library.YoYo;
import com.larvalabs.svgandroid.SVG;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;

import android.util.TypedValue;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.AnticipateOvershootInterpolator;
import android.view.animation.BounceInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ScrollView;

public class MainActivity extends Activity {
    private AvatarTextureView avatarView;        // main view for draw avatar
    private LinearLayout 	        selectPartView;         // select parts view - 1st depth
    private LinearLayout	        detailPartView;         // detail parts view for menu - 2nd depth
    private ScrollView		        detailItemsView;        // scroll view to add items - attach to detailPartView
    private View 			        closeButton;            // close button - attach to detailPartView
    private ImageView               screenShotView;         // image view to preview screenshot of avatar
    private AssetDatabase 	        db;                     // asset DB
    private FloatingActionButton2   fabCamera;              // camera button - bottom / right area
    private ImageButton             backButton;             // back button - top / left area

    private Bitmap                  bitmapHead;             //
    private Bitmap                  bitmapBody;             //
    final private String            URL_SS_BODY = Environment.getExternalStorageDirectory() + "/ss_body.png";
    final private String            URL_SS_HEAD = Environment.getExternalStorageDirectory() + "/ss_head.png";

    private int[] icons = {                         // icon images for select parts menu
			R.drawable.bodyicon,
			R.drawable.shirticon,
			R.drawable.pantsicon,
			R.drawable.shoesicon,
			R.drawable.glassesicon,
			R.drawable.hairicon,
			R.drawable.haircoloricon
	};

    enum Status {
        splash,         // for future
        making,         // editing avatar
        screenShot      // preview screenshot
    }

    enum RunMode {
        standalone,
        from_xoid
    }

    private Status      mStatus = Status.making;     // no splash for now
    private RunMode     mRunMode = RunMode.standalone;
    private int         mColorRed = 0xffe71e6c;
    private int         mColorGreen = 0xff80ba27;


	public float dipToPixels(float dipValue) {
	    DisplayMetrics metrics = getResources().getDisplayMetrics();
	    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, metrics);
	}
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        // Get the intent that started this activity
        Intent intent = getIntent();
        Util.debug(intent.getAction());
        // Figure out what to do based on the intent type
        if (intent.getAction().equalsIgnoreCase("android.intent.action.MAIN")) {
            mRunMode = RunMode.standalone;
        } else {
            mRunMode = RunMode.from_xoid;
        }

        setContentView(R.layout.main);
    	avatarView = (AvatarTextureView)findViewById(R.id.mGameSurfaceView);
    	selectPartView = (LinearLayout)findViewById(R.id.selectPart);
    	detailPartView = (LinearLayout)findViewById(R.id.detailParts);
    	detailItemsView = (ScrollView)findViewById(R.id.detailItems);
        closeButton = findViewById(R.id.closeButton);
        screenShotView = (ImageView)findViewById(R.id.screenShotView);
        backButton = (ImageButton)findViewById(R.id.backButton);
        fabCamera = (FloatingActionButton2) findViewById(R.id.cameraButton);

        fabCamera.setImageResource(R.drawable.camera_2_48);
        fabCamera.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                switch(mStatus) {
                    case making:
                        takeScreen();
                        break;

                    case screenShot:
                        saveScreen();

                        if(mRunMode == RunMode.from_xoid) {
                            Intent result = new Intent();
                            result.putExtra("URL_BODY", URL_SS_BODY);
                            result.putExtra("URL_HEAD", URL_SS_HEAD);
                            setResult(Activity.RESULT_OK, result);
                            finish();
                        }
                        break;
                }
            }
        });

        backButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (mStatus) {
                    case making:
                        setResult(Activity.RESULT_CANCELED);
                        finish();
                        break;

                    case screenShot:
                        changeMode(Status.making);
                        break;
                }
            }
        });
    	
    	selectPartView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    	detailPartView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    	detailItemsView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        Util.debug("start init asset DB");
    	db = new AssetDatabase(getAssets(), getResources());
    	avatarView.initialize(db);
        Util.debug("end init asset DB");

        Util.debug("start making menu");
    	ArrayList<LinearLayout> arr = new ArrayList<>();
    	arr.add(makeSkinMenu());
    	arr.add(makeMenu(ConfigPart.shirt, db.shirtAssets, "chooser"));
    	arr.add(makeMenu(ConfigPart.pants, db.pantsAssets, "chooser"));
    	arr.add(makeMenu(ConfigPart.shoes, db.shoeAssets, null));
    	arr.add(makeMenu(ConfigPart.glasses, db.glassesAssets, null));
    	arr.add(makeMenu(ConfigPart.hair, db.hairAssets, "chooser"));
    	arr.add(makeHairMenu());
        Util.debug("end making menu");
    	
    	for(int i=0; i< arr.size(); i++) {
    		ImageView iv = new ImageView(this);
    		Drawable d = getResources().getDrawable(icons[i]);
    		iv.setImageDrawable(d);
    		iv.setTag(arr.get(i));
    		iv.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					detailItemsView.removeAllViews();
					detailItemsView.addView((View)v.getTag());
					detailPartView.animate().x(getResources().getDimension(R.dimen.detailparts_width_position_x)).start();
				}
			});
    		selectPartView.addView(iv);
    		
    	}

        closeButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View arg0) {
				detailPartView.animate().xBy(detailPartView.getWidth()).start();
			}
		});
    	
        super.onCreate(savedInstanceState);
    }
	
	private LinearLayout makeSkinMenu() {
		LinearLayout LL = makeLL();
	    for(int i = 0; i < SKIN_COLORS.length; i++) {
    		SVG s = db.getSVGForResource(R.raw.avatar_head, ANDROID_COLOR, SKIN_COLORS[i]);	// HAIR_COLORS

    		VectorView vv = new VectorView(getApplicationContext());
    		vv.setVectors(new SVG[] {s});
    		vv.setTag(SKIN_COLORS[i]);
    		vv.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					Integer color = (Integer)v.getTag();
					avatarView.setConfig(ConfigPart.skinColor, color.toString());
				}
			});
    		LL.addView(vv);
	    }
	    return LL;
	}
	
	private LinearLayout makeHairMenu() {
		LinearLayout LL = makeLL();
	    for(int i = 0; i < HAIR_COLORS.length; i++) {

	    	SVG s = db.getSVGForAsset(ASSET_HAIR, db.hairAssets.get(1), HAIR_FRONT, HAIR_COLOR_DEFAULT, HAIR_COLORS[i]);

    		VectorView vv = new VectorView(getApplicationContext());
    		vv.setVectors(new SVG[] {s});
    		vv.setTag(HAIR_COLORS[i]);

    		vv.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					Integer color = (Integer)v.getTag();
					avatarView.setConfig(ConfigPart.hairColor, color.toString());
				}
			});
    		LL.addView(vv);
	    }
	    return LL;
	}
	
	private LinearLayout makeLL() {
		LinearLayout LL = new LinearLayout(this);
	    LL.setBackgroundColor(Color.WHITE);
	    LL.setOrientation(LinearLayout.VERTICAL);

	    LayoutParams LLParams = new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.MATCH_PARENT);
	    LL.setLayoutParams(LLParams);
	    return LL;
	}
	
	private LinearLayout makeMenu(final ConfigPart part, final ArrayList<String> arrItem, final String suffix) {
		final LinearLayout LL = makeLL();

        new Thread(new Runnable() {
            @Override
            public void run() {
                for(String item : arrItem) {
                    SVG s = db.getSVGForAsset(part.name(), item, "chooser");
                    if(s==null)
                        s = db.getSVGForAsset(part.name(), item, suffix);

                    if(s==null) continue;

                    final VectorView vv = new VectorView(getApplicationContext());
                    vv.setVectors(new SVG[] {s});
                    vv.setTag(item);
                    vv.setOnClickListener(new OnClickListener() {

                        @Override
                        public void onClick(View v) {
                            avatarView.setConfig(part, (String) v.getTag());
                        }
                    });

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            LL.addView(vv);
                        }
                    });

                }
            }
        }).start();

	    return LL;
	}

//  no menu!
//	@Override
//	public boolean onCreateOptionsMenu(Menu menu) {
//	    // Inflate the menu items for use in the action bar
//	    MenuInflater inflater = getMenuInflater();
//	    inflater.inflate(R.menu.action_menu, menu);
//	    return super.onCreateOptionsMenu(menu);
//	}
//
//	@Override
//	public boolean onOptionsItemSelected(MenuItem item) {
//	    // Handle presses on the action bar items
//	    switch (item.getItemId()) {
//	        case R.id.action_save:
//	            saveScreen();
//	            return true;
//	        case R.id.action_settings:
//	            //openSettings();
//	            return true;
//	        default:
//	            return super.onOptionsItemSelected(item);
//	    }
//	}
	
	private void takeScreen() {
        bitmapHead = avatarView.takeScreenShot(true, 300, 300);   // head
        bitmapBody = avatarView.takeScreenShot(false, 600, 600);
        screenShotView.setImageBitmap(bitmapBody);
        changeMode(Status.screenShot);
	}

    private void saveScreen() {
        File file = new File(URL_SS_BODY);

        try {
            bitmapBody.compress(Bitmap.CompressFormat.PNG, 100, new FileOutputStream(file));
        } catch (Exception e) {
            e.printStackTrace();
        }


        File f2 = new File(URL_SS_HEAD);

        try {
            bitmapHead.compress(Bitmap.CompressFormat.PNG, 100, new FileOutputStream(f2));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

	@Override
	public void onBackPressed() {


        switch(mStatus) {
            case making:
                if(detailPartView != null && detailPartView.getX() == 0) {
                    detailPartView.animate().x(getResources().getDimension(R.dimen.detailparts_width_position_x)).start();
                    return;
                }
                break;

            case screenShot:
                changeMode(Status.making);
                return;
        }
			
		super.onBackPressed();
	}

    private void changeMode(Status status) {
        if(status == mStatus) return;

        switch(status) {
            case making:
                YoYo.with(Techniques.BounceInLeft).duration(1500).playOn(avatarView);
                YoYo.with(Techniques.BounceInRight).duration(1500).playOn(findViewById(R.id.rightPanel));
                YoYo.with(Techniques.TakingOff).duration(1000).playOn(screenShotView);
                fabCamera.animate().setInterpolator(new AnticipateOvershootInterpolator()).setDuration(1300).translationX(dipToPixels(0)).rotation(0).start();
                fabCamera.setBGColorTransition(mColorRed).setDuration(1300).setInterpolator(new AnticipateOvershootInterpolator()).setDrawableTransition(R.drawable.camera_2_48, 0.4f).start();

                break;

            case screenShot:
                YoYo.with(Techniques.SlideOutLeft).duration(1000).playOn(avatarView);
                YoYo.with(Techniques.SlideOutRight).duration(1000).playOn(findViewById(R.id.rightPanel));
                YoYo.with(Techniques.BounceIn).duration(1000).playOn(screenShotView);
                fabCamera.animate().setInterpolator(new BounceInterpolator()).setDuration(1300).translationX(dipToPixels(150)).rotation(360).start();
                fabCamera.setBGColorTransition(mColorGreen).setDuration(1300).setInterpolator(new BounceInterpolator()).setDrawableTransition(R.drawable.ic_arrow_right_white_24dp, 0.3f).start();
                break;
        }

        mStatus = status;
    }
	
}
