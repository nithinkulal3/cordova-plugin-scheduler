package com.catalpa.scheduler;

import android.annotation.TargetApi;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.util.Timer;
import java.util.concurrent.TimeUnit;
import java.util.TimerTask;

import android.os.Build;
import android.os.Bundle;
import com.bridgefy.sdk.client.Bridgefy;
import com.bridgefy.sdk.client.BridgefyClient;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import com.bridgefy.sdk.client.MessageListener;
import com.bridgefy.sdk.client.RegistrationListener;
import com.bridgefy.sdk.client.StateListener;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import com.bridgefy.sdk.client.Config;
import android.Manifest;
import java.util.HashMap;
import com.bridgefy.sdk.client.Device;
import com.bridgefy.sdk.client.Message;
import com.bridgefy.sdk.client.Session;
import org.json.JSONObject;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothAdapter;
import android.app.Activity;
import android.app.ActivityManager;
// import android.app.ComponentName;
import android.content.Intent;
import com.bridgefy.sdk.client.BFEnergyProfile;
import android.support.v4.app.TaskStackBuilder;
import android.app.PendingIntent;
import org.json.JSONException;
import org.apache.cordova.CallbackContext;

@TargetApi(21)
public class FetchJobService extends JobService {
    private String bridgefyApiKey;
    public static final String CHANNEL_1_ID = "channel1";
    private NotificationManagerCompat notificationManager;
    private Context context;
    private static int count = 0;
    public Timer t;
    public String packageName;
    public Boolean bridgefyStarted = false;
    @Override
    public boolean onStartJob(final JobParameters params) {
        context = getApplicationContext();
        notificationManager = NotificationManagerCompat.from(getApplicationContext());
        createNotificationChannels();
        bridgefyInit(context);
        Log.d(SchedulerPlugin.TAG, "- jobStarted and this is here");

        t = new Timer();
        t.scheduleAtFixedRate(new TimerTask(){
            @Override
            public void run(){
                count++;
                Log.d(SchedulerPlugin.TAG, Integer.toString(count));
                if (count >= 180) {
                    t.cancel();
                    t.purge();
                    count = 0;
                    return;
                }
                if (bridgefyStarted) {
                    Boolean bridgefyResume = Bridgefy.resume();
                    Log.d(SchedulerPlugin.TAG, "is resume? " + bridgefyResume.toString());
                }
            }
        },0,5000);

        Log.d(SchedulerPlugin.TAG, "- jobStarted and wait for 1 minute and came here");

        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        if (activeNetworkInfo == null || !activeNetworkInfo.isConnected()) {
            Log.d(SchedulerPlugin.TAG, "no network available, noop job");
            return false;
        }

        CompletionHandler completionHandler = new CompletionHandler() {
            @Override
            public void finish() {
                Log.d(SchedulerPlugin.TAG, "- jobFinished");
                jobFinished(params, false);
            }
        };
        SchedulerPlugin.getInstance(context).onFetch(completionHandler);
        return true;
    }

    @Override
    public boolean onStopJob(final JobParameters params) {
        Log.d(SchedulerPlugin.TAG, "- onStopJob");
        jobFinished(params, false);
        return true;
    }

    public interface CompletionHandler {
        void finish();
    }

    public void bridgefyInit(Context context) {
        Log.d(SchedulerPlugin.TAG, "- BridgefyInit start");    
        Bridgefy.initialize(getApplicationContext(), "7631f6cf-a401-402d-bcd2-2497e9d5ffe5", new RegistrationListener() {
            @Override
            public void onRegistrationSuccessful(BridgefyClient bridgefyClient) {
                Log.i(SchedulerPlugin.TAG, "onRegistrationSuccessful: current userId is: " + bridgefyClient.getUserUuid());
                Log.i(SchedulerPlugin.TAG, "Device Rating " + bridgefyClient.getDeviceProfile().getRating());
                Log.i(SchedulerPlugin.TAG, "Device Evaluation " + bridgefyClient.getDeviceProfile().getDeviceEvaluation());
                // Start Bridgefy
                startBridgefy();
            }
            
            @Override
            public void onRegistrationFailed(int errorCode, String message) {
                Log.i(SchedulerPlugin.TAG, "onRegistrationFailed");
            }    
        });
    }

    /**
     *      BRIDGEFY METHODS
     */
    private void startBridgefy() {
        Config.Builder builder = new Config.Builder();
        builder.setEnergyProfile(BFEnergyProfile.HIGH_PERFORMANCE);
        Bridgefy.start(messageListener, stateListener,builder.build());
    }

    private MessageListener messageListener = new MessageListener() {
        @Override
        public void onBroadcastMessageReceived(Message message) {
          JSONObject s = new JSONObject(message.getContent());;
          Log.d(SchedulerPlugin.TAG, "Message: " + s);
          try {
                packageName = context.getPackageName().toString();
                if (packageName.equals("com.farmfreshweb.consumer")) {
                    sendNotification(s.getString("enterpriseName"), s.getString("description"));
                }
          } catch (JSONException e) {
            Log.d(SchedulerPlugin.TAG, "notify error: " + e);
            //some exception handler code.
          }  
        }

        @Override
        public void onMessageReceived(Message message) {
            Log.i(SchedulerPlugin.TAG, "onMessageReceived: ");
            // String s = (String) message.getContent().get("key");
            // sendToIonic("bridgefyMessage", s.toString(), true);
            // String s = message.getContent().get("manufacturer ") + " " + message.getContent().get("model");
            // Log.d(TAG, "Message Received: " + message.getSenderId() + ", content: " + s);
        }

    };

    StateListener stateListener = new StateListener() {
        @Override
        public void onDeviceConnected(final Device device, Session session) {
            // send our information to the Device
            Log.w(SchedulerPlugin.TAG, "onDeviceConnected: " + device);
            HashMap<String, Object> map = new HashMap<>();
            map.put("device_name", Build.MANUFACTURER + " " + Build.MODEL);
            device.sendMessage(map);
        }

        @Override
        public void onDeviceLost(Device peer) {
            Log.w(SchedulerPlugin.TAG, "onDeviceLost: " + peer.getUserId());
            // peersAdapter.removePeer(peer);
        }

        @Override
        public void onStarted() {
            bridgefyStarted = true;
            Log.e(SchedulerPlugin.TAG, "onStarted:");
        }

        @Override
        public void onStartError(String message, int errorCode) {
            super.onStartError(message, errorCode);
            Log.e(SchedulerPlugin.TAG, "onStartError: " + message);
        }
    };

    public void sendNotification(String title, String message) {
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        launchIntent.addFlags(Intent.FLAG_FROM_BACKGROUND);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, launchIntent, 0);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_1_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();
        notificationManager.notify(1, notification);

    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel1 = new NotificationChannel(
                    CHANNEL_1_ID,
                    "Channel 1",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel1.setDescription("Market message channel");
            NotificationManager manager = this.getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel1);
        }
    }

}