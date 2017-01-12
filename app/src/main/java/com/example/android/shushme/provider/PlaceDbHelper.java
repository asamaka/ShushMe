package com.example.android.shushme.provider;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.android.shushme.provider.PlaceContract.PlaceEntry;

public class PlaceDbHelper extends SQLiteOpenHelper {

    // The database name
    private static final String DATABASE_NAME = "shushme.db";

    // If you change the database schema, you must increment the database version
    private static final int DATABASE_VERSION = 1;

    // Constructor
    public PlaceDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {

        // Create a table to hold the places data
        // TODO: set the UID as unique and update/keep old when duplicates found
        final String SQL_CREATE_PLACES_TABLE = "CREATE TABLE " + PlaceEntry.TABLE_NAME + " (" +
                PlaceEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                PlaceEntry.COLUMN_PLACE_UID + " TEXT NOT NULL, " +
                PlaceEntry.COLUMN_PLACE_NAME + " TEXT NOT NULL, " +
                PlaceEntry.COLUMN_PLACE_ADDRESS + " TEXT NOT NULL, " +
                PlaceEntry.COLUMN_PLACE_LATITUDE + " FLOAT NOT NULL, " +
                PlaceEntry.COLUMN_PLACE_LONGITUDE + " FLOAT NOT NULL " +
                "); ";

        sqLiteDatabase.execSQL(SQL_CREATE_PLACES_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        // For now simply drop the table and create a new one.
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + PlaceEntry.TABLE_NAME);
        onCreate(sqLiteDatabase);
    }
}
