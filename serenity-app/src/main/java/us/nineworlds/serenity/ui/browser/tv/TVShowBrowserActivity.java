/**
 * The MIT License (MIT)
 * Copyright (c) 2012 David Carver
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS
 * OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF
 * OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package us.nineworlds.serenity.ui.browser.tv;

import java.util.ArrayList;

import us.nineworlds.serenity.core.model.CategoryInfo;
import us.nineworlds.serenity.core.services.TVShowCategoryRetrievalIntentService;

import us.nineworlds.serenity.R;
import com.google.analytics.tracking.android.EasyTracker;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

/**
 * @author dcarver
 * 
 */
public class TVShowBrowserActivity extends Activity {

	private static Spinner categorySpinner;
	private boolean restarted_state = false;
	private static String key;
	private Handler categoryHandler;
	private SharedPreferences preferences;
	public static boolean USE_POSTER_LAYOUT = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		key = getIntent().getExtras().getString("key");
		preferences = PreferenceManager.getDefaultSharedPreferences(this);
		
		USE_POSTER_LAYOUT = preferences.getBoolean("series_layout_posters", false);
		if (USE_POSTER_LAYOUT) {
			setContentView(R.layout.activity_tvbrowser_show_posters);
		} else {
			setContentView(R.layout.activity_tvbrowser_show_banners);
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		EasyTracker.getInstance().activityStart(this);

		if (restarted_state == false) {
			categoryHandler = new CategoryHandler(this);
			Messenger messenger = new Messenger(categoryHandler);
			Intent categoriesIntent = new Intent(this,
					TVShowCategoryRetrievalIntentService.class);
			categoriesIntent.putExtra("key", key);
			categoriesIntent.putExtra("MESSENGER", messenger);
			startService(categoriesIntent);

		}
		restarted_state = false;
	}

	@Override
	protected void onStop() {
		super.onStop();
		EasyTracker.getInstance().activityStop(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		restarted_state = true;
	}

	private static class CategoryHandler extends Handler {

		private ArrayList<CategoryInfo> categories;
		private Activity context;

		public CategoryHandler(Activity context) {
			this.context = context;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.Handler#handleMessage(android.os.Message)
		 */
		@Override
		public void handleMessage(Message msg) {
			if (msg.obj != null) {
				categories = (ArrayList<CategoryInfo>) msg.obj;
				setupShows();
			}
		}

		protected void setupShows() {
			ArrayAdapter<CategoryInfo> spinnerArrayAdapter = new ArrayAdapter<CategoryInfo>(
					context, R.layout.serenity_spinner_textview, categories);
			spinnerArrayAdapter
					.setDropDownViewResource(R.layout.serenity_spinner_textview_dropdown);

			categorySpinner = (Spinner) context
					.findViewById(R.id.tvshow_CategoryFilter);
			categorySpinner.setVisibility(View.VISIBLE);
			categorySpinner.setAdapter(spinnerArrayAdapter);
			categorySpinner
					.setOnItemSelectedListener(new CategorySpinnerOnItemSelectedListener(
							"all", key));
			categorySpinner.requestFocus();
		}

	}

}
