package org.teleportr;

import java.io.BufferedInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

import org.json.JSONObject;
import org.teleportr.Ride.Mode;

import android.content.ContentValues;
import android.content.Context;
import android.preference.PreferenceManager;

public abstract class Connector {

    public abstract long search(Place from, Place to, Date dep, Date arr) throws Exception;

    public abstract String publish(Ride offer) throws Exception;

    public abstract String delete(Ride offer) throws Exception;

    public void resolvePlace(Place place, Context ctx) throws Exception {}

    public String authenticate(String credential) throws Exception {
        return null;
    }

    private HashMap<String, Integer> placeIdx;
    private ArrayList<ContentValues> placesBatch;
    private ArrayList<ContentValues> ridesBatch;
    Context ctx;

    public Connector() {
        placeIdx = new HashMap<String, Integer>();
        placesBatch = new ArrayList<ContentValues>();
        ridesBatch = new ArrayList<ContentValues>();
    }

    public Connector setContext(Context ctx) {
        this.ctx = ctx;
        return this;
    }

    protected String getAuth() throws Exception {
        return PreferenceManager
                .getDefaultSharedPreferences(ctx)
                .getString("auth", null);
    }

    public Place store(Place place) {
        if (place.id != 0) return place;
        String id = place.getId();
        if (placeIdx.containsKey(id)) {
            place.id = placeIdx.get(place.getId());
        } else {
            place.id = placesBatch.size();
            placeIdx.put(id, place.id);
            placesBatch.add(place.cv);
        }
        return place;
    }

    public void store(Ride ride) {
        if (!ride.cv.containsKey("ref"))
            ride.ref(UUID.randomUUID().toString());
        if (!ride.cv.containsKey("mode"))
            ride.mode(Mode.CAR);
        if (!ride.cv.containsKey("active"))
            ride.activate();
        if (!ride.cv.containsKey("price"))
            ride.price(-1);
        if (ride.details != null)
            ride.cv.put("details", ride.details.toString());
        ridesBatch.add(ride.cv);
        if (ride.subrides != null)
            ridesBatch.addAll(ride.subrides);
    }

    public int getNumberOfRidesFound() {
        return ridesBatch.size();
    }

    public void flush(int from, int to, long dep, long arr) {
        placesBatch.addAll(ridesBatch);
        ctx.getContentResolver().bulkInsert(
                RidesProvider.getRidesUri(ctx).buildUpon()
                .appendQueryParameter(Ride.FROM_ID, String.valueOf(from))
                .appendQueryParameter(Ride.TO_ID, String.valueOf(to))
                .appendQueryParameter(Ride.DEP, String.valueOf(dep))
                .appendQueryParameter(Ride.ARR, String.valueOf(arr)).build(),
                placesBatch.toArray(new ContentValues[placesBatch.size()]));
        placesBatch.clear();
        ridesBatch.clear();
        placeIdx.clear();
    }

    public void set(String key, String value) {
        PreferenceManager.getDefaultSharedPreferences(ctx).edit()
                .putString(key, value).commit();
    }

    public String get(String key) {
        return String.valueOf(PreferenceManager
                .getDefaultSharedPreferences(ctx).getAll().get(key));
    }

    public static JSONObject loadJson(HttpURLConnection conn) throws Exception {
        return new JSONObject(loadString(conn));
    }

    public static String loadString(HttpURLConnection conn) throws Exception {
        StringBuilder result = new StringBuilder();
        InputStreamReader in = new InputStreamReader(
                new BufferedInputStream(conn.getInputStream()));
        int read;
        char[] buff = new char[1024];
        while ((read = in.read(buff)) != -1) {
            result.append(buff, 0, read);
        }
        conn.disconnect();
        return result.toString();
    }
}
