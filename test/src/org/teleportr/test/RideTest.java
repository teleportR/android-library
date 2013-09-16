package org.teleportr.test;

import org.json.JSONException;
import org.json.JSONObject;
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


    public void testDeleteUnpublishedRide() {
        Cursor my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("there should be one ride", 1, my_rides.getCount());
        myRide.delete(); // without prior publish!
        my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("there should no rides anymore", 0, my_rides.getCount());
    }


    public void testRideEdit() throws Exception {
        myRide.removeVias();
        Uri uri = myRide.from(home).via(cafe).via(döner).to(park).activate()
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
        Ride anotherRide = new Ride(myRide.duplicate(), ctx);
        Cursor my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("there should be no duplicate yet", 1, my_rides.getCount());
        anotherRide = new Ride(anotherRide.marked().store(ctx), ctx);
        my_rides = query("content://org.teleportr.test/myrides");
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
}
