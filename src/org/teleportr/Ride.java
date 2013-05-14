package org.teleportr;

import java.util.ArrayList;
import java.util.Date;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;

public class Ride {

    public static final int SEARCH = 42;
    public static final int OFFER = 47;

    ContentValues cv;
    ArrayList<ContentValues> subrides;

    public Ride() {
        cv = new ContentValues();
    }

    public Ride(Uri uri) {
        cv = new ContentValues();
        type(Ride.SEARCH);
        from(Integer.parseInt(uri.getQueryParameter("from_id")));
        to(Integer.parseInt(uri.getQueryParameter("to_id")));
        if (uri.getQueryParameter("dep") != null)
            dep(Long.parseLong(uri.getQueryParameter("dep")));
        if (uri.getQueryParameter("arr") != null)
            arr(Long.parseLong(uri.getQueryParameter("arr")));
    }

    public Uri store(Context ctx) {
        ctx.getContentResolver().insert(
                Uri.parse("content://" + ctx.getPackageName() + "/rides"), cv);
        return Uri.parse("content://" + ctx.getPackageName() + "/rides?"
                + "from_id=" + getFromId() + "&to_id=" + getToId()
                + "dep=" + getDep() + "&arr=" + getArr());
    }

    public int getFromId() {
            return cv.getAsInteger("from_id");
    }

    public int getToId() {
        return cv.getAsInteger("to_id");
    }

    public long getDep() {
        if (cv.containsKey("dep"))
            return cv.getAsLong("dep");
        else return System.currentTimeMillis();
    }

    public long getArr() {
        if (cv.containsKey("arr"))
            return cv.getAsLong("arr");
        else return System.currentTimeMillis();
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
            ContentValues sub = new ContentValues();
            sub.put("from_id", cv.getAsLong("from_id"));
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
        return dep(dep.getTime());
    }

    public Ride dep(long dep) {
        cv.put("dep", dep);
        return this;
    }

    public Ride arr(Date arr) {
        return arr(arr.getTime());
    }

    public Ride arr(long arr) {
        cv.put("arr", arr);
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
