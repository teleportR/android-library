/**
 * Fahrgemeinschaft / Ridesharing App
 * Copyright (c) 2013 by it's authors.
 * Some rights reserved. See LICENSE..
 *
 */

package org.teleportr;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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

    private static final String COLON = ": ";
    private static final String ARROW = " -> ";
    public static final String _ID = "_id";
    public static final String TYPE = "type";
    public static final String FROM_ID = "from_id";
    public static final String TO_ID = "to_id";
    public static final String DEP = "dep";
    public static final String ARR = "arr";
    public static final String WHO = "who";
    public static final String MODE = "mode";
    public static final String DIRTY = "dirty";
    public static final String OPERATOR = "operator";
    public static final String PARENT_ID = "parent_id";
    public static final String DETAILS = "details";
    public static final String REFRESH = "refresh";
    public static final String ACTIVE = "active";
    public static final String MARKED = "marked";
    public static final String SEATS = "seats";
    public static final String PRICE = "price";
    public static final String REF = "ref";
    public static final String EMPTY = "";
    public static final short SEARCH = 42;
    public static final short OFFER = 47;
    public static final short FLAG_DELETED = -2;
    public static final short FLAG_DRAFT = -1;
    public static final short FLAG_CLEAN = 0;
    public static final short FLAG_FOR_CREATE = 1;
    public static final short FLAG_FOR_UPDATE = 2;
    public static final short FLAG_FOR_DELETE = 3;

    public static enum Mode {
        CAR, TRAIN
    }

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
        cv.put(FROM_ID, from_id);
        return this;
    }


    public Ride removeVias() {
        subrides = null;
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
            sub.put(FROM_ID, cv.getAsLong(FROM_ID));
            sub.put(TO_ID, via_id);
            subrides.add(sub);
        } else {
            subrides.get(subrides.size()-1).put(TO_ID, via_id);
        }
        ContentValues sub = new ContentValues();
        sub.put(FROM_ID, via_id);
        if (getToId() != 0)
            sub.put(TO_ID, getToId());
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
        cv.put(TO_ID, to_id);
        if (subrides != null && subrides.size() > 0) {
            subrides.get(subrides.size()-1)
                .put(TO_ID, to_id);
        }
        return this;
    }


    public Ride dep(Date dep) {
        return dep(dep.getTime());
    }

    public Ride dep(long dep) {
        cv.put(DEP, dep);
        return this;
    }

    public Ride arr(Date arr) {
        return arr(arr.getTime());
    }

    public Ride arr(long arr) {
        cv.put(ARR, arr);
        return this;
    }


    public Ride mode(Mode mode) {
        cv.put(MODE, mode.name());
        return this;
    }

    public Ride who(String who) {
        cv.put(WHO, who);
        return this;
    }

    public Ride type(int type) {
        cv.put(TYPE, type);
        return this;
    }

    public Ride ref(String ref) {
        if (ref == null)
            cv.remove(REF);
        else
            cv.put(REF, ref);
        return this;
    }

    public Ride price(long price) {
        cv.put(PRICE, price);
        return this;
    }

    public Ride seats(long seats) {
        cv.put(SEATS, seats);
        return this;
    }

    public Ride marked() {
        cv.put(MARKED, 1);
        return this;
    }

    public Ride activate() {
        cv.put(ACTIVE, 1);
        return this;
    }

    public Ride deactivate() {
        cv.put(ACTIVE, 0);
        return this;
    }

    public Ride dirty() {
        if (!cv.containsKey(DIRTY) || cv.getAsShort(DIRTY) == FLAG_DRAFT) {
            cv.put(DIRTY, FLAG_FOR_CREATE);
        } else if (cv.getAsShort(DIRTY) == FLAG_CLEAN) {
            cv.put(DIRTY, FLAG_FOR_UPDATE);
        }
        return this;
    }

    public Uri delete() {
        ContentValues values = new ContentValues();
        if (!cv.containsKey(DIRTY) || getRef() == null // should not be possible
                || cv.getAsShort(DIRTY) == FLAG_FOR_CREATE
                || cv.getAsShort(DIRTY) == FLAG_DRAFT) {
            values.put(DIRTY, FLAG_DELETED);
        } else if (cv.getAsShort(DIRTY) == FLAG_CLEAN) {
            values.put(DIRTY, FLAG_FOR_DELETE);
        }
        Uri uri = RidesProvider.getRideUri(ctx, getId());
        ctx.getContentResolver().update(uri, values, null, null);
        uri = RidesProvider.getRideRefUri(ctx, getRef());
        ctx.getContentResolver().update(uri, values, null, null);
        return uri;
    }

    public Uri duplicate() {
        cv.remove(_ID);
        cv.remove(REF);
        cv.remove(DIRTY);
        cv.remove(MARKED);
        return store(ctx);
    }



    public Uri store(Context ctx) {
        if (!cv.containsKey(MODE)) mode(Mode.CAR);
        if (!cv.containsKey(ACTIVE)) activate();
        if (!cv.containsKey(PRICE)) price(-1);
        if (!cv.containsKey(DIRTY)) cv.put(DIRTY, FLAG_DRAFT);
        if (details != null) cv.put(DETAILS, details.toString());
        Uri ride;
        cv.put(PARENT_ID, 0);
        ride = ctx.getContentResolver().insert(
                RidesProvider.getRidesUri(ctx), cv);
        Integer id = Integer.valueOf(ride.getLastPathSegment());
        System.out.println("STORED " + id);
        if (subrides != null) {
            for (ContentValues v : subrides) {
                v.put(PARENT_ID, id);
                ctx.getContentResolver().insert(
                        RidesProvider.getRidesUri(ctx), v);
//                System.out.println("  SUB  " + Integer.valueOf(ride.getLastPathSegment()));
            }
        }
        return ride;
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

    public Ride(int id, Context ctx) {
        this(RidesProvider.getRideUri(ctx, id), ctx);
    }

    public Ride(Uri uri, Context ctx) {
        this(ctx);
        Cursor c = ctx.getContentResolver().query(uri, null, null, null, null);
        c.moveToFirst();
        load(c, ctx);
        c.close();
    }

    public Ride(Cursor cursor, Context ctx) {
        this(ctx);
        load(cursor, ctx);
    }

    private void load(Cursor cursor, Context ctx) {
        cv.put(_ID, cursor.getLong(COLUMNS.ID));
        type(cursor.getInt(COLUMNS.TYPE));
        from(cursor.getInt(COLUMNS.FROM_ID));
        to(cursor.getInt(COLUMNS.TO_ID));
        dep(cursor.getLong(COLUMNS.DEPARTURE));
        arr(cursor.getLong(COLUMNS.ARRIVAL));
        price(cursor.getInt(COLUMNS.PRICE));
        seats(cursor.getInt(COLUMNS.SEATS));
        cv.put(DIRTY, cursor.getInt(COLUMNS.DIRTY));
        if (cursor.getShort(COLUMNS.MARKED) == 1) marked();
        if (cursor.getShort(COLUMNS.ACTIVE) == 1) activate();
        String val = cursor.getString(COLUMNS.WHO);
        if (val != null && !val.equals(EMPTY))
            who(val);
        val = cursor.getString(COLUMNS.REF);
        if (val != null && !val.equals(EMPTY))
            ref(val);
        else Log.e(RidesProvider.TAG, "REF is NULL! " + this);
        try {
            details = new JSONObject(cursor.getString(COLUMNS.DETAILS));
            mode(Mode.valueOf(cursor.getString(COLUMNS.MODE)));
        } catch (Exception e) {}
        Cursor sub_cursor = ctx.getContentResolver().query(
                RidesProvider.getSubRidesUri(ctx, cursor.getInt(0)),
                        null, null, null, null);
        subrides = new ArrayList<ContentValues>();
        for (int i = 0; i < sub_cursor.getCount(); i++) {
            sub_cursor.moveToPosition(i);
            subrides.add(new Ride(sub_cursor, ctx).cv);
        }
        sub_cursor.close();
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
                        .getAsInteger(FROM_ID), ctx));
            }
        }
        return vias;
    }

    public List<Place> getPlaces() {
        ArrayList<Place> places = new ArrayList<Place>();
        if (subrides != null && subrides.size() > 0) {
            for (int i = 0; i < subrides.size(); i++) {
                places.add(new Place(subrides.get(i)
                        .getAsInteger(FROM_ID), ctx));
            }
            places.add(getTo());
        } else {
            places.add(getFrom());
            places.add(getTo());
        }
        return places;
    }

    public int getFromId() {
        if (cv.containsKey(FROM_ID))
            return cv.getAsInteger(FROM_ID);
        else return 0;
    }

    public int getToId() {
        if (cv.containsKey(TO_ID))
            return cv.getAsInteger(TO_ID);
        else return 0;
    }

    public long getDep() {
        if (cv.containsKey(DEP))
            return cv.getAsLong(DEP);
        else return System.currentTimeMillis();
    }

    public long getArr() {
        if (cv.containsKey(ARR))
            return cv.getAsLong(ARR);
        else return System.currentTimeMillis();
    }

    public Mode getMode() {
        if (cv.containsKey(MODE))
            return Mode.valueOf(cv.getAsString(MODE));
        else return Mode.CAR;
    }

    public String getWho() {
        if (cv.containsKey(WHO))
            return cv.getAsString(WHO);
        else return EMPTY;
    }

    public int getPrice() {
        if (cv.containsKey(PRICE))
            return cv.getAsInteger(PRICE);
        else return 0;
    }

    public int getSeats() {
        if (cv.containsKey(SEATS))
            return cv.getAsInteger(SEATS);
        else return 0;
    }

    public int getId() {
        if (cv.containsKey(_ID))
            return cv.getAsInteger(_ID);
        else return 0;
    }

    public String getRef() {
        if (cv.containsKey(REF))
            return cv.getAsString(REF);
        else return null;
    }

    public boolean isMarked() {
        return cv.containsKey(MARKED) && cv.getAsShort(MARKED) == 1;
    }

    public boolean isActive() {
        return cv.containsKey(ACTIVE) && cv.getAsShort(ACTIVE) == 1;
    }

    public JSONObject getDetails() {
        if (details == null)
            details = new JSONObject();
        return details;
    }

    public Uri toUri() {
        return RidesProvider.getRidesUri(ctx).buildUpon()
            .appendQueryParameter(Ride.FROM_ID, cv.getAsString(Ride.FROM_ID))
            .appendQueryParameter(Ride.TO_ID, cv.getAsString(Ride.TO_ID))
            .appendQueryParameter(Ride.DEP, cv.getAsString(Ride.DEP))
            .appendQueryParameter(Ride.ARR, cv.getAsString(Ride.ARR))
            .build();
    }

    @Override
    public String toString() {
        return new StringBuffer().append(getFrom().getName().substring(0, 3))
                .append(ARROW).append(getTo().getName().substring(0, 3))
                .append(COLON).append(df.format(getDep())).toString();
    }

    private static final SimpleDateFormat df =
            new SimpleDateFormat("EEE dd.MM.", Locale.GERMANY);

    @Override
    public int hashCode() {
        int prim = 59;
        int hash = 17;
        hash = hash + prim * (int) (getFromId() >>> 32);
        hash = hash + prim * (int) (getToId() >>> 32);
        hash = hash + prim * (int) (getDep() >>> 32);
        return hash;
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
        if (details != null) {
            cv.put(DETAILS, details.toString());
        }
        out.writeParcelable(cv, 0);
    }

    public static final Parcelable.Creator<Ride> CREATOR
        = new Parcelable.Creator<Ride>() {

        @Override
        public Ride createFromParcel(Parcel in) {
            Ride ride = new Ride();
            ride.cv = in.readParcelable(getClass().getClassLoader());
            try {
                if (ride.cv.containsKey(DETAILS))
                    ride.details = new JSONObject(
                            ride.cv.getAsString(DETAILS));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return ride;
        }

        @Override
        public Ride[] newArray(int size) {
            return new Ride[size];
        }
    };

}
