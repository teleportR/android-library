/**
 * Fahrgemeinschaft / Ridesharing App
 * Copyright (c) 2013 by it's authors.
 * Some rights reserved. See LICENSE..
 *
 */

package org.teleportr;

import java.util.ArrayList;
import java.util.UUID;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

public class RidesProvider extends ContentProvider {

    private static final String USER = "user";
    private static final String CLEAR_MYRIDES = "clear myrides";
    private static final String ROUTE_MATCHES = "route_matches";
    public static final String OLDER_THAN = "older_than";
    private static final String DELETED = ": deleted ";
    private static final String UPSERTED = ": upserted ";
    public static final String REFRESH = "refresh";
    private static final String PLACE_KEYS = "place_keys";
    private static final String LAST_REFRESH = "last_refresh";
    static final String TAG = "RidesProvider";
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

    private static Uri uri;
    private static Uri rides_uri;
    private static Uri places_uri;
    private static Uri myrides_uri;
    private static Uri search_jobs_uri;
    private static Uri resolve_jobs_uri;
    private static Uri publish_jobs_uri;
    private static final String CONTENT = "content://";
    private static final String PLACES_PATH = "places";
    private static final String PLACE_ID_PATH = "places/#";
    private static final String RIDES_PATH = "rides";
    private static final String MYRIDES_PATH = "myrides";
    private static final String RIDES_ID_PATH = "rides/#";
    private static final String RIDE_REF_PATH = "rides/*";
    private static final String SUBRIDES_PATH = "rides/#/rides";
    private static final String JOBS_PATH = "jobs";
    private static final String RESOLVE_PATH = "jobs/resolve";
    private static final String PUBLISH_PATH = "jobs/publish";
    private static final String SEARCH_PATH = "jobs/search";
    private static final String HISTORY_PATH = "history";

    public void setAuthority(String authority) {
        route = new UriMatcher(0);
        route.addURI(authority, SUBRIDES_PATH, SUBRIDES);
        route.addURI(authority, RESOLVE_PATH, RESOLVE);
        route.addURI(authority, PUBLISH_PATH, PUBLISH);
        route.addURI(authority, SEARCH_PATH, SEARCH);
        route.addURI(authority, HISTORY_PATH, HISTORY);
        route.addURI(authority, MYRIDES_PATH, MYRIDES);
        route.addURI(authority, PLACE_ID_PATH, PLACE);
        route.addURI(authority, PLACES_PATH, PLACES);
        route.addURI(authority, RIDE_REF_PATH, RIDEF);
        route.addURI(authority, RIDES_ID_PATH, RIDE);
        route.addURI(authority, RIDES_PATH, RIDES);
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues cv) {
        long id = 0;
        try {
            db.getWritableDatabase().beginTransaction();
            switch (route.match(uri)) {
            case PLACES:
                id = db.insertPlace(cv);
                break;
            case RIDES:
                if (cv.getAsInteger(Ride.PARENT_ID) == 0) {
                    if (cv.containsKey(Ride._ID)) {
                        cv.put(Ride.REF, db.getLatestRef(
                                cv.getAsInteger(Ride._ID)));
                    }
                    cv.remove(Ride._ID);
                    if (!cv.containsKey(Ride.REF)
                            || cv.getAsString(Ride.REF) == null) {
                        cv.put(Ride.REF, UUID.randomUUID().toString());
                        if (cv.getAsShort(Ride.DIRTY) == Ride.FLAG_CLEAN)
                            cv.put(Ride.DIRTY, Ride.FLAG_FOR_CREATE);
                    }
                }
                id = db.insertRide(cv);
                if (cv.containsKey(Ride.MARKED)
                        && cv.getAsShort(Ride.MARKED) == 1) {
                    ContentValues m = new ContentValues();
                    m.put(Ride.REF, cv.getAsString(Ride.REF));
                    db.getWritableDatabase().insert("markings", null, m);
                }
                break;
            case SEARCH:
                id = db.getWritableDatabase().replace(JOBS_PATH, null, cv);
                break;
            }
            db.getWritableDatabase().setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "error during insert: " + e);
            e.printStackTrace();
        } finally {
            db.getWritableDatabase().endTransaction();
            getContext().getContentResolver()
                    .notifyChange(getMyRidesUri(getContext()), null);
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
                int parent = 0;
                int fr;
                int to;
                int s_f = Integer.parseInt(uri.getQueryParameter(Ride.FROM_ID));
                int s_to = Integer.parseInt(uri.getQueryParameter(Ride.TO_ID));
                ArrayList<Integer> placeIdx = new ArrayList<Integer>();
                long refresh = System.currentTimeMillis();
                int upserted_cnt = 0;
                for (int i = 0; i < values.length; i++) {
                    if (parent == 0 && !values[i].containsKey(Ride.REF)) {
                        placeIdx.add(db.insertPlace(values[i]));
                    } else {
                        fr = placeIdx.get(values[i].getAsInteger(Ride.FROM_ID));
                        to = placeIdx.get(values[i].getAsInteger(Ride.TO_ID));
                        if (values[i].containsKey(Ride.REF)) {
                            parent = db.insertRide(0, fr, to, values[i]);
                            db.insertMatch(fr, to, s_f, s_to);
                            upserted_cnt++;
                        } else if (parent != -1) {
                            db.insertRide(parent, fr, to, values[i]);
                        } else {
                            Log.d(TAG, RIDES_PATH + " not stored");
                        }
                    }
                }
                Log.d(TAG, RIDES_PATH + UPSERTED + upserted_cnt);
                int deleted_cnt = db.deleteOutdated(
                        String.valueOf(s_f), String.valueOf(s_to),
                        uri.getQueryParameter(Ride.DEP),
                        uri.getQueryParameter(Ride.ARR),
                        String.valueOf(refresh));
                Log.d(TAG, RIDES_PATH + DELETED + deleted_cnt);
                ContentValues done = new ContentValues();
                done.put(Ride.FROM_ID, s_f);
                done.put(Ride.TO_ID, s_to);
                done.put(Ride.DEP, uri.getQueryParameter("depp"));
                done.put(Ride.ARR, uri.getQueryParameter(Ride.ARR));
                done.put(LAST_REFRESH, refresh);
                insert(getSearchJobsUri(getContext()), done);
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
    public Cursor query(Uri uri, String[] p, String s, String[] a, String o) {
        switch (route.match(uri)) {
        case PLACE:
            String key = uri.getQueryParameter(Place.KEY);
            if (key != null) {
                return db.getReadableDatabase().query(PLACE_KEYS, null,
                        PLACE_ID_IS + uri.getLastPathSegment() +
                        AND_KEY_IS + key + "'", null, null, null, null);
            } else {
                return db.getReadableDatabase().query(PLACES_PATH, null,
                        ID_IS + uri.getLastPathSegment(),
                        null, null, null, null);
            }
        case PLACES:
            String from_id = uri.getQueryParameter(Ride.FROM_ID);
            String q = uri.getQueryParameter("q");
            if (q == null) q = "%";
            else q = q + "%";
            if (from_id == null) {
                return db.autocompleteFrom(q);
            } else {
                return db.autocompleteTo(from_id, q);
            }
        case RIDES:
            Cursor rslt = db.queryRides(
                    uri.getQueryParameter(Ride.FROM_ID),
                    uri.getQueryParameter(Ride.TO_ID),
                    uri.getQueryParameter(Ride.DEP),
                    uri.getQueryParameter(Ride.ARR));
            rslt.setNotificationUri(getContext().getContentResolver(), uri);
            return rslt;
        case SUBRIDES:
            return db.querySubRides(uri.getPathSegments().get(1));
        case RIDE:
            return db.queryRide(uri.getLastPathSegment());
        case HISTORY:
            return db.getReadableDatabase().query(RIDES_PATH, null,
                    TYPE_IS + Ride.SEARCH, null, null, null, "_id DESC");
        case MYRIDES:
            rslt = db.queryMyRides();
            rslt.setNotificationUri(getContext().getContentResolver(),
                    getMyRidesUri(getContext()));
            return rslt;
        case SEARCH:
            long olderThan = 10 * 60 * 1000; // 10min
            try {
                olderThan = Long.parseLong(PreferenceManager
                        .getDefaultSharedPreferences(
                        getContext()).getString(REFRESH, new String()));
            } catch (Exception e) {}
            return db.queryJobs(System.currentTimeMillis() - olderThan);
        case PUBLISH:
            return db.queryPublishJobs();
        case RESOLVE:
            return db.getReadableDatabase().query(PLACES_PATH, null,
                    GEOHASH_IS_NULL, null, null, null, null);
        }
        return null;
    }

    private static final String GEOHASH_IS_NULL = "geohash IS NULL";
    private static final String PLACE_ID_IS = "place_id=";
    private static final String AND_KEY_IS = " AND key='";
    private static final String TYPE_IS = "type=";
    private static final String ID_IS = "_id=";

    @Override
    public int update(Uri uri, ContentValues values, String sel, String[] args) {
        getContext().getContentResolver().notifyChange(uri, null);
        int id = -1;
        switch (route.match(uri)) {
        case PLACE:
            id =  db.getWritableDatabase().update(PLACES_PATH, values,
                    ID_EQUALS, new String[] { uri.getLastPathSegment() });
            break;
        case RIDE: // only one ride version with specific id
            if (db.isDeleted(uri.getLastPathSegment())) {
                values.put(Ride.DIRTY, Ride.FLAG_FOR_DELETE);
            }
            id =  db.getWritableDatabase().update(RIDES_PATH, values,
                    ID_EQUALS, new String[] { uri.getLastPathSegment() });
            getContext().getContentResolver().notifyChange(
                    getSearchJobsUri(getContext()), null);
            getContext().getContentResolver().notifyChange(
                    getMyRidesUri(getContext()), null);
            break;
        case RIDEF: // all (versions of) rides with same ref
            String version = uri.getQueryParameter("id");
            if (version != null) {
                id =  db.getWritableDatabase().update(RIDES_PATH, values,
                        REF_EQUALS + " AND _id <= ?", // all older versions
                        new String[] { uri.getLastPathSegment(), version });
                values.clear(); // flag meanwhile deleted ride for server delete
                values.put(Ride.DIRTY, Ride.FLAG_FOR_DELETE);
                int up =db.getWritableDatabase().update(RIDES_PATH, values,
                        REF_EQUALS + " AND " + Ride.DIRTY + "=" + Ride.FLAG_DELETED,
                        new String[] { uri.getLastPathSegment() });
            } else {
                id =  db.getWritableDatabase().update(RIDES_PATH, values,
                        REF_EQUALS, new String[] { uri.getLastPathSegment() });
            }
            if (values.containsKey(Ride.REF)) { // update tmp ref
                ContentValues m = new ContentValues();
                m.put(Ride.REF, values.getAsString(Ride.REF));
                db.getWritableDatabase().update("markings", m,
                    REF_EQUALS, new String[] { uri.getLastPathSegment() });
            }
            getContext().getContentResolver().notifyChange(
                    getSearchJobsUri(getContext()), null);
            getContext().getContentResolver().notifyChange(
                    getMyRidesUri(getContext()), null);
            break;
        case RIDES:
            db.invalidateCache();
            break;
        }
        return id;
    }

    private static final String ID_EQUALS = "_id=?";
    private static final String REF_EQUALS = "ref=?";

    @Override
    public int delete(Uri uri, String where, String[] args) {
        getContext().getContentResolver().notifyChange(uri, null);
        switch (route.match(uri)) {
        case RIDE:
            return db.getWritableDatabase().delete(RIDES_PATH,
                    PARENT_EQUALS, new String[] { uri.getLastPathSegment() });
        case RIDES:
            String param = uri.getQueryParameter(OLDER_THAN);
            if (param != null) {
                Log.d(TAG, "clear ride cache older than " + param);
//              db.getWritableDatabase().delete(JOBS_PATH, null, null);
                return db.getWritableDatabase().delete(RIDES_PATH,
                        OFFERS + " AND dep < ?", new String[] { param });
            } else {
                Log.d(TAG, "clear ride cache completely!");
                db.getWritableDatabase().delete(JOBS_PATH, null, null);
                db.getWritableDatabase().delete(ROUTE_MATCHES, null, null);
                return db.getWritableDatabase().delete("rides", OFFERS, null);
            }
        case MYRIDES:
            Log.d(TAG, CLEAR_MYRIDES);
            String user = "someone";
            try {
                user = PreferenceManager.getDefaultSharedPreferences(
                        getContext()).getString(USER, new String());
            } catch (Exception e) {}
            return db.getWritableDatabase().delete(RIDES_PATH, OFFERS +
                    AND_WHO_IS_ME, new String[] { user } );
        }
        return -1;
    }

    private static final String PARENT_EQUALS = "parent_id=?";
    private static final String AND_WHO_IS_ME = " AND (who = '' OR who = ?)";
    private static final String OFFERS = "rides.type >= " + Ride.OFFER;

    private static final int RIDE = 0;
    private static final int RIDEF = 1;
    private static final int RIDES = 2;
    private static final int PLACE = 3;
    private static final int PLACES = 4;
    private static final int SEARCH = 5;
    private static final int RESOLVE = 6;
    private static final int PUBLISH = 7;
    private static final int MYRIDES = 8;
    private static final int HISTORY = 9;
    private static final int SUBRIDES = 10;




    public static Uri getUri(Context ctx) {
        if (uri == null)
            uri = Uri.parse(CONTENT + ctx.getPackageName());
        return uri;
    }

    public static Uri getPlacesUri(Context ctx) {
        if (places_uri == null)
            places_uri = getUri(ctx).buildUpon()
                    .appendPath(PLACES_PATH).build();
        return places_uri;
    }

    public static Uri getPlaceUri(Context ctx, int id) {
        return getPlacesUri(ctx).buildUpon()
                .appendPath(String.valueOf(id)).build();
    }

    public static Uri getRidesUri(Context ctx) {
        if (rides_uri == null)
            rides_uri = getUri(ctx).buildUpon().appendPath(RIDES_PATH).build();
        return rides_uri;
    }

    public static Uri getRideUri(Context c, int i) {
        return getRidesUri(c).buildUpon().appendPath(String.valueOf(i)).build();
    }

    public static Uri getRideRefUri(Context c, String ref) {
        return getRidesUri(c).buildUpon().appendPath(ref).build();
    }

    public static Uri getSubRidesUri(Context ctx, int id) {
        return getRidesUri(ctx).buildUpon()
                .appendPath(String.valueOf(id))
                .appendPath(RIDES_PATH).build();
    }

    public static Uri getMyRidesUri(Context ctx) {
        if (myrides_uri == null)
            myrides_uri = getUri(ctx).buildUpon().appendPath(MYRIDES_PATH).build();
        return myrides_uri;
    }

    public static Uri getSearchJobsUri(Context ctx) {
        if (search_jobs_uri == null)
            search_jobs_uri = getUri(ctx).buildUpon()
                .appendEncodedPath(SEARCH_PATH).build();
        return search_jobs_uri;
    }

    public static Uri getResolveJobsUri(Context ctx) {
        if (resolve_jobs_uri == null)
            resolve_jobs_uri = getUri(ctx).buildUpon()
                .appendEncodedPath(RESOLVE_PATH).build();
        return resolve_jobs_uri;
    }

    public static Uri getPublishJobsUri(Context ctx) {
        if (publish_jobs_uri == null)
            publish_jobs_uri = getUri(ctx).buildUpon()
                .appendEncodedPath(PUBLISH_PATH).build();
        return publish_jobs_uri;
    }
    
}
