package org.teleportr.test;

import java.util.Date;

import org.teleportr.Connector;
import org.teleportr.Place;
import org.teleportr.Ride;

import android.content.Context;

public abstract class MockConnector extends Connector {

    public MockConnector(Context ctx) {
        setContext(ctx);
    }

    public abstract void mockResults();

    @Override
    public long search(Place from, Place to, Date d, Date a) throws Exception {
        mockResults();
        flush(from.id, to.id);
        return 0;
    }


    @Override
    public String publish(Ride offer) throws Exception {
        return null;
    }

    @Override
    public String delete(Ride offer) throws Exception {
        return null;
    }

}
