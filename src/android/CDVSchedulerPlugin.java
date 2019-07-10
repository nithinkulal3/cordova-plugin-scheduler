package com.catalpa.scheduler;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.Manifest;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import java.util.Arrays;
import android.content.Context;
import android.provider.Settings;
import android.util.Log;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothAdapter;

public class CDVSchedulerPlugin extends CordovaPlugin {
    private Context cordovaContext;
    private boolean isForceReload = false;
    private final int PERMISSION_RUNTIME_LOCATION = 6126;
    private CallbackContext schedularCallbackContext;
    JSONObject schedularData = new JSONObject();
    @Override
    protected void pluginInitialize() {
        Activity activity   = cordova.getActivity();
        Intent launchIntent = activity.getIntent();
        String action 		= launchIntent.getAction();

        if ((action != null) && (SchedulerPlugin.ACTION_FORCE_RELOAD.equalsIgnoreCase(action))) {
            isForceReload = true;
            activity.moveTaskToBack(true);
        }
    }

    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {
        boolean result = false;
        if (SchedulerPlugin.ACTION_CONFIGURE.equalsIgnoreCase(action)) {
            result = true;
            
            cordovaContext = this.cordova.getActivity().getApplicationContext();
            Activity activity = this.cordova.getActivity();
            BluetoothManager bluetoothManager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
            bluetoothAdapter.enable();
            if (!cordova.hasPermission(Manifest.permission_group.LOCATION)) {
                cordova.requestPermission(this, PERMISSION_RUNTIME_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION);
            }
            schedularData = data.getJSONObject(0);
            schedularCallbackContext = callbackContext;
            
        } else if (SchedulerPlugin.ACTION_START.equalsIgnoreCase(action)) {
            result = true;
            start(callbackContext);
        } else if (SchedulerPlugin.ACTION_STOP.equalsIgnoreCase(action)) {
            result = true;
            stop(callbackContext);
        } else if (SchedulerPlugin.ACTION_STATUS.equalsIgnoreCase(action)) {
            result = true;
            callbackContext.success(getAdapter().status());
        } else if (SchedulerPlugin.ACTION_FINISH.equalsIgnoreCase(action)) {
            finish(callbackContext);
            result = true;
        }
        return result;
    }

    private void configure(JSONObject options, final CallbackContext callbackContext) throws JSONException {
        SchedulerPlugin adapter = getAdapter();
        SchedulerPluginConfig.Builder config = new SchedulerPluginConfig.Builder();
        if (options.has("minimumFetchInterval")) {
            config.setMinimumFetchInterval(options.getInt("minimumFetchInterval"));
        }
        // Log.d(SchedulerPlugin.TAG, "- Configure section");
        SchedulerPlugin.Callback callback = new SchedulerPlugin.Callback() {
            @Override
            public void onFetch() {
                PluginResult result = new PluginResult(PluginResult.Status.OK);
                result.setKeepCallback(true);
                callbackContext.sendPluginResult(result);
            }
        };
        adapter.configure(config.build(), callback);
        if (isForceReload) {
            callback.onFetch();
        }
        isForceReload = false;
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
      if (permissions.length != 1 || grantResults.length != 1 || !Manifest.permission.ACCESS_COARSE_LOCATION.equals(permissions[0])) {
        throw new RuntimeException("Unexpected permission results " + Arrays.toString(permissions) + ", " + Arrays.toString(grantResults));
      }
      int result = grantResults[0];
      String action = null;
      switch (result) {
        case PackageManager.PERMISSION_DENIED:
          // Do nothing
          Log.d(SchedulerPlugin.TAG, "- Permission Denied");
          break;
        case PackageManager.PERMISSION_GRANTED:
          final LocationManager manager = (LocationManager) cordovaContext.getSystemService(Context.LOCATION_SERVICE);
          if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            cordovaContext.startActivity(intent);
          }
          configure(schedularData, schedularCallbackContext);
          break;
        default:
          throw new RuntimeException("Unexpected permission result int " + result);
      }
    }

    @TargetApi(21)
    private void start(CallbackContext callbackContext) {
        SchedulerPlugin adapter = getAdapter();
        adapter.start();
        callbackContext.success(adapter.status());
    }

    private void stop(CallbackContext callbackContext) {
        SchedulerPlugin adapter = getAdapter();
        adapter.stop();
        callbackContext.success();
    }

    private void finish(CallbackContext callbackContext) {
        SchedulerPlugin adapter = getAdapter();
        adapter.finish();
        callbackContext.success();
    }

    private SchedulerPlugin getAdapter() {
        return SchedulerPlugin.getInstance(cordova.getActivity().getApplicationContext());
    }
}
