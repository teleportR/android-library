/**
 * Fahrgemeinschaft / Ridesharing App
 * Copyright (c) 2013 by it's authors.
 * Some rights reserved. See LICENSE..
 *
 */

package org.teleportr;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import ch.hsr.geohash.GeoHash;

public class Place {

    private static final String COLON = ":";
    private static final String UPDATE_PLACE = "update place ";
    public static final String GEOHASH = "geohash";
    public static final String ADDRESS = "address";
    public static final String NAME = "name";
    public static final String TAG = "Place";
    public static final String KEY = "key";
    private static final String EMPTY = "";
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
                RidesProvider.getPlaceUri(ctx, id), null, null, null, null);
        cursor.moveToFirst();
        geohash(cursor.getString(1));
        name(cursor.getString(2));
        address(cursor.getString(3));
        cursor.close();
    }

    public String getName() {
        return cv.getAsString(NAME);
    }

    public String getAddress() {
        return cv.getAsString(ADDRESS);
    }

    public double getLat() {
        return GeoHash.fromGeohashString(cv.getAsString(GEOHASH))
                .getPoint().getLatitude();
    }

    public double getLng() {
        return GeoHash.fromGeohashString(cv.getAsString(GEOHASH))
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
        if (geohash != null) {
            cv.put(GEOHASH, geohash);
        }
        return this;
    }

    public Place latlon(int lat, int lng) {
        cv.put(GEOHASH,
                GeoHash.withBitPrecision(((double) lat) / 1E6,
                        ((double) lng) / 1E6, 55).toBase32());
        return this;
    }

    @Override
    public String toString() {
        return cv.getAsString(GEOHASH);
    }

    public Place name(String name) {
        cv.put(NAME, name);
        return this;
    }

    public Place address(String address) {
        cv.put(ADDRESS, address);
        return this;
    }

    public Place set(String key, String value) {
        cv.put(key, value);
        return this;
    }

    public String getId() {
        if (id != 0) return String.valueOf(id);
        if (cv.containsKey(GEOHASH))
            return GEOHASH + COLON + cv.getAsString(GEOHASH);
        if (cv.containsKey(NAME))
            return NAME + COLON + cv.getAsString(NAME);
        if (cv.containsKey(ADDRESS))
            return ADDRESS + COLON + cv.getAsString(ADDRESS);
        Log.d(TAG, "place not identifyable! " + this);
        return null;
    }

    public Uri store(Context ctx) {
        if (id == 0) {
            Uri uri = ctx.getContentResolver().insert(
                    RidesProvider.getPlacesUri(ctx), cv);
            id = Integer.parseInt(uri.getLastPathSegment());
            return uri;
        } else {
            Log.d(TAG, UPDATE_PLACE + id);
            ctx.getContentResolver().update(
                    RidesProvider.getPlaceUri(ctx, id), cv, null, null);
            return null;
        }
    }

    protected ContentValues toContentValues() {
        if (!cv.containsKey(NAME))
            cv.put(NAME, EMPTY);
        if (!cv.containsKey(ADDRESS))
            cv.put(ADDRESS, EMPTY);
        if (!cv.containsKey(GEOHASH))
            cv.put(GEOHASH, EMPTY);
        return cv;
    }

    public String get(String key) {
        Cursor values = ctx.getContentResolver().query(
                RidesProvider.getPlaceUri(ctx, id).buildUpon()
                .appendQueryParameter(KEY, key).build(),
                null, null, null, null);
        String value = null;
        if (values.getCount() != 0) {
            values.moveToFirst();
            value = values.getString(3);
        }
        values.close();
        return value;
    }
}
