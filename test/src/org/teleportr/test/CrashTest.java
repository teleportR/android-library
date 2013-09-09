package org.teleportr.test;

import java.util.Date;
import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;
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
    private Date today;
    private Date tomorrow;

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
        today = new Date(1000);
        tomorrow = new Date(2000);
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

        dummyConnector = new MockConnector(ctx) { // dummy search results
            @Override
            public long search(Ride query) {
                store(new Ride().type(Ride.OFFER).who("anyone").ref("a")
                        .from(store(new Place().name("Home"))).dep(1000)
                        .to(store(new Place().name("Slackline"))));
                store(new Ride().type(Ride.OFFER).who("someone").ref("b")
                        .from(store(new Place().address("Hipperstr. 42")))
                        .to(store(new Place().address("Wiesn"))).dep(2000));
                store(new Ride().type(Ride.OFFER).who("someone").ref("c")
                        .from(store(new Place().address("Hipperstr. 42")))
                        .to(store(new Place().address("Wiesn"))).dep(3000));
                return tomorrow.getTime();
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

    public void testSortedAsFrom() throws Exception {
        dummyConnector.doSearch(new Ride().type(Ride.OFFER)
                .from(cafe).to(bar).dep(today).arr(tomorrow), 0);
        Cursor places = query("content://org.teleportr.test/places");
        assertEquals("there should be all places", 5, places.getCount());
        // should be ordered by how often a place was used as 'from' in a search
        places.moveToFirst();
        assertEquals("first", "Home", places.getString(2));
        assertEquals("Hipperstr. 42", places.getString(3));
        assertEquals("used four times as from", 4, places.getLong(4));
        // then alphabetically for equally often used places
        places.moveToNext();
        assertEquals("Slackline", places.getString(2));
        assertEquals("used once as from", 1, places.getLong(4));
        assertEquals("but two times as to", 2, places.getLong(6));
        places.moveToNext();
        assertEquals("Whiskybar", places.getString(2));
        places.moveToNext();
        assertEquals("Moustafa", places.getString(2));
        assertEquals("used once as from", 1, places.getLong(4));
        assertEquals("and once as to", 1, places.getLong(6));
        places.moveToLast();
        assertEquals("Cafe Schön", places.getString(2));
        assertEquals("never used as from", 0, places.getLong(4));
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
        assertEquals("used twice as to from home", 2, places.getLong(4));
        assertEquals("used twice as to", 2, places.getLong(6));
        assertEquals("and once as from", 1, places.getLong(8));
        // then alphabetically for equally often used places
        places.moveToNext();
        assertEquals("Whiskybar", places.getString(2));
        assertEquals("used once as to from home", 1, places.getLong(4));
        assertEquals("used twice as to", 2, places.getLong(6));
        assertEquals("and once as from", 1, places.getLong(8));
        places.moveToNext();
        assertEquals("Cafe Schön", places.getString(2));
        assertEquals("used once as to from home", 1, places.getLong(4));
        assertEquals("used once as to", 1, places.getLong(6));
        assertEquals("and never as from", 0, places.getLong(8));
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
        Ride search = new Ride().type(Ride.SEARCH).from(bar).to(home)
                .dep(new Date(7000)).arr(new Date(9000));
        search.store(ctx);
        assertOneJobToSearch("last search ride dep", 7000);
        // working dummy hard in background..
        reportJobsDone(7000, 8000);
        assertOneJobToSearch("still search from dep", 8000);
        // working dummy hard in background..
        reportJobsDone(7000, 9000);
        assertNoMoreJobsToSearch();
        // cache invalidates after time
        reportJobsDone(7000, 9000, System.currentTimeMillis() - 10 * 60 * 1001);
        assertOneJobToSearch("again search from dep", 7000);
        reportJobsDone(7000, 8000);
        assertOneJobToSearch("again search from dep", 8000);
        // working dummy hard in background..
        reportJobsDone(7000, 9000);
        assertNoMoreJobsToSearch();
        search.arr(new Date(9500)).store(ctx); // inc arr
        assertOneJobToSearch("inc time window: dep", 9000);
        reportJobsDone(7000, 9500);
        assertNoMoreJobsToSearch();
        search.dep(3000).arr(5000).store(ctx); // dec dep
        assertOneJobToSearch("new time window: dep", 3000);
        reportJobsDone(3000, 4000);
        assertOneJobToSearch("job dep", 4000);
        reportJobsDone(3000, 5000);
        assertNoMoreJobsToSearch();
        search.arr(8000).store(ctx); // inc arr
        assertOneJobToSearch("", 5000);
        reportJobsDone(3000, 7000);
        assertOneJobToSearch("", 7000);
//        assertNoMoreJobsToSearch(); 
//        search.arr(new Date(9700)).store(ctx); // inc arr
//        assertOneJobToSearch("", 9500);
    }

    public void assertNoMoreJobsToSearch() {
        String uri = RidesProvider.getSearchJobsUri(ctx).toString();
        Cursor jobs = query(uri);
        assertEquals("now no more jobs to do", 0, jobs.getCount());
    }

    public void assertOneJobToSearch(String msg, long searchtime) {
        String uri = RidesProvider.getSearchJobsUri(ctx).toString();
        Cursor jobs = query(uri);
        assertEquals("one job to search for " + msg, 1, jobs.getCount());
        jobs.moveToFirst();
        assertEquals("from id", bar.id, jobs.getLong(0));
        assertEquals("to id", home.id, jobs.getLong(1));
        assertEquals(msg, searchtime, jobs.getLong(3));
    }

    public void reportJobsDone(long dep, long arr) {
        reportJobsDone(dep, arr, System.currentTimeMillis());
    }

    public void reportJobsDone(long dep, long arr, long refreshtime) {
        String uri = RidesProvider.getSearchJobsUri(ctx).toString();
        ContentValues values = new ContentValues();
        values.put("from_id", bar.id);
        values.put("to_id", home.id);
        values.put("dep", dep);
        values.put("arr", arr);
        values.put("last_refresh", refreshtime);
        getMockContentResolver().insert(Uri.parse(uri), values);
    }

    public void testPublishJobs() {
        Cursor jobs = query(RidesProvider.getPublishJobsUri(ctx).toString());
        assertEquals("there be no rides to publish", 0, jobs.getCount());

        Uri ride = new Ride().from(bar).to(park).marked().dirty().store(ctx);
        jobs = query("content://org.teleportr.test/jobs/publish");
        assertEquals("now there be a ride to publish", 1, jobs.getCount());
        // dummy connector publishing hard in the background..
        jobs.moveToFirst();
        assertNotNull(jobs.getString(COLUMNS.REF)); // tmp ref
        storeServerRef(jobs.getLong(0));
        jobs = query("content://org.teleportr.test/jobs/publish");
        assertEquals("nothing to publish anymore", 0, jobs.getCount());
        // meanwhile the unpublished has been edited with still the tmp ref
        new Ride(ride, ctx).seats(2).dirty().store(ctx);
        jobs = query("content://org.teleportr.test/jobs/publish");
        assertEquals("one ride to update", 1, jobs.getCount());
        Cursor my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("there should be only one ride", 1, my_rides.getCount());
        my_rides.moveToFirst();

    }

    public void storeServerRef(long id) {
        ContentValues values = new ContentValues();
        values.put(Ride.REF, UUID.randomUUID().toString());
        values.put(Ride.DIRTY, Ride.FLAG_CLEAN);
        getMockContentResolver().update(Uri.parse(
                "content://org.teleportr.test/rides/"+id), values, null, null);
    }

    public void testResolveJobs() {
        Cursor jobs = query(RidesProvider.getResolveJobsUri(ctx).toString());
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

    public void testRideMatches() throws Exception {
        new MockConnector(ctx) {
            @Override
            public long search(Ride query) {
                store(new Ride().from(1).to(2).dep(500));
                store(new Ride() // dummy search results
                    .type(Ride.OFFER).who("Sepp")
                    .from(store(new Place().name("Home")))
                    .to(store(new Place().name("Whiskybar")))
                    .dep(new Date(1000)));
                store(new Ride().who("Anyone").price(42).seats(3)
                    .type(Ride.OFFER)
                    .from(store(new Place(52.439716, 13.448982))) // home
                    .to(store(new Place().address("Hafenstr. 125")))
                    .dep(new Date(3000)));
                store(new Ride().who("Riksha 5")
                    .type(Ride.OFFER).price(42)
                    .from(store(new Place().name("Slackline")))
                    .to(store(new Place(57.545375, 17.453748))) // döner
                    .dep(new Date(2000)));
                store(new Ride().from(1).to(2).dep(3000).ref("foo")
                     .deactivate()); // should not show up in search results
                return 0;
            }
        }.doSearch(new Ride().type(Ride.SEARCH)
                .from(home).to(bar).dep(today).arr(tomorrow), 0); // execute  connector

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
        assertEquals("price", 42, rides.getLong(COLUMNS.PRICE));
        assertEquals("seats", 3, rides.getLong(COLUMNS.SEATS));
        assertEquals(1, rides.getInt(COLUMNS.ACTIVE));
    }

    public void testSubRideMatches() throws Exception {
        Ride query = new Ride().type(Ride.SEARCH)
                .from(home).to(bar).dep(today).arr(tomorrow);
        dummyConnector.doSearch(query, 0); // execute connector
        query.from(park);
        Connector connector = new MockConnector(ctx) {
            @Override
            public long search(Ride query) {
                store(new Ride().type(Ride.OFFER).ref("a").who("S7")
                        .from(store(new Place().name("Slackline")))
                        .via(store(new Place().name("Whiskybar")))
                        .to(store(new Place().name("Home")))
                        .dep(new Date(2000)));
                store(new Ride().type(Ride.OFFER).ref("b").who("U5")
                        .from(store(new Place().name("Home")))
                        .to(store(new Place().name("Cafe Schön")))
                        .via(store(new Place().name("Cafe Schön")))
                        .via(store(new Place().name("Slackline")))
                        .dep(new Date(1000)));
                return 0;
            }
        };
        connector.doSearch(query, 0); // execute connector
        connector.doSearch(query, 0); // refresh results
        
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
        assertEquals("there be three (sub)rides", 3, rides.getCount());
        rides.moveToFirst();
        assertEquals("", rides.getString(COLUMNS.REF));
        assertEquals("Home", rides.getString(COLUMNS.FROM_NAME));
        assertEquals("Cafe Schön", rides.getString(COLUMNS.TO_NAME));
        rides.moveToNext();
        assertEquals("Cafe Schön", rides.getString(COLUMNS.FROM_NAME));
        assertEquals("Slackline", rides.getString(COLUMNS.TO_NAME));
        rides.moveToLast();
        assertEquals("Slackline", rides.getString(COLUMNS.FROM_NAME));
        assertEquals("Cafe Schön", rides.getString(COLUMNS.TO_NAME));
    }

    public void testRideFromCursor() throws Exception {
        dummyConnector.doSearch(new Ride().type(Ride.OFFER)
                .from(home).to(park).dep(today).arr(tomorrow), 0);
        Uri uri = new Ride().type(Ride.OFFER)
                .from(home).via(bar).to(park)
                .price(42).dep(1000).marked()
                .ref("foo bar").deactivate()
                .who("someone").store(ctx);
        Ride myRide = new Ride(uri, ctx); // query ride
        assertEquals(home.id, myRide.getFromId());
        assertEquals(park.id, myRide.getToId());
        assertEquals(1, myRide.getVias().size());
        assertEquals(3, myRide.getPlaces().size());
        assertEquals(bar.id, myRide.getVias().get(0).id);
        assertEquals("foo bar", myRide.getRef());
        assertEquals(42, myRide.getPrice());
        assertEquals(1000, myRide.getDep());
        assertEquals(true, myRide.isMarked());
        assertEquals(false, myRide.isActive());
        assertEquals("two subrides as objects", 2, myRide.getSubrides().size());
        assertEquals("Home", myRide.getSubrides().get(0).getFrom().getName());
        assertEquals(home.id, myRide.getSubrides().get(0).getFrom().id);
        assertEquals(bar.id, myRide.getSubrides().get(1).getFrom().id);
        assertEquals(park.id, myRide.getSubrides().get(1).getTo().id);
    }

    public void testRideEdit() throws Exception {
        dummyConnector.doSearch(new Ride().type(Ride.OFFER)
                .from(home).to(park).dep(today).arr(tomorrow), 0);
        Uri uri = new Ride().type(Ride.OFFER)
                .from(home).via(bar).to(park)
                .price(42).dep(1000).marked()
                .ref("foo bar").deactivate()
                .who("someone").store(ctx);
//        storeServerRef(Long.valueOf(uri.getLastPathSegment()));
        Ride myRide = new Ride(uri, ctx);
        myRide.removeVias();
        uri = myRide.from(home).via(cafe).via(döner).to(park).activate()
                .store(ctx);
        Cursor my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("there should be only one ride", 1, my_rides.getCount());
        my_rides.moveToFirst();
        assertEquals(1000, my_rides.getLong(COLUMNS.DEPARTURE));
        assertEquals(true, myRide.isActive());
        Cursor subrides = query("content://org.teleportr.test/rides/"
                + my_rides.getLong(0) + "/rides");
        assertEquals("with three subrides", 3, subrides.getCount());
        Cursor search_results = query("content://org.teleportr.test/rides"
                            + "?from_id=" + home.id + "&to_id=" + park.id);
        assertEquals("in search only once", 4, search_results.getCount());
        myRide = new Ride(uri, ctx);
        storeServerRef(myRide.getId());
        myRide = new Ride(uri, ctx);
        myRide = new Ride(myRide.seats(3).store(ctx), ctx);
        myRide.delete();
        my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("still there, but flagged delete", 1, my_rides.getCount());
        my_rides.moveToFirst();
        assertEquals(Ride.FLAG_FOR_DELETE, my_rides.getInt(COLUMNS.DIRTY));
    }

    public void testRideDuplicate() throws Exception {
        dummyConnector.doSearch(new Ride().type(Ride.OFFER)
                .from(home).to(park).dep(today).arr(tomorrow), 0);
        Uri uri = new Ride().type(Ride.OFFER).marked()
                .from(home).via(bar).to(park)
                .who("someone").store(ctx);
        storeServerRef(Long.valueOf(uri.getLastPathSegment()));
        Ride myRide = new Ride(uri, ctx);
        Ride anotherRide = new Ride(myRide.duplicate(), ctx);
        anotherRide = new Ride(anotherRide.marked().store(ctx), ctx);
        Cursor my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("there should be a duplicate", 2, my_rides.getCount());
        assertNotSame("new tmp ref", myRide.getRef(), anotherRide.getRef());
        anotherRide = new Ride(anotherRide.seats(3).store(ctx), ctx);
        anotherRide.delete();
        my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("one myride deleted immediatelly", 1, my_rides.getCount());
        storeServerRef(anotherRide.getId());
        my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("does not recreate deleted ride", 2, my_rides.getCount());
        my_rides.moveToFirst();
        assertEquals(Ride.FLAG_FOR_DELETE, my_rides.getInt(COLUMNS.DIRTY));
        getMockContentResolver().delete(Uri.parse( // all myrides
                "content://org.teleportr.test/myrides"), null, null);
        my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("there be no myrides anymore", 0, my_rides.getCount());
    }

    public void testRideDetails() throws JSONException {
        Uri uri = new Ride().from(bar).to(park).set("foo", "bar").store(ctx);
        Ride myRide = new Ride(uri, ctx); // query ride
        assertEquals("bar", myRide.get("foo"));
        assertEquals("bar", myRide.getDetails().getString("foo"));
        myRide.set("foo", "baz");
        myRide.set("fooo", "baaz");
        myRide.getDetails().put("another", "way");
        uri = myRide.store(ctx);
        myRide = new Ride(uri, ctx); // query ride
        assertEquals("baz", myRide.get("foo"));
        assertEquals("baaz", myRide.getDetails().getString("fooo"));
        Cursor c = query(uri.toString());
        c.moveToFirst();
        JSONObject details = Ride.getDetails(c);
        assertEquals("way", details.getString("another"));
        assertEquals("way", Ride.getDetails(c, "another"));
    }

    public void testClearCache() throws Exception {
        new MockConnector(ctx) {
            @Override
            public long search(Ride query) {
                store(new Ride().type(Ride.OFFER).ref("a").who("S7")
                        .from(store(new Place().name("Home")))
                        .to(store(new Place().name("Whiskybar"))).dep(1000));
                store(new Ride().type(Ride.OFFER).ref("b").who("M10")
                        .from(store(new Place().name("Home")))
                        .to(store(new Place().name("Whiskybar"))).dep(2000));
                store(new Ride().type(Ride.OFFER).ref("c").who("Sepp")
                        .from(store(new Place().name("Home")))
                        .to(store(new Place().name("Whiskybar"))).dep(3000));
                return 0;
            }
        }.doSearch(new Ride().type(Ride.SEARCH)
                .from(home).to(bar).dep(today).arr(tomorrow), 0);
        String uri = "content://org.teleportr.test/rides/";
        Cursor rides = query(uri + "?from_id=" + home.id + "&to_id=" + bar.id);
        assertEquals("there be three ride matches", 3, rides.getCount());

        getMockContentResolver().delete(
                Uri.parse(uri + "?older_than=2000"), null, null);
        rides = query(uri + "?from_id=" + home.id + "&to_id=" + bar.id);
        assertEquals("two rides departing after time 2", 2, rides.getCount());

        getMockContentResolver().delete(Uri.parse(uri), null, null);
        rides = query(uri + "?from_id=" + home.id + "&to_id=" + bar.id);
        assertEquals("there be all rides deleted", 0, rides.getCount());
        rides = query("content://org.teleportr.test/jobs/search");
//        assertEquals("there be no more search jobs to do", 0, rides.getCount());
    }

    public void testCleanUp() throws Exception { // rides deleted on the server
        Ride query = new Ride().type(Ride.SEARCH)
                .from(home).to(park).dep(today).arr(tomorrow);
        dummyConnector.doSearch(query, 0);
        String uri = "content://org.teleportr.test/rides/";
        Cursor rides = query(uri + "?from_id=" + home.id + "&to_id=" + park.id);
        assertEquals("there be three ride matches", 3, rides.getCount());

        new MockConnector(ctx) { // one ride has been deleted on the server
            @Override
            public long search(Ride query) { // the first ride is found again
                store(new Ride().type(Ride.OFFER).who("someone").ref("b")
                        .from(store(new Place().address("Hipperstr. 42")))
                        .to(store(new Place().address("Wiesn"))).dep(2000));
                return tomorrow.getTime();
            }
        }.doSearch(query, 0);
        rides = query(uri + "?from_id=" + home.id + "&to_id=" + park.id);
        assertEquals("ride is locally deleted too", 2, rides.getCount());
        // third ride should not be cleared since it departs after tomorrow

        new MockConnector(ctx) { // clean up myride(s)
            @Override
            public long search(Ride query) {
                store(new Ride().type(Ride.OFFER).ref("a").marked() //.who("me")
                        .from(store(new Place().name("Home"))).dep(1000)
                        .to(store(new Place().name("Slackline"))));
                return 0;
            }
        }.doSearch(null, 0); // null means myrides
        Cursor my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("there should be only one ride", 1, my_rides.getCount());
        rides = query(uri + "?from_id=" + home.id + "&to_id=" + park.id);
        assertEquals("search results were not cleared", 2, rides.getCount());
        new MockConnector(ctx) { // one ride has been deleted on the server
            @Override
            public long search(Ride query) {
                return 2000; } // ride has been deleted remotely
        }.doSearch(null, 0); // myrides
        my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("myride was locally deleted too", 0, my_rides.getCount());
    }
}
