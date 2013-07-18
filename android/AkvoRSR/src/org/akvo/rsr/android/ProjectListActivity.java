/*
 *  Copyright (C) 2012-2013 Stichting Akvo (Akvo Foundation)
 *
 *  This file is part of Akvo RSR.
 *
 *  Akvo RSR is free software: you can redistribute it and modify it under the terms of
 *  the GNU Affero General Public License (AGPL) as published by the Free Software Foundation,
 *  either version 3 of the License or any later version.
 *
 *  Akvo RSR is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Affero General Public License included below for more details.
 *
 *  The full license text can also be seen at <http://www.gnu.org/licenses/agpl.html>.
 */

package org.akvo.rsr.android;

import org.akvo.rsr.android.dao.RsrDbAdapter;
import org.akvo.rsr.android.service.GetProjectDataService;
import org.akvo.rsr.android.service.SubmitProjectUpdateService;
import org.akvo.rsr.android.util.ConstantUtil;
import org.akvo.rsr.android.util.SettingsUtil;
import org.akvo.rsr.android.view.adapter.ProjectListCursorAdapter;

import android.os.Bundle;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.support.v4.content.LocalBroadcastManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;

public class ProjectListActivity extends ListActivity {



	private static final String TAG = "ProjectListActivity";

	private RsrDbAdapter ad;
	private Cursor dataCursor;
	private TextView projCountLabel;
	private ProgressDialog progress;
	private LinearLayout inProgress;
	private ProgressBar inProgress1;
	private ProgressBar inProgress2;
	private ProgressBar inProgress3;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_project_list);

		projCountLabel = (TextView) findViewById(R.id.projcountlabel);
		inProgress = (LinearLayout) findViewById(R.id.projlistprogress);
		inProgress1 = (ProgressBar) findViewById(R.id.progressBar1);
		inProgress2 = (ProgressBar) findViewById(R.id.progressBar2);
		inProgress3 = (ProgressBar) findViewById(R.id.progressBar3);

		Button refreshButton = (Button) findViewById(R.id.button_refresh_projects);		
		refreshButton.setOnClickListener( new View.OnClickListener() {
			public void onClick(View view) {
				//fetch new data
				startGetProjectsService();
			}
		});
 
        //Create db
        ad = new RsrDbAdapter(this);
	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_project_list, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
        case R.id.menu_settings:
			Intent i = new Intent(this, SettingsActivity.class);
			startActivity(i);
            return true;
        case R.id.menu_logout:
        	SettingsUtil.signOut(this);
        	finish();
            return true;
        case R.id.menu_sendall:
        	startSendUpdatesService();
        	return true;
        default:
        	return super.onOptionsItemSelected(item);
	    }

	}
	
	@Override
	protected void onPause() {
		super.onPause();
		if (dataCursor != null) {
			dataCursor.close();
		}	
	}
	
	@Override
	public void onResume() {
		super.onResume();
		ad.open();
		getData();
	}
	
	
	@Override
	protected void onDestroy() {
		if (dataCursor != null) {
			try {
				dataCursor.close();
			} catch (Exception e) {

			}
		}
		if (ad != null) {
			ad.close();
		}
		super.onDestroy();
	}



	/**
	 * show all the projects in the database
	 */
	private void getData() {
		try {
			if (dataCursor != null) {
				dataCursor.close();
			}
		} catch(Exception e) {
			Log.w(TAG, "Could not close old cursor before reloading list",e);
		}
		dataCursor = ad.listAllProjects();
		//Show count
		projCountLabel.setText(Integer.valueOf(dataCursor.getCount()).toString());
		//Populate list view
		ProjectListCursorAdapter projects = new ProjectListCursorAdapter(this, dataCursor);
		setListAdapter(projects);

	}

	/**
	 * when a list item is clicked, get the id of the selected
	 * item and open one-project activity.
	 */
	@Override
	protected void onListItemClick(ListView list, View view, int position, long id) {
		super.onListItemClick(list, view, position, id);

		Intent i = new Intent(view.getContext(), ProjectDetailActivity.class);
		i.putExtra(ConstantUtil.PROJECT_ID_KEY, ((Long) view.getTag(R.id.project_id_tag)).toString());
		startActivity(i);
	}

	
	private void startSendUpdatesService(){
		//register a listener for a completion intent
        LocalBroadcastManager.getInstance(this).registerReceiver(
                new ResponseReceiver(),
                new IntentFilter(ConstantUtil.UPDATES_SENT_ACTION));
		
		//start upload service
		Intent i = new Intent(this, SubmitProjectUpdateService.class);
		getApplicationContext().startService(i);
		
		//start a "progress" animation
		//TODO: a real filling progress bar?
		progress = new ProgressDialog(this);
		progress.setTitle("Synchronizing");
		progress.setMessage("Sending all unsent updates...");
		progress.show();
		//Now we wait...
	}

	private void onSendFinished(Intent i) {
		// Dismiss any in-progress dialog
		if (progress != null)
			progress.dismiss();

		String err = i.getStringExtra(ConstantUtil.SERVICE_ERRMSG_KEY);
		if (err == null) {
			Toast.makeText(getApplicationContext(), "Updates sent", Toast.LENGTH_SHORT).show();
		} else {
			Toast.makeText(getApplicationContext(), err, Toast.LENGTH_SHORT).show();
		}
		//Refresh the list
		getData();
	}

	/*
	 * Start the service fetching new project data
	 */
	private void startGetProjectsService() {
		//start a service
		//register a listener for the completion intent
		IntentFilter f = new IntentFilter(ConstantUtil.PROJECTS_FETCHED_ACTION);
		f.addAction(ConstantUtil.PROJECTS_PROGRESS_ACTION);
        LocalBroadcastManager.getInstance(this).registerReceiver(
                new ResponseReceiver(),
                f);
		
		Intent i = new Intent(this, GetProjectDataService.class);
//		i.putExtra(SERVER_KEY, "http://test.akvo.org");
		getApplicationContext().startService(i);
		
		//start a "progress" animation
		//TODO: a real filling progress bar?
		inProgress.setVisibility(View.VISIBLE);
		inProgress1.setIndeterminate(true);//no prediction of projects
		inProgress2.setProgress(0);
		inProgress3.setProgress(0);
	}

	private void onFetchFinished(Intent intent) {
		// Hide in-progress indicators
		inProgress.setVisibility(View.GONE);
		
		String err = intent.getStringExtra(ConstantUtil.SERVICE_ERRMSG_KEY);
		if (err == null) {
			Toast.makeText(getApplicationContext(), "Fetch complete", Toast.LENGTH_SHORT);
		} else {
			Toast.makeText(getApplicationContext(), err, Toast.LENGTH_SHORT).show();
		}

		//Refresh the list
		getData();
	}

	private void onFetchProgress(int phase, int done, int total) {
		if (phase == 0) {
			inProgress1.setIndeterminate(false);
			inProgress1.setProgress(100);//Done!
			}
		if (phase == 1) {
			inProgress1.setProgress(100);//just in case...
//			inProgress2.setIndeterminate(false);
			inProgress2.setProgress(done);
			inProgress2.setMax(total);
			}
		if (phase == 2) {
			inProgress2.setProgress(100);//just in case...
			inProgress2.setMax(100);
//			inProgress3.setIndeterminate(false);
			inProgress3.setProgress(done);
			inProgress3.setMax(total);
			}
		}

//Broadcast receiver for receiving status updates from the IntentService
	private class ResponseReceiver extends BroadcastReceiver {
		// Prevents instantiation
		private ResponseReceiver() {
		}
		
		// Called when the BroadcastReceiver gets an Intent it's registered to receive
		public void onReceive(Context context, Intent intent) {
			/*
			 * Handle Intents here.
			 */
			if (intent.getAction() == ConstantUtil.UPDATES_SENT_ACTION)
				onSendFinished(intent);
			else
			if (intent.getAction() == ConstantUtil.PROJECTS_FETCHED_ACTION)
				onFetchFinished(intent);
			else if (intent.getAction() == ConstantUtil.PROJECTS_PROGRESS_ACTION)
				onFetchProgress(intent.getExtras().getInt(ConstantUtil.PHASE_KEY, 0),
								intent.getExtras().getInt(ConstantUtil.SOFAR_KEY, 0),
						        intent.getExtras().getInt(ConstantUtil.TOTAL_KEY, 100));
		}
	}
}
