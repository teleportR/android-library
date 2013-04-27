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

    public Connector() {
        placesBatch = new ArrayList<ContentValues>();
        ridesBatch = new ArrayList<ContentValues>();
    }

    public Place store(Place place) {
        placesBatch.add(place.getContentValues());
        return place;
    }

    public void store(Ride ride) {
        ridesBatch.add(ride.cv);
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
