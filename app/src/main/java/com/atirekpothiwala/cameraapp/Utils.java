package com.atirekpothiwala.cameraapp;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.widget.Toast;

import java.util.List;

public class Utils {

    public static boolean isPermissionsGranted(Context context, String[] permissions) {

        for (int i = 0; i < permissions.length; i++) {
            if (ContextCompat.checkSelfPermission(context, permissions[i]) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static void requestPermissions(Context context, String[] permissions) {
        ActivityCompat.requestPermissions((Activity) context, permissions, Interfaces.REQUESTS.SYSTEM);
    }

    public static void popToast(Context context, Object data) {
        try {
            Toast.makeText(context, data.toString(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            checkLog("popToast>>", data.toString(), null);
        }
    }

    public static void checkLog(String TAG, Object data, Throwable throwable) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.d(TAG + ">>", data.toString(), throwable);
            } else {
                Log.d(TAG + ">>", data.toString());
            }
        }
    }

    public static void checkLongLog(String TAG, String message) {
        if (message.length() > 4000) {
            checkLog(TAG, message.substring(0, 4000), null);
            checkLongLog(TAG, message.substring(4000));
        } else
            checkLog(TAG, message, null);
    }

    public static void restartApp(Context context) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }

    public static boolean isAppOnForeground(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
        if (appProcesses == null) {
            return false;
        }
        final String packageName = context.getPackageName();
        for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
            if (appProcess.processName.equals(packageName) && appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                return true;
            }
        }
        return false;
    }

    public static int getColor(Context context, int color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.getColor(color);
        } else {
            return context.getResources().getColor(color);
        }
    }

    public static Size getScreenSize(Activity activity) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        return new Size(displayMetrics.widthPixels, displayMetrics.heightPixels);
    }

    public static int getScreenRotation(Activity activity) {
        return activity.getWindowManager().getDefaultDisplay().getRotation();
    }

}
