package com.example.android.shushme;

/*
* Copyright (C) 2017 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*  	http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

public class GeofenceTransitionsIntentService extends IntentService {

    public static final String TAG = GeofenceTransitionsIntentService.class.getSimpleName();

    public GeofenceTransitionsIntentService() {
        super(TAG);
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    /***
     * This is called when 1 or more Geofences are triggered
     *
     * @param intent contains information about the Geofence triggered
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        if (geofencingEvent.hasError()) {
            Log.e(TAG, String.format("Error code : %d", geofencingEvent.getErrorCode()));
            return;
        }

        // Get the transition type.
        int geofenceTransition = geofencingEvent.getGeofenceTransition();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        // Check which transition type has triggered this event
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            //Set the phone to silent
            if (android.os.Build.VERSION.SDK_INT < 24 ||
                    (android.os.Build.VERSION.SDK_INT >= 24 && !nm.isNotificationPolicyAccessGranted())) {
                AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
                audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
            }
        } else if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            //Set the phone to normal
            if (android.os.Build.VERSION.SDK_INT < 24 ||
                    (android.os.Build.VERSION.SDK_INT >= 24 && !nm.isNotificationPolicyAccessGranted())) {
                AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
                audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            }
        } else {
            // Log the error.
            Log.e(TAG, String.format("Unknown transition : %d", geofenceTransition));
            // No need to do anything else
            return;
        }
        // Send the notification
        sendNotification(geofenceTransition);
    }

    /**
     * Posts a notification in the notification bar when a transition is detected.
     * If the user clicks the notification, control goes to the MainActivity.
     */
    private void sendNotification(int transitionType) {
        // Create an explicit content Intent that starts the main Activity.
        Intent notificationIntent = new Intent(getApplicationContext(), MainActivity.class);

        // Construct a task stack.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);

        // Add the main Activity to the task stack as the parent.
        stackBuilder.addParentStack(MainActivity.class);

        // Push the content Intent onto the stack.
        stackBuilder.addNextIntent(notificationIntent);

        // Get a PendingIntent containing the entire back stack.
        PendingIntent notificationPendingIntent =
                stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        // Get a notification builder that's compatible with platform versions >= 4
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);

        // Define the notification settings.
        builder.setSmallIcon(getTransitionIcon(transitionType))
                // In a real app, you may want to use a library like Volley
                // to decode the Bitmap.
                .setLargeIcon(BitmapFactory.decodeResource(getResources(),
                        getTransitionIcon(transitionType)))
                .setContentTitle(getTransitionString(transitionType))
                .setContentText(getString(R.string.touch_to_relaunch))
                .setContentIntent(notificationPendingIntent);

        // Dismiss notification once the user touches it.
        builder.setAutoCancel(true);

        // Get an instance of the Notification manager
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Issue the notification
        mNotificationManager.notify(0, builder.build());
    }

    private int getTransitionIcon(int transitionType) {
        switch (transitionType) {
            case Geofence.GEOFENCE_TRANSITION_ENTER:
                return R.drawable.ic_volume_off_white_24dp;
            case Geofence.GEOFENCE_TRANSITION_EXIT:
                return R.drawable.ic_volume_up_white_24dp;
            default:
                return R.drawable.ic_gps_fixed_white_24dp;
        }
    }

    /**
     * Maps geofence transition types to their human-readable equivalents.
     *
     * @param transitionType A transition type constant defined in Geofence
     * @return The message to be displayed in the notification
     */
    private String getTransitionString(int transitionType) {
        switch (transitionType) {
            case Geofence.GEOFENCE_TRANSITION_ENTER:
                return getString(R.string.geofence_entered);
            case Geofence.GEOFENCE_TRANSITION_EXIT:
                return getString(R.string.geofence_exited);
            default:
                return getString(R.string.unknown_geofence_transition);
        }
    }
}
