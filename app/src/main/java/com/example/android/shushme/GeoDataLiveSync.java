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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.util.Log;

import com.example.android.shushme.provider.PlaceContract;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.places.GeoDataApi;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.PlaceBuffer;
import com.google.android.gms.location.places.Places;

import java.util.ArrayList;
import java.util.List;

public class GeoDataLiveSync {

    private static final String TAG = GeoDataApi.class.getSimpleName();
    private final Context mContext;
    private final GoogleApiClient mClient;
    private boolean mNeverSynced;

    public GeoDataLiveSync(Context context, GoogleApiClient client) {
        mContext = context;
        mClient = client;
        mNeverSynced = true;
    }

    /**
     * Calls the API's getPlaceById to request live data for all Places stored locally
     * Triggers onResult when complete
     */
    public void syncWithLivePlaces() {
        if (!mNeverSynced) return;
        mNeverSynced = false;
        Uri uri = PlaceContract.PlaceEntry.CONTENT_URI;
        Cursor data = mContext.getContentResolver().query(
                uri,
                null,
                null,
                null,
                null);
        if (data == null || data.getCount() == 0) return;
        List<String> guids = new ArrayList<String>();
        while (data.moveToNext()) {
            guids.add(data.getString(data.getColumnIndex(PlaceContract.PlaceEntry.COLUMN_PLACE_UID)));
        }
        PendingResult<PlaceBuffer> placeResult = Places.GeoDataApi.getPlaceById(mClient,
                guids.toArray(new String[guids.size()]));
        placeResult.setResultCallback(new ResultCallback<PlaceBuffer>() {
            @Override
            public void onResult(@NonNull PlaceBuffer places) {
                new AsyncTask() {
                    @Override
                    protected Object doInBackground(Object[] objects) {
                        checkAndUpdatePlaces((PlaceBuffer) objects[0]);
                        return null;
                    }
                }.execute(places);
            }
        });
    }

    /**
     * Goes through the places buffer and compares each place with the local cacced information
     * If anything is out of date, it gets updated in the local database
     * @param places The up-to-date Places Buffer
     */
    private void checkAndUpdatePlaces(PlaceBuffer places) {
        for (Place place : places) {
            try {
                Uri uri = PlaceContract.PlaceEntry.CONTENT_URI;
                Cursor res = mContext.getContentResolver().query(
                        uri,
                        null,
                        PlaceContract.PlaceEntry.COLUMN_PLACE_UID + " = ?",
                        new String[]{place.getId()},
                        null);
                if (res.getCount() > 0) {
                    res.moveToFirst();
                    // Check if there is any discrepancy between cached data and live data
                    if (!place.getName().toString().equals(
                            res.getString(res.getColumnIndex(PlaceContract.PlaceEntry.COLUMN_PLACE_NAME))) ||
                            !place.getAddress().toString().equals(
                                    res.getString(res.getColumnIndex(PlaceContract.PlaceEntry.COLUMN_PLACE_ADDRESS)))) {
                        updatePlace(res.getLong(res.getColumnIndex(PlaceContract.PlaceEntry._ID)), place);
                    }
                }
            } catch (Exception ex) {
                Log.e(TAG, "onResult :" + ex.getMessage());
            }
        }
    }

    /**
     * Updates the name and address of the cachedPlace with the up-to-date information in livePlace
     *
     * @param dbId      The locally cached place database ID
     * @param newPlace The live up-to-date Place information
     * @return number of rows updated in the database
     */
    private int updatePlace(long dbId, Place newPlace) {
        // Extract the place information from the API
        String newPlaceName = newPlace.getName().toString();
        String newPlaceAddress = newPlace.getAddress().toString();

        // Update the place with new data into DB
        ContentValues contentValues = new ContentValues();
        contentValues.put(PlaceContract.PlaceEntry.COLUMN_PLACE_NAME, newPlaceName);
        contentValues.put(PlaceContract.PlaceEntry.COLUMN_PLACE_ADDRESS, newPlaceAddress);
        String stringId = Long.toString(dbId);
        Uri uri = PlaceContract.PlaceEntry.CONTENT_URI;
        uri = uri.buildUpon().appendPath(stringId).build();
        return mContext.getContentResolver().update(uri, contentValues, null, null);
    }

}
