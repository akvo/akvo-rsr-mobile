/*
 *  Copyright (C) 2015,2020 Stichting Akvo (Akvo Foundation)
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

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.akvo.rsr.up.dao.RsrDbAdapter;
import org.akvo.rsr.up.domain.Project;
import org.akvo.rsr.up.util.ConstantUtil;
import org.akvo.rsr.up.viewadapter.ResultListArrayAdapter;
import org.akvo.rsr.up.viewadapter.ResultNode;
import org.akvo.rsr.up.viewadapter.ResultNode.NodeType;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class ResultListActivity extends AppCompatActivity {

    private static final String TAG = "ResultListActivity";
	private static final String endash = "\u2013";

	private RsrDbAdapter mDba;
	private Cursor dataCursor;
	private TextView mProjectTitleLabel;
	private TextView mResultCountLabel;
    private ListView mList;
    private String mProjId;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_result_list);

		//find which project we will be showing results for
		Bundle extras = getIntent().getExtras();
		mProjId = extras != null ? extras.getString(ConstantUtil.PROJECT_ID_KEY)
				: null;
		if (mProjId == null) {
			mProjId = savedInstanceState != null ? savedInstanceState
					.getString(ConstantUtil.PROJECT_ID_KEY) : null;
		}

		mProjectTitleLabel = (TextView) findViewById(R.id.rlisttitlelabel);
		mResultCountLabel = (TextView) findViewById(R.id.resultcountlabel);
        mList = (ListView) findViewById(R.id.list_results);
        mList.setOnItemClickListener((parent, view, position, id) -> {
			//see if it is a period
			ResultNode rn = (ResultNode) parent.getItemAtPosition(position);
			if (rn != null && rn.getNodeType() == NodeType.PERIOD) {
				Intent i = new Intent(view.getContext(), PeriodDetailActivity.class);
				i.putExtra(ConstantUtil.PERIOD_ID_KEY, Integer.toString(rn.getId()));
				startActivity(i);
			}
		});
        
		mDba = new RsrDbAdapter(this);
        mDba.open();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.project_detail, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	        case android.R.id.home:
	            finish();
	            return true;
    	    case R.id.action_settings:
    			Intent intent = new Intent(this, SettingsActivity.class);
    			startActivity(intent);
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
		if (dataCursor != null) {
			try {
				dataCursor.close();
			} catch (Exception e) {
				//Ignored
			}
		}
		if (mDba != null) {
			mDba.close();
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
		Project p = mDba.findProject(mProjId);
		if (p != null) {
			mProjectTitleLabel.setText(p.getTitle());
		}
		//fetch data
		dataCursor = mDba.listResultsIndicatorsPeriodsFor(mProjId);
		ArrayList<ResultNode> list = new ArrayList<>();
		int last_res, last_ind, last_per, resultCounter = 0, indicatorCounter = 0;

		if (dataCursor != null) {
			//Populate list view
			final int res_pk = dataCursor.getColumnIndex("result_id");
			final int ind_pk = dataCursor.getColumnIndex("indicator_id");
			final int per_pk = dataCursor.getColumnIndex("period_id");
			final int res_title = dataCursor.getColumnIndex("result_title");
			final int ind_title = dataCursor.getColumnIndex("indicator_title");
			final int per_start = dataCursor.getColumnIndex("period_start");
			final int per_end = dataCursor.getColumnIndex("period_end");
			final int actual_value = dataCursor.getColumnIndex("actual_value");
			final int target_value = dataCursor.getColumnIndex("target_value");
			final int period_locked = dataCursor.getColumnIndex(RsrDbAdapter.LOCKED_COL);

			list = new ArrayList<>();
			indicatorCounter = 0;
			resultCounter = 0;
			last_per = -1;
			last_ind = -1;
			last_res = -1;

			final SimpleDateFormat dateOnly = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

			//Compose the values to be shown for a node
			while (dataCursor.moveToNext()) {  //must be grouped on result and indicator
				int res = dataCursor.getInt(res_pk);
				int ind = dataCursor.getInt(ind_pk);
				int per = dataCursor.getInt(per_pk);
				if (res != last_res) {
					resultCounter++;
					list.add(new ResultNode(NodeType.RESULT, res, dataCursor.getString(res_title), 0));
					last_res = res;
					last_ind = -1;
				}

				if (ind != last_ind && ind > 0) { // ==0 if no indicators
					indicatorCounter++;
					list.add(new ResultNode(NodeType.INDICATOR, ind, dataCursor.getString(ind_title), R.drawable.tacho));
					last_ind = ind;
					last_per = -1;
				}

				if (per != last_per && per > 0) {  // ==0 if no periods
					String av = dataCursor.getString(actual_value);
					String tv = dataCursor.getString(target_value);
					boolean locked = dataCursor.getInt(period_locked) != 0;
					String ps = "";
					if (!dataCursor.isNull(per_start)) {
						Date d = new Date(dataCursor.getInt(per_start)*1000L);
						ps = dateOnly.format(d);
					}
					String pe = "";
					if (!dataCursor.isNull(per_end)) {
						Date d = new Date(dataCursor.getInt(per_end)*1000L);
						pe = dateOnly.format(d);
					}

					String period = "";

					if (!"".equals(ps) || !"".equals(pe)) period += ps + endash + pe + " : ";
					if ( av != null && av.trim().length() > 0 ) period += av;
					if ( tv != null && tv.trim().length() > 0 ) period += "/" + tv;
					if ( locked ) {
						list.add(new ResultNode(NodeType.PERIOD, per, period, R.drawable.ic_menu_lt_date, av, locked));
					} else {
						list.add(new ResultNode(NodeType.PERIOD, per, period, R.drawable.ic_menu_dk_date, av, locked));
					}

					last_per = per;
				}

			}
		}

		//Show count
        mResultCountLabel.setText(String.format("%d, %d", resultCounter, indicatorCounter));

		mList.setAdapter(new ResultListArrayAdapter(this, R.layout.result_list_item, R.id.result_item_text, list));
	}	
}
