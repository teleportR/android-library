package org.teleportr;

import java.io.FileNotFoundException;
import java.util.Date;
import java.util.HashMap;

import org.json.JSONException;
import org.teleportr.Ride.COLUMNS;

import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.widget.Toast;

public class ConnectorService extends Service
        implements OnSharedPreferenceChangeListener {

    private static final String GPLACES =
            "de.fahrgemeinschaft.GPlaces";
    private static final String FAHRGEMEINSCHAFT =
            "de.fahrgemeinschaft.FahrgemeinschaftConnector";
    private static final String WORKER = "worker";
    protected static final String TAG = "ConnectorService";
    public static final String SEARCH = "search";
    public static final String RESOLVE = "resolve";
    public static final String PUBLISH = "publish";
    public static final String MYRIDES = "myrides";
    public static final String UPGRADE = "upgrade";
    public static final String AUTH = "auth";
    private Connector fahrgemeinschaft;
    private Connector gplaces;
    private Handler worker;
    HashMap<Long, Integer> retries;
    private Handler reporter;
    protected boolean authenticating;
    public Search search;
    public Resolve resolve;
    public Publish publish;
    public Myrides myrides;

    @Override
    public void onCreate() {
        HandlerThread thread = new HandlerThread(WORKER);
        thread.start();
        worker = new Handler(thread.getLooper());
        reporter = new Handler();
        try {
            fahrgemeinschaft = (Connector) Class.forName(
                    FAHRGEMEINSCHAFT).newInstance();
            fahrgemeinschaft.setContext(this);
            gplaces = (Connector) Class.forName(
                    GPLACES).newInstance();
            gplaces.setContext(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
        search = new Search(getContext());
        resolve = new Resolve(getContext());
        publish = new Publish(getContext());
        myrides = new Myrides(getContext());
        retries = new HashMap<Long, Integer>();
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
        onSharedPreferenceChanged(prefs, "verbose");
        cleanUp(prefs);
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
            worker.postAtFrontOfQueue(resolve);
            worker.postAtFrontOfQueue(search);
        } else if (action.equals(PUBLISH)) {
            worker.postAtFrontOfQueue(publish);
        } else if (action.equals(MYRIDES)) {
            worker.postAtFrontOfQueue(myrides);
        }
        return START_REDELIVER_INTENT;
    }



    public class Myrides extends Job<String> {

        public Myrides(Context ctx) {
            super(ctx, worker, reporter, null);
        }

        @Override
        void work(Cursor job) throws Exception {
            try {
                progress(MYRIDES, 0);
                fahrgemeinschaft.doSearch(null, 0);
                success(MYRIDES, 0);
            } catch (FileNotFoundException e) {
                fail(MYRIDES, "login");
            }
        }
    };

    public class Resolve extends Job<Place> {

        public Resolve(Context ctx) {
            super(ctx, worker, reporter,
                    RidesProvider.getResolveJobsUri(getContext()));
        }

        @Override
        void work(Cursor job) throws Exception {
            Place p = new Place((int) job.getLong(0), getContext());
            progress(p, 0);
            gplaces.resolvePlace(p, getContext());
            success(p, 0);
            log("resolved " + p.getName() + ": " + p.getLat());
        }
    };


    public class Search extends Job<Ride> {


        public Search(Context ctx) {
            super(ctx, worker, reporter,
                    RidesProvider.getSearchJobsUri(getContext()));
        }

        @Override
        void work(Cursor job) throws Exception {
            Ride query = new Ride(getContext())
                .from(job.getInt(0)).to(job.getInt(1))
                .dep(new Date(job.getLong(3)));
            try {
                progress(query, 0);
                fahrgemeinschaft.doSearch(query, job.getLong(2));
                success(query, fahrgemeinschaft.getNumberOfRidesFound());
                worker.post(this);
            } catch (AuthException e) { // APIKEY invalid?
                sendBroadcast(new Intent(UPGRADE));
            } catch (FileNotFoundException e) { // APIKEY invalid?
                sendBroadcast(new Intent(UPGRADE));
            }
        }
    };


    public class Publish extends Job<String> {

        public Publish(Context ctx) {
            super(ctx, worker, reporter,
                    RidesProvider.getPublishJobsUri(getContext()));
        }

        @Override
        void work(Cursor job) throws Exception {
            String ref = null;
            ContentValues values = new ContentValues();
            Ride offer = new Ride(job, getContext());
            try {
                if (job.getInt(COLUMNS.DIRTY) == 1) {
                    progress(offer.toString(), 1);
                    ref = fahrgemeinschaft.publish(offer);
                    values.put("dirty", 0); // in sync now
                    values.put("ref", ref);
                    getContentResolver().update(RidesProvider
                            .getRidesUri(getContext()).buildUpon()
                            .appendPath(String.valueOf(job.getString(0)))
                            .build(), values, null, null);
                    success(offer.toString(), 1);
                } else if (job.getInt(COLUMNS.DIRTY) == 2) {
                    progress(offer.toString(), 2);
                    if (fahrgemeinschaft.delete(offer) != null) {
                        values.put("dirty", -1); // successfully deleted
                        ref = job.getString(COLUMNS.REF);
                        getContentResolver().update(RidesProvider
                                .getRidesUri(getContext())
                                .buildUpon().appendPath(ref)
                                .build(), values, null, null);
                        success(offer.toString(), 2);
                    }
                }
            } catch (FileNotFoundException e) {
                handleAuth();
            } catch (JSONException e) {
                handleAuth();
            }
        }

        public void handleAuth() {
            if (PreferenceManager
                    .getDefaultSharedPreferences(getContext())
                    .getBoolean("remember_password", false)) {
                log("logging in automatically..");
                authenticate(PreferenceManager
                        .getDefaultSharedPreferences(getContext())
                        .getString("password", ""));
            } else {
                if (!authenticating) {
                    sendBroadcast(new Intent("auth"));
                    Toast.makeText(getContext(), "please login",
                            Toast.LENGTH_LONG).show();
                    fail(PUBLISH, "login required");
                }
            }
        }

        @Override
        protected void success(String what, int number) {
            if (what != null) {
                worker.post(this);
            } else {
                worker.post(myrides);
            }
            super.success(what, number);
        }
    };


    public void authenticate(final String credential) {
        worker.postAtFrontOfQueue(
                new Job<String>(getContext(), worker, reporter, null) {

            @Override
            void work(Cursor job) throws Exception {
                authenticating = true;
                progress(AUTH, 0);
                try {
                    final String a = fahrgemeinschaft.authenticate(credential);
                    PreferenceManager.getDefaultSharedPreferences(
                            getContext()).edit()
                            .putString(AUTH, a).commit();
                    success(AUTH, 0);
                    worker.post(publish);
                } catch (AuthException e) {
                    fail(AUTH, "wrong login or password");
                } catch (FileNotFoundException e) {
                    fail(AUTH, "wrong email or password");
                } finally {
                    authenticating = false;
                }
            }
        }.register(authCallback).setVerbose(PreferenceManager
                .getDefaultSharedPreferences(getContext())
                .getBoolean("verbose", false)));
    }

    private Context getContext() {
        return ConnectorService.this;
    }



    public ServiceCallback<String> authCallback;

    public interface ServiceCallback<T> {
        public void onFail(T what, String reason);
        public void onSuccess(T what, int number);
        public void onProgress(T what, int how);
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

    @Override
    public void unbindService(ServiceConnection conn) {
        super.unbindService(conn);
        authCallback = null;
        search.register(null);
        publish.register(null);
        myrides.register(null);
    }



    public void cleanUp(SharedPreferences prefs) {
        long older_than = System.currentTimeMillis() -
                prefs.getLong("cleanup_interval", 21 * 24 * 3600000); // 3 weeks
        if (prefs.getLong("last_cleanup", 0) < older_than) {
            getContentResolver().delete(Uri.parse(
                    "content://de.fahrgemeinschaft/rides?older_than="
                            + older_than), null, null);
            prefs.edit()
                    .putLong("last_cleanup", System.currentTimeMillis())
                    .commit();
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key.equals("verbose")) {
            boolean verbose = prefs.getBoolean("verbose", false);
            resolve.setVerbose(verbose);
            search.setVerbose(verbose);
            publish.setVerbose(verbose);
            myrides.setVerbose(verbose);
        }
    }
}
