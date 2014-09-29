package org.teleportr.test;

import org.teleportr.Place;
import org.teleportr.Ride;

import android.database.Cursor;

public class TestPlaces extends CrashTest {



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

    public void xtestUniqueGeoHash() {
        new Place(1) // update
            .name("Home again")
            .address("Hipperstr. 42 b")
            .latlon(52.439716, 13.448982) // same geohash again!
            .store(ctx);
    }
}
