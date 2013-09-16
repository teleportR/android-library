package org.teleportr.test;

import java.util.Date;

import org.teleportr.Connector;
import org.teleportr.Place;
import org.teleportr.Ride;
import org.teleportr.Ride.COLUMNS;
import org.teleportr.RidesProvider;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

public class RidesProviderTest extends CrashTest {



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
        Uri ride = new Ride().dep(1).from(bar).to(park).store(ctx);
        jobs = query("content://org.teleportr.test/jobs/publish");
        assertEquals("now there be no rides to publish", 0, jobs.getCount());
        Cursor my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("no myride as draft", 0, my_rides.getCount());
        ride = new Ride(ride, ctx).dep(2).marked().dirty().store(ctx);
        jobs = query("content://org.teleportr.test/jobs/publish");
        assertEquals("now there be a ride to publish", 1, jobs.getCount());
        my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("one myride as draft", 1, my_rides.getCount());
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
        my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("there should be only one ride", 1, my_rides.getCount());
        my_rides.moveToFirst();
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
