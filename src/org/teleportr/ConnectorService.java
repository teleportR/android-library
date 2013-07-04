package org.teleportr;

import java.util.Date;

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
    public static final short START = 1;
    public static final short PAUSE = 2;
    private Connector fahrgemeinschaft;
    private Connector gplaces;
    private Handler worker;
    private boolean verbose;
    private Uri resolve_jobs_uri;
    private Uri search_jobs_uri;

    @Override
    public void onCreate() {
        HandlerThread thread = new HandlerThread("worker");
        thread.start();
        worker = new Handler(thread.getLooper());
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
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        verbose = prefs.getBoolean("verbose", false);
        prefs.registerOnSharedPreferenceChangeListener(this);
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        if (action == null)
            return START_STICKY;
        if (action.equals(RESOLVE)) {
            worker.postAtFrontOfQueue(resolve);
        } else if (action.equals(SEARCH)) {
            worker.postAtFrontOfQueue(search);
        }
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key.equals("username") || key.equals("password")) {
            worker.postAtFrontOfQueue(auth);
        } else if (key.equals("verbose")) {
            verbose = prefs.getBoolean("verbose", false);
        }
    }

    Runnable auth = new Runnable() {

        @Override
        public void run() {
            log("authenticating");
            try {
                log(fahrgemeinschaft.getAuth());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    Runnable resolve = new Runnable() {
        
        @Override
        public void run() {
            
            Cursor c = getContentResolver()
                    .query(resolve_jobs_uri, null, null, null, null);
            if (c.getCount() != 0) {
                c.moveToFirst();
                log("resolving place " + c.getString(2));
                Place p = new Place((int) c.getLong(0), ConnectorService.this);
                gplaces.resolvePlace(p, ConnectorService.this);
                c.close();
                log(" done resolving " + p.getName() + ": " + p.getLat());
            } else {
                log("No places to resolve");
            }
        }
    };

    Runnable search = new Runnable() {

        private Place from;
        private Place to;
        private Date dep;

        @Override
        public void run() {

            Cursor jobs = getContentResolver()
                    .query(search_jobs_uri, null, null, null, null);
            if (jobs.getCount() != 0) {
                log(jobs.getCount() + " jobs to do..");
                jobs.moveToFirst();
                from = new Place(jobs.getInt(0), ConnectorService.this);
                to = new Place(jobs.getInt(1), ConnectorService.this);
                if (jobs.getLong(4) != 0)
                    dep = new Date(jobs.getLong(4));
                else
                    dep = new Date(jobs.getLong(2));
                jobs.close();
                notifyUI("searching from " + from.getName()
                        + " to " + to.getName() + "for " + dep, START);
                long latest_dep = fahrgemeinschaft.search(from, to, dep, null);
                fahrgemeinschaft.flush(from.id, to.id, latest_dep);
                worker.post(search);
            } else {
                log("nothing to search.");
                notifyUI("Done.", PAUSE);
            }
        }

    };

    private void log(String msg) {
        Log.d(TAG, msg);
        if (verbose)
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void notifyUI(String msg, short what) {
        if (gui != null) gui.on(msg, what);
    }

    private BackgroundListener gui;

    public void register(BackgroundListener activity) {
        gui = activity;
    }

    public interface BackgroundListener {
        public void on(String msg, short what);
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
