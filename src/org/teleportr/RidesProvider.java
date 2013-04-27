package org.teleportr;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

public class RidesProvider extends ContentProvider {

    static final String TAG = "RideProvider";
    private DataBaseHelper db;
    private UriMatcher route;

    @Override
    public boolean onCreate() {
        db = new DataBaseHelper(getContext());
        try {
            setAuthority(getContext().getPackageName());
        } catch (UnsupportedOperationException e) {
        }
        return false;
    }

    public void setAuthority(String authority) {
        route = new UriMatcher(0);
        route.addURI(authority, "jobs/places", RESOLVE);
        route.addURI(authority, "jobs/rides", SEARCH);
        route.addURI(authority, "history", HISTORY);
        route.addURI(authority, "places/#", PLACE);
        route.addURI(authority, "places", PLACES);
        route.addURI(authority, "rides/#", RIDE);
        route.addURI(authority, "rides", RIDES);
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        long id = 0;
        try {
            db.getWritableDatabase().beginTransaction();
            switch (route.match(uri)) {
            case PLACES:
                id = db.insertPlace(values);
                break;
            case RIDES:
                id = db.insertRide(values);
                break;
            case SEARCH:
                id = db.getWritableDatabase().replace("jobs", null, values);
                break;
            }
            db.getWritableDatabase().setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "error during insert: " + e);
            e.printStackTrace();
        } finally {
            db.getWritableDatabase().endTransaction();
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return ContentUris.withAppendedId(uri, id);
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        try {
            db.getWritableDatabase().beginTransaction();
            switch (route.match(uri)) {
            case PLACES:
                for (int i = 0; i < values.length; i++) {
                    db.insertPlace(values[i]);
                }
                break;
            case RIDES:
                for (int i = 0; i < values.length; i++) {
                    db.insertRide(values[i]);
                }
                break;
            }
            db.getWritableDatabase().setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "error during bulk insert: " + e);
            e.printStackTrace();
        } finally {
            db.getWritableDatabase().endTransaction();
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return 1;
    }

    @Override
    public Cursor query(Uri uri, String[] proj, String sel, String[] args,
            String ord) {
        switch (route.match(uri)) {
        case HISTORY:
            return db.getReadableDatabase().query("rides", null,
                    "type=" + Ride.SEARCH, null, null, null, "_id DESC");
        case PLACE:
            String key = uri.getQueryParameter("key");
            if (key != null) {
                return db.getReadableDatabase().query("place_keys", null,
                        "place_id=" + uri.getLastPathSegment() +
                        " AND key='" + key + "'", null, null, null, null);
            } else {
                return db.getReadableDatabase().query("places", null,
                        "_id=" + uri.getLastPathSegment(),
                        null, null, null, null);
            }
        case PLACES:
            String from_id = uri.getQueryParameter("from_id");
            String q = uri.getQueryParameter("q");
            if (q == null) q = "%";
            else q = q + "%";
            if (from_id == null) {
                return db.autocompleteFrom(q);
            } else {
                return db.autocompleteTo(from_id, q);
            }
        case SEARCH:
            return db.queryJobs();
        case RESOLVE:
            return db.getReadableDatabase().query("places", null,
                    "geohash IS ''", null, null, null, null);
        case RIDE:
            return db.getReadableDatabase().query("rides", null,
                    "_id=" + uri.getLastPathSegment(), null, null, null, null);
        case RIDES:
            return db.queryRides(
                    uri.getQueryParameter("from_id"),
                    uri.getQueryParameter("to_id"));
        }
        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String sel, String[] args) {
        switch (route.match(uri)) {
        case PLACE:
            return db.getWritableDatabase().update("places", values, sel, args);
        }
        return 0;
    }

    @Override
    public int delete(Uri arg0, String arg1, String[] arg2) {
        // TODO Auto-generated method stub
        return 0;
    }

    private static final int HISTORY = 1;
    private static final int RESOLVE = 2;
    private static final int SEARCH = 3;
    private static final int PLACES = 4;
    private static final int PLACE = 5;
    private static final int RIDES = 6;
    private static final int RIDE = 7;
}
