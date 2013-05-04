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
    ArrayList<Ride> subrides;

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
    
    public Ride from(long from_id) {
        cv.put("from_id", from_id);
        return this;
    }

    public Ride from(Place from) {
        if (from.id != 0)
            return from(from.id);
        else {
            String id = from.cv.getAsString("geohash");
            if (!id.equals("")) {
                cv.put("from_geohash", id);
            } else {
                id = from.cv.getAsString("name");
                if (!id.equals("")) {
                    cv.put("from_name", id);
                } else {
                    Log.d(TAG, "place not identifyable! " + from);
                }
            }
        }
        return this;
    }

    public Ride to(Uri to) {
        return to(Integer.parseInt(to.getLastPathSegment()));
    }

    public Ride to(long to_id) {
        cv.put("to_id", to_id);
        return this;
    }

    public Ride to(Place to) {
        if (to.id != 0)
            return to(to.id);
        else {
            String id = to.cv.getAsString("geohash");
            if (!id.equals("")) {
                cv.put("to_geohash", id);
            } else {
                id = to.cv.getAsString("name");
                if (!id.equals("")) {
                    cv.put("to_name", id);
                } else {
                    Log.d(TAG, "place not identifyable! " + to);
                }
            }
        }
        return this;
    }

    public Ride via(Uri via) {
        return to(Integer.parseInt(via.getLastPathSegment()));
    }
    
    public Ride via(long via_id) {
        cv.put("via_id", via_id);
        return this;
    }
    
    public Ride via(Place via) {
        if (via.id != 0)
            return via(via.id);
        else {
            String id = via.cv.getAsString("geohash");
            if (!id.equals("")) {
                cv.put("via_geohash", id);
            } else {
                id = via.cv.getAsString("name");
                if (!id.equals("")) {
                    cv.put("via_name", id);
                } else {
                    Log.d(TAG, "place not identifyable! " + via);
                }
            }
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

    public Ride guid(String type) {
        cv.put("guid", type);
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
