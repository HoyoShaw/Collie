package com.snail.collie.battery;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.snail.collie.Collie;
import com.snail.collie.core.CollieHandlerThread;
import com.snail.collie.core.ITracker;
import com.snail.collie.core.SimpleActivityLifecycleCallbacks;
import com.snail.collie.debug.DebugHelper;

import java.util.HashMap;

public class BatteryStatsTrack implements ITracker {
    private static BatteryStatsTrack sInstance;
    private Handler mHandler;
    private String display;

    private BatteryStatsTrack() {
        mHandler = new Handler(CollieHandlerThread.getInstance().getHandlerThread().getLooper());
    }

    public static BatteryStatsTrack getInstance() {
        if (sInstance == null) {
            synchronized (DebugHelper.class) {
                if (sInstance == null) {
                    sInstance = new BatteryStatsTrack();
                }
            }
        }
        return sInstance;
    }

    private SimpleActivityLifecycleCallbacks mSimpleActivityLifecycleCallbacks = new SimpleActivityLifecycleCallbacks() {

        @Override
        public void onActivityPaused(final @NonNull Activity activity) {
            super.onActivityPaused(activity);
            BatteryInfo batteryInfo = mBatteryInfoHashMap.get(activity);
            if (batteryInfo == null) {
                batteryInfo = new BatteryInfo();
                mBatteryInfoHashMap.put(activity, batteryInfo);
            }
            final BatteryInfo batteryInfoBack = batteryInfo;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    computeBatteryInfo(activity, batteryInfoBack);
                }
            });
        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {
            super.onActivityStarted(activity);
            lastTimeStamp = SystemClock.uptimeMillis();
            lastPercent = mBatteryLevelReceiver.getCurrentBatteryLevel();
        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {
            super.onActivityDestroyed(activity);
            mBatteryInfoHashMap.remove(activity);
        }
    };

    private long lastTimeStamp;
    private int lastPercent;

    @Override
    public void destroy(Application application) {
        if (mBatteryLevelReceiver != null) {
            application.unregisterReceiver(mBatteryLevelReceiver);
            mBatteryLevelReceiver = null;
        }
        Collie.getInstance().removeActivityLifecycleCallbacks(mSimpleActivityLifecycleCallbacks);
    }

    @Override
    public void startTrack(Application application) {
        Collie.getInstance().addActivityLifecycleCallbacks(mSimpleActivityLifecycleCallbacks);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        application.registerReceiver(mBatteryLevelReceiver = new BatteryLevelReceiver(), intentFilter);
    }

    private BatteryLevelReceiver mBatteryLevelReceiver;
    private HashMap<Activity, BatteryInfo> mBatteryInfoHashMap = new HashMap<>();

    @Override
    public void pauseTrack(Application application) {

    }

    private BatteryInfo computeBatteryInfo(Activity activity, @NonNull BatteryInfo info) {
        info.activityName = activity.getClass().getSimpleName();

        if (TextUtils.isEmpty(display)) {
            display = "" + activity.getResources().getDisplayMetrics().widthPixels + "*" + activity.getResources().getDisplayMetrics().heightPixels;
        }

        BatteryInfo batteryInfo = new BatteryInfo();
        batteryInfo.charging = mBatteryLevelReceiver.isCharging();
        batteryInfo.cost = mBatteryLevelReceiver.isCharging() ? 0 : batteryInfo.cost + (lastPercent - mBatteryLevelReceiver.getCurrentBatteryLevel());
        batteryInfo.duration += SystemClock.uptimeMillis() - lastTimeStamp;
        batteryInfo.screenBrightness = activity.getWindow().getAttributes().screenBrightness;
        batteryInfo.display = display;
        Log.v("Battery", "" + batteryInfo.duration + " cost " + batteryInfo.cost);
        return batteryInfo;
    }

}
