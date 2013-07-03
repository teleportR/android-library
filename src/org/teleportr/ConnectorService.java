package org.teleportr;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

public class ConnectorService extends Service
        implements OnSharedPreferenceChangeListener {

    protected static final String TAG = "ConnectorService";
    public static final String RESOLVE = "geocode";
    public static final String SEARCH = "search";
    private Connector fahrgemeinschaft;
    private Connector gplaces;
    private Handler manager;
    private Uri resolve_jobs_uri;
    private Uri search_jobs_uri;

    @Override
    public void onCreate() {
        // connector = new
        HandlerThread worker = new HandlerThread("worker");
        worker.start();
        manager = new Handler(worker.getLooper());
        try {
            fahrgemeinschaft = (Connector) Class.forName(
                    "de.fahrgemeinschaft.FahrgemeinschaftConnector")
                    .getConstructor(Context.class).newInstance(this);
            gplaces = (Connector) Class.forName(
                    "de.fahrgemeinschaft.GPlaces")
                    .newInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }
        String uri = "content://" + ConnectorService.this.getPackageName();
        search_jobs_uri = Uri.parse(uri + "/jobs/search");
        resolve_jobs_uri = Uri.parse(uri + "/jobs/resolve");
        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(this);
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        if (action == null)
            return START_STICKY;
        if (action.equals(RESOLVE)) {
            manager.postAtFrontOfQueue(resolve);
        } else if (action.equals(SEARCH)) {
            manager.postAtFrontOfQueue(search);
        }
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key.equals("username") || key.equals("password")) {
            manager.postAtFrontOfQueue(auth);
        }
    }

    Runnable auth = new Runnable() {

        @Override
        public void run() {
            Log.d(TAG, "authenticating");
            try {
                Toast.makeText(ConnectorService.this, fahrgemeinschaft.getAuth(), 900).show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    Runnable resolve = new Runnable() {
        
        @Override
        public void run() {
            Log.d(TAG, "resolving a place");
            
            Cursor c = getContentResolver()
                    .query(resolve_jobs_uri, null, null, null, null);
            if (c.getCount() != 0) {
                c.moveToFirst();
                Place place = new Place((int) c.getLong(0), ConnectorService.this);
                gplaces.resolvePlace(place, ConnectorService.this);
                c.close();
                Log.d(TAG, " done resolving.");
            } else {
                Log.d(TAG, "No places to resolve");
            }
        }
    };

    Runnable search = new Runnable() {


        @Override
        public void run() {

            Cursor jobs = getContentResolver()
                    .query(search_jobs_uri, null, null, null, null);
            if (jobs.getCount() != 0) {
                notifyUI(SEARCH, BackgroundListener.START);
                Log.d(TAG, jobs.getCount() + " jobs to do");
                jobs.moveToFirst();
                long dep = jobs.getLong(4);
                if (dep == 0) dep = jobs.getLong(2);
                if (dep < jobs.getLong(3)) {
                    System.out.println("last_dep: " + dep);
                }
                fahrgemeinschaft.search(jobs.getInt(0), jobs.getInt(1), dep, 0);
                Log.d(TAG, " done searching.");
                jobs.close();
                manager.post(search);
            } else {
                Log.d(TAG, "Nothing to search.");
                notifyUI(SEARCH, BackgroundListener.PAUSE);
            }
        }
    };

    private void notifyUI(String what, short how) {
        if (gui != null) gui.on(what, how);
    }

    private BackgroundListener gui;

    public void register(BackgroundListener activity) {
        gui = activity;
    }

    public interface BackgroundListener {
        public static final short START = 1;
        public static final short PAUSE = 2;
        public void on(String what, short how);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new Bind();
    }

    public class Bind extends Binder {
        public ConnectorService getService() {
            return ConnectorService.this;
        }
    }
}
