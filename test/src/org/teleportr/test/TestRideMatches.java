package org.teleportr.test;

import java.util.Date;

import org.teleportr.Connector;
import org.teleportr.Place;
import org.teleportr.Ride;
import org.teleportr.Ride.COLUMNS;

import android.database.Cursor;
import android.net.Uri;

public class TestRideMatches extends CrashTest {



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
                store(new Ride().type(REOCCURING) // not in search results
                        .who("Someone").from(1).to(2).dep(2000));
                store(new Ride().type(Ride.OFFER).who("Anyone")
                    .from(store(new Place(52.439716, 13.448982))) // home
                    .to(store(new Place().address("Hafenstr. 125")))
                    .dep(new Date(3000)).price(42).seats(3));
                store(new Ride().who("Riksha 5")
                    .type(Ride.OFFER).price(42)
                    .from(store(new Place().name("Slackline")))
                    .to(store(new Place(57.545375, 17.453748))) // döner
                    .dep(new Date(2000)));
                store(new Ride().type(Ride.OFFER).ref("baz").who("Anyone")
                        .from(1).to(2).dep(5000)); // too late
                store(new Ride().type(Ride.OFFER).ref("foo").who("Anyone")
                        .from(1).to(2).dep(3000).deactivate());
                return 0;
            }
        }.doSearch(new Ride().type(Ride.SEARCH)
                .from(home).to(bar).dep(today).arr(tomorrow), 0); // execute  connector

        Cursor rides = query("content://org.teleportr.test/rides"
                            + "?from_id=" + home.id + "&to_id=" + bar.id
                            + "&dep=999&arr=4000");
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

    public void testHereToAnywhere() throws Exception {
        Ride query = new Ride().type(Ride.SEARCH).from(bar);
        dummyConnector.doSearch(query, 0); // execute connector
        Connector connector = new MockConnector(ctx) {
            @Override
            public long search(Ride query) {
                store(new Ride().type(Ride.OFFER).ref("aa").who("S7")
                        .from(store(new Place().name("Slackline")))
                        .via(store(new Place().name("Whiskybar")))
                        .to(store(new Place().name("Home")))
                        .dep(new Date(2000)));
                store(new Ride().type(Ride.OFFER).ref("bb").who("U5")
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
                + "?from_id=" + bar.id);
        assertEquals("there be five ride matches", 5, rides.getCount());
        rides = getMockContentResolver()
                .query(query.toUri(), null, null, null, null);
        assertEquals("there be five ride matches", 5, rides.getCount());
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
                store(new Ride().type(REOCCURING).ref("a").marked() //.who("me")
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

    public void testDeleteCache() throws Exception {
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

}
