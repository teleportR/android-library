package org.teleportr;

import java.util.ArrayList;
import java.util.Date;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;



public class Ride {
	
	public static final int SEARCH = 42;
	public static final int OFFER = 47;

	ContentValues values;

	public static ArrayList<ContentValues> batch = new ArrayList<ContentValues>();
	
	public static void saveAll(Context context) {
		
		context.getContentResolver().bulkInsert(
				Uri.parse("content://" + context.getPackageName() + "/places"),
				(ContentValues[]) Place.batch.toArray(new ContentValues[Place.batch.size()]));
		Place.batch.clear();
        
		context.getContentResolver().bulkInsert(
				Uri.parse("content://" + context.getPackageName() + "/rides"),
        		(ContentValues[]) batch.toArray(new ContentValues[batch.size()]));
        batch.clear();
	}

	public Ride() {
		values = new ContentValues();
		batch.add(values);
	}	
	
	public static Ride store() {
		return new Ride();
	}
	
	
	public Ride from(Place from) {
		values.put("from", from.geohash);
		return this;
	}
	
	public Ride from(String from) {
		values.put("from", from);
		return this;
	}
	
	public Ride to(Place to) {
		values.put("to", to.geohash);
		return this;
	}
	
	public Ride to(String to) {
		values.put("to", to);
		return this;
	}
	
	public Ride dep(Date dep) {
		values.put("dep", dep.getTime());
		return this;
	}
	
	public Ride arr(Date arr) {
		values.put("arr", arr.getTime());
		return this;
	}
	
	public Ride who(String who) {
		values.put("who", who);
		return this;
	}

	public Ride type(int type) {
		values.put("type", type);
		return this;
	}
	
}
