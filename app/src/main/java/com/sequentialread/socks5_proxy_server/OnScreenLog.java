package com.sequentialread.socks5_proxy_server;

// this code was lifted directly from
// https://stackoverflow.com/questions/4242765/show-log-messages-on-screen-for-android-application

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;


/**
 * Created by ariel on 07/07/2016.
 */
public class OnScreenLog {
    private static String TAG = "socks5_proxy_server";
    private static int timeoutTime = 1000;
    private static TextView textView;
    private static int logCount = 0;
    private static int logCountMax = 60;
    private static String[] logs = new String[logCountMax];


    public static void initialize(Activity activity){

        // grab the first TextView with text="OnScreenLog" from the main activity and use it as the view.
        textView = searchForOnScreenLogTextViewRecurse(activity.getWindow().getDecorView());
    }

    public static void logScreenOnly (String text){
        maintainLog(text);
    }

    public static void log (String text){
        Log.d(TAG, text);
        maintainLog(text);
    }

    private static void maintainLog(String newText){
        String logText = "";
        if(logCount<logCountMax) logCount++;
        for(int i=logCount-1; i>0; i--){
            logs[i] = logs[i-1];
        }
        logs[0] = newText;
        for(int i=0; i<logCount; i++) {
            // display the 0th log element at the bottom like a terminal.
            int reverseIndex = (logCount-1)-i;

            if(i<logCount-1) logText+=logs[reverseIndex]+System.getProperty("line.separator");
            else logText+=logs[reverseIndex];
        }
        textView.setText(logText);
    }

    private static TextView searchForOnScreenLogTextViewRecurse(View parent) {

        //Log.d(TAG, "parent is a" + parent.getClass().getSimpleName() + ", text:" + (parent instanceof TextView ? "true" : "false") + ", group:" + (parent instanceof ViewGroup ? "true" : "false"));

        if(parent instanceof TextView && ((TextView)parent).getText().toString().equals("OnScreenLog")) {
            return (TextView)parent;
        }
        if(parent instanceof ViewGroup) {
            ViewGroup parentAsVG = (ViewGroup)parent;
            for (int i=0; i < parentAsVG.getChildCount(); i++) {
                TextView found = searchForOnScreenLogTextViewRecurse(parentAsVG.getChildAt(i));
                if(found != null) {
                    return found;
                }
            }
        }
        return null;
    }

}