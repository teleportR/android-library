package org.teleportr;

import java.util.ArrayList;
import java.util.Date;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

public class Ride {

    public static final int SEARCH = 42;
    public static final int OFFER = 47;
    private static final String TAG = "Connector";

    ContentValues cv;
    ArrayList<ContentValues> subrides;

    public Ride() {
        cv = new ContentValues();
    }

    public Uri store(Context ctx) {
        return ctx.getContentResolver().insert(
                Uri.parse("content://" + ctx.getPackageName() + "/rides"), cv);
    }



    public Ride from(Uri from) {
        return from(Integer.parseInt(from.getLastPathSegment()));
    }

    public Ride from(Place from) {
        return from(from.id);
    }

    public Ride from(int from_id) {
        cv.put("from_id", from_id);
        return this;
    }



    public Ride via(Uri via) {
        return to(Integer.parseInt(via.getLastPathSegment()));
    }

    public Ride via(Place via) {
        return via(via.id);
    }

    public Ride via(long via_id) {
        if (subrides == null) {
            subrides = new ArrayList<ContentValues>();
            ContentValues sub = new ContentValues(cv);
            sub.put("to_id", via_id);
            subrides.add(sub);
        } else
            subrides.get(subrides.size()-1).put("to_id", via_id);
        ContentValues sub = new ContentValues();
        sub.put("from_id", via_id);
        subrides.add(sub);
        return this;
    }


    public Ride to(Uri to) {
        return to(Integer.parseInt(to.getLastPathSegment()));
    }

    public Ride to(Place to) {
        return to(to.id);
    }

    public Ride to(long to_id) {
        cv.put("to_id", to_id);
        if (subrides != null) {
            subrides.get(subrides.size()-1)
                .put("to_id", to_id);
        }
        return this;
    }



    public Ride dep(Date dep) {
        cv.put("dep", dep.getTime());
        return this;
    }

    public Ride arr(Date arr) {
        cv.put("arr", arr.getTime());
        return this;
    }

    public Ride who(String who) {
        cv.put("who", who);
        return this;
    }

    public Ride details(String details) {
        cv.put("details", details);
        return this;
    }

    public Ride type(int type) {
        cv.put("type", type);
        return this;
    }

    public Ride ref(String ref) {
        cv.put("ref", ref);
        return this;
    }

    public Ride price(long price) {
        cv.put("price", price);
        return this;
    }

    public Ride seats(long seats) {
        cv.put("seats", seats);
        return this;
    }
}
