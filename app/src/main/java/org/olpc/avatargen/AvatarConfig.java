package org.olpc.avatargen;

import android.graphics.Picture;

import org.json.JSONObject;

import rx.Observable;
import rx.functions.Action1;
import rx.subjects.PublishSubject;

public class AvatarConfig {

    public Part droidHead;
    public Part droidBody;
    public Part droidArm;
    public Part droidLegs;
    public Part droidFeet;

    private JSONObject jsonObject = new JSONObject();

    PublishSubject  ps = PublishSubject.create();
    Observable      observable = ps.asObservable();

    public AvatarConfig(AssetDatabase db) {

        Picture body = db.getSVGForResource(R.raw.avatar_body).getPicture();
        Picture head = db.getSVGForResource(R.raw.avatar_head).getPicture();
        Picture arm = db.getSVGForResource(R.raw.avatar_arm).getPicture();
        Picture legs = db.getSVGForResource(R.raw.avatar_legs).getPicture();
        Picture feet = db.getSVGForResource(R.raw.avatar_feet).getPicture();

        droidFeet = new Part(feet, 0, 0, 0, 0);
        droidBody = new Part(body, Constants.RESIZE_BODY_MIN_X, Constants.RESIZE_BODY_MIN_Y, Constants.RESIZE_BODY_MAX_X, Constants.RESIZE_BODY_MAX_Y);
        droidHead = new Part(head, Constants.RESIZE_HEAD_MIN_X, Constants.RESIZE_HEAD_MIN_Y, Constants.RESIZE_HEAD_MAX_X, Constants.RESIZE_HEAD_MAX_Y);
        droidArm = new Part(arm, Constants.RESIZE_ARMS_MIN_X, Constants.RESIZE_ARMS_MIN_Y, Constants.RESIZE_ARMS_MAX_X, Constants.RESIZE_ARMS_MAX_Y);
        droidLegs = new Part(legs, Constants.RESIZE_LEGS_MIN_X, Constants.RESIZE_LEGS_MIN_Y, Constants.RESIZE_LEGS_MAX_X, Constants.RESIZE_LEGS_MAX_Y);
    }

    public void setPart(AssetDatabase.ConfigPart part, String item) {
        try {
            if(part == AssetDatabase.ConfigPart.sets) {
                jsonObject.put(AssetDatabase.ConfigPart.shirt.name(), item);
                jsonObject.put(AssetDatabase.ConfigPart.pants.name(), item);
                jsonObject.put(AssetDatabase.ConfigPart.shoes.name(), item);
                jsonObject.put(AssetDatabase.ConfigPart.hats.name(), item);
                jsonObject.put(AssetDatabase.ConfigPart.handAcc.name(), item);
                jsonObject.put(AssetDatabase.ConfigPart.hair.name(), item);
                jsonObject.put(AssetDatabase.ConfigPart.bodyAcc.name(), item);
                jsonObject.put(AssetDatabase.ConfigPart.beard.name(), item);
            } else {
                jsonObject.put(part.name(), item);
            }
            ps.onNext(jsonObject);
        } catch (Exception e) {
            Util.debug(e.getMessage());
        }
    }

    public void loadConfig(JSONObject json) {
        jsonObject = json;
        try {
            droidBody.setScale((float)json.getJSONObject("droidBody").getDouble("scaleX"), (float)json.getJSONObject("droidBody").getDouble("scaleY"));
            droidHead.setScale((float)json.getJSONObject("droidHead").getDouble("scaleX"), (float)json.getJSONObject("droidHead").getDouble("scaleY"));
            droidArm.setScale((float)json.getJSONObject("droidArm").getDouble("scaleX"), (float)json.getJSONObject("droidArm").getDouble("scaleY"));
            droidLegs.setScale((float)json.getJSONObject("droidLegs").getDouble("scaleX"), (float)json.getJSONObject("droidLegs").getDouble("scaleY"));

        } catch(Exception e) {
            Util.debug(e.getMessage());
        }
        ps.onNext(jsonObject);
    }

    public JSONObject getConfig() {
        try {
            jsonObject.put("droidHead", droidHead.getJson());
            jsonObject.put("droidBody", droidBody.getJson());
            jsonObject.put("droidArm", droidArm.getJson());
            jsonObject.put("droidLegs", droidLegs.getJson());

        } catch(Exception e) {
            Util.debug(e.getMessage());
        }
        return jsonObject;
    }

    public void setSubscriber(Action1 action) {
        observable.subscribe(action);
        ps.onNext(jsonObject);
    }

}
