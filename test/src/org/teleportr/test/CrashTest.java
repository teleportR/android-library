package org.teleportr.test;

import java.util.Date;

import org.teleportr.Place;
import org.teleportr.Ride;
import org.teleportr.RidesProvider;

import android.content.ContentResolver;
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
        park = new Place(53.4324245, 12.443534572).name("Slackline");
        park.store(ctx);

        home = new Place(52.439716, 13.448982)
            .name("Home").address("Hipperstr. 42");
        home.store(ctx);

        cafe = new Place(52.549375, 13.413748)
            .name("Cafe Schön").set("4sq:id", "42");
        cafe.store(ctx);

        bar = new Place(52.4345, 13.44234232)
            .name("Whiskybar").address("Weserstr. 125");
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

    public void testSearchHistory() {
        Cursor history = getMockContentResolver().query(
                Uri.parse("content://org.teleportr.test/history"),
                null, null, null, null);
        assertEquals("there be dummy search history", 7, history.getCount());
    }

    public void testPlace() {
        Cursor place = getMockContentResolver().query(
                Uri.parse("content://org.teleportr.test/places/4"),
                null, null, null, null);
        assertEquals("there be a stored whiskybar", 1, place.getCount());
        place.moveToFirst();
        assertEquals("Whiskybar", place.getString(2));
        assertEquals("Weserstr. 125", place.getString(3));
    }

    public void testPlaceKeys() {
        assertEquals("42", new Place(3).get("4sq:id", ctx));
    }

    public void testAutocompleteFrom() {
        Cursor places = getMockContentResolver().query(
                Uri.parse("content://org.teleportr.test/places"),
                null, null, null, null);
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

    public void testAutocompleteTo() {
        Cursor places = getMockContentResolver().query(
                Uri.parse("content://org.teleportr.test/places" +
                        "?from_id=" + home.id), null, null, null, null);
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

    public void testRides() {
        // dummy search results
        new Ride().type(Ride.OFFER).from(home).to(bar).dep(new Date(1000))
                .store(ctx);
        new Ride().type(Ride.OFFER).from(home).to(bar).dep(new Date(2000))
                .store(ctx);
        new Ride().type(Ride.OFFER).from(park).to(döner).dep(new Date(3000))
                .store(ctx);
        Cursor rides = getMockContentResolver().query(
                Uri.parse("content://org.teleportr.test/rides"),
                null, null, null, null);
        assertEquals("there be three dummy rides", 3, rides.getCount());
        rides.moveToFirst();
        assertEquals("from name", "Home", rides.getString(1));
        assertEquals("from address", "Hipperstr. 42", rides.getString(2));
        assertEquals("to_name", "Whiskybar", rides.getString(3));
        assertEquals("to_adress", "Weserstr. 125", rides.getString(4));
        assertEquals("departure", 1000, rides.getLong(5));
    }
}
