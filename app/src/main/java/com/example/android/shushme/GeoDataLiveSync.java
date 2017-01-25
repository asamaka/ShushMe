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

import java.util.HashMap;

public class GeoDataLiveSync implements ResultCallback<PlaceBuffer> {

    private static final String TAG = GeoDataApi.class.getSimpleName();
    private HashMap<String, CachedPlace> mCachedPlaces;
    private Context mContext;
    private GoogleApiClient mGoogleApiClient;

    /**
     * Constructs a GeoDataLiveSync object that will allow local cached data to be synced with live data
     *
     * @param context The context for data provider access to the locally cached places
     * @param client  A GoogleApiClient with GEO_DATA_API enabled
     */
    public GeoDataLiveSync(Context context, GoogleApiClient client) {
        mCachedPlaces = new HashMap<>();
        mContext = context;
        mGoogleApiClient = client;
    }

    /**
     * Synchronizes the locally cached places data by calling getPlaceById and updating any out of
     * date information
     *
     * @param data A Cursor with the locally cached data
     */
    public void syncData(Cursor data) {
        buildDictionary(data);
        getLivePlaces();
    }

    /**
     * Build a HashMap to speed up the lookup process when comparing live data with local data
     *
     * @param data A Cursor with the locally cached data
     */
    private void buildDictionary(Cursor data) {
        if (data == null || data.getCount() == 0) return;
        while (data.moveToNext()) {
            long placeId = data.getLong(data.getColumnIndex(PlaceContract.PlaceEntry._ID));
            String placeUID = data.getString(data.getColumnIndex(PlaceContract.PlaceEntry.COLUMN_PLACE_UID));
            String placeName = data.getString(data.getColumnIndex(PlaceContract.PlaceEntry.COLUMN_PLACE_NAME));
            String placeAddress = data.getString(data.getColumnIndex(PlaceContract.PlaceEntry.COLUMN_PLACE_ADDRESS));

            CachedPlace place = new CachedPlace();
            place.cachedId = placeId;
            place.apiId = placeUID;
            place.name = placeName;
            place.address = placeAddress;

            mCachedPlaces.put(placeUID, place);
        }
    }

    /**
     * Calls the API's getPlaceById to request live data for all Places stored locally
     * Triggers onResult when complete
     */
    private void getLivePlaces() {
        if(mCachedPlaces.size()>0) {
            PendingResult<PlaceBuffer> placeResult = Places.GeoDataApi.getPlaceById(mGoogleApiClient,
                    mCachedPlaces.keySet().toArray(new String[mCachedPlaces.size()]));
            placeResult.setResultCallback(this);
        }
    }

    /**
     * Called when getPlaceById API request has returned with a buffer of places
     * @param places The resulted list of live Places returned from the API request
     */
    @Override
    public void onResult(@NonNull PlaceBuffer places) {
        for (Place place : places) {
            try {
                CachedPlace cachedPlace = mCachedPlaces.get(place.getId());
                // Check if there is any discrepancy between cached data and live data
                if (!place.getName().toString().equals(cachedPlace.name) ||
                        !place.getAddress().toString().equals(cachedPlace.address)) {
                    updateCachedPlace(cachedPlace, place);
                }
            } catch (Exception ex) {
                Log.e(TAG, ex.getMessage());
            }
        }
    }

    /**
     * Updates the name and address of the cachedPlace with the up-to-date information in livePlace
     * @param cachedPlace The locally cached place information
     * @param livePlace The live up-to-date Place information
     * @return number of rows updated in the database
     */
    private int updateCachedPlace(CachedPlace cachedPlace, Place livePlace) {
        // Extract the place information from the API
        String newPlaceName = livePlace.getName().toString();
        String newPlaceAddress = livePlace.getAddress().toString();

        // Update the place with new data into DB
        ContentValues contentValues = new ContentValues();
        contentValues.put(PlaceContract.PlaceEntry.COLUMN_PLACE_NAME, newPlaceName);
        contentValues.put(PlaceContract.PlaceEntry.COLUMN_PLACE_ADDRESS, newPlaceAddress);
        String stringId = Long.toString(cachedPlace.cachedId);
        Uri uri = PlaceContract.PlaceEntry.CONTENT_URI;
        uri = uri.buildUpon().appendPath(stringId).build();
        return mContext.getContentResolver().update(uri, contentValues, null, null);
    }

    class CachedPlace {
        public long cachedId;
        public String apiId;
        public String name;
        public String address;
    }
}
