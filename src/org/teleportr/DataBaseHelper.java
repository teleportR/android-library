package org.teleportr;

import java.util.HashSet;
import java.util.Map.Entry;
import java.util.UUID;

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
    private SQLiteStatement getIdByGeohash;
    private SQLiteStatement getIdByName;
    private SQLiteStatement insertMatch;

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("create table places ("
                + "'_id' integer primary key autoincrement,"
                + " 'geohash' text, 'name' text, 'address' text,"
                + " 'lat' integer, 'lng' integer);");
        db.execSQL("CREATE UNIQUE INDEX places_idx"
                + " ON places ('geohash', 'name');");
        db.execSQL("create table place_keys ("
                + "'_id' integer primary key autoincrement,"
                + " 'place_id' integer, 'key' text, 'value' text"
                + ");");
        db.execSQL("CREATE UNIQUE INDEX place_keys_idx"
                + " ON place_keys ('place_id', 'key');");
        db.execSQL("create table rides ("
                + "'_id' integer primary key autoincrement, 'type' integer,"
                + " 'from_id' integer, 'to_id' integer,"
                + " 'dep' integer, 'arr' integer,"
                + " 'mode' text, 'operator' text, 'who' text, 'details' text,"
                + " 'distance' integer, 'price' integer, 'seats' integer,"
                + " 'parent' integer, 'expire' integer, 'ref' text"
                + "); ");
        db.execSQL("CREATE UNIQUE INDEX rides_idx"
                + " ON rides ('type', 'from_id', 'to_id',"
                + " 'dep', 'arr', 'who', 'mode', 'operator');");
        db.execSQL("create table jobs ("
                + "'_id' integer primary key autoincrement,"
                + " from_id integer, to_id integer, last_refresh integer);");
        db.execSQL("CREATE UNIQUE INDEX jobs_idx"
                + " ON jobs ('from_id', 'to_id');");
        db.execSQL("create table route_matches ("
                + "'_id' integer primary key autoincrement,"
                + " from_id integer, to_id integer,"
                + " sub_from_id integer, sub_to_id integer"
                + ");");
        db.execSQL("CREATE UNIQUE INDEX matches_idx ON route_matches"
                + " ('from_id', 'to_id', 'sub_from_id', 'sub_to_id' );");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersn, int newVersn) {
    }

    public DataBaseHelper(Context context) {
        super(context, "teleportr.db", null, 1);
        insertPlace = getWritableDatabase().compileStatement(INSERT_PLACE);
        insertPlaceKey = getWritableDatabase().compileStatement(INSERT_KEY);
        insertRide = getWritableDatabase().compileStatement(INSERT_RIDE);
        insertMatch = getWritableDatabase().compileStatement(INSERT_MATCH);
        getIdByGeohash = getWritableDatabase().compileStatement(BY_GEOHASH);
        getIdByName = getWritableDatabase().compileStatement(BY_NAME);
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
        Log.d(RidesProvider.TAG, "+ stored place " + cv.getAsString("name"));
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

    static final String INSERT_RIDE = "INSERT OR IGNORE INTO rides"
            + " ('type', 'from_id', 'to_id', 'dep', 'arr', 'mode', 'operator',"
            + "  'who', 'details', 'price', 'seats', 'expire', 'parent', 'ref')"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
    static final String INSERT_MATCH = "INSERT OR IGNORE INTO route_matches"
            + " ('from_id', 'to_id', sub_from_id, 'sub_to_id')"
            + " VALUES (?, ?, ?, ?);";

    static final String BY_GEOHASH = "SELECT _id FROM places WHERE geohash=?";
    static final String BY_NAME = "SELECT _id FROM places WHERE name=?";

    public long insertRide(long from, long to, ContentValues cv) {
        
        long from_id = getPlaceId(cv, "from");
        long to_id = getPlaceId(cv, "to");

        insertMatch.bindLong(1, from_id);
        insertMatch.bindLong(2, to_id);
        insertMatch.bindLong(3, from);
        insertMatch.bindLong(4, to);
        insertMatch.executeInsert();

        insertRide.bindLong(2, from_id);
        insertRide.bindLong(3, to_id);
        
        bind(cv, 1, "type", 0);
        bind(cv, 4, "dep", 0);
        bind(cv, 5, "arr", 0);
        bind(cv, 6, "mode", "");
        bind(cv, 7, "operator", "");
        bind(cv, 8, "who", "");
        bind(cv, 9, "details", "");
        bind(cv, 10, "price", 0);
        bind(cv, 11, "seats", 0);
        bind(cv, 12, "expire", 0);
        bind(cv, 13, "parent", 0);
        bind(cv, 14, "ref", UUID.randomUUID().toString());
        if (cv.containsKey("ref"))
            insertRide.bindString(14, cv.getAsString("ref"));
        long id = insertRide.executeInsert();
        
        long via_id = getPlaceId(cv, "via");
        if (via_id != -1) {
            // insert two subrides
            insertRide.bindLong(13, id); // parent ride
            insertRide.bindLong(2, from_id);
            insertRide.bindLong(3, via_id);
            insertRide.executeInsert();
            insertRide.bindLong(2, via_id);
            insertRide.bindLong(3, to_id);
            insertRide.executeInsert();
            // TODO calculate dep/arr times
        }
        Log.d(RidesProvider.TAG, "+ stored ride " + id
                + ": from=" + from_id + " to=" + to_id);
        return id;
    }

    private void bind(ContentValues cv, int index, String key, String defVal) {
        if (cv.containsKey(key))
            insertRide.bindString(index, cv.getAsString(key));
        else if (defVal != null)
            insertRide.bindString(index, defVal);
    }

    private void bind(ContentValues cv, int index, String key, long defVal) {
        if (cv.containsKey(key))
            insertRide.bindLong(index, cv.getAsLong(key));
        else if (defVal != 0)
            insertRide.bindLong(index, defVal);
    }

    private long getPlaceId(ContentValues cv, String namespace) {
        if (cv.containsKey(namespace + "_id")) {
            return cv.getAsLong(namespace + "_id");
        }
        if (cv.containsKey(namespace + "_geohash")) {
            getIdByGeohash.bindString(1, cv.getAsString(namespace +"_geohash"));
            return getIdByGeohash.simpleQueryForLong();
        }
        if (cv.containsKey(namespace + "_name")) {
            getIdByName.bindString(1, cv.getAsString(namespace + "_name"));
            return getIdByName.simpleQueryForLong();
        }
        if (cv.containsKey(namespace + "_address")) {
            getIdByName.bindString(1, cv.getAsString(namespace + "_address"));
            return getIdByName.simpleQueryForLong();
        }
        return -1;
    }

    static final String SELECT_FROM = "SELECT * FROM 'places'"
            + " LEFT JOIN ("
                + "SELECT count(_id) AS count, from_id FROM 'rides'"
                + " GROUP BY from_id"
            + ") AS history ON _id=history.from_id"
            + " WHERE name LIKE ? OR address LIKE ?"
            + " ORDER BY history.count DESC, name ASC;";

    public Cursor autocompleteFrom(String q) {
        return getReadableDatabase().rawQuery(SELECT_FROM, new String[] {q, q});
    }

    static final String SELECT_TO = "SELECT * FROM 'places'"
            + " LEFT JOIN ("
                + "SELECT count(_id) AS count, to_id FROM 'rides'"
                + " WHERE from_id=?" + " GROUP BY to_id"
            + ") AS history ON _id=history.to_id"
            + " WHERE _id<>? AND (name LIKE ? OR address LIKE ?)"
            + " ORDER BY history.count DESC, name ASC;";

    public Cursor autocompleteTo(String from, String q) {
        return getReadableDatabase().rawQuery(SELECT_TO,
                new String[] { from, from, q, q });
    }

    static final String SELECT_JOBS = " SELECT * FROM rides"
            + " LEFT JOIN jobs ON"
                + " rides.from_id=jobs.from_id AND rides.to_id=jobs.to_id"
            + " WHERE type=" + Ride.SEARCH
                + " AND (last_refresh IS null OR last_refresh<?)"
            + " ORDER BY rides._id DESC;";

    public Cursor queryJobs() {
        return getReadableDatabase().rawQuery(SELECT_JOBS,
                new String[] { "" + (System.currentTimeMillis() - 60*1000) });
    }

    static final String SELECT_RIDES = "SELECT"
                + " rides._id, \"from\".name, \"from\".address,"
                + " \"to\".name, \"to\".address, rides.dep, rides.arr,"
                + " rides.who, rides.details, rides.price, rides.seats,"
                + " rides.parent, rides.ref FROM 'route_matches' AS match"
            + " JOIN 'places' AS \"from\" ON rides.from_id=\"from\"._id"
            + " JOIN 'places' AS \"to\" ON rides.to_id=\"to\"._id"
            + " LEFT JOIN 'rides' ON "
                + " rides.from_id=match.from_id AND rides.to_id=match.to_id"
            + " WHERE rides.type=" + Ride.OFFER
                + " AND match.sub_from_id=?"
                + " AND match.sub_to_id=?"
            + " GROUP BY rides.ref"
            + " ORDER BY rides.dep;";

    public Cursor queryRides(String from_id, String to_id) {
        return getReadableDatabase().rawQuery(SELECT_RIDES,
                new String[] { from_id, to_id });
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
