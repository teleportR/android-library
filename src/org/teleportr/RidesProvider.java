package org.teleportr;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import ch.hsr.geohash.BoundingBox;
import ch.hsr.geohash.GeoHash;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.test.IsolatedContext;
import android.util.Log;

public class RidesProvider extends ContentProvider {
    private static final String TAG = "RideProvider";
    private DataBaseHelper db;
	private UriMatcher route;

    class DataBaseHelper extends SQLiteOpenHelper {
        public DataBaseHelper(Context context) {
            super(context, "teleportr.db", null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("create table places ("
                    + "'_id' integer primary key autoincrement, "
                    + "'key' text, "
                    + "'value' text, "
                    + "'lat' integer, "
                    + "'lng' integer, "
                    + "'geohash' text"
                    + ");");
            db.execSQL("CREATE UNIQUE INDEX places_idx ON places (" +
                        "key, value, geohash);");
            db.execSQL("create table rides ("
                    + "'_id' integer primary key autoincrement, "
            		+ "'type' integer, "
                    + "'from' text, "
                    + "'to' text, "
                    + "'dep' integer, "
                    + "'arr' integer, "
                    + "'who' text, "
                    + "'mode' text, "
                    + "'operator' text, "
                    + "'distance' integer, "
                    + "'price' integer, "
                    + "'parent' text, "
                    + "'expire' integer, "
                    + "'key' text);");
            db.execSQL("CREATE UNIQUE INDEX rides_idx ON rides (" +
                    "'from', 'to', 'dep', 'arr', 'who', 'mode', 'operator');");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersn, int newVersn) {
        }
    }

    @Override
    public boolean onCreate() {
        db = new DataBaseHelper(getContext());
        try {
        	setAuthority(getContext().getPackageName());
        } catch (UnsupportedOperationException e) {}
        return false;
    }
    
    public void setAuthority(String authority) {
    	route = new UriMatcher(0);
    	route.addURI(authority, "history", HISTORY);
    	route.addURI(authority, "places", PLACES);
        route.addURI(authority, "places/*", PLACE);
        route.addURI(authority, "rides", RIDES);
        route.addURI(authority, "jobs", JOBS);
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        switch (route.match(uri)) {
        case PLACES:
            db.getWritableDatabase().insert("places", null, values);
            break;
        case RIDES:
            db.getWritableDatabase().insert("rides", null, values);
            break;
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return Uri.withAppendedPath(uri, values.getAsString("key"));
    }
    
    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        try {
            String sql;
            SQLiteStatement insert;
            db.getWritableDatabase().beginTransaction();
            switch (route.match(uri)) {
            case PLACES:
                sql = "INSERT OR IGNORE into places "
                        + "(key, value, lat, lng, geohash) "
                        + "values (?, ?, ?, ?, ?);";
                insert = db.getWritableDatabase().compileStatement(sql);
                for (int i = 0; i < values.length; i++) {
                    ContentValues cv = values[i];
                    insert.bindString(1, cv.getAsString("key"));
                    insert.bindString(2, cv.getAsString("value"));
                    insert.bindLong(3, cv.getAsLong("lat"));
                    insert.bindLong(4, cv.getAsLong("lng"));
                    insert.bindString(5, cv.getAsString("geohash"));
                    insert.executeInsert();
                    Log.d(TAG, "stored " + cv.getAsString("geohash"));
                }
                break;
            case RIDES:
                sql = "INSERT OR IGNORE into rides "
                        + "('type', 'from', 'to', 'dep', 'arr', 'who'," +
                        "'mode', 'operator', 'expire', 'parent', 'key') "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";  
                insert = db.getWritableDatabase().compileStatement(sql);
                for (int i = 0; i < values.length; i++) {
                    ContentValues cv = values[i];
                    if (cv.containsKey("type")) 
                    	insert.bindString(1, cv.getAsString("type"));
                    else
                    	insert.bindLong(1, 0);
                    if (cv.containsKey("from")) 
                    	insert.bindString(2, cv.getAsString("from"));
                    else
                    	insert.bindString(2, "");
                    if (cv.containsKey("to")) 
                    	insert.bindString(3, cv.getAsString("to"));
                    else
                    	insert.bindString(3, "");
                    if (cv.containsKey("dep")) 
                    	insert.bindLong(4, cv.getAsLong("dep"));
                    else
                    	insert.bindLong(4, 0);
                    if (cv.containsKey("arr")) 
                    	insert.bindLong(5, cv.getAsLong("arr"));
                    else
                    	insert.bindLong(5, 0);
                    if (cv.containsKey("who")) 
                    	insert.bindString(6, cv.getAsString("who"));
                    else
                    	insert.bindString(6, "");
                    if (cv.containsKey("mode")) 
                    	insert.bindString(7, cv.getAsString("mode"));
                    else
                    	insert.bindString(7, "");
                    if (cv.containsKey("operator")) 
                    	insert.bindString(8, cv.getAsString("operator"));
                    else
                    	insert.bindString(8, "");
                    if (cv.containsKey("expire")) 
                    	insert.bindLong(9, cv.getAsLong("expire"));
                    else
                    	insert.bindLong(9, 0);
                    if (cv.containsKey("parent")) 
                    	insert.bindString(10, cv.getAsString("parent"));
                    else
                    	insert.bindString(10, "");
                    if (cv.containsKey("key")) 
                    	insert.bindString(11, cv.getAsString("key"));
                    else
                    	insert.bindString(11, "");
                    insert.executeInsert();  
                }
                break;
            }
                db.getWritableDatabase().setTransactionSuccessful();
                getContext().getContentResolver().notifyChange(uri, null);
        } finally {
            db.getWritableDatabase().endTransaction();
        }
        return 1;
    }

    @Override
    public Cursor query(Uri uri, String[] proj, String sel, String[] args, String ord) {
        switch (route.match(uri)) {
        case HISTORY:
            return db.getReadableDatabase().query("rides", null,
            		"type=" + Ride.SEARCH, null, null, null, null);
        case PLACE:
        	String where = "geohash='"+uri.getLastPathSegment()+"'";
        	String key = uri.getQueryParameter("key");
        	if (key != null)
        		where += " AND key='"+key+"'";
        	return db.getReadableDatabase().query("places",
        			null, where, null, null, null, null);
        case PLACES:
            return db.getReadableDatabase().rawQuery(
            		"SELECT R._id, name.value, address.value, R.\"from\" FROM 'rides' AS R " +
            				"LEFT JOIN 'places' AS name ON R.\"from\" = name.geohash AND name.key = 'name' " +
            				"LEFT JOIN 'places' AS address ON R.\"from\" = address.geohash AND address.key = 'address' " +
            				"GROUP BY R.\"from\" ORDER BY count(R.\"from\") DESC;", null);
        
        }
        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String sel, String[] args) {
        switch (route.match(uri)) {
        case JOBS:
            db.getWritableDatabase().update("rides", values, sel, null);
            break;
        }
        return 0;
    }

    @Override
    public int delete(Uri arg0, String arg1, String[] arg2) {
        // TODO Auto-generated method stub
        return 0;
    }
    
    
    
    
    private static final int HISTORY = 0;
    private static final int PLACES = 1;
    private static final int PLACE = 2;
    private static final int RIDES = 3;
    private static final int JOBS = 4;

    
    
}
