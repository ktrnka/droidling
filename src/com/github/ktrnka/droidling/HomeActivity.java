package com.github.ktrnka.droidling;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Formatter;

import com.actionbarsherlock.app.SherlockListActivity;
import com.github.ktrnka.droidling.R;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;

public class HomeActivity extends SherlockListActivity
	{
	public static final String PACKAGE_NAME = "com.github.ktrnka.droidling";
	private static final int[] nameIDs = { R.string.personal_name, R.string.interpersonal_name, R.string.lid_name, R.string.email_name, R.string.rate_name };
	private static final int[] descriptionIDs = { R.string.personal_description, R.string.interpersonal_description, 0, R.string.email_description, R.string.rate_description };
	
	// TODO:  I don't like how this uses parallel arrays.  I'd much rather do something like make an instance that has all this
	private static final Class<?>[] activities = { PersonalActivity.class, InterpersonalActivity.class, LanguageIdentificationActivity.class, null, null };
	
	public static final boolean DEVELOPER_MODE = false;
	
	public static final String TAG = "com.github.ktrnka.droidling.HomeActivity";
	
	private static final long VERSION_NOT_FOUND = -1;
	
	public void onCreate(Bundle savedInstanceState)
		{
		super.onCreate(savedInstanceState);
		
		checkShowWhatsNew();
		
		setListAdapter(new DescriptionMenuAdapter(this, getStrings(nameIDs), getStrings(descriptionIDs)));
		}

	/**
	 * Check if this is first install, app update, normal load and show
	 * What's New dialog if appropriate.
	 */
	private void checkShowWhatsNew()
	    {
	    try
			{
			PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_META_DATA);
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
			
			long lastRunVersion = prefs.getLong("lastRunVersionCode", VERSION_NOT_FOUND);
			
			// show the what's new dialog - updates only, not first install
			if (lastRunVersion != VERSION_NOT_FOUND && lastRunVersion < packageInfo.versionCode)
				showWhatsNew();

			// update the stored version - new install and updates
			if (lastRunVersion != packageInfo.versionCode)
				{
				// update the preference
				SharedPreferences.Editor editor = prefs.edit();
				editor.putLong("lastRunVersionCode", packageInfo.versionCode);
				editor.commit();
				}
			}
		catch (PackageManager.NameNotFoundException exc)
			{
			// This error shouldn't be possible; I don't know an accurate way to test it.
			Log.e(TAG, "PackageManager lookup failed");
			Log.e(TAG, Log.getStackTraceString(exc));
			}
	    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
		{
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.help, menu);
		return true;
		}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
		{
		switch (item.getItemId())
			{
			case R.id.helpMenu:
				Intent intent = new Intent(this, AboutActivity.class);
				startActivity(intent);
				break;
			default:
				Log.e(TAG, "Undefined menu item selected");
			}
		return false;
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
	 * Show the What's New dialog.
	 */
	private void showWhatsNew()
		{
		new AsyncTask<Void,Void,CharSequence>()
			{
			@Override
            protected CharSequence doInBackground(Void... params)
                {
                StringBuilder builder = new StringBuilder();
				try
					{
	                BufferedReader in = new BufferedReader(new InputStreamReader(getAssets().open("changelog.txt")));
					String line;
					
					while ((line = in.readLine()) != null && !isCancelled())
						{
						builder.append(line);
						builder.append('\n');
						}
					in.close();
					}
				catch (IOException e)
					{
					return null;
					}
				
				return builder;
                }
			
			@Override
			protected void onPostExecute(CharSequence result)
				{
				if (isCancelled() || result == null)
					return;
				
				AlertDialog.Builder alertBuilder = new AlertDialog.Builder(HomeActivity.this);
				alertBuilder.setTitle("What's New");
				alertBuilder.setMessage(result);
				alertBuilder.setIcon(android.R.drawable.ic_menu_help);

				alertBuilder.setPositiveButton("Close", null);
				alertBuilder.show();
				}
			
			}.execute();
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
		configBuilder.append("SDK version " + android.os.Build.VERSION.SDK_INT + "\n\n");
		
		// application information
		try
			{
			PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_META_DATA);
			configBuilder.append(getString(packageInfo.applicationInfo.labelRes) + " Version " + packageInfo.versionName + " (" + packageInfo.versionCode + ")\n");
			}
		catch (PackageManager.NameNotFoundException exc)
			{
			configBuilder.append("Version unknown\n");
			}
		
		String profiling = summarizeRuntime(getApplicationContext(), PersonalActivity.PROFILING_KEY_ORDER);
		if (profiling != null)
			{
			configBuilder.append("\nPersonal Stats Runtime Profiling (Last Run):\n");
			configBuilder.append(profiling);
			}

		profiling = summarizeRuntime(getApplicationContext(), InterpersonalActivity.PROFILING_KEY_ORDER);
		if (profiling != null)
			{
			configBuilder.append("\n\nInterpersonal Stats Runtime Profiling (Last Run):\n");
			configBuilder.append(profiling);
			}

		profiling = summarizeRuntime(getApplicationContext(), LanguageIdentificationActivity.PROFILING_KEY_ORDER);
		if (profiling != null)
			{
			configBuilder.append("\n\nLanguage Identification Runtime Profiling (Last Run):\n");
			configBuilder.append(profiling);
			}

		sendIntent.putExtra(Intent.EXTRA_TEXT, configBuilder.toString());
		
		startActivity(Intent.createChooser(sendIntent, getString(R.string.send_email_with)));
		}

	public static String summarizeRuntime(Context context, String[] PROFILING_KEYS)
		{
		StringBuilder computeBuilder = new StringBuilder();
		Formatter f = new Formatter(computeBuilder);
		double totalSeconds = 0;
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

		for (String key : PROFILING_KEYS)
			{
			long value = prefs.getLong(key, -1);
			
			key = key.replaceAll(".*:\\s*", "");
			
			// doesn't really need a localization; it's only for me
			f.format("%s: %.1fs\n", key, value / 1000.0);
			totalSeconds += value / 1000.0;
			}
		f.format("Total: %.1fs", totalSeconds);
		return computeBuilder.toString();
		}
	}
