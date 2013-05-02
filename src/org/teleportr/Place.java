package org.teleportr;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import ch.hsr.geohash.GeoHash;

public class Place {

    public long id;
    protected ContentValues cv;
    private Cursor cursor;

    public Place(long id) {
        this();
        this.id = id;
    }

    public Place(long id, Context ctx) {
        this(id);
        cursor = ctx.getContentResolver().query(
                Uri.parse("content://" + ctx.getPackageName() + "/places/"+id),
                        null, null, null, null);
        cursor.moveToFirst();
    }

    public String getName() {
        return cursor.getString(2);
    }

    public double getLat() {
        return ((double) cursor.getLong(4)) / 1E6;
    }

    public double getLng() {
        return ((double) cursor.getLong(5)) / 1E6;
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
            cv.put("lat", (int) (Math.round(gh.getPoint().getLatitude() * 1E6)));
            cv.put("lng",
                    (int) (Math.round(gh.getPoint().getLongitude() * 1E6)));
        } catch (NullPointerException e) {
            System.out.println("not a geohash: " + geohash);
        }
        return this;
    }

    public Place latlon(int lat, int lng) {
        cv.put("geohash",
                GeoHash.withBitPrecision(((double) lat) / 1E6,
                        ((double) lng) / 1E6, 55).toBase32());
        cv.put("lat", lat);
        cv.put("lng", lng);
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

    public Uri store(Context ctx) {
        if (id == 0) {
            toContentValues();
            Uri uri = ctx.getContentResolver().insert(Uri.parse(
                    "content://" + ctx.getPackageName() + "/places"), cv);
            id = Long.parseLong(uri.getLastPathSegment());
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

    public String get(String key, Context ctx) {
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
