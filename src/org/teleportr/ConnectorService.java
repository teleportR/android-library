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
    private Handler manager;
    private Connector connector;

    @Override
    public void onCreate() {
        // connector = new
        HandlerThread worker = new HandlerThread("worker");
        worker.start();
        manager = new Handler(worker.getLooper());
        try {
            connector = (Connector) Class.forName(
                    "de.fahrgemeinschaft.FahrgemeinschaftConnector")
                    .newInstance();
        } catch (InstantiationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        manager.postAtFrontOfQueue(doSomeWork);
        return START_STICKY;
    }

    Runnable doSomeWork = new Runnable() {

        @Override
        public void run() {
            Log.d(TAG, "working..");
            // Toast.makeText(ConnectorService.this, "working hard!",
            // 1500).show();

            Cursor job = getContentResolver().query(
                    Uri.parse("content://" + getPackageName() + "/history"),
                    null, null, null, null);
            if (job.getCount() != 0) {
                job.moveToFirst();
                Place orig = new Place(job.getString(2));
                Place dest = null;
                String dest_geohash = job.getString(3);
                if (!dest_geohash.equals(""))
                    dest = new Place(dest_geohash);
                Date dep = new Date(job.getLong(4));
                Date arr = new Date(job.getLong(5));
                Log.d(TAG, "  ..on " + dep);

                connector.getRides(orig, dest, dep, arr);

                // ContentValues values = new ContentValues();
                // values.put("expire", System.currentTimeMillis() + 42000);
                // getContentResolver().update(Uri.parse("content://" +
                // getPackageName() + "/history"),
                // values, "_id="+job.getInt(0), null);

                Log.d(TAG, " work done.");
                // manager.post(doSomeWork);
            } else {
                Log.d(TAG, "Nothing to do.");
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
