package org.teleportr;

import java.util.Date;

import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

public class ConnectorService extends Service {

    protected static final String TAG = "ConnectorService";
    public static final String RESOLVE = "geocode";
    public static final String SEARCH = "search";
    private Connector fahrgemeinschaft;
    private Connector gplaces;
    private Handler manager;

    @Override
    public void onCreate() {
        // connector = new
        HandlerThread worker = new HandlerThread("worker");
        worker.start();
        manager = new Handler(worker.getLooper());
        try {
            fahrgemeinschaft = (Connector) Class.forName(
                    "de.fahrgemeinschaft.FahrgemeinschaftConnector")
                    .newInstance();
            gplaces = (Connector) Class.forName(
                    "de.fahrgemeinschaft.GPlaces")
                    .newInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction().equals(RESOLVE)) {
            manager.postAtFrontOfQueue(resolve);
        } else if (intent.getAction().equals(SEARCH)) {
            manager.postAtFrontOfQueue(search);
        }
        return START_REDELIVER_INTENT;
    }

    Runnable resolve = new Runnable() {
        
        @Override
        public void run() {
            Log.d(TAG, "resolving a place");
            
            Uri uri = Uri.parse("content://" 
                    + ConnectorService.this.getPackageName() + "/jobs/places");
            Cursor c = getContentResolver().query(uri, null, null, null, null);
            if (c.getCount() != 0) {
                c.moveToFirst();
                Place place = new Place(c.getLong(0), ConnectorService.this);
                gplaces.resolvePlace(place, ConnectorService.this);
                Log.d(TAG, " done resolving.");
            } else {
                Log.d(TAG, "No places to resolve");
            }
        }
    };

    Runnable search = new Runnable() {

        @Override
        public void run() {
            Log.d(TAG, "searching for rides..");
            Uri uri = Uri.parse("content://" 
                    + ConnectorService.this.getPackageName() + "/jobs/rides");
            
            Cursor jobs = getContentResolver()
                    .query(uri, null, null, null, null);
            if (jobs.getCount() != 0) {
                jobs.moveToFirst();
                Log.d(TAG, jobs.getCount() + " jobs to do. search from "
                        + jobs.getLong(2) + " to " + jobs.getLong(3));
                fahrgemeinschaft.search(ConnectorService.this,
                        jobs.getLong(2), jobs.getLong(3), 0, 0);

                Log.d(TAG, " done searching.");
//                manager.post(search);
            } else {
                Log.d(TAG, "Nothing to search.");
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
