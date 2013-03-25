package com.github.ktrnka.droidling;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import com.github.ktrnka.droidling.R;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


/**
 * @author Keith
 *
 */
public class LanguageIdentificationActivity extends Activity
	{
	private boolean started = false;
	private ProgressDialog progress;
	
	private LanguageIdentifier langID;
	private HashMap<String,CorpusStats> sentStats;
	private HashMap<String,LanguageIdentifier.Identification> identifications;
	
	private static final String TAG = "com.github.ktrnka.droidling/LanguageIdentificationActivity";
	static final int PROGRESS_DIALOG = 0;
	
	public static final String LOAD_CONTACTS_KEY = "LanguageIdentificationActivity: loading contacts";
	public static final String LOAD_MESSAGES_KEY = "LanguageIdentificationActivity: loading messages";
	public static final String IDENTIFYING_KEY = "LanguageIdentificationActivity: identifying languages";
	public static final String GENERATE_TEXT_KEY = "LanguageIdentificationActivity: building text";
	
	public static final String[] PROFILING_KEY_ORDER = { LOAD_CONTACTS_KEY, LOAD_MESSAGES_KEY, IDENTIFYING_KEY, GENERATE_TEXT_KEY };

	public void onCreate(Bundle savedInstanceState)
		{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.simple_scroll);
		}
	
	public void onStart()
		{
		super.onStart();
	
		if (!started)
			{
			// start progress
			// TODO: This is deprecated; I should use DialogFragment with FragmentManager via Android compatibility package
			showDialog(PROGRESS_DIALOG);
	
			// run thread with callback to stop progress
			new Thread()
				{
				public void run()
					{
					process();

					dismissDialog(PROGRESS_DIALOG);
					progress.dismiss();
					}
				}.start();
				started = true;
			}
		}
	
	protected Dialog onCreateDialog(int id)
		{
		switch (id)
			{
			case PROGRESS_DIALOG:
				progress = new ProgressDialog(LanguageIdentificationActivity.this);
				progress.setIndeterminate(true);
				progress.setMessage(getString(R.string.loading));
				return progress;
			default:
				return null;
			}
		}

	// TODO: This code is duplicated and shouldn't be.
	private void setPreference(String name, long longValue)
		{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		SharedPreferences.Editor editor = prefs.edit();
		editor.putLong(name, longValue);
		editor.commit();
		}

	// TODO: This code is duplicated and shouldn't be.
	public void warning(final String message)
		{
		runOnUiThread(new Runnable()
			{
				public void run()
					{
					Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
					}
			});
		}

	// TODO: This code is duplicated and shouldn't be.
	public void error(final String message)
		{
		runOnUiThread(new Runnable()
			{
				public void run()
					{
					Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
					}
			});
		}
	
	/**
	 * The main processing of the language identification task.
	 */
	public void process()
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
		
		time = System.currentTimeMillis();
		buildHelpDisplay();
		buildLIDDisplays();
		setPreference(GENERATE_TEXT_KEY, System.currentTimeMillis() - time);
		
		if (HomeActivity.DEVELOPER_MODE)
			buildRuntimeDisplay();
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
		if (langID == null || identifications == null)
			return;
		
		final ArrayList<String> titles = new ArrayList<String>();
		final ArrayList<String> descriptions = new ArrayList<String>();
		for (String contactName : identifications.keySet())
			{
			LanguageIdentifier.Identification identification = identifications.get(contactName);
			
			titles.add(contactName);
			String[] topLanguages = identification.getTopN();
			descriptions.add(identification.describeTopN() + "\nWhy " + topLanguages[0] + " and not " + topLanguages[1] + "?\n" + identification.explain());
			}
		
		runOnUiThread(new Runnable()
			{
			public void run()
				{
				ViewGroup parent = (ViewGroup) findViewById(R.id.linear);
	
				LayoutInflater inflater = getLayoutInflater();
				
				for (int i = 0; i < titles.size() && i < descriptions.size(); i++)
					{
					parent.addView(inflateResults(inflater, titles.get(i), descriptions.get(i)));
					}
				}
			});
		}

	/**
	 * Create a display that explains the languages that can be identified.
	 */
	private void buildHelpDisplay()
		{
		final StringBuilder languageBuilder = new StringBuilder();

		ArrayList<String> languageList = langID.getSupportedLanguages();
		for (String language : languageList)
			{
			languageBuilder.append(language);
			languageBuilder.append("\n");
			}
		
		runOnUiThread(new Runnable()
			{
			public void run()
				{
				ViewGroup parent = (ViewGroup) findViewById(R.id.linear);
	
				LayoutInflater inflater = getLayoutInflater();
				
				parent.addView(inflateResults(inflater, "Languages", languageBuilder.toString()));
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
		}

	private void buildUnigramModels()
		{
		ExtendedApplication app = (ExtendedApplication) getApplication();

		Cursor messages = getContentResolver().query(Sms.SENT_URI, new String[] { Sms.BODY, Sms.ADDRESS }, null, null, null);

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
		}

	/**
	 * Inflates a R.layout.result with the specified title and details, using
	 * the specified inflater
	 * 
	 * @param inflater
	 * @param title
	 * @param details
	 * @return the inflated view
	 */
	public View inflateResults(LayoutInflater inflater, final String title, final String details)
		{
		View view = inflater.inflate(R.layout.result, null);
		TextView  textView = (TextView) view.findViewById(android.R.id.text1);
		textView.setText(title);


		textView = (TextView) view.findViewById(android.R.id.text2);
		textView.setText(details);

		ImageView shareView = (ImageView) view.findViewById(R.id.share);
		shareView.setOnClickListener(new View.OnClickListener()
			{
			public void onClick(View v)
				{
				String subject = "Shared stats from " + getString(R.string.app_name);
				String text = "Stats: " + title + ":\n" + details;

				Intent sendIntent = new Intent(Intent.ACTION_SEND);
				sendIntent.setType("text/plain");
				sendIntent.putExtra(Intent.EXTRA_TEXT, text);
				sendIntent.putExtra(Intent.EXTRA_SUBJECT, subject);

				startActivity(Intent.createChooser(sendIntent, "Share with..."));
				}
			});

		return view;
		}
	}
