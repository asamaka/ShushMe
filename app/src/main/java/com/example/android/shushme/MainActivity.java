package com.example.android.shushme;

import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.example.android.shushme.provider.PlaceContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Result;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlacePicker;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity
        implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LoaderManager.LoaderCallbacks<Cursor>,
        ResultCallback{

    // Constants
    public static final String TAG = MainActivity.class.getSimpleName();
    private static final float GEOFENCE_RADIUS = 50; // 100 meters
    private static final long GEOFENCE_TIMEOUT = 24 * 60 * 60 * 1000; // 24 hours
    private static final int PERMISSIONS_REQUEST_FINE_LOCATION = 111;
    private static final int PLACE_PICKER_REQUEST = 1;
    private static final int PLACE_LOADER_ID = 0;

    // Member variables
    private GoogleApiClient mGoogleApiClient;
    private List<Geofence> mGeofenceList;
    private PlaceListAdapter mAdapter;
    private RecyclerView mRecyclerView;
    private PendingIntent mGeofencePendingIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mRecyclerView = (RecyclerView) findViewById(R.id.places_list_recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new PlaceListAdapter(this);
        mRecyclerView.setAdapter(mAdapter);

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }
            // Called when a user swipes left or right on a ViewHolder
            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int swipeDir) {
                long id = (long) viewHolder.itemView.getTag();
                // Build appropriate uri with String row id appended
                String stringId = Long.toString(id);
                Uri uri = PlaceContract.PlaceEntry.CONTENT_URI;
                uri = uri.buildUpon().appendPath(stringId).build();
                getContentResolver().delete(uri, null, null);
                getSupportLoaderManager().restartLoader(PLACE_LOADER_ID, null, MainActivity.this);
            }
        }).attachToRecyclerView(mRecyclerView);

        //TODO: check the enable switch and listen to any change of state in it

        // Initialize the pending intent to null
        mGeofencePendingIntent = null;

        // Initialize the Geofences list
        mGeofenceList = new ArrayList<>();

        // Initialize the loader to load the list from the database
        getSupportLoaderManager().initLoader(PLACE_LOADER_ID, null, this);

        // Build up the LocationServices API client
        buildGoogleApiClient();

        // Check for required permissions and request them for Android 6.0+
        requestPermission();

    }

    /**
     * Builds a GoogleApiClient. Uses the {@code #addApi} method to request the LocationServices API.
     * We are not using the {@code #enableAutoManage} because we want to control when to connect the client
     * namely after the list of geofences has been loaded and ready for registering
     */
    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .enableAutoManage(this,this)
                .build();
    }

    private void requestPermission(){
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_FINE_LOCATION);
            return;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        getSupportLoaderManager().restartLoader(PLACE_LOADER_ID, null, this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay!
                    Log.i(TAG, "FINE_LOCATION permission granted!");
                    if(mGoogleApiClient.isConnected()) mGoogleApiClient.disconnect();
                    mGoogleApiClient.connect();
                } else {
                    Log.i(TAG, "FINE_LOCATION permission denied by user!");
                }
                return;
            }
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.i(TAG, "API Client Connected!");
        // Refresh the loader to force registering the geofences
        getSupportLoaderManager().restartLoader(PLACE_LOADER_ID, null, MainActivity.this);
    }

    private void registerGeofences(){
        if (mGeofenceList == null || mGeofenceList.size()==0 || !mGoogleApiClient.isConnected()){
            return;
        }
        try {
            LocationServices.GeofencingApi.addGeofences(
                    mGoogleApiClient,
                    getGeofencingRequest(),
                    getGeofencePendingIntent()
            ).setResultCallback(this);
            Toast.makeText(this, "Geofences Registered!", Toast.LENGTH_SHORT).show();
        }catch (SecurityException securityException) {
            // Catch exception generated if the app does not use ACCESS_FINE_LOCATION permission.
            Log.e(TAG, securityException.getMessage());
        }
    }

    private void unRegisterGeofences(){
        if (!mGoogleApiClient.isConnected()) {
            return;
        }
        try {
            // Remove geofences.
            LocationServices.GeofencingApi.removeGeofences(
                    mGoogleApiClient,
                    // This is the same pending intent that was used in addGeofences().
                    getGeofencePendingIntent()
            ).setResultCallback(this); // Result processed in onResult().
            Toast.makeText(this, "Geofences Unregistered!", Toast.LENGTH_SHORT).show();
        } catch (SecurityException securityException) {
            // Catch exception generated if the app does not use ACCESS_FINE_LOCATION permission.
            Log.e(TAG, securityException.getMessage());
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "API Client Connection Suspended!");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.i(TAG, "API Client Connection Failed!");
    }

    public void onAddPlaceButtonClicked(View view) {
        /**
         * This if condition is not required for the place picker to work, however we need to make sure
         * the user knows the app (geofences) will not work properly without that permission
         */
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED){
            Toast.makeText(this, getString(R.string.need_location_permission_message), Toast.LENGTH_LONG).show();
            return;
        }
        try {
            PlacePicker.IntentBuilder builder = new PlacePicker.IntentBuilder();
            Intent i = builder.build(this);
            startActivityForResult(i, PLACE_PICKER_REQUEST);
        } catch (GooglePlayServicesRepairableException e) {
            Log.e(TAG, String.format("GooglePlayServices Not Available [%s]", e.getMessage()));
        } catch (GooglePlayServicesNotAvailableException e) {
            Log.e(TAG, String.format("GooglePlayServices Not Available [%s]", e.getMessage()));
        } catch (Exception e){
            Log.e(TAG, String.format("PlacePicker Exception: %s", e.getMessage()));
        }
    }


    private void createGeofences(Cursor data){
        mGeofenceList = new ArrayList<Geofence>();
        if(data == null || data.getCount()==0) return;
        while (data.moveToNext()) {
            // Read the place infromation from the DB cursor
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

    private GeofencingRequest getGeofencingRequest() {
        GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);
        builder.addGeofences(mGeofenceList);
        return builder.build();
    }

    private PendingIntent getGeofencePendingIntent() {
        // Reuse the PendingIntent if we already have it.
        if (mGeofencePendingIntent != null) {
            return mGeofencePendingIntent;
        }
        Intent intent = new Intent(this, GeofenceTransitionsIntentService.class);
        mGeofencePendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.
                FLAG_UPDATE_CURRENT);
        return mGeofencePendingIntent;
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PLACE_PICKER_REQUEST && resultCode == RESULT_OK) {
            Place place = PlacePicker.getPlace(this,data);
            if (place == null) {
                Log.i(TAG,"No place selected");
                return;
            }

            // Extract the place information from the API
            String placeName =  place.getName().toString();
            String placeAddress =  place.getAddress().toString();
            String placeUID =  place.getId();
            double placeLat =  place.getLatLng().latitude;
            double placeLng =  place.getLatLng().longitude;

            // Insert a new place into DB
            ContentValues contentValues = new ContentValues();
            contentValues.put(PlaceContract.PlaceEntry.COLUMN_PLACE_NAME,  placeName);
            contentValues.put(PlaceContract.PlaceEntry.COLUMN_PLACE_ADDRESS,  placeAddress);
            contentValues.put(PlaceContract.PlaceEntry.COLUMN_PLACE_UID,  placeUID);
            contentValues.put(PlaceContract.PlaceEntry.COLUMN_PLACE_LATITUDE,  (float) placeLat);
            contentValues.put(PlaceContract.PlaceEntry.COLUMN_PLACE_LONGITUDE,  (float) placeLng);
            Uri uri = getContentResolver().insert(PlaceContract.PlaceEntry.CONTENT_URI, contentValues);
            if(uri != null) {
                Log.i(TAG,"New place added to DB");
                getSupportLoaderManager().restartLoader(PLACE_LOADER_ID, null, MainActivity.this);
            }

        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new AsyncTaskLoader<Cursor>(this) {
            Cursor mPlaceData = null;
            @Override
            protected void onStartLoading() {
                // QUESTION: don't I also need to check for takeContentChanged? (Sunshine and ANDFUN1 don't!)
                if (mPlaceData != null) {
                    deliverResult(mPlaceData);
                } else {
                    forceLoad();
                }
            }
            @Override
            public Cursor loadInBackground() {
                // Query and load all data in the background
                try {
                    return getContentResolver().query(PlaceContract.PlaceEntry.CONTENT_URI,
                            null,
                            null,
                            null,
                            PlaceContract.PlaceEntry.COLUMN_PLACE_NAME);

                } catch (Exception e) {
                    Log.e(TAG, "Failed to load data!");
                    e.printStackTrace();
                    return null;
                }
            }
            public void deliverResult(Cursor data) {
                mPlaceData = data;
                super.deliverResult(data);
            }
        };
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        unRegisterGeofences();
        mAdapter.swapCursor(data);
        createGeofences(data);
        registerGeofences();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        unRegisterGeofences();
        mAdapter.swapCursor(null);
    }

    @Override
    public void onResult(@NonNull Result result) {
        Log.i(TAG,result.toString());
    }

}
