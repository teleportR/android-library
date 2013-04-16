package org.teleportr.test;


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
		
		// search dummy #1
		home = new Place(52.439716, 13.448982)
			.name("Home")
			.address("Hipperstr. 42");
			
		schönhauser = new Place(52.549375, 13.413748)
			.name("Cafe Schön")
			.set("4sq-id", "42");
		
		new Ride()
			.type(Ride.SEARCH)
			.from(home)
			.to(schönhauser);
		
		// search dummy #2
		bar = new Place(52.4345, 13.44234232)
			.name("Whiskybar")
			.address("Weserstr. 125");
				
		döner = new Place(57.545375, 17.453748)
			.name("Moustafa");
				
		new Ride()
			.type(Ride.SEARCH)
			.from(home)
			.to(bar);
		
		// search dummy #3
		park = new Place(53.4324245, 12.443534572)
			.name("Slackline");
				
		new Ride()
			.type(Ride.SEARCH)
			.from(park)
			.to(home);
		
		Ride.saveAll(context);
	}

	public void testSearchHistory() {
		Cursor history = getMockContentResolver().query(
				Uri.parse("content://org.teleportr.test/history"),
				null, null, null, null);
		
    	assertEquals("there be dummy search history", 3, history.getCount());    	
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
    	assertEquals("Home is most often used","Home", places.getString(1));
    	assertEquals("Address","Hipperstr. 42", places.getString(2));
    	
    }
	
    public void testBackgroundJobs() {
    	assertEquals("something must be", "sth", "sth");
    }
}
