package org.teleportr;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

public abstract class Connector {

    private static final String TAG = "Connector";

    public void getRides(Place from, Place to, Date dep, Date arr) {}

    public void postRide(Place from, Place to, Date dep, Date arr) {}

    public void resolvePlace(Place place, Context ctx) {}

    private HashMap<String, Integer> placeIdx;
    private ArrayList<ContentValues> placesBatch;
    private ArrayList<ContentValues> ridesBatch;
    Context ctx;

    public Connector(Context ctx) {
        placeIdx = new HashMap<String, Integer>();
        placesBatch = new ArrayList<ContentValues>();
        ridesBatch = new ArrayList<ContentValues>();
        this.ctx = ctx;
    }

    public Place store(Place place) {
        if (place.id != 0) return place;
        String id = place.getId();
        if (placeIdx.containsKey(id)) {
            place.id = placeIdx.get(place.getId());
            System.out.println("connector store place " + id + "already");
        } else {
            place.id = placesBatch.size();
            placeIdx.put(id, place.id);
            placesBatch.add(place.cv);
            System.out.println("connector store place " + id + "added");
        }
        return place;
    }

    public void store(Ride ride) {
        if (!ride.cv.containsKey("ref"))
            ride.cv.put("ref", UUID.randomUUID().toString());
        ridesBatch.add(ride.cv);
        if (ride.subrides != null)
            ridesBatch.addAll(ride.subrides);
    }

    public void search(int from, int to, long dep, long arr) {
        getRides(new Place(from, ctx), new Place(to, ctx), new Date(), null);
        placesBatch.addAll(ridesBatch);
        ctx.getContentResolver().bulkInsert(
                Uri.parse("content://" + ctx.getPackageName() +
                        "/rides?from_id=" + from + "&to_id=" + to),
                placesBatch.toArray(new ContentValues[placesBatch.size()]));
        ContentValues done = new ContentValues();
        done.put("from_id", from);
        done.put("to_id", to);
        done.put("last_refresh", System.currentTimeMillis());
        ctx.getContentResolver().insert(
                Uri.parse("content://" + ctx.getPackageName() + "/jobs"), done);
        placesBatch.clear();
        ridesBatch.clear();
    }

    public static String httpGet(String url) {
        HttpURLConnection conn = null;
        StringBuilder jsonResults = new StringBuilder();
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            InputStreamReader in = new InputStreamReader(conn.getInputStream());

            int read;
            char[] buff = new char[1024];
            while ((read = in.read(buff)) != -1) {
                jsonResults.append(buff, 0, read);
            }
        } catch (IOException e) {
            Log.e(TAG, "io exception " + e.getMessage());
            Log.e(TAG, "no internet???", e);
            e.printStackTrace();
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return jsonResults.toString();
    }
}
