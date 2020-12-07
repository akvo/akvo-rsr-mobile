/*
 *  Copyright (C) 2012-2016 Stichting Akvo (Akvo Foundation)
 *
 *  This file is part of Akvo RSR.
 *
 *  Akvo RSR is free software: you can redistribute it and modify it under the terms of
 *  the GNU Affero General Public License (AGPL) as published by the Free Software Foundation,
 *  either version 3 of the License or any later version.
 *
 *  Akvo RSR is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Affero General Public License included with this program for more details.
 *
 *  The full license text can also be seen at <http://www.gnu.org/licenses/agpl.html>.
 */

package org.akvo.rsr.up.service;

import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import org.akvo.rsr.up.R;
import org.akvo.rsr.up.dao.RsrDbAdapter;
import org.akvo.rsr.up.domain.Project;
import org.akvo.rsr.up.domain.User;
import org.akvo.rsr.up.util.ConstantUtil;
import org.akvo.rsr.up.util.Downloader;
import org.akvo.rsr.up.util.FileUtil;
import org.akvo.rsr.up.util.SettingsUtil;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

public class GetProjectDataService extends IntentService {

    private static final String TAG = "GetProjectDataService";
    private static boolean mRunning = false;
    private static final boolean mFetchUsers = true;
    private static final boolean mFetchCountries = true;
    private static final boolean mFetchUpdates = true;
    private static final boolean mFetchOrgs = true;
    private static final boolean mFetchResults = false;

    public GetProjectDataService() {
        super(TAG);
    }

	
    public static boolean isRunning(Context context) {
	    return mRunning;
    }
	
	

    /**
     * Fetch data from server.
     * TODO: Send the object type as a string in the broadcastProgress call so we can have that displayed as part of the progress bar.
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        mRunning = true;
        String projectId = intent.getStringExtra(ConstantUtil.PROJECT_ID_KEY); //just this project?
        RsrDbAdapter ad = new RsrDbAdapter(this);
        Downloader dl = new Downloader();
        String errMsg = null;
        boolean fetchImages = !SettingsUtil.ReadBoolean(this, "setting_delay_image_fetch", false);
        boolean fullSynch = SettingsUtil.ReadBoolean(this, "setting_fullsynch", false);
        String host = SettingsUtil.host(this);
        Long start = System.currentTimeMillis();
        
        ad.open();
        User user = SettingsUtil.getAuthUser(this);
        try {
            try {
                // Make the list of projects to update
                Set<String> projectSet;
                if (projectId == null) {
                    projectSet = user.getPublishedProjIds();
                } else {
                    projectSet = new HashSet<String>();
                    projectSet.add(projectId);
                }
                
                int i = 0;
                int projects = projectSet.size();
                //Iterate over projects instead of using a complex query URL, since it can take so long that the proxy times out
                for (String id : projectSet) {
                    dl.fetchProject(this,
                                    ad, 
                                    new URL(SettingsUtil.host(this) +
                                            String.format(ConstantUtil.FETCH_PROJ_URL_PATTERN, id))); //TODO: JSON
                    if (mFetchResults) {
                        dl.fetchProjectResultsPaged(this, ad,
                                new URL(host + String.format(ConstantUtil.FETCH_RESULTS_URL_PATTERN, id)));                                            
                    }
                    broadcastProgress(0, ++i, projects);
                }
                // country list rarely changes, so only fetch countries if we never did that
                if (mFetchCountries && (fullSynch || ad.getCountryCount() == 0)) {
                    dl.fetchCountryListRestApiPaged(this, ad, new URL(SettingsUtil.host(this) +
                            String.format(ConstantUtil.FETCH_COUNTRIES_URL)));
                }
                broadcastProgress(0, 100, 100);

                if (mFetchUpdates) {
                	SimpleDateFormat df1 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            		df1.setTimeZone(TimeZone.getTimeZone("UTC"));
            		ArrayList<String> fetchedIds = new  ArrayList<String>();
            		 
                    int k = 0;
                    for (String projId : projectSet) {
                    	Project p = ad.findProject(projId);
                    	if (p != null) {
                    	    //since last fetch or forever?
                    	    String u = String.format(ConstantUtil.FETCH_UPDATE_URL_PATTERN, projId, df1.format(fullSynch?0:p.getLastFetch()));
                            Date d = dl.fetchUpdateListRestApiPaged(this, new URL(host + u), fetchedIds);
                            //fetch completed; remember fetch date of this project - other users of the app may have different project set
                    	    ad.updateProjectLastFetch(projId, d);
                            if (fullSynch) { //now delete those that went away
                                List<String> removeIds = ad.getUpdatesForList(projId);
                                removeIds.removeAll(fetchedIds);
                                for (String id : removeIds) {
                                    Log.i(TAG, "Deleting update " + id);
                                    ad.deleteUpdate(id.toString());
                                }
                            }                    	    
                    	}
                    	//show progress
                    	 //TODO this is *very* uninformative for a user with one project and many updates!
                        broadcastProgress(1, ++k, projects);
                    }
                }
            } catch (FileNotFoundException e) {
                Log.e(TAG, "Cannot find:", e);
                errMsg = getResources().getString(R.string.errmsg_not_found_on_server) + e.getMessage();
            } catch (Exception e) {//get e==null here!!!!
                Log.e(TAG, "Bad updates fetch:", e);
                if (e != null) {
                    errMsg = getResources().getString(R.string.errmsg_update_fetch_failed) + e.getMessage();
                } else {
                    errMsg = getResources().getString(R.string.errmsg_update_fetch_failed) + "NULL exception";
                }
            }
            if (mFetchUsers) { //Remove this once we use the _extra update API
                // Fetch missing user data for authors of the updates.
                // This API requires authorization
//                int k = 0;
                List<String> orgIds = ad.getMissingUsersList();
                for (String id : orgIds) {
                    try {
                        dl.fetchUser(
                                this,
                                ad,
                                new URL(host
                                        +
                                        String.format(Locale.US,
                                                ConstantUtil.FETCH_USER_URL_PATTERN, id)),
                                id
                                );
//                        k++;
//                    } catch (FileNotFoundException e) {
                        // possibly because user is no longer active
//                        Log.w(TAG, "Cannot find user:" + id);
                        // errMsg = "Cannot find: "+ e.getMessage(); //not serious
                    } catch (Exception e) { // probably network reasons
                        Log.e(TAG, "Bad user fetch:", e);
                        errMsg = getResources().getString(R.string.errmsg_user_fetch_failed) + e.getMessage();
                    }
                }
                // Log.i(TAG, "Fetched users: " + j);
            }

            if (mFetchOrgs) {
                // Fetch user data for the organisations of users.
                List<String> orgIds = ad.getMissingOrgsList();
                int j = 0;
                for (String id : orgIds)
                    try {
                        dl.fetchOrg(
                                this,
                                ad,
                                new URL(host
                                        + String.format(Locale.US,
                                                ConstantUtil.FETCH_ORG_URL_PATTERN, id)),
                                id
                                );
                        j++;
//                    } catch (FileNotFoundException e) { // possibly because user
                                                        // is no longer active
//                        Log.w(TAG, "Cannot find org:" + id);
                    } catch (Exception e) { // probably network reasons
                        Log.e(TAG, "Bad org fetch:", e);
                        errMsg = getResources().getString(R.string.errmsg_org_fetch_failed) + e.getMessage();
                    }
                Log.i(TAG, "Fetched " + j + " orgs");
            }

            broadcastProgress(1, 100, 100);

            if (fetchImages) {
                try {
                    dl.fetchMissingThumbnails(this,
                            host,
                            FileUtil.getExternalCacheDir(this).toString(),
                            new Downloader.ProgressReporter() {
                                public void sendUpdate(int sofar, int total) {
                                    Intent intent = new Intent(
                                            ConstantUtil.PROJECTS_PROGRESS_ACTION);
                                    intent.putExtra(ConstantUtil.PHASE_KEY, 2);
                                    intent.putExtra(ConstantUtil.SOFAR_KEY, sofar);
                                    intent.putExtra(ConstantUtil.TOTAL_KEY, total);
                                    LocalBroadcastManager.getInstance(GetProjectDataService.this)
                                            .sendBroadcast(intent);
                                }
                            }
                            );
                } catch (MalformedURLException e) {
                    Log.e(TAG, "Bad thumbnail URL:", e);
                    errMsg = "Thumbnail url problem: " + e;
                }
            }
        } finally {
            if (ad != null)
                ad.close();
        }
        
        Long end = System.currentTimeMillis();
        Log.i(TAG, "Fetch complete in: "+ (end-start)/1000.0);
        
        mRunning = false;

        // broadcast completion
        Intent intent2 = new Intent(ConstantUtil.PROJECTS_FETCHED_ACTION);
        if (errMsg != null)
            intent2.putExtra(ConstantUtil.SERVICE_ERRMSG_KEY, errMsg);

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent2);

    }

    private void broadcastProgress(int p, int s, int t) {
        Intent i1 = new Intent(ConstantUtil.PROJECTS_PROGRESS_ACTION);
        i1.putExtra(ConstantUtil.PHASE_KEY, p);
        i1.putExtra(ConstantUtil.SOFAR_KEY, s);
        i1.putExtra(ConstantUtil.TOTAL_KEY, t);
        LocalBroadcastManager.getInstance(this).sendBroadcast(i1);
    }

}
