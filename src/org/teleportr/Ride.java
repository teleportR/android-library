package org.teleportr;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

public class Ride implements Parcelable {

    public static final int SEARCH = 42;
    public static final int OFFER = 47;

    public static enum Mode {
        CAR, TRAIN
    };

    Context ctx;
    ContentValues cv;
    ArrayList<ContentValues> subrides;
    JSONObject details;

    public Ride() {
        cv = new ContentValues();
    }

    public Ride set(String key, String value) {
        try {
            getDetails().put(key, value);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return this;
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


    public void removeVias() {
        subrides = null;
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
        } else {
            subrides.get(subrides.size()-1).put("to_id", via_id);
        }
        ContentValues sub = new ContentValues();
        sub.put("from_id", via_id);
        if (getToId() != 0)
            sub.put("to_id", getToId());
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
        if (subrides != null && subrides.size() > 0) {
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


    public Ride mode(Mode mode) {
        cv.put("mode", mode.name());
        return this;
    }

    public Ride who(String who) {
        cv.put("who", who);
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

    public Ride marked() {
        cv.put("marked", 1);
        return this;
    }

    public Ride dirty() {
        cv.put("dirty", 1);
        return this;
    }

    public Ride activate() {
        cv.put("active", 1);
        return this;
    }

    public Ride deactivate() {
        cv.put("active", 0);
        return this;
    }



    public Uri store(Context ctx) {
        if (!cv.containsKey("mode")) mode(Mode.CAR);
        if (!cv.containsKey("active")) activate();
        if (details != null) cv.put("details", details.toString());
        Uri ride;
        cv.put("parent_id", 0);
        Uri uri = Uri.parse("content://" + ctx.getPackageName() + "/rides");
        ride = ctx.getContentResolver().insert(uri, cv);
        Integer parent_id = Integer.valueOf(ride.getLastPathSegment());
        if (subrides != null) {
            for (ContentValues v : subrides) {
                v.put("parent_id", parent_id);
                ctx.getContentResolver().insert(uri, v);
            }
        }
        return ride;
    }

    public void delete(Context ctx) {
        cv.put("dirty", 2);
        store(ctx);
    }


    public static final class COLUMNS {
        public static final short ID = 0;
        public static final short TYPE = 1;
        public static final short FROM_ID = 2;
        public static final short FROM_NAME = 3;
        public static final short FROM_ADDRESS = 4;
        public static final short TO_ID = 5;
        public static final short TO_NAME = 6;
        public static final short TO_ADDRESS = 7;
        public static final short DEPARTURE = 8;
        public static final short ARRIVAL = 9;
        public static final short MODE = 10;
        public static final short OPERATOR = 11;
        public static final short WHO = 12;
        public static final short DETAILS = 13;
        public static final short DISTANCE = 14;
        public static final short PRICE = 15;
        public static final short SEATS = 16;
        public static final short MARKED = 17;
        public static final short DIRTY = 18;
        public static final short ACTIVE = 19;
        public static final short PARENT_ID = 20;
        public static final short REF = 21;
    }


    public Ride(Context ctx) {
        this();
        this.ctx = ctx;
    }

    public void setContext(Context ctx) {
        this.ctx = ctx;
    }

    public Ride(Uri uri, Context ctx) {
        this(ctx);
        Cursor c = ctx.getContentResolver().query(uri, null, null, null, null);
        c.moveToFirst();
        load(c, ctx);
    }

    public Ride(Cursor cursor, Context ctx) {
        this(ctx);
        load(cursor, ctx);
    }

    private void load(Cursor cursor, Context ctx) {
        cv.put("_id", cursor.getLong(COLUMNS.ID));
        type(cursor.getInt(COLUMNS.TYPE));
        from(cursor.getInt(COLUMNS.FROM_ID));
        to(cursor.getInt(COLUMNS.TO_ID));
        dep(cursor.getLong(COLUMNS.DEPARTURE));
        arr(cursor.getLong(COLUMNS.ARRIVAL));
        price(cursor.getInt(COLUMNS.PRICE));
        seats(cursor.getInt(COLUMNS.SEATS));
        if (cursor.getShort(COLUMNS.MARKED) == 1) marked();
        if (cursor.getShort(COLUMNS.ACTIVE) == 1) activate();
        String val = cursor.getString(COLUMNS.WHO);
        if (val != null && !val.equals(""))
            who(val);
        val = cursor.getString(COLUMNS.REF);
        if (val != null && !val.equals(""))
            ref(val);
        try {
            details = new JSONObject(cursor.getString(COLUMNS.DETAILS));
            mode(Mode.valueOf(cursor.getString(COLUMNS.MODE)));
        } catch (Exception e) {}
        Cursor s = ctx.getContentResolver().query(
                Uri.parse("content://" + ctx.getPackageName() + "/rides/"
                        + cursor.getInt(0) + "/rides/"), null, null, null, null);
        subrides = new ArrayList<ContentValues>();
        for (int i = 0; i < s.getCount(); i++) {
            s.moveToPosition(i);
            subrides.add(new Ride(s, ctx).cv);
        }
        s.close();
        this.ctx = ctx;
    }

    public String get(String key) {
        try {
            return getDetails().getString(key);
        } catch (JSONException e) {
            return null;
        }
    }

    public List<Ride> getSubrides() {
        ArrayList<Ride> subs = new ArrayList<Ride>();
        for (ContentValues v : subrides) {
            Ride r = new Ride(ctx);
            r.cv = v;
            subs.add(r);
        }
        return subs;
    }

    public Place getFrom() {
        if (getFromId() != 0)
            return new Place(getFromId(), ctx);
        else
            return null;
    }

    public Place getTo() {
        if (getToId() != 0)
            return new Place(getToId(), ctx);
        else
            return null;
    }

    public List<Place> getVias() {
        ArrayList<Place> vias = new ArrayList<Place>();
        if (subrides != null) {
            for (int i = 1; i < subrides.size(); i++) {
                vias.add(new Place(subrides.get(i)
                        .getAsInteger("from_id"), ctx));
            }
        }
        return vias;
    }

    public List<Place> getPlaces() {
        ArrayList<Place> places = new ArrayList<Place>();
        if (subrides != null && subrides.size() > 0) {
            for (int i = 0; i < subrides.size(); i++) {
                places.add(new Place(subrides.get(i)
                        .getAsInteger("from_id"), ctx));
            }
            places.add(getTo());
        } else {
            places.add(getFrom());
            places.add(getTo());
        }
        return places;
    }

    public int getFromId() {
        if (cv.containsKey("from_id"))
            return cv.getAsInteger("from_id");
        else return 0;
    }

    public int getToId() {
        if (cv.containsKey("to_id"))
            return cv.getAsInteger("to_id");
        else return 0;
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

    public Mode getMode() {
        if (cv.containsKey("mode"))
            return Mode.valueOf(cv.getAsString("mode"));
        else return Mode.CAR;
    }

    public String getWho() {
        if (cv.containsKey("who"))
            return cv.getAsString("who");
        else return "";
    }

    public int getPrice() {
        if (cv.containsKey("price"))
            return cv.getAsInteger("price");
        else return 0;
    }

    public int getSeats() {
        if (cv.containsKey("seats"))
            return cv.getAsInteger("seats");
        else return 0;
    }

    public String getRef() {
        if (cv.containsKey("ref"))
            return cv.getAsString("ref");
        else return null;
    }

    public boolean isMarked() {
        return cv.containsKey("marked") && cv.getAsShort("marked") == 1;
    }

    public boolean isActive() {
        return cv.containsKey("active") && cv.getAsShort("active") == 1;
    }

    public JSONObject getDetails() {
        if (details == null)
            details = new JSONObject();
        return details;
    }

    public static JSONObject getDetails(Cursor cursor) {
        try {
            return new JSONObject(cursor.getString(COLUMNS.DETAILS));
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String getDetails(Cursor cursor, String key) {
        try {
            return getDetails(cursor).getString(key);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }



    @Override
    public int describeContents() {
        return 0;
    }



    @Override
    public void writeToParcel(Parcel out, int flags) {
//        out.writeTypedList(subrides);
        if (details != null) {
            cv.put("details", details.toString());
        }
        out.writeParcelable(cv, 0);
    }

    public static final Parcelable.Creator<Ride> CREATOR
        = new Parcelable.Creator<Ride>() {

            @Override
            public Ride createFromParcel(Parcel in) {
                Ride ride = new Ride();
//                ride.subrides = new ArrayList<ContentValues>();
//                in.readTypedList(ride.subrides, ContentValues.CREATOR);
                ride.cv = in.readParcelable(getClass().getClassLoader());
                try {
                    if (ride.cv.containsKey("details"))
                        ride.details = new JSONObject(
                                ride.cv.getAsString("details"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                Log.d("Ride", "read " + ride.cv.getAsString("from_id"));
                return ride;
            }

            @Override
            public Ride[] newArray(int size) {
                return new Ride[size];
            }
        };
}
