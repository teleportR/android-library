package org.teleportr;

import java.util.Date;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;

public class Ride {

	public static final int SEARCH = 42;
	public static final int OFFER = 47;

	ContentValues cv;

	public Ride() {
		cv = new ContentValues();
	}

	public Uri store(Context ctx) {
		return ctx.getContentResolver().insert(
				Uri.parse("content://" + ctx.getPackageName() + "/rides"), cv);
	}

	public Ride from(Place from) {
		return from(from.id);
	}

	public Ride from(Uri from) {
		return from(Integer.parseInt(from.getLastPathSegment()));
	}

	public Ride from(long from_id) {
		cv.put("from_id", from_id);
		return this;
	}

	public Ride to(Place to) {
		return to(to.id);
	}

	public Ride to(Uri to) {
		return to(Integer.parseInt(to.getLastPathSegment()));
	}

	public Ride to(long to_id) {
		cv.put("to_id", to_id);
		return this;
	}

	public Ride to(String to) {
		cv.put("to", to);
		return this;
	}

	public Ride dep(Date dep) {
		cv.put("dep", dep.getTime());
		return this;
	}

	public Ride arr(Date arr) {
		cv.put("arr", arr.getTime());
		return this;
	}

	public Ride who(String who) {
		cv.put("who", who);
		return this;
	}

	public Ride type(int type) {
		cv.put("type", type);
		return this;
	}

}
