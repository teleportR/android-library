package org.teleportr.test;

import java.util.Date;

import org.teleportr.Connector;
import org.teleportr.Place;
import org.teleportr.Ride;
import org.teleportr.RidesProvider;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.test.IsolatedContext;
import android.test.ProviderTestCase2;

public class CrashTest extends ProviderTestCase2<RidesProvider> {

    private Place home;
    private Place cafe;
    private Place bar;
    private Place döner;
    private Place park;
    private IsolatedContext ctx;

    public CrashTest() {
        super(RidesProvider.class, "org.teleportr.test");
        ctx = new IsolatedContext(getMockContentResolver(), getContext()) {
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
        park = new Place(53.4324245, 12.443534572)
            .name("Slackline").address("Wiesn");
        park.store(ctx);

        home = new Place(52.439716, 13.448982)
            .name("Home").address("Hipperstr. 42");
        home.store(ctx);

        cafe = new Place(52.549375, 13.413748)
            .name("Cafe Schön").set("4sq:id", "42");
        cafe.store(ctx);

        bar = new Place(52.4345, 13.44234232)
            .name("Whiskybar").address("Hafenstr. 125");
        bar.store(ctx);

        döner = new Place(57.545375, 17.453748).name("Moustafa");
        döner.store(ctx);

        // dummy rides
        new Ride().type(Ride.SEARCH).from(home).to(bar).dep(new Date(1000))
                .store(ctx);
        new Ride().type(Ride.SEARCH).from(home).to(cafe).dep(new Date(2000))
                .store(ctx);
        new Ride().type(Ride.SEARCH).from(home).to(park).dep(new Date(3000))
                .store(ctx);
        new Ride().type(Ride.SEARCH).from(home).to(park).dep(new Date(4000))
                .store(ctx);
        new Ride().type(Ride.SEARCH).from(park).to(döner).dep(new Date(5000))
                .store(ctx);
        new Ride().type(Ride.SEARCH).from(döner).to(bar).dep(new Date(6000))
                .store(ctx);
        new Ride().type(Ride.SEARCH).from(bar).to(home).dep(new Date(7000))
                .store(ctx);
    }

    // helper
    private Cursor query(String uri) {
        return getMockContentResolver()
                .query(Uri.parse(uri), null, null, null, null);
    }

    public void testSearchHistory() {
        Cursor history = query("content://org.teleportr.test/history");
        assertEquals("there be dummy search history", 7, history.getCount());
    }

    public void testPlace() {
        Cursor place = query("content://org.teleportr.test/places/4");
        assertEquals("there be a stored whiskybar", 1, place.getCount());
        place.moveToFirst();
        assertEquals("Whiskybar", place.getString(2));
        assertEquals("Hafenstr. 125", place.getString(3));
    }

    public void testPlaceKeys() {
        assertEquals("42", new Place(cafe.id).get("4sq:id", ctx));
    }

    public void testSortedFromPlaces() {
        Cursor places = query("content://org.teleportr.test/places");
        assertEquals("there should be all places", 5, places.getCount());
        // should be ordered by how often a place was used as 'from' in a search
        places.moveToFirst();
        assertEquals("first", "Home", places.getString(2));
        assertEquals("Hipperstr. 42", places.getString(3));
        assertEquals("used four times as from", 4, places.getLong(6));
        // then alphabetically for equally often used places
        places.moveToNext();
        assertEquals("Moustafa", places.getString(2));
        assertEquals("used once as from", 1, places.getLong(6));
        places.moveToNext();
        assertEquals("Slackline", places.getString(2));
        places.moveToNext();
        assertEquals("Whiskybar", places.getString(2));
        places.moveToLast();
        assertEquals("Cafe Schön", places.getString(2));
        assertEquals("never used as from", 0, places.getLong(6));
    }

    public void testAutocompleteFrom() {
        Cursor places = query("content://org.teleportr.test/places?q=h");
        assertEquals("two places starting with 'h'", 2, places.getCount());
        places.moveToFirst();
        assertEquals("Home", places.getString(2));
        places.moveToNext();
        assertEquals("Whiskybar", places.getString(2));
        assertEquals("Hafenstr. 125", places.getString(3));
    }

    public void testSortedToPlaces() {
        Cursor places = query(
                "content://org.teleportr.test/places?from_id=" + home.id);
        assertEquals("there should be all other places", 4, places.getCount());
        // should be ordered by how often a place was used as 'to' from 'home'
        places.moveToFirst();
        assertEquals("first", "Slackline", places.getString(2));
        assertEquals("used two times as to", 2, places.getLong(6));
        // then alphabetically for equally often used places
        places.moveToNext();
        assertEquals("Cafe Schön", places.getString(2));
        assertEquals("used once as to", 1, places.getLong(6));
        places.moveToNext();
        assertEquals("Whiskybar", places.getString(2));
        places.moveToLast();
        assertEquals("Moustafa", places.getString(2));
        assertEquals("never used as to", 0, places.getLong(6));
    }
    
    public void testAutocompleteTo() {
        Cursor places = query("content://org.teleportr.test/places" +
                                "?from_id=" + home.id + "&q=W");
        assertEquals("two places starting with 'W'", 2, places.getCount());
        places.moveToFirst();
        assertEquals("Wiesn", places.getString(3));
        places.moveToNext();
        assertEquals("Whiskybar", places.getString(2));
    }

    public void testBackgroundJobs() {
        Cursor jobs = query("content://org.teleportr.test/jobs");
        assertEquals("there be seven jobs to search", 7, jobs.getCount());
        
        // dummy working hard in background..
        ContentValues values = new ContentValues();
        values.put("from_id", home.id);
        values.put("to_id", bar.id);
        values.put("last_refresh", System.currentTimeMillis());
        getMockContentResolver().insert(
                Uri.parse("content://org.teleportr.test/jobs"), values);
        jobs = getMockContentResolver().query(
                Uri.parse("content://org.teleportr.test/jobs"),
                null, null, null, null);
        assertEquals("now one less job to search", 6, jobs.getCount());
    }

    public void testRideMatches() {
        Connector connector = new Connector() {
            @Override
            public void getRides(Place from, Place to, Date dep, Date arr) {
                store(new Ride() // dummy search results
                    .type(Ride.OFFER).from(home).to(bar).dep(new Date(1000)));
                store(new Ride()
                    .type(Ride.OFFER).from(home).to(bar).dep(new Date(2000)));
            }
        };
        connector.getRides(null, null, null, null); // search the dummy rides
        connector.flushBatch(ctx);

        Cursor rides = query("content://org.teleportr.test/rides"
                            + "?from_id=" + home.id + "&to_id=" + bar.id);
        assertEquals("there be two ride matches", 2, rides.getCount());
        rides.moveToLast();
        assertEquals("from name", "Home", rides.getString(1));
        assertEquals("from address", "Hipperstr. 42", rides.getString(2));
        assertEquals("to_name", "Whiskybar", rides.getString(3));
        assertEquals("to_adress", "Hafenstr. 125", rides.getString(4));
        assertEquals("departure", 2000, rides.getLong(5));
    }

    public void testSubRideMatches() {
        Connector connector = new Connector() {
            @Override
            public void getRides(Place from, Place to, Date dep, Date arr) {
                store(new Ride().type(Ride.OFFER) // dummy search results
                     .from(park).via(döner).to(bar).dep(new Date(1000)));
                store(new Ride().type(Ride.OFFER)
                    .from(home).via(park).to(döner).dep(new Date(2000)));
                store(new Ride() // should not match the query below
                    .type(Ride.OFFER).from(home).to(bar).dep(new Date(3000)));
            }
        };
        connector.getRides(null, null, null, null); // search the dummy rides
        connector.flushBatch(ctx);
        
        Cursor rides = query("content://org.teleportr.test/rides"
                + "?from_id=" + park.id + "&to_id=" + döner.id);
        assertEquals("there be two (sub)ride matches", 2, rides.getCount());
        rides.moveToFirst();
        assertEquals("from name", "Slackline", rides.getString(1));
        assertEquals("to_name", "Whiskybar", rides.getString(3));
        rides.moveToLast();
        assertEquals("from name", "Home", rides.getString(1));
        assertEquals("to_name", "Moustafa", rides.getString(3));
    }

}
