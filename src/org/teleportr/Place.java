package org.teleportr;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import ch.hsr.geohash.GeoHash;

public class Place {

    private static final String TAG = "Place";
    protected ContentValues cv;
    private Context ctx;
    public int id;

    public Place(int id) {
        this();
        this.id = id;
    }

    public Place(int id, Context ctx) {
        this(id);
        this.ctx = ctx;
        Cursor cursor = ctx.getContentResolver().query(
                Uri.parse("content://" + ctx.getPackageName()
                        + "/places/"+id), null, null, null, null);
        cursor.moveToFirst();
        geohash(cursor.getString(1));
        name(cursor.getString(2));
        address(cursor.getString(3));
        cursor.close();
    }

    public String getName() {
        return cv.getAsString("name");
    }

    public String getAddress() {
        return cv.getAsString("address");
    }

    public double getLat() {
        return GeoHash.fromGeohashString(cv.getAsString("geohash"))
                .getPoint().getLatitude();
    }

    public double getLng() {
        return GeoHash.fromGeohashString(cv.getAsString("geohash"))
                .getPoint().getLongitude();
    }

    public Place() {
        cv = new ContentValues();
    }

    public Place(String geohash) {
        this();
        geohash(geohash);
    }

    public Place(int lat, int lng) {
        this();
        latlon(lat, lng);
    }

    public Place(double lat, double lng) {
        this();
        latlon(lat, lng);
    }

    public Place latlon(double lat, double lng) {
        return geohash(GeoHash.withBitPrecision(lat, lng, 55).toBase32());
    }

    public Place geohash(String geohash) {
        cv.put("geohash", geohash);
        try {
            GeoHash gh = GeoHash.fromGeohashString(geohash);
//            cv.put("lat", (int) (Math.round(gh.getPoint().getLatitude() * 1E6)));
//            cv.put("lng",(int) (Math.round(gh.getPoint().getLongitude() * 1E6)));
        } catch (NullPointerException e) {
            System.out.println("not a geohash: " + geohash);
        }
        return this;
    }

    public Place latlon(int lat, int lng) {
        cv.put("geohash",
                GeoHash.withBitPrecision(((double) lat) / 1E6,
                        ((double) lng) / 1E6, 55).toBase32());
//        cv.put("lat", lat);
//        cv.put("lng", lng);
        return this;
    }

    @Override
    public String toString() {
        return cv.getAsString("geohash");
    }

    public Place name(String name) {
        cv.put("name", name);
        return this;
    }

    public Place address(String address) {
        cv.put("address", address);
        return this;
    }

    public Place set(String key, String value) {
        cv.put(key, value);
        return this;
    }

    public String getId() {
        if (id != 0) return String.valueOf(id);
        if (cv.containsKey("geohash"))
            return "geohash:" + cv.getAsString("geohash");
        if (cv.containsKey("name"))
            return "name:" + cv.getAsString("name");
        if (cv.containsKey("address"))
            return "address:" + cv.getAsString("address");
        Log.d(TAG, "place not identifyable! " + this);
        return null;
    }

    public Uri store(Context ctx) {
        if (id == 0) {
//            toContentValues();
            Uri uri = ctx.getContentResolver().insert(Uri.parse(
                    "content://" + ctx.getPackageName() + "/places"), cv);
            id = Integer.parseInt(uri.getLastPathSegment());
            return uri;
        } else {
            Log.d("Places", "update place " + id + " : " + cv);
            ctx.getContentResolver().update(
                    Uri.parse("content://"+ctx.getPackageName()+"/places/"+id),
                    cv, null, null);
            return null;
        }
    }

    protected ContentValues toContentValues() {
        if (!cv.containsKey("name"))
            cv.put("name", "");
        if (!cv.containsKey("address"))
            cv.put("address", "");
        if (!cv.containsKey("geohash"))
            cv.put("geohash", "");
        if (!cv.containsKey("lat"))
            cv.put("lat", 0);
        if (!cv.containsKey("lng"))
            cv.put("lng", 0);
        return cv;
    }

    public String get(String key) {
        Cursor values = ctx.getContentResolver().query(
                Uri.parse("content://" + ctx.getPackageName() + "/places/" + id
                        + "?key=" + key), null, null, null, null);
        String value = null;
        if (values.getCount() != 0) {
            values.moveToFirst();
            value = values.getString(3);
        }
        values.close();
        return value;
    }
}
