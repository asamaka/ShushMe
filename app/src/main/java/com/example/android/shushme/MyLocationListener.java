package com.example.android.shushme;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MyLocationListener implements LocationListener {

    public static final String TAG = MainActivity.class.getSimpleName();
    private Context mContext;

    public MyLocationListener(Context context){
        mContext = context;
    }

    @Override
    public void onLocationChanged(Location loc) {

        String message =    "Location changed: Latitude: " + loc.getLatitude() +
                            " Longitude: " + loc.getLongitude();
        Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
        Log.i(TAG,message);

        /*------- To get city name from coordinates -------- */
        String cityName = null;
        Geocoder gcd = new Geocoder(mContext, Locale.getDefault());
        List<Address> addresses;
        try {
            addresses = gcd.getFromLocation(loc.getLatitude(),
                    loc.getLongitude(), 1);
            if (addresses.size() > 0) {
                System.out.println(addresses.get(0).getLocality());
                cityName = addresses.get(0).getLocality();
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        String s = loc.getLongitude() + "\n" + loc.getLatitude() + "\n\nMy Current City is: "
                + cityName;
        Log.i(TAG,s);
        ((MainActivity)mContext).getCurrentPlaceList();

    }

    @Override
    public void onProviderDisabled(String provider) {}

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}
}