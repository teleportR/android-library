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
		route.addURI(authority, "history", HISTORY);
		route.addURI(authority, "places", PLACES);
		route.addURI(authority, "places/#", PLACE);
		route.addURI(authority, "rides", RIDES);
		route.addURI(authority, "jobs", JOBS);
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
			}
			db.getWritableDatabase().setTransactionSuccessful();
		} catch (Exception e) {
			Log.e(TAG, "error " + e);
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
			Log.e(TAG, "error " + e);
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
			return db.getReadableDatabase().query("rides",
					null, "type=" + Ride.SEARCH, null, null, null, "_id DESC");
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
			if (from_id == null) {
				return db.autocompleteFrom();
			} else {
				return db.autocompleteTo(from_id);
			}
		case RIDES:
			return db.queryRides();
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
