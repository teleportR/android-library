package org.teleportr.test;

import org.json.JSONException;
import org.json.JSONObject;
import org.teleportr.Place;
import org.teleportr.Ride;
import org.teleportr.Ride.COLUMNS;

import android.database.Cursor;
import android.net.Uri;

public class RideTest extends CrashTest {

    private Ride myRide;



    @Override
    protected void setUp() throws Exception {
        super.setUp();
        dummyConnector.doSearch(new Ride().type(Ride.OFFER)
                .from(home).to(park).dep(today).arr(tomorrow), 0);
        Uri uri = new Ride().type(Ride.OFFER)
                .from(home).via(bar).to(park)
                .price(42).dep(1000).marked()
                .ref("foo bar").deactivate()
                .who("someone").store(ctx);
        myRide = new Ride(uri, ctx); // query ride
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

    public void testRideFromCursor() throws Exception {
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

    public void testStoreEmptyRide() throws Exception {
        Uri uri = new Ride().type(Ride.OFFER).store(ctx);
        Ride ride = new Ride(uri, ctx);
        assertEquals(-3, ride.getFromId());
    }

    public void testDeleteUnpublishedRide() throws Exception {
        Cursor my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("there should be one ride", 1, my_rides.getCount());
        myRide.delete(); // without prior upload
        my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("myride is locally deleted", 0, my_rides.getCount());
        storeServerRef(myRide.getId()); // meanwhile upload succeeded
        my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("myride shows serverside delete", 1, my_rides.getCount());
        my_rides.moveToFirst();
        assertEquals(Ride.FLAG_FOR_DELETE, my_rides.getInt(COLUMNS.DIRTY));
    }

    public void testDeletePublishedRide() throws Exception {
        storeServerRef(myRide.getId());
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
        myRide.from(bar).via(cafe).via(döner).to(park).activate()
                .store(ctx);
        Cursor my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("there should be only one ride", 1, my_rides.getCount());
        my_rides.moveToFirst();
        assertEquals(bar.id, my_rides.getLong(COLUMNS.FROM_ID));
        assertEquals(1000, my_rides.getLong(COLUMNS.DEPARTURE));
        assertEquals(1, my_rides.getShort(COLUMNS.ACTIVE));
        Cursor subrides = query("content://org.teleportr.test/rides/"
                + my_rides.getLong(0) + "/rides");
        assertEquals("with three subrides", 3, subrides.getCount());
        Cursor search_results = query("content://org.teleportr.test/rides"
                            + "?from_id=" + home.id + "&to_id=" + park.id);
        assertEquals("not show up in search", 3, search_results.getCount());
    }

    public void testEditPublishedRide() throws Exception {
        storeServerRef(myRide.getId());
        refreshMyRides(myRide);
        Cursor my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("there should be only one ride", 1, my_rides.getCount());
        my_rides.moveToFirst();
        myRide = new Ride(my_rides.getInt(COLUMNS.ID), ctx);
        myRide.removeVias().from(bar).via(cafe).via(döner).to(park).activate()
                .dirty().store(ctx);
        my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("there should be only one ride", 1, my_rides.getCount());
        my_rides.moveToFirst();
        assertEquals(bar.id, my_rides.getLong(COLUMNS.FROM_ID));
        assertEquals(Ride.FLAG_FOR_UPDATE, my_rides.getShort(COLUMNS.DIRTY));
        assertEquals(1000, my_rides.getLong(COLUMNS.DEPARTURE));
        assertEquals(1, my_rides.getShort(COLUMNS.ACTIVE));
        Cursor subrides = query("content://org.teleportr.test/rides/"
                + my_rides.getLong(0) + "/rides");
        assertEquals("with three subrides", 3, subrides.getCount());
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

    public void deleteMyRides() throws Exception {
        getMockContentResolver().delete(Uri.parse( // all myrides
                "content://org.teleportr.test/myrides"), null, null);
        Cursor my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("there be no myrides anymore", 0, my_rides.getCount());
    }
}
