package org.teleportr.test;


import java.util.Date;

import org.teleportr.Place;
import org.teleportr.Ride;
import org.teleportr.RidesProvider;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.test.IsolatedContext;
import android.test.ProviderTestCase2;
import android.test.mock.MockContext;

public class CrashTest extends ProviderTestCase2<RidesProvider> {

    private Place home;
	private Place schönhauser;
	private Place bar;
	private Place döner;
	private Place park;
	private IsolatedContext context;

	public CrashTest() {
		super(RidesProvider.class, "org.teleportr.test");
		context = new IsolatedContext(getMockContentResolver(), getContext()) {
			@Override
			public String getPackageName() {
				return "org.teleportr.test";
			}
			@Override
			public ContentResolver getContentResolver() {
				return getMockContentResolver();
			}
		};
    }


	@Override
	protected void setUp() throws Exception {
		super.setUp();

		getProvider().setAuthority("org.teleportr.test");
		
		// dummy places
		home = new Place(52.439716, 13.448982)
			.name("Home")
			.address("Hipperstr. 42");
			
		schönhauser = new Place(52.549375, 13.413748)
			.name("Cafe Schön")
			.set("4sq-id", "42");
		
		bar = new Place(52.4345, 13.44234232)
			.name("Whiskybar")
			.address("Weserstr. 125");
				
		döner = new Place(57.545375, 17.453748)
			.name("Moustafa");
				
		park = new Place(53.4324245, 12.443534572)
			.name("Slackline");
		
		// dummy rides
		new Ride()
			.type(Ride.SEARCH)
			.from(home)
			.to(schönhauser)
			.dep(new Date(124));
		
		new Ride()
			.type(Ride.SEARCH)
			.from(home)
			.to(park)
			.dep(new Date(12657));
		
		new Ride()
			.type(Ride.SEARCH)
			.from(park)
			.to(home)
			.dep(new Date(65345));
		
		new Ride()
			.type(Ride.SEARCH)
			.from(home)
			.to(park)
			.dep(new Date(4533));
		
		new Ride()
			.type(Ride.SEARCH)
			.from(home)
			.to(bar)
			.dep(new Date(543258));
		
		Ride.saveAll(context);
	}

	public void testSearchHistory() {
		Cursor history = getMockContentResolver().query(
				Uri.parse("content://org.teleportr.test/history"),
				null, null, null, null);
		
    	assertEquals("there be dummy search history", 5, history.getCount());    	
    }

	public void testPlaceKeyValues() {
		Place.context = context; // in background service
		assertEquals("Moustafa", new Place(57.545375, 17.453748).get("name"));
	}
	
	public void testAutocompletion() {
		Cursor places = getMockContentResolver().query(
				Uri.parse("content://org.teleportr.test/places"),
				null, null, null, null);
		
    	assertEquals("there be two from places", 2, places.getCount());
    	places.moveToFirst();
    	assertEquals("most often used as from","Home", places.getString(1));
    	assertEquals("Address","Hipperstr. 42", places.getString(2));
    	
    }
	
	public void testAutocomplete_to() {
		Cursor places = getMockContentResolver().query(
				Uri.parse("content://org.teleportr.test/places?from=" + home.geohash),
				null, null, null, null);
		
    	assertEquals("there be three places from home", 3, places.getCount());
    	places.moveToFirst();
    	assertEquals("most often used as to","Slackline", places.getString(1));
    	
    }
	
    public void testBackgroundJobs() {
    	assertEquals("something must be", "sth", "sth");
    }
}
