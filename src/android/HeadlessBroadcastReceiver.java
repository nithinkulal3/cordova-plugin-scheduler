package com.catalpa.scheduler;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;


public class HeadlessBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SchedulerPlugin adapter = SchedulerPlugin.getInstance(context.getApplicationContext());
        if (adapter.isMainActivityActive()) {
            return;
        }
        Log.d(SchedulerPlugin.TAG, "HeadlessBroadcastReceiver onReceive");
        new SchedulerPluginHeadlessTask().onFetch(context.getApplicationContext());
    }
}
