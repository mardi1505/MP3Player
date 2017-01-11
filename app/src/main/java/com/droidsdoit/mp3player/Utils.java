package com.droidsdoit.mp3player;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Paul Keeling on 1/7/2017.
 */

public class Utils {
    private String TAG = "Utils";

    private Context mCtx = null;
    private SharedPreferences mPrefs = null;

    public Utils(Context ctx) {

        mCtx = ctx;
        mPrefs = ctx.getSharedPreferences("mp3player", Context.MODE_MULTI_PROCESS);
    }

    //Shared Prefernces
    public int getPreference(String name, int defaultValue) {
        return mPrefs.getInt(name, defaultValue);
    }

    public void setPreference(String name, int value) {
        SharedPreferences.Editor e = mPrefs.edit();
        e.putInt(name, value);
        e.commit();
    }

    //Connectivity
    public boolean urlExists(String testURL, int timeout) {
        boolean retVal = false;
        try {
            URL url = new URL(testURL);
            HttpURLConnection cn = (HttpURLConnection) url.openConnection();
            cn.setInstanceFollowRedirects(true);
            cn.setConnectTimeout(timeout);
            cn.connect();

            Log.i(TAG, "PlayMP3Task urlExists http status code: " + cn.getResponseCode());

            //Now test if the url contains any data
            InputStream stream = cn.getInputStream();
            BufferedInputStream in = new BufferedInputStream(stream);
            int i = in.read();
            in.close();
            retVal = i > 0 ? true : false;
        } catch(Exception ex) {
            Log.e(TAG, "PlayMP3Task urlExists exception: " + ex.getMessage());
        }

        Log.i(TAG, "PlayMP3Task urlExists returning " + retVal + " for " + testURL);
        return retVal;
    }

    /**
     * Checks for a regular expression match.
     * @param input String to check for a regular expression match
     * @param regex Specifies the regular expression, e.g. ".*"
     * @param flags Specifies the flags, e.g. Pattern.CASE_INSENSITIVE
     * @return return true if a match is found, otherwise false
     */

    public boolean findMatch(String input, String regex, int flags) {
        try {
            Pattern p = Pattern.compile(regex, flags);
            Matcher m = p.matcher(input);
            if (m.find())
                return true;
        } catch(Exception ex) {
            Log.i(TAG, "findMatch exception: " + ex.getMessage());
        }
        return false;
    }

    /**
     * Checks for a regular expression match.
     * @param input String to check for a regular expression match
     * @param regex Specifies the regular expression, e.g. ".*"
     * @param index Specifies the index of the match, 0 is the whole string, 1 is the first match captured in (), etc.
     * @param flags Specifies the flags, e.g. Pattern.CASE_INSENSITIVE
     * @return returns the matching string if a match is found, otherwise null
     */

    public String getMatch(String input, String regex, int index, int flags) {
        try {
            Pattern p = Pattern.compile(regex, flags);
            Matcher m = p.matcher(input);
            if (m.find())
                return m.group(index);
        } catch(Exception ex) {
            Log.i(TAG, "getMatch exception: " + ex.getMessage());
        }
        return null;
    }
}
