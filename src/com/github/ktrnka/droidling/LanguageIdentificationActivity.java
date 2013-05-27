package com.github.ktrnka.droidling;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;

import com.github.ktrnka.droidling.R;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;


/**
 * @author Keith
 *
 */
public class LanguageIdentificationActivity extends RefreshableActivity
	{
	public static final String EXTRA_LANGUAGES = "languages";
	private boolean started = false;
	
	private LanguageIdentifier langID;
	private HashMap<String,CorpusStats> sentStats;
	private HashMap<String,LanguageIdentifier.Identification> identifications;
	
	private static final String TAG = "com.github.ktrnka.droidling/LanguageIdentificationActivity";
	static final int PROGRESS_DIALOG = 0;
	
	public static final String LOAD_CONTACTS_KEY = "LanguageIdentificationActivity: loading contacts";
	public static final String LOAD_MESSAGES_KEY = "LanguageIdentificationActivity: loading messages";
	public static final String IDENTIFYING_KEY = "LanguageIdentificationActivity: identifying languages";
	public static final String GENERATE_TEXT_KEY = "LanguageIdentificationActivity: building text";
	private static final String SAVE_DISPLAY_KEY = "LanguageIdentificationActivity: caching results";
	
	public static final String[] PROFILING_KEY_ORDER = { LOAD_CONTACTS_KEY, LOAD_MESSAGES_KEY, IDENTIFYING_KEY, GENERATE_TEXT_KEY, SAVE_DISPLAY_KEY };
	private static final String DISPLAY_FILENAME = "LanguageIdentificationActivity.cache";
	private static final String PROCESSED_MESSAGES = "LanguageIdentificationActivity.processedMessages";
	
	private InterpersonalStats stats;

	public void onCreate(Bundle savedInstanceState)
		{
		super.onCreate(savedInstanceState);
		setHelpActivity(AboutLangIDActivity.class);
		setContentView(R.layout.simple_scroll);
		}
	
	public void onStart()
		{
		super.onStart();
	
		if (!started)
			refresh(false);
		}

	protected void refresh(final boolean forceRefresh)
	    {
		setRefreshActionButtonState(true);

		// run thread with callback to stop progress
		new Thread()
			{
			public void run()
				{
				cachedBuildAll(forceRefresh);
				setRefreshActionButtonState(false);
				}
			}.start();
		started = true;
		}

	/**
	 * The main processing of the language identification task.
	 */
	public void buildAll()
		{
		long time = System.currentTimeMillis();
		ExtendedApplication app = (ExtendedApplication) getApplication();
		if (!app.blockingLoadContacts())
			{
			warning("No contacts found");
			}
		setPreference(LOAD_CONTACTS_KEY, System.currentTimeMillis() - time);
		
		time = System.currentTimeMillis();
		buildUnigramModels();
		setPreference(LOAD_MESSAGES_KEY, System.currentTimeMillis() - time);
		
		time = System.currentTimeMillis();
		identifyLanguages();
		setPreference(IDENTIFYING_KEY, System.currentTimeMillis() - time);
		}

	private void buildRuntimeDisplay()
		{
		final String runtimeString = HomeActivity.summarizeRuntime(getApplicationContext(), PROFILING_KEY_ORDER);
		
		runOnUiThread(new Runnable()
			{
			public void run()
				{
				ViewGroup parent = (ViewGroup) findViewById(R.id.linear);
	
				LayoutInflater inflater = getLayoutInflater();
				
				parent.addView(inflateResults(inflater, getString(R.string.runtime), runtimeString));
				}
			});
		}

	private void buildLIDDisplays()
		{
		runOnUiThread(new Runnable()
			{
			public void run()
				{
				ViewGroup parent = (ViewGroup) findViewById(R.id.linear);
				parent.removeAllViews();
	
				LayoutInflater inflater = getLayoutInflater();
				
				for (InterpersonalStats.Item item : stats.list)
					{
					parent.addView(inflateResults(inflater, item.name, item.details));
					}
				}
			});
		}

	private void identifyLanguages()
		{
		// load the language identification file
		try
			{
			langID = new LanguageIdentifier(getAssets().open("lid_model.utf8.txt"));
			}
		catch (IOException e)
			{
			error("Failed to load language ID asset: " + e);
			}
		
		identifications = new HashMap<String, LanguageIdentifier.Identification>();
		String localeLanguageCode2 = Locale.getDefault().getLanguage();
		for (String contactName : sentStats.keySet())
			{
			CorpusStats stats = sentStats.get(contactName);
			LanguageIdentifier.Identification identification = langID.identify(stats.getUnigrams(), localeLanguageCode2, 1.1);
			identifications.put(contactName, identification);
			}
		
		stats = new InterpersonalStats();
		
		ArrayList<String> contactList = new ArrayList<String>(identifications.keySet());
		Collections.sort(contactList);
		for (String contactName : contactList)
			{
			LanguageIdentifier.Identification identification = identifications.get(contactName);

			String[] topLanguages = identification.getTopN();
			
			// TODO: replace with a nicer StringBuilder version
			String details = identification.describeTopN() + "\nWhy " + topLanguages[0] + " and not " + topLanguages[1] + "?\n" + identification.explain();
			
			stats.add(contactName, details);
			}
		
		long time = System.currentTimeMillis();
		try
	        {
	        stats.writeTo(openFileOutput(DISPLAY_FILENAME, Context.MODE_PRIVATE));
	        }
        catch (IOException e)
	        {
	        Log.e(TAG, "Failed to save displayStats");
	        Log.e(TAG, Log.getStackTraceString(e));
	        }
		setPreference(SAVE_DISPLAY_KEY, System.currentTimeMillis() - time);
		}

	/**
	 * Build unigram models of messages sent to each contact.
	 */
	private void buildUnigramModels()
		{
		ExtendedApplication app = (ExtendedApplication) getApplication();

		Cursor messages = getContentResolver().query(Sms.SENT_URI, new String[] { Sms.BODY, Sms.ADDRESS }, null, null, null);
		int numMessages = messages.getCount();

		sentStats = new HashMap<String,CorpusStats>();
		
		if (messages.moveToFirst())
			{
			final int addressIndex = messages.getColumnIndexOrThrow(Sms.ADDRESS);
			final int bodyIndex = messages.getColumnIndexOrThrow(Sms.BODY);
			do
				{
				// figure out the name of the destination, store it in person
				String recipientId = messages.getString(addressIndex);

				String recipientName = app.lookupContactName(recipientId);

				if (recipientName != null)
					{
					String body = messages.getString(bodyIndex);
					if (!sentStats.containsKey(recipientName))
						sentStats.put(recipientName, new CorpusStats());
					
					sentStats.get(recipientName).train(body);
					}
				} while (messages.moveToNext());
			}
		else
			{
			error(getString(R.string.error_no_sent_sms));
			messages.close();
			return;
			}
		messages.close();
		
		setPreference(PROCESSED_MESSAGES, numMessages);
		}

	/**
	 * Inflates a R.layout.result with the specified title and details, using
	 * the specified inflater
	 * 
	 * @param inflater
	 * @param name
	 * @param details
	 * @return the inflated view
	 */
	public View inflateResults(LayoutInflater inflater, final CharSequence name, final CharSequence details)
		{
		View view = inflater.inflate(R.layout.results_generic, null);
		TextView  textView = (TextView) view.findViewById(android.R.id.text1);
		textView.setText(name);


		textView = (TextView) view.findViewById(android.R.id.text2);
		textView.setText(details);

		ImageView shareView = (ImageView) view.findViewById(R.id.share);
		shareView.setOnClickListener(new View.OnClickListener()
			{
			public void onClick(View v)
				{
				String subject = "Shared stats from " + getString(R.string.app_name);
				String text = "Stats: " + name + ":\n" + details;

				Intent sendIntent = new Intent(Intent.ACTION_SEND);
				sendIntent.setType("text/plain");
				sendIntent.putExtra(Intent.EXTRA_TEXT, text);
				sendIntent.putExtra(Intent.EXTRA_SUBJECT, subject);

				startActivity(Intent.createChooser(sendIntent, "Share with..."));
				}
			});

		return view;
		}

	private void cachedBuildAll(boolean forceRefresh)
	    {
	    if (forceRefresh)
	    	{
	    	buildAll();
	    	}
	    else
	    	{
	    	try
	            {
	            stats = new InterpersonalStats(openFileInput(DISPLAY_FILENAME));
	            }
            catch (IOException e)
	            {
	            buildAll();
	            }
	    	}

		long time = System.currentTimeMillis();
		buildLIDDisplays();
		setPreference(GENERATE_TEXT_KEY, System.currentTimeMillis() - time);
		
		if (HomeActivity.DEVELOPER_MODE)
			buildRuntimeDisplay();
	    }

	@Override
    protected boolean hasNewData()
	    {
	    Cursor messages = getContentResolver().query(Sms.SENT_URI, new String[] { Sms.ADDRESS }, null, null, null);
	    int numMessages = messages.getCount();
	    messages.close();
	    
	    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		if (prefs.getInt(PROCESSED_MESSAGES, 0) != numMessages)
			return true;
	    
	    return false;
	    }
	}
