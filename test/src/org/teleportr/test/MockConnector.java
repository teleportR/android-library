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
        flush(from != null? from.id: -1 , to != null? to.id : -2,
                d != null? d.getTime() : 0, a != null? a.getTime() : 0);
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
