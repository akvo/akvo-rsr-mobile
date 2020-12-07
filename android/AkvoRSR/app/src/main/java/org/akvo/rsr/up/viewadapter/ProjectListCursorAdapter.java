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

package org.akvo.rsr.up.viewadapter;

import org.akvo.rsr.up.R;
import org.akvo.rsr.up.dao.RsrDbAdapter;
import org.akvo.rsr.up.util.SettingsUtil;
import org.akvo.rsr.up.util.ThumbnailUtil;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class ProjectListCursorAdapter extends CursorAdapter{

/**
 * This adapter formats Project list items.
 * 
 * @author Stellan Lagerstroem
 * 
 */
    private final String TAG = "ProjectListCursorAdapter";
	private RsrDbAdapter dba;
	private boolean debug;
	
	public ProjectListCursorAdapter(Context context, Cursor c) {
		super(context, c);
		dba = new RsrDbAdapter(context);
//		dba.open();
		debug = SettingsUtil.ReadBoolean(context, "setting_debug", false);
	}

	
	
	@Override
	public void bindView(View view, Context context, Cursor cursor) {

        Long thisId = cursor.getLong(cursor.getColumnIndexOrThrow(RsrDbAdapter.PK_ID_COL));
//        Long oldId = (Long) view.getTag(R.id.project_id_tag);
//        if (oldId != null && oldId.compareTo(thisId) !=0 ) {
//            Log.w(TAG,"switch!");
//        }
        
        //Text data
		TextView titleView = (TextView) view.findViewById(R.id.list_item_title);
		if (debug) {
			titleView.setText("["+ thisId +"] "+
					cursor.getString(cursor.getColumnIndexOrThrow(RsrDbAdapter.TITLE_COL)));
		} else {
			titleView.setText(cursor.getString(cursor.getColumnIndexOrThrow(RsrDbAdapter.TITLE_COL)));
		}
		String projId = cursor.getString(cursor.getColumnIndexOrThrow(RsrDbAdapter.PK_ID_COL));
		dba.open();
		int [] stateCounts = {0,0,0};
		try {
			stateCounts = dba.countAllUpdatesFor(projId);
		} finally {
			dba.close();	
		}
		Resources res = context.getResources();
		//hiding counts of 0
		TextView publishedCountView = (TextView) view.findViewById(R.id.list_item_published_count);
		publishedCountView.setText(Integer.toString(stateCounts[2]) + res.getString(R.string.count_published));
		publishedCountView.setVisibility(stateCounts[2]==0?View.GONE:View.VISIBLE);

//	unsent not shown any more - just draft and published
//		TextView unsynchCountView = (TextView) view.findViewById(R.id.list_item_unsynchronized_count);
// 		unsynchCountView.setText(Integer.toString(stateCounts[1]) + res.getString(R.string.count_unsent));
//		unsynchCountView.setVisibility(stateCounts[1]==0?View.GONE:View.VISIBLE);

		TextView draftCountView = (TextView) view.findViewById(R.id.list_item_draft_count);
		draftCountView.setText(Integer.toString(stateCounts[0]) + res.getString(R.string.count_draft));
		draftCountView.setVisibility(stateCounts[0]==0?View.GONE:View.VISIBLE);
				
		//Image
		ImageView thumbnail = (ImageView) view.findViewById(R.id.list_item_thumbnail);
		String fn = cursor.getString(cursor.getColumnIndexOrThrow(RsrDbAdapter.THUMBNAIL_FILENAME_COL));
		String url = cursor.getString(cursor.getColumnIndexOrThrow(RsrDbAdapter.THUMBNAIL_URL_COL));
		ThumbnailUtil.setPhotoFile(thumbnail, url, fn, projId, null, false);
		
		//set tag so we will know what got clicked
		view.setTag(R.id.project_id_tag, thisId);

	}

	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent) {
		LayoutInflater inflater = (LayoutInflater) context
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.project_list_item, null);
		bindView(view, context, cursor);
		
		return view;
	}

}
