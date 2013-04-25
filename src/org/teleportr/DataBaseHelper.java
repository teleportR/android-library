package org.teleportr;

import java.util.HashSet;
import java.util.Map.Entry;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

class DataBaseHelper extends SQLiteOpenHelper {

    private SQLiteStatement insertPlace;
    private SQLiteStatement insertPlaceKey;
    private SQLiteStatement insertRide;

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("create table places ("
                + "'_id' integer primary key autoincrement,"
                + " 'geohash' text," + " 'name' text," + " 'address' text,"
                + " 'lat' integer," + " 'lng' integer" + ");");
        db.execSQL("CREATE UNIQUE INDEX places_idx"
                + " ON places ('geohash', 'name')" + ";");
        db.execSQL("create table place_keys ("
                + "'_id' integer primary key autoincrement,"
                + " 'place_id' integer," + " 'key' text," + " 'value' text"
                + ");");
        db.execSQL("CREATE UNIQUE INDEX place_keys_idx"
                + " ON place_keys ('place_id', 'key')" + ";");
        db.execSQL("create table rides ("
                + "'_id' integer primary key autoincrement, 'type' integer,"
                + " 'from_id' integer, 'to_id' integer,"
                + " 'dep' integer," + " 'arr' integer,"
                + " 'who' text," + " 'mode' text," + " 'operator' text,"
                + " 'distance' integer," + " 'price' integer,"
                + " 'parent' text," + " 'expire' integer," + " 'key' text"
                + "); ");
        db.execSQL("CREATE UNIQUE INDEX rides_idx"
                + " ON rides ('type', 'from_id', 'to_id',"
                + " 'dep', 'arr', 'who', 'mode', 'operator');");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersn, int newVersn) {
    }

    public DataBaseHelper(Context context) {
        super(context, "teleportr.db", null, 1);
        insertPlace = getWritableDatabase().compileStatement(INSERT_PLACE);
        insertPlaceKey = getWritableDatabase().compileStatement(INSERT_KEY);
        insertRide = getWritableDatabase().compileStatement(INSERT_RIDE);
    }

    static final String INSERT_PLACE = "INSERT OR IGNORE INTO places"
            + " ('geohash', 'name', 'address', 'lat', 'lng')"
            + " VALUES (?, ?, ?, ?, ?);";

    public long insertPlace(ContentValues cv) {
        insertPlace.bindString(1, cv.getAsString("geohash"));
        insertPlace.bindString(2, cv.getAsString("name"));
        insertPlace.bindString(3, cv.getAsString("address"));
        insertPlace.bindLong(4, cv.getAsLong("lat"));
        insertPlace.bindLong(5, cv.getAsLong("lng"));
        long place_id = insertPlace.executeInsert();
        if (place_id == -1) { // insert has been ignored as place already exists
            Cursor c = getReadableDatabase().rawQuery(
                    "SELECT _id from places" + " WHERE name=?",
                    new String[] { cv.getAsString("name") });
            c.moveToFirst();
            place_id = c.getLong(0);
        }
        if (cv.size() > 5) {
            insertPlaceKeys(cv, place_id);
        }
        Log.d(RidesProvider.TAG, "+ stored place " + cv.getAsString("geohash"));
        return place_id;
    }

    static final String INSERT_KEY = "INSERT OR IGNORE INTO place_keys"
            + " ('place_id', 'key', 'value')" + " VALUES (?, ?, ?);";

    public void insertPlaceKeys(ContentValues cv, long place_id) {
        insertPlaceKey.bindLong(1, place_id);
        for (Entry<String, Object> entry : cv.valueSet()) {
            if (!PLACE_COLUMNS.contains(entry.getKey())) {
                insertPlaceKey.bindString(2, entry.getKey());
                insertPlaceKey.bindString(3, (String) entry.getValue());
                insertPlaceKey.executeInsert();
                Log.d(RidesProvider.TAG, "+ stored key " + entry);
            }
        }
    }

    static final String INSERT_RIDE = "INSERT OR REPLACE INTO rides"
            + " ('type', 'from_id', 'to_id', 'dep', 'arr', 'who',"
            + " 'mode', 'operator', 'expire', 'parent', 'key') "
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";

    public long insertRide(ContentValues cv) {
        if (cv.containsKey("type"))
            insertRide.bindString(1, cv.getAsString("type"));
        else
            insertRide.bindLong(1, 0);
        if (cv.containsKey("from_id"))
            insertRide.bindString(2, cv.getAsString("from_id"));
        else
            insertRide.bindString(2, "");
        if (cv.containsKey("to_id"))
            insertRide.bindString(3, cv.getAsString("to_id"));
        else
            insertRide.bindString(3, "");
        if (cv.containsKey("dep"))
            insertRide.bindLong(4, cv.getAsLong("dep"));
        else
            insertRide.bindLong(4, 0);
        if (cv.containsKey("arr"))
            insertRide.bindLong(5, cv.getAsLong("arr"));
        else
            insertRide.bindLong(5, 0);
        if (cv.containsKey("who"))
            insertRide.bindString(6, cv.getAsString("who"));
        else
            insertRide.bindString(6, "");
        if (cv.containsKey("mode"))
            insertRide.bindString(7, cv.getAsString("mode"));
        else
            insertRide.bindString(7, "");
        if (cv.containsKey("operator"))
            insertRide.bindString(8, cv.getAsString("operator"));
        else
            insertRide.bindString(8, "");
        if (cv.containsKey("expire"))
            insertRide.bindLong(9, cv.getAsLong("expire"));
        else
            insertRide.bindLong(9, 0);
        if (cv.containsKey("parent"))
            insertRide.bindString(10, cv.getAsString("parent"));
        else
            insertRide.bindString(10, "");
        if (cv.containsKey("key"))
            insertRide.bindString(11, cv.getAsString("key"));
        else
            insertRide.bindString(11, "");
        long id = insertRide.executeInsert();
        Log.d(RidesProvider.TAG, "+ stored ride " + id);
        return id;
    }

    static final String SELECT_FROM = "SELECT * FROM 'places'"
            + " LEFT JOIN ("
                + "SELECT count(_id) AS count, from_id FROM 'rides'"
                + " GROUP BY from_id"
            + ") AS history ON _id=history.from_id"
            + " ORDER BY history.count DESC, name ASC;";

    public Cursor autocompleteFrom() {
        return getReadableDatabase().rawQuery(SELECT_FROM, null);
    }

    static final String SELECT_TO = "SELECT * FROM 'places'"
            + " LEFT JOIN ("
                + "SELECT count(_id) AS count, to_id FROM 'rides'"
                + " WHERE from_id=?" + " GROUP BY to_id"
            + ") AS history ON _id=history.to_id" + " WHERE _id<>?"
            + " ORDER BY history.count DESC, name ASC;";

    public Cursor autocompleteTo(String from) {
        return getReadableDatabase().rawQuery(SELECT_TO,
                new String[] { from, from });
    }

    static final String SELECT_RIDES = "SELECT"
                + " rides._id, \"from\".name, \"from\".address,"
                + " \"to\".name, \"to\".address, dep" + " FROM 'rides'"
            + " JOIN 'places' AS \"from\" ON from_id=\"from\"._id"
            + " JOIN 'places' AS \"to\" ON to_id=\"to\"._id"
            + " WHERE type=" + Ride.OFFER + " ORDER BY dep;";

    public Cursor queryRides() {
        return getReadableDatabase().rawQuery(SELECT_RIDES, new String[] {});
    }

    static final HashSet<String> PLACE_COLUMNS = new HashSet<String>(5);
    static {
        PLACE_COLUMNS.add("geohash");
        PLACE_COLUMNS.add("name");
        PLACE_COLUMNS.add("address");
        PLACE_COLUMNS.add("lat");
        PLACE_COLUMNS.add("lng");
    }
}
