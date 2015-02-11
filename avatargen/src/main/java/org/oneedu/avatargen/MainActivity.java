package org.oneedu.avatargen;

import static android.widget.LinearLayout.LayoutParams.*;
import static org.oneedu.avatargen.AssetDatabase.ASSET_HAIR;
import static org.oneedu.avatargen.AssetDatabase.HAIR_FRONT;
import static org.oneedu.avatargen.Constants.ANDROID_COLOR;
import static org.oneedu.avatargen.Constants.HAIR_COLOR_DEFAULT;
import static org.oneedu.avatargen.Constants.SKIN_COLORS;
import static org.oneedu.avatargen.Constants.HAIR_COLORS;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;


import com.afollestad.materialdialogs.MaterialDialog;
import com.daimajia.androidanimations.library.Techniques;
import com.daimajia.androidanimations.library.YoYo;
import com.larvalabs.svgandroid.SVG;
import com.xoid.xodatainterface.XoDataProvider;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.widget.ContentLoadingProgressBar;
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

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreConnectionPNames;
import org.json.JSONException;
import org.json.JSONObject;
import org.oneedu.avatargen.AssetDatabase.ConfigPart;


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
    private ContentLoadingProgressBar progress;
    private String                  userToken;
    private String                  uploadPath;
    private String                  uploadHeader;

    private Bitmap                  bitmapHead;             //
    private Bitmap                  bitmapBody;             //
    final private String            URL_SS_BODY = Environment.getExternalStorageDirectory() + "/ss_body.png";
    final private String            URL_SS_HEAD = Environment.getExternalStorageDirectory() + "/ss_head.png";
    final private String            URL_CONFIG  = "config.json";

    private AvatarConfig            avatarConfig;

    private XoDataProvider          xoDataProvider;

    private int[] icons = {                         // icon images for select parts menu
			R.raw.skin_colour,
            R.raw.hair_type,
            R.raw.hair_colour,
            R.raw.set,
			R.raw.top,
            R.raw.bottoms,
            R.raw.shoes,
			R.raw.eye_wear,
            R.raw.hats,
            R.raw.hair_accessorie,  //face acc
            R.raw.body_accessorie,
            R.raw.background
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

    Callback uploadCallback = new Callback() {
        int responseCount = 0;
        int successCount = 0;
        String HeadServerURL;
        String BodyServerURL;

        public void reset() {
            responseCount = 0;
            successCount = 0;
        }
        @Override
        public void onFinished(HttpResponse res, JSONObject resJson) {
            responseCount++;
            Util.debug("response code : "+res.getStatusLine().getStatusCode());
            if(res.getStatusLine().getStatusCode() == 200) {
                try {
                    if(resJson.getJSONObject("ok").getString("name").equals("ss_head.png")) {
                        HeadServerURL = resJson.getJSONObject("ok").getString("url");
                    } else {
                        BodyServerURL = resJson.getJSONObject("ok").getString("url");
                    }
                } catch(JSONException e) {
                    e.printStackTrace();
                }

                successCount++;
            }

            if(responseCount == 2) {
                progress.hide();
                if(successCount == 2) {
                    new MaterialDialog.Builder(MainActivity.this)
                            .title("Success")
                            .content("Avatar updated.")
                            .positiveText("Done")
                            .callback(new MaterialDialog.ButtonCallback() {
                                @Override
                                public void onPositive(MaterialDialog dialog) {
                                    if (mRunMode == RunMode.from_xoid) {
                                        Intent result = new Intent();
                                        result.putExtra("URL_BODY", BodyServerURL);
                                        result.putExtra("URL_HEAD", HeadServerURL);
                                        setResult(Activity.RESULT_OK, result);
                                    }
                                    finish();
                                }
                            })
                            .show();
                } else {
                    new MaterialDialog.Builder(MainActivity.this)
                        .title("Failed")
                        .content("Would you like to retry?")
                        .positiveText("Retry")
                        .negativeText("Cancel")
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                requestUpload();
                            }

                            @Override
                            public void onNegative(MaterialDialog dialog) {
                                dialog.dismiss();
                            }
                        })
                        .show();
                }
            }
        }
    };


	public float dipToPixels(float dipValue) {
	    DisplayMetrics metrics = getResources().getDisplayMetrics();
	    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, metrics);
	}

    private boolean checkOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    private void requestUpload() {
        if(!checkOnline()) {
            new MaterialDialog.Builder(MainActivity.this)
                    .title("Caution")
                    .content("Check your network status and retry.")
                    .positiveText("Ok")
                    .callback(new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {
                            dialog.dismiss();
                        }
                    })
                    .show();
            return;
        }

        progress.show();
        uploadCallback.reset();
        String url = "http://id.one-education.org"; //"http://10.0.0.53:5000";  //"http://192.168.1.4:5000";
        new AsyncFileThread(url+uploadPath+"/large", new File(URL_SS_BODY))
                .setFinishHandler(uploadCallback).start();

        new AsyncFileThread(url+uploadPath+"/small", new File(URL_SS_HEAD))
                .setFinishHandler(uploadCallback).start();
    }

    @Override
    protected  void onDestroy() {
        saveJson();
        super.onDestroy();
    }

	@Override
    public void onCreate(Bundle savedInstanceState) {
//        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
//                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
//                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
//                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
//                | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
//                //| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
//                );

        super.onCreate(savedInstanceState);
        //overridePendingTransition(R.anim.start_enter, R.anim.start_exit);

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
        progress = (ContentLoadingProgressBar)findViewById(R.id.progress);

        fabCamera.setImageResource(R.drawable.camera_2_48);
        fabCamera.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                switch(mStatus) {
                    case making:
                        takeScreen();
                        break;

                    case screenShot:
                        new MaterialDialog.Builder(MainActivity.this)
                            .title("Upload")
                            .content("Would you like to update avatar?")
                            .positiveText("Upload")
                            .negativeText("Cancel")
                            .callback(new MaterialDialog.ButtonCallback() {
                                @Override
                                public void onPositive(MaterialDialog dialog) {
                                    saveScreen();
                                    requestUpload();
                                }

                                @Override
                                public void onNegative(MaterialDialog dialog) {
                                    dialog.dismiss();
                                }
                            })
                            .show();

                        break;
                }
            }
        });

        backButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (mStatus) {
                    case making:
                        showQuitDialog();
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



        db = new AssetDatabase(getAssets(), getResources());
        avatarConfig = new AvatarConfig(db);
        avatarView.initialize(db, avatarConfig);

        for(int i=0; i< icons.length; i++) {
            SVG s = db.getSVGForResource(icons[i]);

            if(s==null) continue;

            ImageView iv = new ImageView(getApplicationContext());
            iv.setImageDrawable(s.createPictureDrawable());

            //ImageView iv = new ImageView(MainActivity.this);
            //Drawable d = getResources().getDrawable(icons[i]);
            //iv.setImageDrawable(d);
            selectPartView.addView(iv, new LayoutParams(MATCH_PARENT, (int)dipToPixels(180)));
        }

        new Thread(new Runnable() {
            @Override
            public void run() {

                Util.debug("start init asset DB");
                try {
                    db.scanAssets();
                } catch(Exception e) {
                    Util.debug(e.getMessage());
                }
                Util.debug("end init asset DB");

                Util.debug("start making menu");
                ArrayList<LinearLayout> arr = new ArrayList<>();
                arr.add(makeSkinMenu());
                arr.add(makeMenu(ConfigPart.hair, db.hairAssets, "chooser"));
                arr.add(makeHairMenu());
                arr.add(makeMenu(ConfigPart.sets, db.setAssets, "chooser"));
                arr.add(makeMenu(ConfigPart.shirt, db.shirtAssets, "chooser"));
                arr.add(makeMenu(ConfigPart.pants, db.pantsAssets, "chooser"));
                arr.add(makeMenu(ConfigPart.shoes, db.shoeAssets, "chooser"));
                arr.add(makeMenu(ConfigPart.glasses, db.glassesAssets, "chooser"));
                arr.add(makeMenu(ConfigPart.hats, db.hatAssets, "chooser"));
                arr.add(makeMenu(ConfigPart.face, db.faceAssets, "chooser"));
                arr.add(makeMenu(ConfigPart.bodyAcc, db.bodyAccAssets, "chooser"));
                arr.add(makeMenu(ConfigPart.backgrounds, db.backgroundAssets, null));
                Util.debug("end making menu");

                for(int i=0; i< arr.size(); i++) {
                    View iv = selectPartView.getChildAt(i);
                    iv.setTag(arr.get(i));
                    iv.setOnClickListener(new OnClickListener() {

                        @Override
                        public void onClick(View v) {
                            detailItemsView.removeAllViews();
                            detailItemsView.addView((View)v.getTag());
                            detailPartView.animate().x(getResources().getDimension(R.dimen.detailparts_width_position_x)).start();
                        }
                    });
                }
            }
        }).start();


        closeButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View arg0) {
				detailPartView.animate().xBy(detailPartView.getWidth()).start();
			}
		});

        xoDataProvider = new XoDataProvider(this);
    }

    private void checkLastAvatar() {
        try {
            final InputStream inputStream = openFileInput(URL_CONFIG);

            if ( inputStream != null ) {

                new MaterialDialog.Builder(MainActivity.this)
                        .title("Avatar")
                        .content("You have an avatar last time. Do you want continue or new?")
                        .positiveText("New")
                        .negativeText("Continue")
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                            }

                            @Override
                            public void onNegative(MaterialDialog dialog) {
                                try {
                                    InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                                    BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                                    String receiveString = "";
                                    StringBuilder stringBuilder = new StringBuilder();

                                    while ((receiveString = bufferedReader.readLine()) != null) {
                                        stringBuilder.append(receiveString);
                                    }

                                    inputStream.close();
                                    avatarConfig.loadConfig(new JSONObject(stringBuilder.toString()));
                                } catch (Exception e) {
                                    Util.debug(e.getMessage());
                                }
                            }
                        })
                        .show();
            }
        }
        catch (Exception e) {
            Util.debug("File not found: " + e.toString());
        }
    }

    private void showNotLoginAlertDialog() {
        new MaterialDialog.Builder(MainActivity.this)
                .title("XO-ID")
                .content("You need to log in XO-ID first. Click Login button to launch XO-ID.")
                .positiveText("Login")
                .negativeText("Quit")
                .cancelable(false)
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        dialog.dismiss();
                        //TODO : launch xo-id
                        xoDataProvider.launchLoginActivity(MainActivity.this);
                        //checkLastAvatar();
                    }

                    @Override
                    public void onNegative(MaterialDialog dialog) {
                        finish();
                    }
                })
                .show();
    }

    private void showQuitDialog() {
        new MaterialDialog.Builder(MainActivity.this)
                .title("Quit")
                .content("Do you really want to quit?")
                .positiveText("QUIT")
                .negativeText("KEEP EDITING")
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        setResult(Activity.RESULT_CANCELED);
                        finish();
                    }

                    @Override
                    public void onNegative(MaterialDialog dialog) {
                        dialog.dismiss();
                    }
                })
                .show();
    }
	
	private LinearLayout makeSkinMenu() {
		final LinearLayout LL = makeLL();
	    for(int i = 0; i < SKIN_COLORS.length; i++) {
    		SVG s = db.getSVGForResource(R.raw.avatar_head, ANDROID_COLOR, SKIN_COLORS[i]);	// HAIR_COLORS

    		final VectorView vv = new VectorView(getApplicationContext());
    		vv.setVectors(new SVG[] {s});
    		vv.setTag(SKIN_COLORS[i]);
    		vv.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					Integer color = (Integer)v.getTag();
                    avatarConfig.setPart(ConfigPart.skinColor, color.toString());
				}
			});
            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    LL.addView(vv);
                }
            });
	    }
	    return LL;
	}
	
	private LinearLayout makeHairMenu() {
		final LinearLayout LL = makeLL();
	    for(int i = 0; i < HAIR_COLORS.length; i++) {

	    	SVG s = db.getSVGForAsset(ASSET_HAIR, db.hairAssets.get(1), HAIR_FRONT, HAIR_COLOR_DEFAULT, HAIR_COLORS[i]);

    		final VectorView vv = new VectorView(getApplicationContext());
    		vv.setVectors(new SVG[] {s});
    		vv.setTag(HAIR_COLORS[i]);

    		vv.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					Integer color = (Integer)v.getTag();
                    avatarConfig.setPart(ConfigPart.hairColor, color.toString());
				}
			});
            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    LL.addView(vv);
                }
            });
	    }
	    return LL;
	}
	
	private LinearLayout makeLL() {
		LinearLayout LL = new LinearLayout(this);
	    LL.setBackgroundColor(Color.WHITE);
	    LL.setOrientation(LinearLayout.VERTICAL);

	    LayoutParams LLParams = new LayoutParams(WRAP_CONTENT, MATCH_PARENT);
	    LL.setLayoutParams(LLParams);
	    return LL;
	}
	
	private LinearLayout makeMenu(final ConfigPart part, final ArrayList<String> arrItem, final String suffix) {
		final LinearLayout LL = makeLL();

        new Thread(new Runnable() {
            @Override
            public void run() {
                int i=0;
                for(String item : arrItem) {
                    //SVG s = db.getSVGForAsset(part.name(), item, "chooser");
                    //if(s==null)
                    SVG s = db.getSVGForAsset(part.name(), item, suffix);

                    if(s==null) continue;

                    //final VectorView vv = new VectorView(getApplicationContext());
                    //vv.setVectors(new SVG[] {s});
                    final ImageView vv = new ImageView(getApplicationContext());
                    vv.setImageDrawable(s.createPictureDrawable());
                    //if(i%2==0) vv.setBackgroundColor(getResources().getColor(R.color.button_material_light));
                    vv.setTag(item);
                    vv.setOnClickListener(new OnClickListener() {

                        @Override
                        public void onClick(View v) {
                            avatarConfig.setPart(part, (String) v.getTag());
                        }
                    });

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            LL.addView(vv, new LayoutParams(MATCH_PARENT, (int)dipToPixels(160)));
                        }
                    });

                    ++i;

                }
            }
        }).start();

	    return LL;
	}

    private LinearLayout makeAccMenu(final ConfigPart part, final ArrayList<Accessory> arrItem, final String suffix) {
        final LinearLayout LL = makeLL();

        new Thread(new Runnable() {
            @Override
            public void run() {
                for(Accessory item : arrItem) {
                    SVG s = db.getSVGForAsset(part.name(), item.getName(), "chooser");
                    if(s==null)
                        s = db.getSVGForAsset(part.name(), item.getName(), suffix);

                    if(s==null) continue;

                    final VectorView vv = new VectorView(getApplicationContext());
                    vv.setVectors(new SVG[] {s});
                    vv.setTag(item);
                    vv.setOnClickListener(new OnClickListener() {

                        @Override
                        public void onClick(View v) {
                            avatarConfig.setPart(part, (String) v.getTag());
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
        //avatarView.printMessage(avatarConfig.getConfig().toString());

        screenShotView.setImageBitmap(bitmapBody);
        changeMode(Status.screenShot);
	}

    private void saveJson() {
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(openFileOutput(URL_CONFIG, Context.MODE_PRIVATE));
            outputStreamWriter.write(avatarConfig.getConfig().toString());
            outputStreamWriter.close();
        }
        catch (IOException e) {
            Util.debug("File write failed: " + e.toString());
        }
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
                } else {
                    showQuitDialog();
                    return;
                }

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

    private abstract class Callback {
        public void reset() {}
        public void onFinished(HttpResponse res, JSONObject resJson) {}
    }

    private class AsyncFileThread extends Thread {
        String address;
        File file;
        Callback callback;

        public AsyncFileThread(String addr, File _file){
            address = addr;
            file = _file;
        }

        public AsyncFileThread setFinishHandler(Callback callback) {
            this.callback = callback;
            return this;
        }

        public void run(){

            try {
                HttpClient httpClient = new DefaultHttpClient();
                httpClient.getParams().setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 10 * 1000);
                httpClient.getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT, 10 * 1000);
                HttpPost postRequest = new HttpPost(address);
                postRequest.setHeader("X-Api-Key", "abc123");
                postRequest.setHeader("X-Device-Id", Build.SERIAL);
                postRequest.setHeader(uploadHeader, userToken);
                avatarView.printMessage(Build.SERIAL + " / " + userToken);
                HttpEntity reqEntity = MultipartEntityBuilder.create().addBinaryBody("file", file).build();
                postRequest.setEntity(reqEntity);

                final HttpResponse response = httpClient.execute(postRequest);
                BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent(), "UTF-8"));
                String sResponse;
                StringBuilder s = new StringBuilder();
                while ((sResponse = reader.readLine()) != null) {
                    s = s.append(sResponse);
                }
                Util.debug("Response: " + s);
                final JSONObject result = new JSONObject(s.toString());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        callback.onFinished(response, result);
                    }
                });
            } catch (Exception e) {

                Util.debug(e.getMessage());
            }

        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        xoDataProvider.requestStudentData(new XoDataProvider.DataCallback() {
            @Override
            public void onDataAvailable(final String data) {
                Util.debug(data);
                try {
                    JSONObject dataJson = new JSONObject(data);
                    String key = dataJson.keys().next();
                    JSONObject user = dataJson.getJSONObject(key);
                    userToken = user.getString("token");
                    if(key.equalsIgnoreCase("teacher")) {
                        uploadHeader = "X-Teacher-Token";
                        uploadPath = "/teacher-avatar";
                    } else {
                        uploadHeader = "X-Student-Token";
                        uploadPath = "/student-avatar";
                    }
                } catch (JSONException e) {
                    Util.debug(e.getMessage());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showNotLoginAlertDialog();
                        }
                    });
                }
                //checkLastAvatar();
            }

            @Override
            public void onError(String description) {
                Util.debug("XoDataProvider error: " + description);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showNotLoginAlertDialog();
                        //checkLastAvatar();
                    }
                });
            }
        });
    }
}
