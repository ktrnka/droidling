package com.github.ktrnka.droidling;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import com.github.ktrnka.droidling.R;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;

public class HomeActivity extends ListActivity
	{
	public static final String PACKAGE_NAME = "com.github.ktrnka.droidling";
	private static final int[] nameIDs = { R.string.personal_name, R.string.interpersonal_name, R.string.lid_name, R.string.email_name, R.string.rate_name, R.string.about_stats_name, R.string.about_app_name };
	private static final int[] descriptionIDs = { R.string.personal_description, R.string.interpersonal_description, 0, R.string.email_description, R.string.rate_description, 0, 0 };
	
	// TODO:  I don't like how this uses parallel arrays.  I'd much rather do something like make an instance that has all this
	private static final Class<?>[] activities = { PersonalActivity.class, InterpersonalActivity.class, LanguageIdentificationActivity.class, null, null, AboutStatsActivity.class, AboutActivity.class };
	
	public static final boolean DEVELOPER_MODE = false;
	
	public static final String TAG = "com.github.ktrnka.droidling.HomeActivity";
	
	public void onCreate(Bundle savedInstanceState)
		{
		super.onCreate(savedInstanceState);
		
		try
			{
			PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_META_DATA);
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
			if (prefs.getLong("lastRunVersionCode", 0) < packageInfo.versionCode)
				{
				showWelcome();
				
				// update the preference
				SharedPreferences.Editor editor = prefs.edit();
				editor.putLong("lastRunVersionCode", packageInfo.versionCode);
				editor.commit();
				}
			}
		catch (PackageManager.NameNotFoundException exc)
			{
			// This error shouldn't be possible; I don't know an accurate way to test it.
			// If the PackageManager is broken somehow, we probably shouldn't show What's New every time.
			// Should we show a warning?  I don't know.
			Log.e(TAG, "PackageManager lookup failed");
			Log.e(TAG, Log.getStackTraceString(exc));
			}
		
		setListAdapter(new DescriptionMenuAdapter(this, getStrings(nameIDs), getStrings(descriptionIDs)));
		}
	
	/**
	 * Utility function to get strings for an array of IDs.
	 * @param stringIDs
	 * @return
	 */
	private String[] getStrings(int[] stringIDs)
		{
		if (stringIDs == null)
			return null;
		
		String[] strings = new String[stringIDs.length];
		for (int i = 0; i < stringIDs.length; i++)
			if (stringIDs[i] != 0)
				strings[i] = getString(stringIDs[i]);
			else
				strings[i] = null;
		
		return strings;
		}

	/**
	 * Show the welcome message.  Intended for first load
	 * and app updates.
	 */
	private void showWelcome()
		{
		new Thread()
		{
			public void run()
				{
				try 
					{
					// try loading the changelog file
					final StringBuilder changeLogBuilder = new StringBuilder();
					BufferedReader in = new BufferedReader(new InputStreamReader(getAssets().open("changelog.txt")));
					String line;
					while ( (line = in.readLine()) != null)
						{
						changeLogBuilder.append(line);
						changeLogBuilder.append("\n");
						}
					in.close();
					
					runOnUiThread(new Runnable()
						{
						public void run()
							{
							AlertDialog.Builder alertBuilder = new AlertDialog.Builder(HomeActivity.this);
							alertBuilder.setTitle("What's New");
							alertBuilder.setMessage(changeLogBuilder);
							alertBuilder.setIcon(android.R.drawable.ic_menu_help);
							
							alertBuilder.setPositiveButton("Close", null);
							alertBuilder.show();
							}
						});
		
					} 
				catch (IOException e)
					{
					Log.e(TAG, "Failure to load changelog");
					Log.e(TAG, Log.getStackTraceString(e));
					}
				}
		}.start();


		}

	public void onListItemClick(ListView list, View view, int position, long id)
		{
		if (position < activities.length && activities[position] != null)
			{
			// launch the activity if it's found
			Intent intent = new Intent(this, activities[position]);
			startActivity(intent);
			}
		else if (position < nameIDs.length && nameIDs[position] == R.string.email_name)
			{
			sendFeedback();
			}
		else if (position < nameIDs.length && nameIDs[position] == R.string.rate_name)
			{
			rateApp();
			}
		else
			{
			Toast.makeText(getApplicationContext(), getString(R.string.not_implemented), Toast.LENGTH_SHORT).show();
			}
		}
	
	private void rateApp()
		{
		this.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + PACKAGE_NAME)));
		}

	/**
	 * Email feedback to the development account.  Note that most of the strings
	 * aren't from strings.xml, because we don't need to localize them (cause I need to be able to read them)
	 */
	public void sendFeedback()
		{
		// special case for the feedback option
		Intent sendIntent = new Intent(Intent.ACTION_SEND);
		sendIntent.setType("message/rfc822");
		sendIntent.putExtra(Intent.EXTRA_EMAIL, new String[] { getString(R.string.developer_email) });
		sendIntent.putExtra(Intent.EXTRA_SUBJECT, "Feedback on " + getString(R.string.app_name));
		
		// read the config and make it pretty
		Configuration config = getResources().getConfiguration();
		StringBuilder configBuilder = new StringBuilder();

		configBuilder.append("Locale: " + config.locale.toString() + "\n");
		
		configBuilder.append("Size: ");
		switch (config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK)
		{
		case Configuration.SCREENLAYOUT_SIZE_SMALL:
			configBuilder.append("small\n");
			break;
		case Configuration.SCREENLAYOUT_SIZE_NORMAL:
			configBuilder.append("normal\n");
			break;
		case Configuration.SCREENLAYOUT_SIZE_LARGE:
			configBuilder.append("large\n");
			break;
		default:
			configBuilder.append("unknown (" + config.screenLayout + ")\n");
			break;
		}
		
		configBuilder.append("Model: " + android.os.Build.MODEL + "\n");
		
		configBuilder.append("Android version " + android.os.Build.VERSION.RELEASE + "\n");
		configBuilder.append("SDK version " + android.os.Build.VERSION.SDK_INT + "\n");
		
		String personalStatsProfiling = PersonalActivity.summarizeRuntime();
		if (personalStatsProfiling != null)
			{
			configBuilder.append("\nPersonal Stats Runtime Profiling:\n");
			configBuilder.append(personalStatsProfiling);
			}

		sendIntent.putExtra(Intent.EXTRA_TEXT, configBuilder.toString());
		
		startActivity(Intent.createChooser(sendIntent, getString(R.string.send_email_with)));
		}


	}