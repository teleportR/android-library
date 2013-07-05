package org.teleportr;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;
import org.teleportr.Ride.Mode;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

public abstract class Connector {

    private static final String TAG = "Connector";

    public abstract long search(Place from, Place to, Date dep, Date arr);

    public int publish(Ride offer) throws Exception { return 0; }

    public void resolvePlace(Place place, Context ctx) {}

    public String authenticate() throws Exception { return null; }

    private HashMap<String, Integer> placeIdx;
    private ArrayList<ContentValues> placesBatch;
    private ArrayList<ContentValues> ridesBatch;
    private Uri search_jobs_uri;
    Context ctx;

    public Connector() {
        placeIdx = new HashMap<String, Integer>();
        placesBatch = new ArrayList<ContentValues>();
        ridesBatch = new ArrayList<ContentValues>();
    }

    public Connector setContext(Context ctx) {
        String uri = "content://" + ctx.getPackageName();
        search_jobs_uri = Uri.parse(uri + "/jobs/search");
        this.ctx = ctx;
        return this;
    }

    protected String getAuth() {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(ctx);
        String auth = prefs.getString("auth", null);
        if (auth == null) {
            try {
                auth = authenticate();
                prefs.edit().putString("auth", auth);
                return auth;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return "";
    }

    public Place store(Place place) {
        if (place.id != 0) return place;
        String id = place.getId();
        if (placeIdx.containsKey(id)) {
            place.id = placeIdx.get(place.getId());
            Log.d(TAG, "  place " + id + " - already");
        } else {
            place.id = placesBatch.size();
            placeIdx.put(id, place.id);
            placesBatch.add(place.cv);
            Log.d(TAG, "  place " + id + " - added");
        }
        return place;
    }

    public void store(Ride ride) {
        if (!ride.cv.containsKey("ref"))
            ride.ref(UUID.randomUUID().toString());
        if (!ride.cv.containsKey("mode")) {
            ride.mode(Mode.CAR);
        }
        ridesBatch.add(ride.cv);
        if (ride.subrides != null)
            ridesBatch.addAll(ride.subrides);
    }

    public void flush(int from, int to, long latest_dep) {
        placesBatch.addAll(ridesBatch);
        ctx.getContentResolver().bulkInsert(
                Uri.parse("content://" + ctx.getPackageName() +
                        "/rides?from_id=" + from + "&to_id=" + to),
                placesBatch.toArray(new ContentValues[placesBatch.size()]));
        placesBatch.clear();
        ridesBatch.clear();
        placeIdx.clear();
        ContentValues done = new ContentValues();
        done.put("from_id", from);
        done.put("to_id", to);
        done.put("latest_dep", latest_dep);
        done.put("last_refresh", System.currentTimeMillis());
        ctx.getContentResolver().insert(search_jobs_uri, done);
    }

    public void set(String key, String value) {
        PreferenceManager.getDefaultSharedPreferences(ctx).edit()
                .putString(key, value).commit();
    }

    public String get(String key) {
        return String.valueOf(PreferenceManager
                .getDefaultSharedPreferences(ctx).getAll().get(key));
    }

    public static JSONObject loadJson(HttpURLConnection conn) {
        try {
            return new JSONObject(loadString(conn));
        } catch (JSONException e) {
            System.out.println("json error");
            e.printStackTrace();
            return null;
        }
    }

    public static String loadString(HttpURLConnection conn) {
        StringBuilder result = new StringBuilder();
        try {
            InputStreamReader in = new InputStreamReader(
                    new BufferedInputStream(conn.getInputStream()));
            int read;
            char[] buff = new char[1024];
            while ((read = in.read(buff)) != -1) {
                result.append(buff, 0, read);
            }
            return result.toString();
        } catch (IOException e) {
            System.out.println("io error");
            e.printStackTrace();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return "";
    }
}
