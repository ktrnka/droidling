package edu.udel.trnka.pl;

import static edu.udel.trnka.pl.Tokenizer.isNonword;
import static edu.udel.trnka.pl.Tokenizer.tokenize;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.widget.TextView;

public class PersonalLingActivity extends Activity
	{
	public static final int maxPhrases = 50;
	private boolean scanned = false;
	private WordDistribution corpusUnigrams;
	private HashSet<String> smallStopwords;
	private HashSet<String> largeStopwords;
	private DateDistribution dates;
	
	public double unigramScale = 0.3;
	public double bigramScale = 0.9;
	public double trigramScale = 1.0;
	public double shortMessageFactor = 1.5;
	public double simplePhraseFactor = 1.3;
	
	public HashMap<String,Long> runtime;
	
	public void onCreate(Bundle savedInstanceState)
		{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.personal_basic);
		
		TextView textView = (TextView) findViewById(R.id.phraseText);
		textView.setOnLongClickListener(buildLongClickListener(getString(R.string.key_phrases)));
		
		textView = (TextView) findViewById(R.id.contactText);
		textView.setOnLongClickListener(buildLongClickListener(getString(R.string.contacts)));
		
		textView = (TextView) findViewById(R.id.statText);
		textView.setOnLongClickListener(buildLongClickListener(getString(R.string.stats)));

		textView = (TextView) findViewById(R.id.runtimeText);
		textView.setOnLongClickListener(buildLongClickListener(getString(R.string.runtime)));
		}
	
	public OnLongClickListener buildLongClickListener(final String category)
		{
		return new OnLongClickListener()
			{
			public boolean onLongClick(View v)
				{
				String text = category + " from " + getString(R.string.app_name) + ":\n" + ((TextView)v).getText().toString();
				
				Intent sendIntent = new Intent(Intent.ACTION_SEND);
				sendIntent.setType("text/plain");
				sendIntent.putExtra(Intent.EXTRA_TEXT, text);
				
				startActivity(Intent.createChooser(sendIntent, "Share with..."));
				
				return true;
				}
			};
		}
	
	public void onStart()
		{
		super.onStart();
		
		runtime = new HashMap<String,Long>();
		
		if (!scanned)
			{
			// start progress
			final ProgressDialog progress = ProgressDialog.show(PersonalLingActivity.this, "", "Scanning...", true);
			
			// run thread with callback to stop progress
			new Thread()
				{
				public void run()
					{
					scanSMS();
					
					progress.dismiss();
					}
				}.start();
			scanned = true;
			}
		}
	
	public void loadUnigrams()
		{
		long time = System.currentTimeMillis();
		try
			{
			corpusUnigrams = new WordDistribution(getAssets().open("unigrams.utf8.txt"));
			}
		catch (IOException e)
			{
			corpusUnigrams = null;
			}
		runtime.put("loadUnigrams", System.currentTimeMillis() - time);
		}
	
	public void loadStopwords()
		{
		long time = System.currentTimeMillis();
		smallStopwords = new HashSet<String>();
		try
			{
			// load the small list
			// TODO: move the filename somewhere else
			BufferedReader in = new BufferedReader(new InputStreamReader(getAssets().open("stopwords2.txt")));
			String line;
			while ( (line = in.readLine()) != null)
				{
				line = line.trim();
				if (line.length() > 0)
					smallStopwords.add(line.toLowerCase());
				}
			in.close();
			}
		catch (IOException e)
			{
			// TODO: do something if we can't load
			}

		largeStopwords = new HashSet<String>();
		try
			{
			// load the small list
			// TODO: move the filename somewhere else
			BufferedReader in = new BufferedReader(new InputStreamReader(getAssets().open("stopwords.txt")));
			String line;
			while ( (line = in.readLine()) != null)
				{
				line = line.trim();
				if (line.length() > 0)
					largeStopwords.add(line.toLowerCase());
				}
			in.close();
			}
		catch (IOException e)
			{
			// TODO: do something if we can't load
			}

		runtime.put("loadStopwords", System.currentTimeMillis() - time);
		}
	
	public void scanSMS()
		{
		loadUnigrams();
		loadStopwords();

		// step 1: scan contacts, build a mapping of contact number to name
		long time = System.currentTimeMillis();
		final HashMap<String, String> contactMap = new HashMap<String, String>();

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
		runtime.put("buildContactMap", System.currentTimeMillis() - time);

		// step 2: scan sent messages
		time = System.currentTimeMillis();
		Uri uri = Uri.parse("content://sms/sent");
		Cursor messages = getContentResolver().query(uri, null, null, null, null);

		final HashMap<String, int[]> personCounts = new HashMap<String, int[]>();

		// unigrams, bigrams, trigrams
		final HashMap<String,int[]> unigrams = new HashMap<String,int[]>();
		final HashMap<String,HashMap<String, int[]>> bigrams = new HashMap<String,HashMap<String, int[]>>();
		final HashMap<String,HashMap<String,HashMap<String, int[]>>> trigrams = new HashMap<String,HashMap<String,HashMap<String, int[]>>>();
		
		// totals for those
		int unigramTotal = 0;
		int bigramTotal = 0;
		int trigramTotal = 0;
		
		// full-message distribution (sort messages only)
		final HashMap<String,int[]> shortMessages = new HashMap<String,int[]>();
		int shortMessageTotal = 0;
		final int maxShortMessageLength = 20;
		
		// segmented phrases distribution (think hacky chunking)
		final HashMap<String,int[]> simplePhrases = new HashMap<String,int[]>();
		int simplePhraseTotal = 0;
		
		ArrayList<String> simplePhrase = new ArrayList<String>();
		
		
		int totalMessages = 0;
		int totalWords = 0;
		int totalChars = 0;
		int wordLength = 0;
		
		dates = new DateDistribution();
		
		if (messages.moveToFirst())
			{
			do
				{
				String body = messages.getString(messages.getColumnIndexOrThrow("body"));
				
				long millis = messages.getLong(messages.getColumnIndexOrThrow("date"));
				Date date = new Date(millis);
				dates.add(date);
//				String dateString = date.toString();
//				String[] columns = messages.getColumnNames();
				
				totalMessages++;
				totalChars += body.length();
				
				// handle the simple message thing
				if (body.length() <= maxShortMessageLength)
					{
					String text = body.toLowerCase();

					if (shortMessages.containsKey(text))
						shortMessages.get(text)[0]++;
					else
						shortMessages.put(text, new int[] { 1 });
					shortMessageTotal++;
					}
				
				ArrayList<String> tokens = tokenize(body);
				
				// clear out the simplePhrase sequence
				simplePhrase.clear();
				
				// update the ngrams!
				String previous = null, ppWord = null;
				for (String token : tokens)
					{
					// TODO: change this to truecasing
					token = token.toLowerCase();
					
					// unigrams
					if (unigrams.containsKey(token))
						unigrams.get(token)[0]++;
					else
						unigrams.put(token, new int[] { 1 });
					unigramTotal++;
					
					// filtered unigram stats
					// TODO: compute this from the distribution at the end (faster)
					if (!isNonword(token))
						{
						totalWords++;
						wordLength += token.length();
						}
					
					// simple phrases
					if (!isNonword(token) && !smallStopwords.contains(token))
						{
						// add to the phrase
						simplePhrase.add(token);
						}
					else
						{
						if (simplePhrase.size() > 0)
							{
							StringBuilder phraseBuilder = new StringBuilder();
							phraseBuilder.append(simplePhrase.get(0));
							for (int i = 1; i < simplePhrase.size(); i++)
								{
								phraseBuilder.append(" ");
								phraseBuilder.append(simplePhrase.get(i));
								}
							
							String phraseString = phraseBuilder.toString();
							
							if (simplePhrases.containsKey(phraseString))
								simplePhrases.get(phraseString)[0]++;
							else
								simplePhrases.put(phraseString, new int[] { 1 });
							
							simplePhraseTotal++;
							}
						// flush the phrase
						simplePhrase.clear();
						}
					
					// bigrams
					if (previous != null)
						{
						if (!bigrams.containsKey(previous))
							bigrams.put(previous, new HashMap<String, int[]>());
						
						if (!bigrams.get(previous).containsKey(token))
							bigrams.get(previous).put(token, new int[] { 1 });
						else
							bigrams.get(previous).get(token)[0]++;
						bigramTotal++;
						
						// trigrams
						if (ppWord != null)
							{
							if (!trigrams.containsKey(ppWord))
								trigrams.put(ppWord, new HashMap<String,HashMap<String, int[]>>());
							
							HashMap<String,HashMap<String, int[]>> bigramSubdist = trigrams.get(ppWord);
							
							if (!bigramSubdist.containsKey(previous))
								bigramSubdist.put(previous, new HashMap<String, int[]>());
							
							HashMap<String,int[]> dist = bigramSubdist.get(previous);
							
							if (!dist.containsKey(token))
								dist.put(token, new int[] { 1 });
							else
								dist.get(token)[0]++;
							
							trigramTotal++;
							}
						}
					
					// move the history back
					ppWord = previous;
					previous = token;
					}
				
				// figure out the name of the destination, store it in person
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

				if (personCounts.containsKey(person))
					personCounts.get(person)[0]++;
				else
					personCounts.put(person, new int[] { 1 });
				} while (messages.moveToNext());
			}
		messages.close();
		runtime.put("processSentTexts", System.currentTimeMillis() - time);
		
		time = System.currentTimeMillis();
		// generate candidates
		final HashMap<String,double[]> candidates = new HashMap<String,double[]>();

		// unigram candidates
		for (String word : unigrams.keySet())
			if (!isNonword(word) && !largeStopwords.contains(word))
				candidates.put(word, 
						new double[] { 
								unigramScale * (unigrams.get(word)[0] - corpusUnigrams.expectedFrequency(word, unigramTotal))	
						});
		
		// analyse bigrams
		for (String word1 : bigrams.keySet())
			{
			if (isNonword(word1) || smallStopwords.contains(word1))
				continue;

			for (String word2 : bigrams.get(word1).keySet())
				{
				if (isNonword(word2) || smallStopwords.contains(word2))
					continue;

				int freq = bigrams.get(word1).get(word2)[0];
				
				double freqDiff = freq - corpusUnigrams.expectedFrequency(word1, word2, bigramTotal);
				
				candidates.put(word1 + " " + word2, new double[] { bigramScale * freqDiff });
				}
			}
		
		// analyse trigrams
		for (String word1 : trigrams.keySet())
			{
			if (isNonword(word1) || smallStopwords.contains(word1))
				continue;
			
			for (String word2 : trigrams.get(word1).keySet())
				{
				if (isNonword(word2))
					continue;
				
				for (String word3 : trigrams.get(word1).get(word2).keySet())
					{
					if (isNonword(word3) || smallStopwords.contains(word3))
						continue;
					
					int freq = trigrams.get(word1).get(word2).get(word3)[0];
					double expected = corpusUnigrams.expectedFrequency(word1, word2, word3, trigramTotal);
					
					candidates.put(word1 + " " + word2 + " " + word3, new double[] { trigramScale * (freq - expected) });
					}
				}
			}
		
		// adjust candidates based on phrases, etc
		for (String candidate : candidates.keySet())
			{
			if (simplePhrases.containsKey(candidate))
				candidates.get(candidate)[0] *= simplePhraseFactor * Math.log(simplePhrases.get(candidate)[0]);
			
			if (shortMessages.containsKey(candidate))
				candidates.get(candidate)[0] *= shortMessageFactor * Math.log(shortMessages.get(candidate)[0]);
			}
		
		// sort candidate pairs
		ArrayList<String> pairs = new ArrayList<String>(candidates.keySet());
		Collections.sort(pairs, new Comparator<String>()
			{
				public int compare(String a, String b)
					{
					return (int)(100 * (candidates.get(b)[0] - candidates.get(a)[0]));
					}
			});
		
		// fold unigrams into bigrams (top K bigrams only)
		for (int i = 0; i < pairs.size() && i <= maxPhrases * 2; i++)
			{
			String[] words = pairs.get(i).split(" ");
			
			if (words.length == 2)
				{
				// discount from the first word
				if (candidates.containsKey(words[0]))
					{
					double ratio = bigrams.get(words[0]).get(words[1])[0] / unigrams.get(words[0])[0];
					double discount = ratio * candidates.get(words[0])[0];
					
					candidates.get(words[0])[0] -= discount;
					candidates.get(pairs.get(i))[0] += discount;
					}
				
				// discount from the second word
				if (candidates.containsKey(words[1]))
					{
					double ratio = bigrams.get(words[0]).get(words[1])[0] / unigrams.get(words[1])[0];
					double discount = ratio * candidates.get(words[1])[0];
					
					candidates.get(words[1])[0] -= discount;
					candidates.get(pairs.get(i))[0] += discount;
					}
				}
			}
		
		// fold bigrams into trigrams (top K trigrams only)
		for (int i = 0; i < pairs.size() && i <= maxPhrases * 2; i++)
			{
			String[] words = pairs.get(i).split(" ");
			
			if (words.length == 3)
				{
				String first = words[0] + " " + words[1];
				String second = words[1] + " " + words[2];
				
				// discount from the first pair
				if (candidates.containsKey(first))
					{
					double ratio = trigrams.get(words[0]).get(words[1]).get(words[2])[0] / bigrams.get(words[0]).get(words[1])[0];
					double discount = ratio * candidates.get(first)[0];
					
					candidates.get(first)[0] -= discount;
					candidates.get(pairs.get(i))[0] += discount;
					}
				
				// discount from the second word
				if (candidates.containsKey(second))
					{
					double ratio = trigrams.get(words[0]).get(words[1]).get(words[2])[0] / bigrams.get(words[1]).get(words[2])[0];
					double discount = ratio * candidates.get(second)[0];
					
					candidates.get(second)[0] -= discount;
					candidates.get(pairs.get(i))[0] += discount;
					}
				}
			}

		// resort candidate pairs
		Collections.sort(pairs, new Comparator<String>()
			{
				public int compare(String a, String b)
					{
					return (int)(100 * (candidates.get(b)[0] - candidates.get(a)[0]));
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

		// build out the general stats
		final StringBuilder statsBuilder = new StringBuilder();
		Formatter f = new Formatter(statsBuilder, Locale.US);
		statsBuilder.append(totalMessages + " sent messages\n");
		statsBuilder.append((totalWords / totalMessages) + " words per message\n");
		statsBuilder.append((totalChars / totalMessages) + " chars per message\n");
		f.format("%.1f average word length\n", wordLength / (double)totalWords);
		f.format("%.1f texts per month\n", dates.computeTextsPerMonth());
		
		// day of the week histogram
		String[] days = new String[]{ "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday" };
		int[] dayHist = dates.computeDayOfWeekHistogram();
		for (int i = 0; i < dayHist.length; i++)
			statsBuilder.append("Texts sent on " + days[i] + "s: " + dayHist[i] + "\n");

		// build the display
		final StringBuilder phraseBuilder = new StringBuilder();
		int current = 0;
		for (String wordPair : pairs)
			{
			phraseBuilder.append(wordPair);
			phraseBuilder.append("\n");
			
			if (++current >= maxPhrases)
				break;
			}
		
		final StringBuilder contactBuilder = new StringBuilder();
		for (String person : people)
			{
			if (personCounts.get(person)[0] <= 1)
				break;
			
			contactBuilder.append(person);
			contactBuilder.append(": ");
			contactBuilder.append(personCounts.get(person)[0]);
			contactBuilder.append(" messages\n");
			}
		runtime.put("other", System.currentTimeMillis() - time);
		
		final StringBuilder computeBuilder = new StringBuilder();
		f = new Formatter(computeBuilder, Locale.US);
		for (String unit : runtime.keySet())
			{
			f.format("%s: %.1fs\n", unit, runtime.get(unit)/1000.0);
			}

		runOnUiThread(new Runnable()
			{
			public void run()
				{
				TextView textView = (TextView) findViewById(R.id.phraseText);
				
				textView.setText(phraseBuilder.toString());

				textView = (TextView) findViewById(R.id.contactText);
				textView.setText(contactBuilder.toString());

				textView = (TextView) findViewById(R.id.statText);
				textView.setText(statsBuilder.toString());

				textView = (TextView) findViewById(R.id.runtimeText);
				textView.setText(computeBuilder.toString());
				}
			});
		}
	}
