package org.teleportr.test;

import org.json.JSONException;
import org.json.JSONObject;
import org.teleportr.Place;
import org.teleportr.Ride;
import org.teleportr.Ride.COLUMNS;

import android.database.Cursor;
import android.net.Uri;

public class TestRides extends CrashTest {

    private Ride myRide;
    private Ride query;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        query = new Ride().type(Ride.OFFER)
                .from(home).to(park).dep(today).arr(tomorrow);
        dummyConnector.doSearch(query, 0);
        Uri uri = new Ride().type(Ride.OFFER)
                .from(home).via(bar).to(park)
                .price(42).dep(1000).marked()
                .ref("foo bar").activate()
                .who("someone").store(ctx);
        myRide = new Ride(uri, ctx); // query ride
    }



    public void testRideFromCursor() throws Exception {
        assertEquals(12, myRide.getId());
        assertEquals(home.id, myRide.getFromId());
        assertEquals(park.id, myRide.getToId());
        assertEquals(1, myRide.getVias().size());
        assertEquals(3, myRide.getPlaces().size());
        assertEquals(bar.id, myRide.getVias().get(0).id);
        assertEquals("foo bar", myRide.getRef());
        assertEquals(42, myRide.getPrice());
        assertEquals(1000, myRide.getDep());
        assertEquals(true, myRide.isMarked());
        assertEquals(true, myRide.isActive());
        assertEquals("two subrides as objects", 2, myRide.getSubrides().size());
        assertEquals("Home", myRide.getSubrides().get(0).getFrom().getName());
        assertEquals(home.id, myRide.getSubrides().get(0).getFrom().id);
        assertEquals(bar.id, myRide.getSubrides().get(1).getFrom().id);
        assertEquals(park.id, myRide.getSubrides().get(1).getTo().id);
    }

    public void testEditRideRoute() {
        myRide.to(döner);
        assertEquals(döner.id, myRide.getTo().id);
        assertEquals(döner.id, myRide.getPlaces().get(2).id);
        assertEquals(döner.id, myRide.getSubrides().get(1).getTo().id);
        myRide.from(cafe);
        assertEquals(cafe.id, myRide.getFrom().id);
        assertEquals(cafe.id, myRide.getPlaces().get(0).id);
        assertEquals(cafe.id, myRide.getSubrides().get(0).getFrom().id);
    }

    public void testStoreEmptyRide() throws Exception {
        Uri uri = new Ride().type(Ride.OFFER).store(ctx);
        Ride ride = new Ride(uri, ctx);
        assertEquals(-3, ride.getFromId());
    }

    public void testDeleteUnpublishedRide() throws Exception {
        Cursor my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("there should be one ride", 1, my_rides.getCount());
        Cursor results = query("content://org.teleportr.test/rides"
                + "?from_id=" + home.id + "&to_id=" + park.id);
        assertEquals("in results", 4, results.getCount());
        Ride theNotYetDeletedRideInBackground = new Ride(
                Uri.parse("content://org.teleportr.test/rides/12"), ctx);
        assertEquals(12, myRide.getId());
        myRide.delete(); // without prior upload
        assertEquals(0, myRide.getId()); // TODO dirty mutation!!!
        my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("myride is locally deleted", 0, my_rides.getCount());
        results = query("content://org.teleportr.test/rides"
                + "?from_id=" + home.id + "&to_id=" + park.id);
        assertEquals("no more results", 3, results.getCount());
        storeServerRef(theNotYetDeletedRideInBackground); // meanwhile succeeds
        my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("myride shows serverside delete", 1, my_rides.getCount());
        my_rides.moveToFirst();
        assertEquals(Ride.FLAG_FOR_DELETE, my_rides.getInt(COLUMNS.DIRTY));
    }

    public void testDeletePublishedRide() throws Exception {
        storeServerRef(myRide);
        refreshMyRides(myRide);
        myRide = new Ride(myRide.getId(), ctx);
        myRide.delete();
        Cursor my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("still there, but flagged delete", 1, my_rides.getCount());
        my_rides.moveToFirst();
        assertEquals(Ride.FLAG_FOR_DELETE, my_rides.getInt(COLUMNS.DIRTY));
    }


    public void testEditUnpublishedRide() throws Exception {
        myRide.removeVias();
        Uri uri = myRide.removeVias().from(bar).via(cafe).to(home)
                .seats(3).activate().dirty().store(ctx);
        myRide = new Ride(uri, ctx);
        uri = myRide.removeVias().from(bar).via(cafe).via(döner).to(park)
                .seats(2).activate().dirty().store(ctx);
        myRide = new Ride(uri, ctx);
        Cursor my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("there should be only one ride", 1, my_rides.getCount());
        my_rides.moveToFirst();
        assertEquals(bar.id, my_rides.getLong(COLUMNS.FROM_ID));
        assertEquals(1000, my_rides.getLong(COLUMNS.DEPARTURE));
        assertEquals(1, my_rides.getShort(COLUMNS.ACTIVE));
        assertEquals(Ride.FLAG_FOR_CREATE, my_rides.getShort(COLUMNS.DIRTY));
        Cursor subrides = query("content://org.teleportr.test/rides/"
                + my_rides.getLong(0) + "/rides");
        assertEquals("with three subrides", 3, subrides.getCount());
        Cursor search_results = query("content://org.teleportr.test/rides"
                            + "?from_id=" + home.id + "&to_id=" + park.id);
        assertEquals("show up in search", 4, search_results.getCount());
        Cursor jobs = query("content://org.teleportr.test/jobs/publish");
        assertEquals("two versions to upload", 2, jobs.getCount());
        jobs.moveToFirst();
        assertEquals(2, jobs.getInt(16)); // seats
        storeServerRef(myRide);
        refreshMyRides(myRide);
        jobs = query("content://org.teleportr.test/jobs/publish");
        assertEquals("there should be no old version to upload",
                0, jobs.getCount());
    }

    public void testEditPublishedRide() throws Exception {
        Cursor search_results = query("content://org.teleportr.test/rides"
                + "?from_id=" + home.id + "&to_id=" + park.id);
        assertEquals("four search results", 4, search_results.getCount());
        storeServerRef(myRide);
        refreshMyRides(myRide);
        Cursor my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("there should be only one ride", 1, my_rides.getCount());
        my_rides.moveToFirst();
        assertEquals(0, my_rides.getShort(COLUMNS.SEATS));
        myRide = new Ride(my_rides.getInt(COLUMNS.ID), ctx);
        myRide.removeVias().from(home).via(cafe).via(döner).to(park).activate()
                .dep(1700).who("another").seats(1).dirty().store(ctx);
        my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("there should be only one ride", 1, my_rides.getCount());
        my_rides.moveToFirst();
        assertEquals(home.id, my_rides.getLong(COLUMNS.FROM_ID));
        assertEquals(Ride.FLAG_FOR_UPDATE, my_rides.getShort(COLUMNS.DIRTY));
        assertEquals(1700, my_rides.getLong(COLUMNS.DEPARTURE));
        assertEquals(1, my_rides.getShort(COLUMNS.ACTIVE));
        assertEquals(1, my_rides.getShort(COLUMNS.SEATS));
        Cursor subrides = query("content://org.teleportr.test/rides/"
                + my_rides.getLong(0) + "/rides");
        assertEquals("with three subrides", 3, subrides.getCount());
        search_results = query("content://org.teleportr.test/rides"
                + "?from_id=" + home.id + "&to_id=" + park.id);
        // TODO fix that ride shows up twice temporarily!
        assertEquals("twice in results", 5, search_results.getCount());
        dummyConnector.doSearch(query, 0);
        search_results = query("content://org.teleportr.test/rides"
                + "?from_id=" + home.id + "&to_id=" + park.id);
        // but disappear after refreshing search results.
        assertEquals("twice in results", 3, search_results.getCount());
        // all because reoccuring ride instances share the same guid ref
        // and can thus only be distinguished by their departure dates.
        myRide.removeVias().from(home).to(park).activate() // same as in rslts
                .dep(1500).who("someone").ref("b").seats(0).store(ctx);
        dummyConnector.doSearch(query, 0);
        my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("there should be only one ride", 1, my_rides.getCount());
        my_rides.moveToFirst();
//        assertEquals(1, my_rides.getShort(COLUMNS.SEATS));
    }

    public void testRideDuplicate() throws Exception {
        Ride anotherRide = new Ride(myRide.duplicate(), ctx);
        Cursor my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("there should be no duplicate yet", 1, my_rides.getCount());
        anotherRide = new Ride(anotherRide.marked().store(ctx), ctx);
        my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("there should be a duplicate", 2, my_rides.getCount());
        assertNotSame("new tmp ref", myRide.getRef(), anotherRide.getRef());
        anotherRide = new Ride(anotherRide.seats(3).store(ctx), ctx);
    }

    public void testRideDetails() throws JSONException {
        Uri uri = new Ride().from(bar).to(park).set("foo", "bar").store(ctx);
        myRide = new Ride(uri, ctx); // query ride
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

    public void testReoccuringRides() throws Exception {
        Cursor search_results = query("content://org.teleportr.test/rides"
                + "?from_id=" + home.id + "&to_id=" + park.id);
        assertEquals("there be four result", 4, search_results.getCount());
        myRide.type(REOCCURING).store(ctx);
        search_results = query("content://org.teleportr.test/rides"
                + "?from_id=" + home.id + "&to_id=" + park.id);
        assertEquals("prev version still there", 4, search_results.getCount());
    }

    public void testReoccuringRideSortedToTop() throws Exception {
        new Ride().type(REOCCURING).from(bar).to(park).marked().store(ctx);
        Cursor my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("there be two myrides", 2, my_rides.getCount());
        my_rides.moveToFirst();
        assertEquals("sort to top", REOCCURING, my_rides.getInt(COLUMNS.TYPE));
    }

    public void testDeleteMyRides() throws Exception {
        myRide.type(REOCCURING).store(ctx);
        Cursor my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("there be one myrides anymore", 1, my_rides.getCount());
        getMockContentResolver().delete(Uri.parse( // all myrides
                "content://org.teleportr.test/myrides"), null, null);
        my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("there be no myrides anymore", 0, my_rides.getCount());
    }



    public void refreshMyRides(final Ride ride) throws Exception {
        new MockConnector(ctx) {
            
            @Override
            public long search(Ride search) throws Exception {
                Ride r = new Ride().type(Ride.OFFER);
                r.from(ride.getFromId());
                for (Place via : ride.getVias())
                    r.via(via);
                r.to(ride.getToId());
                r.dep(ride.getDep());
                store(r);
                return 0;
            }
        }.doSearch(null, 0);
    }
}
