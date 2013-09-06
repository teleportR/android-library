package org.teleportr;

import java.util.HashMap;

import org.teleportr.ConnectorService.ServiceCallback;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;


@SuppressLint("UseSparseArrays")
public abstract class Job<T> implements Runnable {

    private static final String NOTHING_TO = "nothing to ";
    private static final String GIVING_UP_AFTER_3_RETRY_ATTEMPTS
            = "Giving up after 3 retry attempts";
    private static final String TAG = ConnectorService.TAG;
    private Handler worker;
    private Handler reporter;
    private Runnable fail;
    private Runnable success;
    private Runnable progress;
    private ServiceCallback<T> listener;
    private HashMap<Integer, Integer> retries;
    private Context ctx;
    private Uri query;
    private boolean verbose;
    private T what;


    public Job(Context ctx, Handler worker, Handler reporter, Uri query) {
        this.ctx = ctx;
        this.query = query;
        this.worker = worker;
        this.reporter = reporter;
        retries = new HashMap<Integer, Integer>();
    }

    /**
     * implement this
     */
    abstract void work(Cursor job) throws Exception;


    @Override
    public void run() {
        what = null;
        if (query == null) {
            try {
                work(null);
                worker.removeCallbacks(this);
            } catch (Exception e) {
                retry(what, e.getClass().getName());
                e.printStackTrace();
            }
            return;
        }
        Cursor c = ctx.getContentResolver()
                .query(query, null, null, null, null);
        try {
            if (c.getCount() > 0) {
                c.moveToFirst();
                work(c);
            } else {
                log(NOTHING_TO + query);
                success(null, 0);
                worker.removeCallbacks(this);
            }
        } catch (Exception e) {
            retry(what, e.getClass().getName());
            e.printStackTrace();
        } finally {
            c.close();
        }
    }



    protected void retry(final T what, final String reason) {
        int attempt = getRetryAttemptFor(what);
        if (attempt <= 3) {
            long wait = (long) (Math.pow(2, attempt));
            worker.postDelayed(this, wait * 1000);
            log(reason + " #" + attempt + "  Retry in " + wait + " sec..");
        } else {
            log(GIVING_UP_AFTER_3_RETRY_ATTEMPTS);
            fail(what, reason);
        }
    }

    private int getRetryAttemptFor(T what) {
        int id = what.hashCode();
        if (!retries.containsKey(id) || retries.get(id) > 3) retries.put(id, 2);
        else retries.put(id, retries.get(id) + 1);
        return retries.get(id);
    }

    protected void fail(final T what, final String reason) {
        log("fail: " + what);
        fail = new Runnable() {
            
            @Override
            public void run() {
                if (listener != null)
                    listener.onFail(what, reason);
            }
        };
        if (listener != null) {
            reporter.post(fail);
            fail = null;
        }
    }

    protected void progress(final T what, final int how) {
        this.what = what;  
        log("working on " + what);
        progress = new Runnable() {
            
            @Override
            public void run() {
                if (listener != null)
                    listener.onProgress(what, how);
            }
        };
        if (listener != null) {
            reporter.post(progress);
            progress = null;
        }
    }

    protected void success(final T what, final int number) {
        log("success " + what);
        retries.remove(what);
        if (what == null) return;
        success = new Runnable() {
            
            @Override
            public void run() {
                if (listener != null)
                    listener.onSuccess(what, number);
            }
        };
        if (listener != null) {
            reporter.post(success);
            success = null;
        }
    }

    protected void log(String msg) {
        Log.d(TAG, msg);
        if (verbose)
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
    }


    public Job<T> setVerbose(boolean verbose) {
        this.verbose = verbose;
        return this;
    }
    
    public Job<T> register(ServiceCallback<T> listener) {
        this.listener = listener;
        if (success != null) {
            reporter.post(success);
            success = null;
        }
        if (progress != null) {
            reporter.post(progress);
            progress = null;
        }
        if (fail != null) {
            reporter.post(fail);
            fail = null;
        }
        return this;
    }

}
