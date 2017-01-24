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

import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

import com.example.android.shushme.provider.PlaceContract;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Result;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.PlaceBuffer;
import com.google.android.gms.location.places.Places;

import java.util.ArrayList;
import java.util.List;

public class Geofencing implements ResultCallback {

    // Constants
    public static final String TAG = Geofencing.class.getSimpleName();
    private static final float GEOFENCE_RADIUS = 50; // 50 meters
    private static final long GEOFENCE_TIMEOUT = 24 * 60 * 60 * 1000; // 24 hours

    private List<Geofence> mGeofenceList;
    private PendingIntent mGeofencePendingIntent;
    private GoogleApiClient mGoogleApiClient;
    private Context mContext;

    public Geofencing(Context context, GoogleApiClient client) {
        mContext = context;
        mGoogleApiClient = client;
        mGeofencePendingIntent = null;
        mGeofenceList = new ArrayList<>();
    }

    /***
     * Registers the list of Geofences specified in mGeofenceList with Google Place Services
     * Uses {@code #mGoogleApiClient} to connect to Google Place Services
     * Uses {@link #getGeofencingRequest} to get the list of Geofences to be registered
     * Uses {@link #getGeofencePendingIntent} to get the pending intent to launch the IntentService
     * when the Geofence is triggered
     * Triggers {@link #onResult} when the geofences have been registered successfully
     */
    public void registerAllGeofences() {
        // Check that the API client is connected and that the list has Geofences in it
        if (mGoogleApiClient == null || !mGoogleApiClient.isConnected() ||
                mGeofenceList == null || mGeofenceList.size() == 0) {
            return;
        }
        try {
            LocationServices.GeofencingApi.addGeofences(
                    mGoogleApiClient,
                    getGeofencingRequest(),
                    getGeofencePendingIntent()
            ).setResultCallback(this);
        } catch (SecurityException securityException) {
            // Catch exception generated if the app does not use ACCESS_FINE_LOCATION permission.
            Log.e(TAG, securityException.getMessage());
        }
    }

    /***
     * Unregisters all the Geofences created by this app from Google Place Services
     * Uses {@code #mGoogleApiClient} to connect to Google Place Services
     * Uses {@link #getGeofencePendingIntent} to get the pending intent passed when
     * registering the Geofences in the first place
     * Triggers {@link #onResult} when the geofences have been unregistered successfully
     */
    public void unRegisterAllGeofences() {
        if (mGoogleApiClient == null || !mGoogleApiClient.isConnected()) {
            return;
        }
        try {
            LocationServices.GeofencingApi.removeGeofences(
                    mGoogleApiClient,
                    // This is the same pending intent that was used in registerGeofences
                    getGeofencePendingIntent()
            ).setResultCallback(this);
        } catch (SecurityException securityException) {
            // Catch exception generated if the app does not use ACCESS_FINE_LOCATION permission.
            Log.e(TAG, securityException.getMessage());
        }
    }


    /***
     * Updates the local ArrayList of Geofences using data from the passed in cursor
     * Uses the Place UID defined by the API as the Geofence object Id
     *
     * @param data the cursor result of the local database query
     */
    public void updateGeofencesList(Cursor data) {
        mGeofenceList = new ArrayList<Geofence>();
        if (data == null || data.getCount() == 0) return;
        while (data.moveToNext()) {
            // Read the place information from the DB cursor
            String placeUID = data.getString(data.getColumnIndex(PlaceContract.PlaceEntry.COLUMN_PLACE_UID));
            float placeLat = data.getFloat(data.getColumnIndex(PlaceContract.PlaceEntry.COLUMN_PLACE_LATITUDE));
            float placeLng = data.getFloat(data.getColumnIndex(PlaceContract.PlaceEntry.COLUMN_PLACE_LONGITUDE));
            // Build a Geofence object
            Geofence geofence = new Geofence.Builder()
                    .setRequestId(placeUID)
                    .setExpirationDuration(GEOFENCE_TIMEOUT)
                    .setCircularRegion(placeLat, placeLng, GEOFENCE_RADIUS)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                    .build();
            // Add it to the list
            mGeofenceList.add(geofence);
        }
    }

    // TODO: move this method somewhere else?

    /**
     * Calls the Geo Data API getPlaceById to sync any outdated information cached in the database
     *
     * @param data A Cursor of all the data currently in the database
     */
    public void syncPlacesData(Cursor data) {
        if (data == null || data.getCount() == 0) return;
        while (data.moveToNext()) {
            final long placeId = data.getLong(data.getColumnIndex(PlaceContract.PlaceEntry._ID));
            final String placeUID = data.getString(data.getColumnIndex(PlaceContract.PlaceEntry.COLUMN_PLACE_UID));
            final String placeName = data.getString(data.getColumnIndex(PlaceContract.PlaceEntry.COLUMN_PLACE_NAME));
            final String placeAddress = data.getString(data.getColumnIndex(PlaceContract.PlaceEntry.COLUMN_PLACE_ADDRESS));
            // TODO: call getPlaceById only once by passing in all the IDs and then compare with local data
            PendingResult<PlaceBuffer> placeResult = Places.GeoDataApi
                    .getPlaceById(mGoogleApiClient, placeUID);
            placeResult.setResultCallback(new ResultCallback<PlaceBuffer>() {
                @Override
                public void onResult(@NonNull PlaceBuffer places) {
                    // Check if the place was found successfully
                    if (!places.getStatus().isSuccess() || places.getCount() == 0) {
                        return;
                    }

                    // Get the Place object from the buffer.
                    final Place place = places.get(0);

                    // Check is there is any discrepancy between cached data and live data
                    if (!place.getName().toString().equals(placeName) ||
                            !place.getAddress().toString().equals(placeAddress)) {

                        // Extract the place information from the API
                        String newPlaceName = place.getName().toString();
                        String newPlaceAddress = place.getAddress().toString();
                        double newPlaceLat = place.getLatLng().latitude;
                        double newPlaceLng = place.getLatLng().longitude;

                        // Update the place with new data into DB
                        ContentValues contentValues = new ContentValues();
                        contentValues.put(PlaceContract.PlaceEntry.COLUMN_PLACE_NAME, newPlaceName);
                        contentValues.put(PlaceContract.PlaceEntry.COLUMN_PLACE_ADDRESS, newPlaceAddress);
                        contentValues.put(PlaceContract.PlaceEntry.COLUMN_PLACE_UID, placeUID);
                        contentValues.put(PlaceContract.PlaceEntry.COLUMN_PLACE_LATITUDE, (float) newPlaceLat);
                        contentValues.put(PlaceContract.PlaceEntry.COLUMN_PLACE_LONGITUDE, (float) newPlaceLng);
                        String stringId = Long.toString(placeId);
                        Uri uri = PlaceContract.PlaceEntry.CONTENT_URI;
                        uri = uri.buildUpon().appendPath(stringId).build();
                        mContext.getContentResolver().update(uri, contentValues, null, null);
                    }
                }
            });
        }

    }

    /***
     * Creates a GeofencingRequest object using the mGeofenceList ArrayList of Geofences
     * Used by {@code #registerGeofences}
     *
     * @return the GeofencingRequest object
     */
    private GeofencingRequest getGeofencingRequest() {
        GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);
        builder.addGeofences(mGeofenceList);
        return builder.build();
    }

    /***
     * Creates a PendingIntent object using the GeofenceTransitionsIntentService class
     * Used by {@code #registerGeofences}
     *
     * @return the PendingIntent object
     */
    private PendingIntent getGeofencePendingIntent() {
        // Reuse the PendingIntent if we already have it.
        if (mGeofencePendingIntent != null) {
            return mGeofencePendingIntent;
        }
        Intent intent = new Intent(mContext, GeofenceBroadcastReceiver.class);
        mGeofencePendingIntent = PendingIntent.getBroadcast(mContext, 0, intent, PendingIntent.
                FLAG_UPDATE_CURRENT);
        return mGeofencePendingIntent;
    }

    @Override
    public void onResult(@NonNull Result result) {
        if (!result.getStatus().isSuccess()) {
            Log.e(TAG, String.format("Error adding/removing geofence : %s",
                    result.getStatus().toString()));
        }
    }

}
