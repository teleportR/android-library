package org.teleportr.test;

import java.util.Date;

import org.teleportr.Connector;
import org.teleportr.Place;
import org.teleportr.Ride;
import org.teleportr.Ride.COLUMNS;
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
    private Context ctx;
    private Connector dummyConnector;

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
        park = new Place()
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
        new Ride().type(Ride.SEARCH).from(bar).to(home)
                .dep(new Date(7000)).arr(new Date(9000))
                .store(ctx);

        dummyConnector = new Connector(ctx) { // dummy search results
            @Override
            public long search(Place from, Place to, Date dep, Date arr) {
                store(new Ride().type(Ride.OFFER)
                        .from(store(new Place().name("Moustafa")))
                        .to(store(new Place().name("Cafe Schön"))));
                store(new Ride().type(Ride.OFFER)
                        .from(store(new Place().name("Cafe Schön")))
                        .to(store(new Place().name("Somewhere..."))));
                return 0;
            }
        };
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
        assertEquals("42", new Place(cafe.id, ctx).get("4sq:id"));
        assertEquals(52.4397162348032, new Place(home.id, ctx).getLat());
        assertEquals("2", new Place().name("Home").store(ctx).getLastPathSegment());
    }

    public void testSortedAsFrom() {
        dummyConnector.search(cafe.id, bar.id, 0, 0); // execute
        Cursor places = query("content://org.teleportr.test/places");
        assertEquals("there should be all places", 5, places.getCount());
        // should be ordered by how often a place was used as 'from' in a search
        places.moveToFirst();
        assertEquals("first", "Home", places.getString(2));
        assertEquals("Hipperstr. 42", places.getString(3));
        assertEquals("used four times as from", 4, places.getLong(6));
        // then alphabetically for equally often used places
        places.moveToNext();
        assertEquals("Slackline", places.getString(2));
        assertEquals("used once as from", 1, places.getLong(6));
        assertEquals("but two times as to", 2, places.getLong(8));
        places.moveToNext();
        assertEquals("Whiskybar", places.getString(2));
        places.moveToNext();
        assertEquals("Moustafa", places.getString(2));
        assertEquals("used once as from", 1, places.getLong(6));
        assertEquals("and once as to", 1, places.getLong(8));
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

    public void testSortedAsTo() {
        Cursor places = query(
                "content://org.teleportr.test/places?from_id=" + home.id);
        assertEquals("there should be all other places", 4, places.getCount());
        // should be ordered by how often a place was used as 'to' from 'home'
        places.moveToFirst();
        assertEquals("first", "Slackline", places.getString(2));
        assertEquals("used twice as to from home", 2, places.getLong(6));
        assertEquals("used twice as to", 2, places.getLong(8));
        assertEquals("and once as from", 1, places.getLong(10));
        // then alphabetically for equally often used places
        places.moveToNext();
        assertEquals("Whiskybar", places.getString(2));
        assertEquals("used once as to from home", 1, places.getLong(6));
        assertEquals("used twice as to", 2, places.getLong(8));
        assertEquals("and once as from", 1, places.getLong(10));
        places.moveToNext();
        assertEquals("Cafe Schön", places.getString(2));
        assertEquals("used once as to from home", 1, places.getLong(6));
        assertEquals("used once as to", 1, places.getLong(8));
        assertEquals("and never as from", 0, places.getLong(10));
        places.moveToLast();
        assertEquals("Moustafa", places.getString(2));
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

    public void testSearchJobs() {
        String uri = "content://org.teleportr.test/jobs/search";
        Cursor jobs = query(uri);
        assertEquals("there be seven jobs to search", 7, jobs.getCount());
        jobs.moveToFirst();
        assertEquals("last search first", bar.id, jobs.getLong(0));
        assertEquals("last search first", home.id, jobs.getLong(1));
        assertEquals("dep of last search", 7000, jobs.getLong(2));
        assertEquals("arr of last search", 9000, jobs.getLong(3));
        
        // working dummy hard in background..
        ContentValues values = new ContentValues();
        values.put("from_id", bar.id);
        values.put("to_id", home.id);
        values.put("latest_dep", 8000); // smaller than arr
        values.put("last_refresh", System.currentTimeMillis());
        getMockContentResolver().insert(Uri.parse(uri), values);
        jobs = query(uri);
        assertEquals("still seven jobs to search", 7, jobs.getCount());

        values.put("latest_dep", 9000); // not smaller than arr
        values.put("last_refresh", System.currentTimeMillis() - 10 * 60 * 1001);
        getMockContentResolver().insert(Uri.parse(uri), values);
        jobs = query(uri);
        assertEquals("still seven jobs to search", 7, jobs.getCount());
        
        values.put("last_refresh", System.currentTimeMillis());
        getMockContentResolver().insert(Uri.parse(uri), values);
        jobs = query(uri);
        assertEquals("now one job less to search", 6, jobs.getCount());

        new Ride().type(Ride.SEARCH).from(bar).to(home)
            .dep(new Date(7000)).arr(new Date(9500))
            .store(ctx); // same ride arrive later
        jobs = query(uri);
        assertEquals("again seven jobs to search", 7, jobs.getCount());
        jobs.moveToFirst();
        assertEquals("latest_dep", 9000, jobs.getLong(4)); // continue..
    }

    public void testPublishJobs() {
        Cursor jobs = query("content://org.teleportr.test/jobs/publish");
        assertEquals("there be no rides to publish", 0, jobs.getCount());

        Ride myRide = new Ride().type(Ride.OFFER).from(bar).to(park).seats(3);
        myRide.store(ctx);
        jobs = query("content://org.teleportr.test/jobs/publish");
        assertEquals("now there be a ride to publish", 1, jobs.getCount());
        // dummy connector publishing hard in the background..
        ContentValues values = new ContentValues();
        values.put("dirty", 0); // in sync now
        getMockContentResolver().update(Uri.parse(
                "content://org.teleportr.test/rides/"), values, null, null);
        jobs = query("content://org.teleportr.test/jobs/publish");
        assertEquals("nothing to publish anymore", 0, jobs.getCount());

        myRide.seats(2);
        jobs = query("content://org.teleportr.test/jobs/publish");
        assertEquals("one ride to update", 1, jobs.getCount());
    }

    public void testResolveJobs() {
        Cursor jobs = query("content://org.teleportr.test/jobs/resolve");
        assertEquals("there be one place to resolve", 1, jobs.getCount());
        // dummy working hard in background..
        jobs.moveToFirst();
        ContentValues values = new ContentValues();
        values.put("geohash", "abc");
        getMockContentResolver().update(Uri.parse(
                "content://org.teleportr.test/places/1"), values, null, null);
        jobs = query("content://org.teleportr.test/jobs/resolve");
        assertEquals("now no places to resolve any more", 0, jobs.getCount());
    }
    
    public void testRideMatches() {
        dummyConnector.search(cafe.id, bar.id, 0, 0); // execute connector
        new Connector(ctx) {
            @Override
            public long search(Place from, Place to, Date dep, Date arr) {
                store(new Ride().from(1).to(2).dep(500));
                store(new Ride() // dummy search results
                    .type(Ride.OFFER)
                    .from(store(new Place().name("Home")))
                    .to(store(new Place().name("Whiskybar")))
                    .dep(new Date(1000)));
                store(new Ride().who("Anyone").details("fu").price(42).seats(3)
                    .type(Ride.OFFER)
                    .from(store(new Place(52.439716, 13.448982))) // home
                    .to(store(new Place().address("Hafenstr. 125")))
                    .dep(new Date(3000)));
                store(new Ride()
                    .type(Ride.OFFER).price(42)
                    .from(store(new Place().name("Slackline")))
                    .to(store(new Place(57.545375, 17.453748))) // döner
                    .dep(new Date(2000)));
                return 0;
            }
        }.search(home.id, bar.id, 0, 0); // execute  connector

        Cursor rides = query("content://org.teleportr.test/rides"
                            + "?from_id=" + home.id + "&to_id=" + bar.id
                            + "&dep=999");
        assertEquals("there be three ride matches", 3, rides.getCount());
        rides.moveToLast(); // sorted by departure date
        assertEquals("Home", rides.getString(COLUMNS.FROM_NAME));
        assertEquals("Hipperstr. 42", rides.getString(COLUMNS.FROM_ADDRESS));
        assertEquals("Whiskybar", rides.getString(COLUMNS.TO_NAME));
        assertEquals("Hafenstr. 125", rides.getString(COLUMNS.TO_ADDRESS));
        assertEquals("departure", 3000, rides.getLong(COLUMNS.DEPARTURE));
        assertEquals("who", "Anyone", rides.getString(COLUMNS.WHO));
        assertNotSame("details", "fu", rides.getString(COLUMNS.DETAILS)); // BEWARE!
        Ride convenience = new Ride(rides, ctx);
        assertNotSame("details", "fu", convenience.getDetails());
        assertEquals("price", 42, rides.getLong(COLUMNS.PRICE));
        assertEquals("seats", 3, rides.getLong(COLUMNS.SEATS));
    }

    public void testSubRideMatches() {
        dummyConnector.search(cafe.id, bar.id, 0, 0); // execute connector
        Connector connector = new Connector(ctx) {
            @Override
            public long search(Place from, Place to, Date dep, Date arr) {
                store(new Ride().type(Ride.OFFER).ref("a").dep(new Date(2000))
                        .from(store(new Place().name("Slackline")))
                        .via(store(new Place().name("Whiskybar")))
                        .to(store(new Place().name("Home"))));
                store(new Ride().type(Ride.OFFER).ref("b").dep(new Date(1000))
                        .from(store(new Place().name("Home")))
                        .via(store(new Place().name("Cafe Schön")))
                        .via(store(new Place().name("Slackline")))
                        .to(store(new Place().name("Cafe Schön"))));
                return 0;
            }
        };
        connector.search(park.id, bar.id, 0, 0); // execute connector
        connector.search(park.id, bar.id, 0, 0);
        
        Cursor rides = query("content://org.teleportr.test/rides"
                + "?from_id=" + park.id + "&to_id=" + bar.id);
        assertEquals("there be two ride matches", 2, rides.getCount());
        rides.moveToLast();
        assertEquals("Slackline", rides.getString(COLUMNS.FROM_NAME));
        assertEquals("Home", rides.getString(COLUMNS.TO_NAME));
        rides.moveToFirst();
        assertEquals("Home", rides.getString(COLUMNS.FROM_NAME));
        assertEquals("Cafe Schön", rides.getString(COLUMNS.TO_NAME));
        rides = query("content://org.teleportr.test"
                + "/rides/" + rides.getLong(0) + "/rides");
        assertEquals("there be four (sub)rides", 3, rides.getCount());
        rides.moveToFirst();
        assertEquals("Home", rides.getString(COLUMNS.FROM_NAME));
        assertEquals("Cafe Schön", rides.getString(COLUMNS.TO_NAME));
        rides.moveToNext();
        assertEquals("Cafe Schön", rides.getString(COLUMNS.FROM_NAME));
        assertEquals("Slackline", rides.getString(COLUMNS.TO_NAME));
        rides.moveToLast();
        assertEquals("Slackline", rides.getString(COLUMNS.FROM_NAME));
        assertEquals("Cafe Schön", rides.getString(COLUMNS.TO_NAME));
    }

    public void testRideEdit() {
        Uri uri = new Ride().type(Ride.OFFER)
                .from(home).via(bar).to(park)
                .price(42).details("foo bar")
                .dep(100).marked().store(ctx); // 'm' is some unique user id
        Cursor cursor = query(uri.toString());
        cursor.moveToFirst();
        Ride myRide = new Ride(cursor, ctx); // query ride
        assertEquals("foo bar", myRide.getDetails());
        assertEquals(home.id, myRide.getFromId());
        assertEquals(park.id, myRide.getToId());
        assertEquals(1, myRide.getVias().size());
        assertEquals(3, myRide.getPlaces().size());
        assertEquals(bar.id, myRide.getVias().get(0).id);
        assertEquals(42, myRide.getPrice());
        assertEquals(100, myRide.getDep());
        
        assertEquals("two subrides as objects", 2, myRide.getSubrides().size());
        assertEquals("Home", myRide.getSubrides().get(0).getFrom().getName());
        assertEquals(home.id, myRide.getSubrides().get(0).getFrom().id);
        assertEquals(bar.id, myRide.getSubrides().get(1).getFrom().id);
        assertEquals(park.id, myRide.getSubrides().get(1).getTo().id);
        
        // edit and update Ride
        myRide.removeVias();
        myRide.dep(200).from(home).via(cafe).via(döner).to(park).store(ctx);

        Cursor my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("there should be only one ride", 1, my_rides.getCount());
        my_rides.moveToFirst();
        Cursor subrides = query("content://org.teleportr.test/rides/"
                + my_rides.getLong(0) + "/rides");
        assertEquals("with two subrides", 3, subrides.getCount());
    }

    public void testRideProperties() {
        Uri uri = new Ride().from(bar).to(park).set("foo", "bar").store(ctx);
        Cursor cursor = query(uri.toString());
        cursor.moveToFirst();
        Ride myRide = new Ride(cursor, ctx); // query ride
        assertEquals("bar", myRide.get("foo"));
        myRide.set("foo", "baz").store(ctx);
        myRide.set("fooo", "baaz").store(ctx);
        myRide.details("some comment").store(ctx);
        cursor = query(uri.toString());
        cursor.moveToFirst();
        System.out.println(cursor.getString(COLUMNS.DETAILS));
        myRide = new Ride(cursor, ctx); // query ride
        assertEquals("baz", myRide.get("foo"));
        assertEquals("baaz", myRide.get("fooo"));
        assertEquals("some comment", myRide.getDetails());
    }

    public void testClearCache() {
        new Connector(ctx) {
            @Override
            public long search(Place from, Place to, Date dep, Date arr) {
                store(new Ride().from(home).to(bar).dep(1000)); // ms
                store(new Ride().from(home).to(bar).dep(2000));
                store(new Ride().from(home).to(bar).dep(3000));
                return 0;
            }
        }.search(home.id, bar.id, 0, 0); // execute
        String uri = "content://org.teleportr.test/rides/";
        Cursor rides = query(uri + "?from_id=" + home.id + "&to_id=" + bar.id);
        assertEquals("there be three ride matches", 3, rides.getCount());

        getMockContentResolver().delete(
                Uri.parse(uri + "?older_than=2000"), null, null);
        Cursor left = query(uri + "?from_id=" + home.id + "&to_id=" + bar.id);
        assertEquals("two rides departing after time 2", 2, left.getCount());

        getMockContentResolver().delete(Uri.parse(uri), null, null);
        rides = query(uri + "?from_id=" + home.id + "&to_id=" + bar.id);
        assertEquals("there be all rides deleted", 0, rides.getCount());
    }
}
