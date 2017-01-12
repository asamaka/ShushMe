package com.example.android.shushme.provider;

import android.net.Uri;
import android.provider.BaseColumns;

public class PlaceContract {

    // The authority, which is how your code knows which Content Provider to access
    public static final String AUTHORITY = "com.example.android.shushme";

    // The base content URI = "content://" + <authority>
    public static final Uri BASE_CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    // Define the possible paths for accessing data in this contract
    // This is the path for the "places" directory
    public static final String PATH_PLACES = "places";

    public static final class PlaceEntry implements BaseColumns {

        // TaskEntry content URI = base content URI + path
        public static final Uri CONTENT_URI =
                BASE_CONTENT_URI.buildUpon().appendPath(PATH_PLACES).build();

        public static final String TABLE_NAME = "places";
        public static final String COLUMN_PLACE_UID = "placeUID";
        public static final String COLUMN_PLACE_NAME = "placeName";
        public static final String COLUMN_PLACE_ADDRESS = "placeAddress";
        public static final String COLUMN_PLACE_LATITUDE = "placeLatitude";
        public static final String COLUMN_PLACE_LONGITUDE = "placeLongitude";
    }
}
