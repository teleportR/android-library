package org.teleportr;

import java.util.ArrayList;
import java.util.Date;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

public abstract class Connector {

    public abstract void getRides(Place from, Place to, Date dep, Date arr);

    public void postRide(Place from, Place to, Date dep, Date arr) { }


    private ArrayList<ContentValues> placesBatch;
    private ArrayList<ContentValues> ridesBatch;
    private String search_guid;

    public Connector() {
        placesBatch = new ArrayList<ContentValues>();
        ridesBatch = new ArrayList<ContentValues>();
    }

    public Place store(Place place) {
        placesBatch.add(place.getContentValues());
        return place;
    }

    public void store(Ride ride) {
        ride.cv.put("search_guid", search_guid);
        ridesBatch.add(ride.cv);
    }

    public void search(Cursor query) {
        query.moveToFirst();
        search_guid = query.getString(13);
        getRides(new Place(query.getLong(2)), new Place(query.getLong(3)),
                new Date(query.getLong(4)), new Date(query.getLong(5)));
    }

    public void flushBatch(Context ctx) {
        ctx.getContentResolver().bulkInsert(
                Uri.parse("content://" + ctx.getPackageName() + "/places"),
                placesBatch.toArray(new ContentValues[placesBatch.size()]));
        ctx.getContentResolver().bulkInsert(
                Uri.parse("content://" + ctx.getPackageName() + "/rides"),
                ridesBatch.toArray(new ContentValues[ridesBatch.size()]));
    }

}
