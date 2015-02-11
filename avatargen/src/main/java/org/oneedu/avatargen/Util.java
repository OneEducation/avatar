package org.oneedu.avatargen;

import android.util.Log;

public class Util {

    public static boolean debugMode = false;

    private static String tag = "AvatarGen";

    public static void setProjectName(String t) {
        tag = t;
    }

    public static String getProjectTag() {
        return tag;
    }

    public static void setDebugMode(boolean debugMode) {
        Util.debugMode = debugMode;
    }

    public static void debug(String message) {
        if (debugMode) {
            Log.d(tag, message);
        }
    }

    public static void error(String message, Throwable t) {
        Log.e(tag, message, t);
    }

}
