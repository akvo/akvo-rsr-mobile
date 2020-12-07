/*
 *  Copyright (C) 2012-2015 Stichting Akvo (Akvo Foundation)
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

package org.akvo.rsr.up;

import org.akvo.rsr.up.R;
import org.akvo.rsr.up.dao.RsrDbAdapter;
import org.akvo.rsr.up.domain.Project;
import org.akvo.rsr.up.util.ConstantUtil;
import org.akvo.rsr.up.viewadapter.UpdateListCursorAdapter;

import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.database.Cursor;

public class UpdateListActivity extends ActionBarActivity {


	private static final String TAG = "UpdateListActivity";

	private RsrDbAdapter ad;
	private Cursor dataCursor;
	private TextView projectTitleLabel;
	private TextView updateCountLabel;
    private ListView mList;
	private String projId;
	private BroadcastReceiver broadRec;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		//find which project we will be showing updates for
		Bundle extras = getIntent().getExtras();
		projId = extras != null ? extras.getString(ConstantUtil.PROJECT_ID_KEY)
				: null;
		if (projId == null) {
			projId = savedInstanceState != null ? savedInstanceState
					.getString(ConstantUtil.PROJECT_ID_KEY) : null;
		}

		setContentView(R.layout.activity_update_list);

		projectTitleLabel = (TextView) findViewById(R.id.ulisttitlelabel);
		updateCountLabel = (TextView) findViewById(R.id.updatecountlabel);
        mList = (ListView) findViewById(R.id.list_updates);
        mList.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent i = new Intent(view.getContext(), UpdateDetailActivity.class);
                i.putExtra(ConstantUtil.UPDATE_ID_KEY, ((Long) view.getTag(R.id.update_id_tag)).toString());
                i.putExtra(ConstantUtil.PROJECT_ID_KEY, ((Long) view.getTag(R.id.project_id_tag)).toString());
                startActivity(i);
            }
        });
        
        

		View listFooter = getLayoutInflater().inflate(R.layout.update_list_footer, null, false);
		//if the button were not the outermost view we would need to find it to set onClick
		Button addButton = (Button) listFooter.findViewById(R.id.btn_add_update2);
		addButton.setOnClickListener( new View.OnClickListener() {
			public void onClick(View view) {
				startEditorNew();
			}
		});
		
		mList.addFooterView(listFooter);
		
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		
        //Create db

		ad = new RsrDbAdapter(this);
        ad.open();
	}

	private void startEditorNew() {
		Intent i3 = new Intent(this, UpdateEditorActivity.class);
		i3.putExtra(ConstantUtil.PROJECT_ID_KEY, projId);
		startActivity(i3);		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.update_list, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	        case android.R.id.home:
	            finish();
	            //NavUtils.navigateUpFromSameTask(this);
	            return true;
    	    case R.id.action_settings:
    			Intent intent = new Intent(this, SettingsActivity.class);
    			startActivity(intent);
                return true;
            case R.id.action_add_update:
            	startEditorNew();
            	return true;
    	    default:
    	        return super.onOptionsItemSelected(item);
	    }

	}
	

	@Override
	public void onResume() {
		super.onResume();
		getData();
	}
	
	
	@Override
	protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadRec);
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
	 * show count and list of all the updates in the database for this project
	 */
	private void getData() {
		try {
			if (dataCursor != null) {
				dataCursor.close();
			}
		} catch(Exception e) {
			Log.w(TAG, "Could not close old cursor before reloading list",e);
		}

		//Show title
		Project p = ad.findProject(projId);
		projectTitleLabel.setText(p.getTitle());
		//fetch data
		dataCursor = ad.listAllUpdatesNewestFirstFor(projId);
		//Show count
		updateCountLabel.setText(Integer.valueOf(dataCursor.getCount()).toString());
		//Populate list view
		UpdateListCursorAdapter updates = new UpdateListCursorAdapter(this, dataCursor);
		mList.setAdapter(updates);

	}	

}
