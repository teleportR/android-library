package org.teleportr;


import java.util.ArrayList;
import java.util.HashMap;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import ch.hsr.geohash.GeoHash;


public class Place {
	
	public static ArrayList<ContentValues> batch = new ArrayList<ContentValues>();
	public static Context context;

	public Place set(String key, String value) {
        ContentValues values = new ContentValues();
        values.put("geohash", geohash);
        values.put("lat", lat);
        values.put("lng", lng);
        values.put("value", value);
        values.put("key", key);
        batch.add(values);
        return this;
	}

	public String get(String key) {
		Cursor values = context.getContentResolver().query(Uri.parse(
				"content://" + context.getPackageName() +
				"/places/"+geohash+"?key="+key),
                null, null, null, null);
        String value = null;
        if (values.getCount() != 0) {
            values.moveToFirst();
            value = values.getString(2);
        }
        values.close();
        return value;
	}

    public int lat;
    public int lng;
    public String geohash;
    
    public Place(String geohash) {
        this.geohash = geohash;
        try {
            GeoHash gh = GeoHash.fromGeohashString(geohash);
            this.lat = (int) (Math.round(gh.getPoint().getLatitude() * 1E6));
            this.lng = (int) (Math.round(gh.getPoint().getLongitude() * 1E6));
        } catch (NullPointerException e) {
        }
    }

    public Place(int lat, int lng) {
        geohash = GeoHash.withBitPrecision(
                ((double) lat) / 1E6, ((double) lng) / 1E6, 55).toBase32();
        this.lat = lat;
        this.lng = lng;
    }
    
    public Place(double lat, double lng) {
        this(GeoHash.withBitPrecision(lat, lng, 55).toBase32());
    }

    
    @Override
    public String toString() {
        return geohash;
    }

	public Place name(String name) {
		set("name", name);
		return this;
	}


	public Place address(String address) {
		set("address", address);
		return this;
	}

}
