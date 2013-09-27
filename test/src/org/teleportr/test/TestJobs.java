package org.teleportr.test;

import java.util.Date;

import org.teleportr.Ride;
import org.teleportr.Ride.COLUMNS;
import org.teleportr.RidesProvider;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

public class TestJobs extends CrashTest {



    public void testResolveJobs() {
        Cursor jobs = query(RidesProvider.getResolveJobsUri(ctx).toString());
        assertEquals("there be one place to resolve", 1, jobs.getCount());
        // dummy working hard in background..
        jobs.moveToFirst();
        ContentValues values = new ContentValues();
        values.put("geohash", "abc");
        getMockContentResolver().update(Uri.parse(
                "content://org.teleportr.test/places/1"), values, null, null);
        jobs = query("content://org.teleportr.test/jobs/resolve");
        assertEquals("now no places to resolve any more", 0, jobs.getCount());
    }

    public void testPublishJobs() {
        Cursor jobs = query(RidesProvider.getPublishJobsUri(ctx).toString());
        assertEquals("there be no rides to publish", 0, jobs.getCount());
        Uri ride = new Ride().dep(1).from(bar).to(park).store(ctx);
        jobs = query("content://org.teleportr.test/jobs/publish");
        assertEquals("now there be no rides to publish", 0, jobs.getCount());
        Cursor my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("no myride as draft", 0, my_rides.getCount());
        ride = new Ride(ride, ctx).dep(2).marked().dirty().store(ctx);
        jobs = query("content://org.teleportr.test/jobs/publish");
        assertEquals("now there be a ride to publish", 1, jobs.getCount());
        my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("one myride as draft", 1, my_rides.getCount());
        // dummy connector publishing hard in the background..
        jobs.moveToFirst();
        assertNotNull(jobs.getString(COLUMNS.REF)); // tmp ref
        storeServerRef(jobs.getLong(0));
        jobs = query("content://org.teleportr.test/jobs/publish");
        assertEquals("nothing to publish anymore", 0, jobs.getCount());
        // meanwhile the unpublished has been edited with still the tmp ref
        new Ride(ride, ctx).seats(2).dirty().store(ctx);
        jobs = query("content://org.teleportr.test/jobs/publish");
        assertEquals("one ride to update", 1, jobs.getCount());
        my_rides = query("content://org.teleportr.test/myrides");
        assertEquals("there should be only one ride", 1, my_rides.getCount());
        my_rides.moveToFirst();
    }

    public void testSearchJobs() {
        Ride search = new Ride().type(Ride.SEARCH).from(bar).to(home)
                .dep(new Date(7000)).arr(new Date(9000));
        search.store(ctx);
        assertOneJobToSearch("last search ride dep", 7000);
        // working dummy hard in background..
        reportJobsDone(7000, 8000);
        assertOneJobToSearch("still search from dep", 8000);
        // working dummy hard in background..
        reportJobsDone(7000, 9000);
        assertNoMoreJobsToSearch();
        // cache invalidates after time
        reportJobsDone(7000, 9000, System.currentTimeMillis() - 10 * 60 * 1001);
        assertOneJobToSearch("again search from dep", 7000);
        reportJobsDone(7000, 8000);
        assertOneJobToSearch("again search from dep", 8000);
        // working dummy hard in background..
        reportJobsDone(7000, 9000);
        assertNoMoreJobsToSearch();
        search.arr(new Date(9500)).store(ctx); // inc arr
        assertOneJobToSearch("inc time window: dep", 9000);
        reportJobsDone(7000, 9500);
        assertNoMoreJobsToSearch();
        search.dep(3000).arr(5000).store(ctx); // dec dep
        assertOneJobToSearch("new time window: dep", 3000);
        reportJobsDone(3000, 4000);
        assertOneJobToSearch("job dep", 4000);
        reportJobsDone(3000, 5000);
        assertNoMoreJobsToSearch();
        search.arr(8000).store(ctx); // inc arr
        assertOneJobToSearch("", 5000);
        reportJobsDone(3000, 7000);
        assertOneJobToSearch("", 7000);
//        assertNoMoreJobsToSearch(); 
//        search.arr(new Date(9700)).store(ctx); // inc arr
//        assertOneJobToSearch("", 9500);
    }

    public void testInvalidateCache() throws Exception {
        Ride search = new Ride().type(Ride.SEARCH).from(bar).to(home)
                .dep(new Date(7000)).arr(new Date(9000));
        search.store(ctx);
        assertOneJobToSearch("last search ride dep", 7000);
        // working dummy hard in background..
        reportJobsDone(7000, 9000);
        assertNoMoreJobsToSearch();
        getMockContentResolver().update(
                RidesProvider.getRidesUri(ctx), null, null, null);
        assertOneJobToSearch("last search ride dep", 7000);
    }



    public void assertNoMoreJobsToSearch() {
        String uri = RidesProvider.getSearchJobsUri(ctx).toString();
        Cursor jobs = query(uri);
        assertEquals("now no more jobs to do", 0, jobs.getCount());
    }

    public void assertOneJobToSearch(String msg, long searchtime) {
        String uri = RidesProvider.getSearchJobsUri(ctx).toString();
        Cursor jobs = query(uri);
        assertEquals("one job to search for " + msg, 1, jobs.getCount());
        jobs.moveToFirst();
        assertEquals("from id", bar.id, jobs.getLong(0));
        assertEquals("to id", home.id, jobs.getLong(1));
        assertEquals(msg, searchtime, jobs.getLong(3));
    }

    public void reportJobsDone(long dep, long arr) {
        reportJobsDone(dep, arr, System.currentTimeMillis());
    }

    public void reportJobsDone(long dep, long arr, long refreshtime) {
        String uri = RidesProvider.getSearchJobsUri(ctx).toString();
        ContentValues values = new ContentValues();
        values.put("from_id", bar.id);
        values.put("to_id", home.id);
        values.put("dep", dep);
        values.put("arr", arr);
        values.put("last_refresh", refreshtime);
        getMockContentResolver().insert(Uri.parse(uri), values);
    }
}
