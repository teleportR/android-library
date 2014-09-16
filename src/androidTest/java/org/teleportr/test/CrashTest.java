package org.teleportr.test;

import java.util.Date;
import java.util.UUID;

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

    protected static final int REOCCURING = 53; // custom type
    public Place home;
    public Place cafe;
    public Place bar;
    public Place döner;
    public Place park;
    public Context ctx;
    public Connector dummyConnector;
    public Date today;
    public Date tomorrow;

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
                store(new Ride().type(REOCCURING).who("someone").ref("d")
                        .from(store(new Place().address("Hipperstr. 42")))
                        .to(store(new Place().address("Wiesn"))).dep(1500));
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


// helpers

    public Cursor query(String uri) {
        return getMockContentResolver()
                .query(Uri.parse(uri), null, null, null, null);
    }

    public void storeServerRef(long id) {
        ContentValues values = new ContentValues();
        values.put(Ride.REF, UUID.randomUUID().toString());
        values.put(Ride.DIRTY, Ride.FLAG_CLEAN);
        getMockContentResolver().update(Uri.parse(
                "content://org.teleportr.test/rides/"+id), values, null, null);
    }

}
