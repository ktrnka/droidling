package com.github.ktrnka.droidling;

import java.util.ArrayList;
import java.util.HashMap;

import com.github.ktrnka.droidling.R;

import android.app.ListActivity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;

public class HomeActivity extends ListActivity
	{
	// TODO: use strings.xml for localization
	private static final String[] names = { "Personal Stats", "Interpersonal Stats", "Language ID Playground", "Send Feedback", "About the stats", "About the app" };
	private static String[] descriptions = { "Analyse sent messages.", "Compare SMS text analytics with contacts.", null, null, null, null };
	
	// TODO:  I don't like how this uses parallel arrays.  I'd much rather do something like make an instance that has all this (could I do it by overriding toString in the Activities?)
	private static final Class<?>[] activities = { PersonalActivity.class, InterpersonalActivity.class, LanguageIdentificationActivity.class, null, AboutStatsActivity.class, AboutActivity.class };
	
	public static final boolean DEVELOPER_MODE = true;
	
	public void onCreate(Bundle savedInstanceState)
		{
		super.onCreate(savedInstanceState);
		
		// I don't like this code AT ALL, but getString is an instance method :(
		descriptions[3] = "Send email to " + getString(R.string.developer_email);
		
		// build the structure we need to pass to SimpleAdapter
		ArrayList<HashMap<String,String>> fields = new ArrayList<HashMap<String,String>>();
		for (int i = 0; i < names.length; i++)
			{
			HashMap<String,String> item = new HashMap<String,String>();
			item.put("name", names[i]);
			item.put("desc", descriptions[i]);
			fields.add(item);
			}
		
		setListAdapter(new DescriptionMenuAdapter(this, names, descriptions));
		}

	public void onListItemClick(ListView list, View view, int position, long id)
		{
		if (position < activities.length && activities[position] != null)
			{
			// launch the activity if it's found
			Intent intent = new Intent(this, activities[position]);
			startActivity(intent);
			}
		else if (position < names.length && names[position].equals("Send Feedback"))
			{
			sendFeedback();
			}
		else
			{
			Toast.makeText(getApplicationContext(), getString(R.string.not_implemented), Toast.LENGTH_SHORT).show();
			}

	
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
		
		/*
		configBuilder.append("\n\nOrientation: ");
		switch (config.orientation)
		{
		case Configuration.ORIENTATION_LANDSCAPE:
			configBuilder.append("landscape\n");
			break;
		case Configuration.ORIENTATION_PORTRAIT:
			configBuilder.append("portrait\n");
			break;
		default:
			configBuilder.append("unknown (" + config.orientation + ")\n");
			break;
		}
		*/

		configBuilder.append("Locale: " + config.locale.toString() + "\n");
		
		/*
		TelephonyManager tm = (TelephonyManager) getApplicationContext().getSystemService(TELEPHONY_SERVICE);
		if (tm != null)
			configBuilder.append("Network country: " + tm.getNetworkCountryIso() + "\n");
		*/
		
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

		/*
		if (android.os.Build.BRAND != null)
			configBuilder.append("Branding: " + android.os.Build.BRAND + "\n");
		*/
		
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