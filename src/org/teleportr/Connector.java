package org.teleportr;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

public abstract class Connector {

    private static final String TAG = "Connector";

    public void getRides(Place from, Place to, Date dep, Date arr) {}

    public void postRide(Place from, Place to, Date dep, Date arr) {}

    public void resolvePlace(Place place, Context ctx) {}

    private ArrayList<ContentValues> placesBatch;
    private ArrayList<ContentValues> ridesBatch;
    Context ctx;

    public Connector(Context ctx) {
        placesBatch = new ArrayList<ContentValues>();
        ridesBatch = new ArrayList<ContentValues>();
        this.ctx = ctx;
    }

    public Place store(Place place) {
        placesBatch.add(place.toContentValues());
        return place;
    }

    public void store(Ride ride) {
        ridesBatch.add(ride.cv);
    }

    public void search(long from, long to, long dep, long arr) {
        getRides(new Place(from, ctx), new Place(to, ctx), new Date(dep), null);
        ctx.getContentResolver().bulkInsert(
                Uri.parse("content://" + ctx.getPackageName() + "/places"),
                placesBatch.toArray(new ContentValues[placesBatch.size()]));
        placesBatch.clear();
        ctx.getContentResolver().bulkInsert(
                Uri.parse("content://" + ctx.getPackageName() +
                        "/rides?from_id=" + from + "&to_id=" + to),
                ridesBatch.toArray(new ContentValues[ridesBatch.size()]));
        ridesBatch.clear();
        ContentValues done = new ContentValues();
        done.put("from_id", from);
        done.put("to_id", to);
        done.put("last_refresh", System.currentTimeMillis());
        ctx.getContentResolver().insert(
                Uri.parse("content://" + ctx.getPackageName() + "/jobs"), done);
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
