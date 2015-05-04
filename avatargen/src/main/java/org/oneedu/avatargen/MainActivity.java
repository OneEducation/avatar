package org.oneedu.avatargen;

import static android.widget.LinearLayout.LayoutParams.*;
import static org.oneedu.avatargen.AssetDatabase.ASSET_HAIR;
import static org.oneedu.avatargen.AssetDatabase.HAIR_FRONT;
import static org.oneedu.avatargen.Constants.ANDROID_COLOR;
import static org.oneedu.avatargen.Constants.HAIR_COLOR_DEFAULT;
import static org.oneedu.avatargen.Constants.SKIN_COLORS;
import static org.oneedu.avatargen.Constants.HAIR_COLORS;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;


import com.afollestad.materialdialogs.MaterialDialog;
import com.daimajia.androidanimations.library.Techniques;
import com.daimajia.androidanimations.library.YoYo;
import com.larvalabs.svgandroid.SVG;

import org.apache.http.entity.ContentType;
import org.oneedu.xoid.xodatainterface.XoDataProvider;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.widget.ContentLoadingProgressBar;
import android.util.DisplayMetrics;

import android.util.Log;
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
import android.widget.Toast;

import org.apache.http.HttpEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
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
    private String                  uploadHeader;

    private Bitmap                  bitmapHead;             //
    private Bitmap                  bitmapBody;             //
    final private String            URL_SS_BODY = "ss_body.png";
    final private String            URL_SS_HEAD = "ss_head.png";
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
    private String serverURL;

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
        String HeadServerURL;
        String BodyServerURL;

        @Override
        public void onFinished(int responseCode, String result) {
            Util.debug("response code : " + responseCode);
            final boolean success = responseCode == 200;

            if(success) {
                try {
                    Log.d("result", result);
                    JSONObject resJson = new JSONObject(result);
                    JSONObject files = resJson.getJSONObject("ok");
                    HeadServerURL = files.getJSONObject("small").getString("url");
                    BodyServerURL = files.getJSONObject("large").getString("url");
                } catch(JSONException e) {
                    e.printStackTrace();
                }
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    executeUI(success);
                }
            });
        }

        private void executeUI(boolean success) {
            progress.hide();
            if(success) {
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
        new AsyncAvatarUploadThread(serverURL+"/avatar", uploadCallback).start();
    }

    @Override
    protected  void onDestroy() {
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
                                    saveJson();
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

    private void checkLastAvatar(final Callback callback) {
        progress.show();

        new Thread(new Runnable() {
            int responseCode;

            @Override
            public void run() {
                HttpURLConnection conn;
                final StringBuilder sbResult = new StringBuilder();
                try {
                    URL url = new URL(serverURL+"/app-data/avatar");
                    conn = (HttpURLConnection)url.openConnection();
                    conn.setRequestProperty("X-Api-Key", "abc123");
                    conn.setRequestProperty("X-Device-Id", Build.SERIAL);
                    conn.setRequestProperty(uploadHeader, userToken);

                    responseCode = conn.getResponseCode();

                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sbResult.append(line);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progress.hide();
                        callback.onFinished(responseCode, sbResult.toString());
                    }
                });
            }
        }).start();
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
                        try {
                            xoDataProvider.launchLoginActivity(MainActivity.this);
                        } catch (ActivityNotFoundException e) {
                            Toast.makeText(getApplicationContext(), "XO-id is not installed!", Toast.LENGTH_SHORT).show();
                            finish();
                        }
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
        try {
            bitmapBody.compress(Bitmap.CompressFormat.PNG, 100, openFileOutput(URL_SS_BODY, MODE_PRIVATE));
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            bitmapHead.compress(Bitmap.CompressFormat.PNG, 100, openFileOutput(URL_SS_HEAD, MODE_PRIVATE));
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
        public void onFinished(int res, String resJson) {}
    }

    private class AsyncAvatarUploadThread extends Thread {
        String address;
        Callback callback;

        public AsyncAvatarUploadThread(String addr, Callback callback){
            address = addr;
            this.callback = callback;
        }

        public void run(){
            HttpURLConnection conn = null;
            int responseCode = 0;
            StringBuilder sbResult = new StringBuilder();

            try {
                HttpEntity reqEntity = MultipartEntityBuilder.create()
                        .addBinaryBody("large", openFileInput(URL_SS_BODY), ContentType.DEFAULT_BINARY, URL_SS_BODY)
                        .addBinaryBody("small", openFileInput(URL_SS_HEAD), ContentType.DEFAULT_BINARY, URL_SS_HEAD)
                        .addBinaryBody("json_config", openFileInput(URL_CONFIG), ContentType.APPLICATION_JSON, URL_CONFIG)
                        .build();

                URL url = new URL(address);
                conn = (HttpURLConnection)url.openConnection();
                conn.setDoOutput(true);
                conn.setConnectTimeout(10 * 1000);
                conn.setRequestProperty("X-Api-Key", "abc123");
                conn.setRequestProperty("X-Device-Id", Build.SERIAL);
                conn.setRequestProperty(uploadHeader, userToken);
                conn.setRequestProperty(reqEntity.getContentType().getName(), reqEntity.getContentType().getValue());

                OutputStream os = conn.getOutputStream();
                reqEntity.writeTo(os);
                os.close();

                responseCode = conn.getResponseCode();

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line = "";
                while ((line = reader.readLine()) != null) {
                    sbResult.append(line);
                }
            } catch (IOException e) {

                Util.debug(e.getMessage());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }

            callback.onFinished(responseCode, sbResult.toString());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // If xo-id need to be updated, do not get data
        if (!checkXOIDVersion()) {
            return;
        }

        xoDataProvider.requestStudentData(new XoDataProvider.DataCallback() {
            @Override
            public void onDataAvailable(final String data) {
                Util.debug(data);
                boolean needCheckAvatar = false;
                if (serverURL == null) {
                    needCheckAvatar = true;
                }
                try {
                    JSONObject dataJson = new JSONObject(data);
                    serverURL = dataJson.getString("serverURL");

                    JSONObject userData = dataJson.getJSONObject("user");
                    String userType = userData.getString("type");
                    JSONObject user = userData.getJSONObject(userType);

                    userToken = user.getString("token");

                    if(userType.equalsIgnoreCase("teacher")) {
                        uploadHeader = "X-Teacher-Token";
                    } else if (userType.equalsIgnoreCase("student")) {
                        uploadHeader = "X-Student-Token";
                    } else if (userType.equalsIgnoreCase("public_user")) {
                        uploadHeader = "X-Public-User-Token";
                    } else {
                        throw new JSONException("userType is not valid");
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

                if (needCheckAvatar) {
                    checkLastAvatar(new Callback() {
                        @Override
                        public void onFinished(int responseCode, String resJson) {
                            try {
                                if (responseCode == 200) {
                                    JSONObject config = new JSONObject(resJson);
                                    showLoadAvatarDlg(config);
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
            }

            @Override
            public void onError(String description) {
                Util.debug("XoDataProvider error: " + description);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showNotLoginAlertDialog();
                    }
                });
            }
        });
    }

    private int getVersionCode(String packageName) {
        PackageManager packageManager = getPackageManager();
        try {
            return packageManager.getPackageInfo(packageName, 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return Integer.MAX_VALUE;
        }
    }

    private boolean checkXOIDVersion() {
        boolean xoid = getVersionCode("org.oneedu.xoid") <= 110;

        if (!xoid) {
            return true;
        }

        new AlertDialog.Builder(this).setTitle("Warning")
                .setCancelable(false)
                .setMessage("You need to upgrade XO-ID. Click OK to update XO-ID.")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent();
                        intent.setClassName("org.oneedu.appuniverse","org.oneedu.appuniverse.FDroid");
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.putExtra("extraTab", true);
                        startActivity(intent);
                    }
                }).create().show();

        return false;
    }

    private void showLoadAvatarDlg(final JSONObject config) {
        new MaterialDialog.Builder(MainActivity.this)
                .title("XO-ID")
                .content("Do you want to load current avatar or start new one?")
                .positiveText("Load")
                .negativeText("New")
                .cancelable(false)
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        dialog.dismiss();
                        avatarConfig.loadConfig(config);
                    }

                    @Override
                    public void onNegative(MaterialDialog dialog) {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // Reset serverURL to re-check avatar from server.
        // It's because onNewIntent means it's not just resuming.
        serverURL = null;
    }
}
