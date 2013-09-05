package org.teleportr.test;

import org.teleportr.Connector;
import org.teleportr.Ride;

import android.content.Context;

public abstract class MockConnector extends Connector {

    public MockConnector(Context ctx) {
        setContext(ctx);
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
