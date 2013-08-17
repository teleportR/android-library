package org.teleportr;

import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

import org.teleportr.Ride.COLUMNS;

import android.app.Service;
import android.content.ContentValues;
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
import android.util.Log;
import android.widget.Toast;

public class ConnectorService extends Service
        implements OnSharedPreferenceChangeListener {

    protected static final String TAG = "ConnectorService";
    public static final String RESOLVE = "geocode";
    public static final String SEARCH = "search";
    public static final String PUBLISH = "publish";
    private Connector fahrgemeinschaft;
    private Connector gplaces;
    private Handler worker;
    private boolean verbose;
    private Uri resolve_jobs_uri;
    HashMap<Long, Integer> retries;
    private Uri publish_jobs_uri;
    private Uri search_jobs_uri;
    private Uri rides_uri;
    private Handler main;
    protected boolean authenticating;

    @Override
    public void onCreate() {
        HandlerThread thread = new HandlerThread("worker");
        thread.start();
        worker = new Handler(thread.getLooper());
        try {
            fahrgemeinschaft = (Connector) Class.forName(
                    "de.fahrgemeinschaft.FahrgemeinschaftConnector")
                    .newInstance();
            fahrgemeinschaft.setContext(this);
            gplaces = (Connector) Class.forName(
                    "de.fahrgemeinschaft.GPlaces")
                    .newInstance();
            gplaces.setContext(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
        String uri = "content://" + ConnectorService.this.getPackageName();
        rides_uri = Uri.parse(uri + "/rides");
        retries = new HashMap<Long, Integer>();
        search_jobs_uri = Uri.parse(uri + "/jobs/search");
        resolve_jobs_uri = Uri.parse(uri + "/jobs/resolve");
        publish_jobs_uri = Uri.parse(uri + "/jobs/publish");
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        verbose = prefs.getBoolean("verbose", false);
        prefs.registerOnSharedPreferenceChangeListener(this);
        main = new Handler();
        super.onCreate();
        long older_than = System.currentTimeMillis() -
                prefs.getLong("cleanup_interval", 21 * 24 * 3600000); // 3 weeks
        if (prefs.getLong("last_cleanup", 0) < older_than) {
            getContentResolver().delete(Uri.parse(
                    "content://de.fahrgemeinschaft/rides?older_than="
                            + older_than), null, null);
            prefs.edit().putLong("last_cleanup", System.currentTimeMillis());
        }
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
        } else if (action.equals(PUBLISH)) {
            worker.postAtFrontOfQueue(publish);
        }
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key.equals("verbose")) {
            verbose = prefs.getBoolean("verbose", false);
        }
    }



    Runnable publish = new Runnable() {

        @Override
        public void run() {
            int attempt = 0;
            Cursor c = getContentResolver()
                    .query(publish_jobs_uri, null, null, null, null);
            try {
                if (c.getCount() != 0) {
                    c.moveToFirst();
                    Ride offer = new Ride(c, ConnectorService.this);
                    attempt = getRetryAttempt(c.getLong(0));
                    ContentValues values = new ContentValues();
                    String ref = null;
                    if (c.getInt(COLUMNS.DIRTY) == 1) {
                        log("publishing " + offer.getFrom().getName() + " #" + attempt);
                        ref = fahrgemeinschaft.publish(offer);
                        values.put("dirty", 0); // in sync now
                        values.put("ref", ref);
                        getContentResolver().update(rides_uri.buildUpon()
                                .appendPath(String.valueOf(c.getString(0)))
                                .build(), values, null, null);
                        log("published " + ref);
                    } else if (c.getInt(COLUMNS.DIRTY) == 2) {
                        log("deleting " + offer.getFrom().getName() + " #" + attempt);
                        if (fahrgemeinschaft.delete(offer) != null) {
                            values.put("dirty", -1); // successfully deleted
                            ref = c.getString(COLUMNS.REF);
                            getContentResolver().update(rides_uri
                                    .buildUpon().appendPath(ref)
                                    .build(), values, null, null);
                            log("deleted " + ref);
                        }
                    }
                    Toast.makeText(ConnectorService.this,
                            "upload success", Toast.LENGTH_LONG).show();
                } else log("nothing to publish");
                attempt = getRetryAttempt(-1);
                log("load myrides #" + attempt);
                fahrgemeinschaft.search(null, null, new Date(), null);
                fahrgemeinschaft.flush(-1, -2, 0, Long.MAX_VALUE); // clean all
                log("myrides updated");
            } catch (FileNotFoundException e) {
                log("auth failed.");
                if (PreferenceManager
                        .getDefaultSharedPreferences(ConnectorService.this)
                        .getBoolean("remember_password", false)) {
                    log("logging in..");
                    authenticate(PreferenceManager
                            .getDefaultSharedPreferences(ConnectorService.this)
                            .getString("password", ""));
                } else {
                    sendBroadcast(new Intent("auth"));
                    Toast.makeText(ConnectorService.this, "please login",
                            Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                log(e.getMessage());
                if (attempt < 3) {
                    long wait = (long) (Math.pow(2, attempt+1));
                    worker.postDelayed(publish, wait * 1000);
                    log(e.getClass().getName()
                            + ". Retry in " + wait + " sec..");
                } else {
                    log("Giving up after " + attempt + " retry attempts");
                }
            } finally {
                c.close();
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
                Place p = new Place((int) c.getLong(0), ConnectorService.this);
                log("resolving " + p.getName());
                try {
                    gplaces.resolvePlace(p, ConnectorService.this);
                    log("resolved " + p.getName() + ": " + p.getLat());
                } catch (Exception e) {
                    log("resolve error: " + e);
                } finally {
                    c.close();
                }
            } else {
                log("No places to resolve");
            }
        }
    };



    Runnable search = new Runnable() {

        Place from;
        Place to;
        Date dep;

        @Override
        public void run() {

            Cursor jobs = getContentResolver()
                    .query(search_jobs_uri, null, null, null, null);
            if (jobs.getCount() != 0) {
                jobs.moveToFirst();
                from = new Place(jobs.getInt(0), ConnectorService.this);
                to = new Place(jobs.getInt(1), ConnectorService.this);
                long arr = jobs.getLong(4);
                if (arr == 0 // first search - no latest_dep yet
                        || arr >= jobs.getLong(3)) // or refresh
                    dep = new Date(jobs.getLong(2)); // search dep
                else
                    dep = new Date(jobs.getLong(4)); // continue from latest_dep
                int attempt = getRetryAttempt(dep.getTime());
                Ride query = new Ride().dep(dep).from(from).to(to);
                log("searching for "
                        + new SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY)
                            .format(dep) + " #" + attempt);
                onSearch(query);
                try {
                    arr = fahrgemeinschaft.search(from, to, dep, null);
                    onSuccess(query, fahrgemeinschaft.getNumberOfRidesFound());
                    fahrgemeinschaft.flush(from.id, to.id, dep.getTime(), arr);
                    worker.post(search);
                } catch (Exception e) {
                    if (attempt < 3) {
                        long wait = (long) (Math.pow(2, attempt+1));
                        worker.postDelayed(resolve, wait * 1000);
                        worker.postDelayed(search, wait * 1001);
                        log(e.getClass().getName()
                                + ". Retry in " + wait + " sec..");
                    } else {
                        log("Giving up after " + attempt + "retry attempts");
                        onFail(query, e.getMessage());
                    }
                } finally {
                    jobs.close();
                }
            } else {
                log("no more to search.");
            }
        }
    };

    public Integer getRetryAttempt(long id) {
        if (!retries.containsKey(id) || retries.get(id) > 2) retries.put(id, 1);
        else retries.put(id, retries.get(id) + 1);
        return retries.get(id);
    }

    private Runnable notify;

    private void log(String msg) {
        Log.d(TAG, msg);
        if (verbose)
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    protected void onSearch(final Ride query) {
        notify = new Runnable() {
            
            @Override
            public void run() {
                if (searchCallback != null) {
                    searchCallback.onBackgroundSearch(query);
                }
            }
        };
        if (searchCallback != null) {
            main.post(notify);
            notify = null;
        }
    }

    protected void onSuccess(final Ride query, final int numberOfRidesFound) {
        main.post(new Runnable() {
            
            @Override
            public void run() {
                if (searchCallback != null) {
                    searchCallback.onBackgroundSuccess(query, numberOfRidesFound);
                }
            }
        });
    }

    protected void onFail(final Ride query, final String reason) {
        main.post(new Runnable() {
            
            @Override
            public void run() {
                if (searchCallback != null) {
                    searchCallback.onBackgroundFail(query, reason);
                }
            }
        });
    }


    public void searchCallback(SearchListener activity) {
        searchCallback = activity;
        if (notify != null)
            main.post(notify);
    }

    private SearchListener searchCallback;

    public interface SearchListener {
        public void onBackgroundSearch(Ride query);
        public void onBackgroundSuccess(Ride query, int numberOfRidesFound);
        public void onBackgroundFail(Ride query, String reason);
    }



    public void authenticate(final String credential) {
        worker.postAtFrontOfQueue(new Runnable() {

            @Override
            public void run() {
                log("get auth");
                try {
                    authenticating = true;
                    main.post(new Runnable() {
                        
                        @Override
                        public void run() {
                            authCallback.onAuth();
                        }
                    });
                    PreferenceManager.getDefaultSharedPreferences(
                            ConnectorService.this).edit().putString("auth", 
                                    fahrgemeinschaft.authenticate(credential))
                                    .commit();
                    log("auth success");
                    main.post(new Runnable() {
                        
                        @Override
                        public void run() {
                            if (authCallback != null)
                                authCallback.onAuthSuccess();
                        }
                    });
                    worker.post(publish);
                } catch (AuthException e) {
                    log("auth failed");
                    main.post(new Runnable() {
                        
                        @Override
                        public void run() {
                            if (authCallback != null)
                                authCallback.onAuthFail(
                                        "wrong login or password");
                        }
                    });
                } catch (FileNotFoundException e) {
                    log("auth failed");
                    main.post(new Runnable() {
                        
                        @Override
                        public void run() {
                            if (authCallback != null)
                                authCallback.onAuthFail(
                                        "wrong username or password");
                        }
                    });
                } catch (final Exception e) {
                    log("auth failed");
                    main.post(new Runnable() {
                        
                        @Override
                        public void run() {
                            if (authCallback != null)
                                authCallback.onAuthFail(e.getMessage());
                        }
                    });
                } finally {
                    authenticating = false;
                }
            }
        });
    }

    private AuthListener authCallback;

    public void authCallback(AuthListener callback) {
        authCallback = callback;
        if (authenticating)
            authCallback.onAuth();
    }

    public interface AuthListener {
        public void onAuth();
        public void onAuthSuccess();
        public void onAuthFail(String reason);
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
        log("unbinding");
        authCallback = null;
        searchCallback = null;
    }
}
