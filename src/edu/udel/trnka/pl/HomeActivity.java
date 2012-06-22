package edu.udel.trnka.pl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import edu.udel.trnka.pl.R;
import static edu.udel.trnka.pl.Tokenizer.tokenize;
import static edu.udel.trnka.pl.Tokenizer.isNonword;
import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class HomeActivity extends Activity
	{
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
		{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		final Button button1 = (Button) findViewById(R.id.button1);
		button1.setOnClickListener(new View.OnClickListener()
			{

				public void onClick(View v)
					{
					scanSMS();
					}
			});
		}

	public void scanSMS()
		{
		TextView text = (TextView) findViewById(R.id.outputLabel);
		
		text.setText("Loading unigrams...");
		WordDistribution unigrams;
		try
			{
			unigrams = new WordDistribution(getAssets().open("unigrams.utf8.txt"));
			}
		catch (IOException e)
			{
			unigrams = null;
			}

		// TODO: This should really set some sort of progress bar and run the
		// scan in another thread
		text.setText("Scanning...");

		// step 1: scan contacts, build a mapping of contact number to name
		HashMap<String, String> contactMap = new HashMap<String, String>();

		String numberName = ContactsContract.CommonDataKinds.Phone.NUMBER;
		String labelName = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME;
		String[] phoneLookupProjection = new String[] { numberName, labelName };

		Cursor phones = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
				phoneLookupProjection, null, null, null);

		if (phones.moveToFirst())
			{
			do
				{
				String number = phones.getString(phones.getColumnIndex(numberName));
				String label = phones.getString(phones.getColumnIndex(labelName));

				contactMap.put(number, label);
				} while (phones.moveToNext());
			}
		phones.close();

		// step 2: scan sent messages
		Uri uri = Uri.parse("content://sms/sent");
		Cursor messages = getContentResolver().query(uri, null, null, null, null);

		final HashMap<String, int[]> personCounts = new HashMap<String, int[]>();

		final HashMap<String,HashMap<String, int[]>> bigrams = new HashMap<String,HashMap<String, int[]>>();
		int total = 0;
		
		if (messages.moveToFirst())
			{
			do
				{
				String body = messages.getString(messages.getColumnIndexOrThrow("body"));
				
				if (unigrams != null)
					{
					ArrayList<String> tokens = tokenize(body);
					
					// update the bigrams!
					String previous = null;
					for (String token : tokens)
						{
						token = token.toLowerCase();
						if (previous != null)
							{
							if (!bigrams.containsKey(previous))
								bigrams.put(previous, new HashMap<String, int[]>());
							
							if (!bigrams.get(previous).containsKey(token))
								bigrams.get(previous).put(token, new int[] { 1 });
							else
								bigrams.get(previous).get(token)[0]++;
							total++;
							}
						previous = token;
						}
					}
				
				String address = messages.getString(messages.getColumnIndexOrThrow("address"));

				String person = address;

				if (contactMap.containsKey(address))
					person = contactMap.get(address);
				else
					{
					address = PhoneNumberUtils.formatNumber(address);
					if (contactMap.containsKey(address))
						person = contactMap.get(address);
					}

				/*
				 * This is super inefficient
				 * Uri lookupUri = Uri.withAppendedPath(PhoneLookup
				 * .CONTENT_FILTER_URI, Uri.encode(address)); Cursor
				 * lookupCursor = getContentResolver().query(lookupUri,
				 * phoneLookupProjection, null, null, null); if
				 * (lookupCursor.moveToFirst()) { person = lookupCursor
				 * .getString(lookupCursor.getColumnIndex (labelName)); }
				 */

				if (personCounts.containsKey(person))
					personCounts.get(person)[0]++;
				else
					personCounts.put(person, new int[] { 1 });
				} while (messages.moveToNext());
			}
		messages.close();

		
		// analyse bigrams
		final HashMap<String,double[]> scores = new HashMap<String,double[]>();
		if (unigrams != null)
			{
			for (String word1 : bigrams.keySet())
				{
				if (isNonword(word1))
					continue;

				for (String word2 : bigrams.get(word1).keySet())
					{
					if (isNonword(word2))
						continue;

					int freq = bigrams.get(word1).get(word2)[0];
					double prob = freq / (double)total;
					
					double freqDiff = freq - unigrams.expectedFrequency(word1, word2, total);
					//if (freqDiff < 2)
					//	continue;
					
					double pmi = prob/unigrams.expectedProb(word1, word2);
					
					double score = bigrams.get(word1).get(word2)[0];
					// freqDiff * pmi
					
					scores.put(word1 + " " + word2, new double[] { freqDiff });
					}
				}
			}
		
		// sort candidate pairs
		ArrayList<String> pairs = new ArrayList<String>(scores.keySet());
		Collections.sort(pairs, new Comparator<String>()
			{
				public int compare(String a, String b)
					{
					return (int)(100 * (scores.get(b)[0] - scores.get(a)[0]));
					}
			});
		
		// analyse contacts
		ArrayList<String> people = new ArrayList<String>(personCounts.keySet());
		Collections.sort(people, new Comparator<String>()
			{
				public int compare(String a, String b)
					{
					return personCounts.get(b)[0] - personCounts.get(a)[0];
					}
			});


		// build the display
		StringBuilder b = new StringBuilder();
		for (String wordPair : pairs)
			{
			b.append(wordPair);
			b.append("\n");
			}
		for (String person : people)
			{
			if (personCounts.get(person)[0] <= 1)
				break;
			
			b.append(person);
			b.append(": ");
			b.append(personCounts.get(person)[0]);
			b.append("\n");
			}

		text.setText(b.toString());
		}
	}