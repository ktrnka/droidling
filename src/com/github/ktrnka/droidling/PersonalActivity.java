package com.github.ktrnka.droidling;

import static com.github.ktrnka.droidling.Tokenizer.isNonword;
import static com.github.ktrnka.droidling.Tokenizer.tokenize;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import org.achartengine.GraphicalView;
import org.achartengine.chart.BarChart;
import org.achartengine.model.CategorySeries;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.renderer.SimpleSeriesRenderer;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYMultipleSeriesRenderer.Orientation;

import com.github.ktrnka.droidling.R;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Color;
import android.graphics.Paint.Align;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.ContactsContract;
import android.provider.MediaStore.Images;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class PersonalActivity extends Activity
	{
	public static final int maxPhrases = 50;
	private boolean scanned = false;
	private WordDistribution corpusUnigrams;
	private HashSet<String> smallStopwords;
	private HashSet<String> largeStopwords;
	private DateDistribution dates;

	public double unigramScale = 0.25;
	public double bigramScale = 0.9;
	public double trigramScale = 1.2;
	public double shortMessageFactor = 1.3;
	public double simplePhraseFactor = 1.6;

	public HashMap<String, Long> runtime;

	static final int PROGRESS_DIALOG = 0;
	private ProgressDialog progress;
	private static final String TAG = "com.github.ktrnka.droidling/PersonalActivity";
	
	private HashMap<String,ArrayList<String[]>> contactMap;

	public void onCreate(Bundle savedInstanceState)
		{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.simple_scroll);
		}

	public void onStart()
		{
		super.onStart();

		runtime = new HashMap<String, Long>();

		if (!scanned)
			{
			// start progress
			// TODO: This is deprecated; I should use DialogFragment with FragmentManager via Android compatibility package
			showDialog(PROGRESS_DIALOG);

			// run thread with callback to stop progress
			new Thread()
				{
					public void run()
						{
						scanSMS();

						dismissDialog(PROGRESS_DIALOG);
						progress.dismiss();
						}
				}.start();
			scanned = true;
			}
		}

	protected Dialog onCreateDialog(int id)
		{
		switch (id)
			{
			case PROGRESS_DIALOG:
				progress = new ProgressDialog(PersonalActivity.this);
				progress.setIndeterminate(true);
				progress.setMessage(getString(R.string.loading));
				return progress;
			default:
				return null;
			}
		}


	private void loadUnigrams()
		{
		long time = System.currentTimeMillis();
		try
			{
			corpusUnigrams = new WordDistribution(getAssets().open("unigrams.utf8.txt"), false);
			//corpusUnigrams = new WordDistribution(getAssets().open("unigrams.bin"), true);
			}
		catch (IOException e)
			{
			corpusUnigrams = null;
			}
		runtime.put("load unigrams", System.currentTimeMillis() - time);
		}

	private void loadStopwords()
		{
		long time = System.currentTimeMillis();
		smallStopwords = new HashSet<String>();
		try
			{
			// TODO: move the filename somewhere else
			BufferedReader in = new BufferedReader(new InputStreamReader(getAssets().open("stopwords-small.txt")), 8192);
			String line;
			while ((line = in.readLine()) != null)
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
			// TODO: move the filename somewhere else
			BufferedReader in = new BufferedReader(new InputStreamReader(getAssets().open("stopwords-medium.txt")), 8192);
			String line;
			while ((line = in.readLine()) != null)
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

		runtime.put("load stopwords", System.currentTimeMillis() - time);
		}

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

	public void scanSMS()
		{
		loadUnigrams();
		loadStopwords();

		// step 1: scan contacts, build a mapping of contact number to name
		long time;
		buildContactMap();

		// step 2: scan sent messages
		time = System.currentTimeMillis();
		String[] sentColumns = new String[] { Sms.BODY, Sms.DATE, Sms.ADDRESS };
		Cursor messages = getContentResolver().query(Sms.SENT_URI, sentColumns, null, null, null);

		final HashMap<String, int[]> personCounts = new HashMap<String, int[]>();

		// unigrams, bigrams, trigrams
		final HashMap<String, int[]> unigrams = new HashMap<String, int[]>();
		final HashMap<String, HashMap<String, int[]>> bigrams = new HashMap<String, HashMap<String, int[]>>();
		final HashMap<String, HashMap<String, HashMap<String, int[]>>> trigrams = new HashMap<String, HashMap<String, HashMap<String, int[]>>>();

		// totals for those
		int unigramTotal = 0;
		int bigramTotal = 0;
		int trigramTotal = 0;

		// full-message distribution (sort messages only)
		final HashMap<String, int[]> shortMessages = new HashMap<String, int[]>();
		int shortMessageTotal = 0;
		final int maxShortMessageLength = 20;

		// segmented phrases distribution (think hacky chunking)
		final HashMap<String, int[]> simplePhrases = new HashMap<String, int[]>();
		int simplePhraseTotal = 0;

		ArrayList<String> simplePhrase = new ArrayList<String>();

		int totalMessages = 0;
		int totalWords = 0;
		int totalChars = 0;
		int wordLength = 0;

		dates = new DateDistribution();

		if (messages.moveToFirst())
			{
			final int bodyIndex = messages.getColumnIndexOrThrow(Sms.BODY);
			final int dateIndex = messages.getColumnIndexOrThrow(Sms.DATE);
			final int addressIndex = messages.getColumnIndexOrThrow(Sms.ADDRESS);
			
			do
				{
				String body = messages.getString(bodyIndex).toLowerCase();

				long millis = messages.getLong(dateIndex);
				Date date = new Date(millis);
				dates.add(date);

				totalMessages++;
				totalChars += body.length();

				// handle the simple message thing
				if (body.length() <= maxShortMessageLength)
					{
					String text = body;

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
					// TODO: first short term solution would be to run lowercase on the entire message in a single pass first
					//token = token.toLowerCase();

					// unigrams
					if (unigrams.containsKey(token))
						unigrams.get(token)[0]++;
					else
						unigrams.put(token, new int[] { 1 });
					unigramTotal++;

					// filtered unigram stats
					// TODO: compute this from the distribution at the end
					// (faster)
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
								trigrams.put(ppWord, new HashMap<String, HashMap<String, int[]>>());

							HashMap<String, HashMap<String, int[]>> bigramSubdist = trigrams.get(ppWord);

							if (!bigramSubdist.containsKey(previous))
								bigramSubdist.put(previous, new HashMap<String, int[]>());

							HashMap<String, int[]> dist = bigramSubdist.get(previous);

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
				String address = messages.getString(addressIndex);
				
				String displayName = lookupName(address);
				if (displayName != null)
					{
					if (personCounts.containsKey(displayName))
						personCounts.get(displayName)[0]++;
					else
						personCounts.put(displayName, new int[] { 1 });
					}
				} while (messages.moveToNext());
			}
		else
			{
			messages.close();
			error("No sent messages found, aborting.");
			return;
			}
		messages.close();
		runtime.put("scanning sent messages", System.currentTimeMillis() - time);

		time = System.currentTimeMillis();
		// generate candidates
		final HashMap<String, double[]> candidates = new HashMap<String, double[]>();

		// unigram candidates
		for (String word : unigrams.keySet())
			if (!isNonword(word) && !largeStopwords.contains(word))
				candidates.put(
						word,
						new double[] { unigramScale
								* (unigrams.get(word)[0] - corpusUnigrams.expectedFrequency(word, unigramTotal)) });

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

					candidates
							.put(word1 + " " + word2 + " " + word3, new double[] { trigramScale * (freq - expected) });
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
					return Double.compare(candidates.get(b)[0], candidates.get(a)[0]);
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
					double ratio = bigrams.get(words[0]).get(words[1])[0] / (double) unigrams.get(words[0])[0];
					double discount = ratio * candidates.get(words[0])[0];

					candidates.get(words[0])[0] -= discount;
					candidates.get(pairs.get(i))[0] += discount;
					
					if (Double.isNaN(ratio) || Double.isNaN(candidates.get(pairs.get(i))[0]))
						Log.e(TAG, "NaN in " + pairs.get(i) + " / " + words[0]);
					}

				// discount from the second word
				if (candidates.containsKey(words[1]))
					{
					double ratio = bigrams.get(words[0]).get(words[1])[0] / (double) unigrams.get(words[1])[0];
					double discount = ratio * candidates.get(words[1])[0];

					candidates.get(words[1])[0] -= discount;
					candidates.get(pairs.get(i))[0] += discount;
					if (Double.isNaN(ratio) || Double.isNaN(candidates.get(pairs.get(i))[0]))
						Log.e(TAG, "NaN in " + pairs.get(i) + " / " + words[1]);
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
					double ratio = trigrams.get(words[0]).get(words[1]).get(words[2])[0]
							/ (double) bigrams.get(words[0]).get(words[1])[0];
					double discount = ratio * candidates.get(first)[0];

					candidates.get(first)[0] -= discount;
					candidates.get(pairs.get(i))[0] += discount;
					if (Double.isNaN(ratio) || Double.isNaN(candidates.get(pairs.get(i))[0]))
						Log.e(TAG, "NaN in " + pairs.get(i) + " / " + first);

					}

				// discount from the second word
				if (candidates.containsKey(second))
					{
					double ratio = trigrams.get(words[0]).get(words[1]).get(words[2])[0]
							/ (double) bigrams.get(words[1]).get(words[2])[0];
					double discount = ratio * candidates.get(second)[0];

					candidates.get(second)[0] -= discount;
					candidates.get(pairs.get(i))[0] += discount;

					if (Double.isNaN(ratio) || Double.isNaN(candidates.get(pairs.get(i))[0]))
						Log.e(TAG, "NaN in " + pairs.get(i) + " / " + second);
					}
				}
			}

		// resort candidate pairs
		Collections.sort(pairs, new Comparator<String>()
			{
				public int compare(String a, String b)
					{
					return Double.compare(candidates.get(b)[0], candidates.get(a)[0]);
					}
			});

		runtime.put("finding the best phrases", System.currentTimeMillis() - time);

		time = System.currentTimeMillis();

		/*********************** BUILD THE STRINGS ************************/

		// KEY PHRASE DISPLAY
		final StringBuilder phraseBuilder = new StringBuilder();
		int current = 0;
		for (String wordPair : pairs)
			{
			phraseBuilder.append(wordPair);
			phraseBuilder.append("\n");

			if (++current >= maxPhrases)
				break;
			}

		if (phraseBuilder.length() == 0)
			phraseBuilder.append("no phrases found");

		// CONTACT DISPLAY
		final StringBuilder contactBuilder = new StringBuilder();

		ArrayList<String> people = new ArrayList<String>(personCounts.keySet());
		Collections.sort(people, new Comparator<String>()
			{
				public int compare(String a, String b)
					{
					return personCounts.get(b)[0] - personCounts.get(a)[0];
					}
			});

		for (String person : people)
			{
			if (personCounts.get(person)[0] <= 1)
				break;

			contactBuilder.append(person);
			contactBuilder.append(": ");
			contactBuilder.append(personCounts.get(person)[0]);
			contactBuilder.append(" messages\n");
			}

		if (contactBuilder.length() == 0)
			contactBuilder.append("no frequent contacts found");

		// build out the general stats
		final StringBuilder statsBuilder = new StringBuilder();
		// TODO: confirm that this correctly uses Locale when it's German or a Euro Locale
		Formatter f = new Formatter(statsBuilder);
		statsBuilder.append(totalMessages + " sent messages\n");
		f.format("%.1f texts per month\n", dates.computeTextsPerMonth());
		statsBuilder.append((totalWords / totalMessages) + " words per message\n");
		statsBuilder.append((totalChars / totalMessages) + " chars per message\n");
		f.format("%.1f average word length\n", wordLength / (double) totalWords);

		// day of the week histogram
		final int[] dayHist = dates.computeDayOfWeekHistogram();

		// time of day histogram
		final int[] hourHist = dates.computeHourHistogram();

		runtime.put("generating descriptions", System.currentTimeMillis() - time);

		// RUNTIME DISPLAY
		final StringBuilder computeBuilder = new StringBuilder();
		f = new Formatter(computeBuilder);
		double totalSeconds = 0;
		for (String unit : runtime.keySet())
			{
			f.format("%s: %.1fs\n", unit, runtime.get(unit) / 1000.0);
			totalSeconds += runtime.get(unit) / 1000.0;
			}
		f.format("Total: %.1fs", totalSeconds);

		/*************** SHOW IT *******************/
		runOnUiThread(new Runnable()
			{
			public void run()
				{
				ViewGroup parent = (ViewGroup) findViewById(R.id.linear);

				LayoutInflater inflater = getLayoutInflater();

				parent.addView(inflateResults(inflater, getString(R.string.key_phrases), phraseBuilder.toString()));
				parent.addView(inflateResults(inflater, getString(R.string.contacts), contactBuilder.toString()));
				parent.addView(inflateResults(inflater, getString(R.string.stats), statsBuilder.toString()));
				
				GraphicalView dayChart = buildDayChart(PersonalActivity.this, dayHist);
				parent.addView(inflateChart(inflater, getString(R.string.day_of_week), dayChart));
				
				GraphicalView hourChart = buildHourChart(PersonalActivity.this, hourHist);
				parent.addView(inflateChart(inflater, getString(R.string.time_of_day), hourChart));

				parent.addView(inflateResults(inflater, getString(R.string.runtime), computeBuilder.toString()));
				
				}
			});
		}

	private void buildContactMap()
		{
		long time = System.currentTimeMillis();
		contactMap = new HashMap<String,ArrayList<String[]>>();

		String numberName = ContactsContract.CommonDataKinds.Phone.NUMBER;
		String labelName = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME;
		String[] phoneLookupProjection = new String[] { numberName, labelName };

		Cursor phones = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
				phoneLookupProjection, null, null, null);

		if (phones.moveToFirst())
			{
			final int phoneIndex = phones.getColumnIndex(numberName);
			final int labelIndex = phones.getColumnIndex(labelName);
			
			do
				{
				String number = phones.getString(phoneIndex);
				String label = phones.getString(labelIndex);
				
				String minMatch = PhoneNumberUtils.toCallerIDMinMatch(number);
				
				if (contactMap.containsKey(minMatch))
					{
					contactMap.get(minMatch).add(new String[] { number, label });
					}
				else
					{
					ArrayList<String[]> matchList = new ArrayList<String[]>();
					matchList.add(new String[] { number, label });
					contactMap.put(minMatch, matchList);
					}
				
				} while (phones.moveToNext());
			}
		else
			{
			warning("No contacts found.");
			}
		phones.close();
		runtime.put("scanning contacts", System.currentTimeMillis() - time);
		}
	
	/**
	 * The number may be in a very different format than the way it's stored in contacts.
	 * @param number
	 * @return The display name value if found, null if not.
	 */
	private String lookupName(String number)
		{
		if (contactMap == null)
			return null;
		
		String minMatch = PhoneNumberUtils.toCallerIDMinMatch(number);
		
		if (!contactMap.containsKey(minMatch))
			return null;
		
		ArrayList<String[]> matchList = contactMap.get(minMatch);
		if (matchList.size() == 1)
			return matchList.get(0)[1];
		
		for (String[] pair : matchList)
			{
			if (PhoneNumberUtils.compare(number, pair[0]))
				return pair[1];
			}
		
		return null;
		}

	public static HashMap<String, String> buildNameDesc(String name, String desc)
		{
		HashMap<String, String> map = new HashMap<String, String>();
		map.put("name", name);
		map.put("desc", desc);
		return map;
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
		// contacts
		View view = inflater.inflate(R.layout.result, null);

		TextView textView = (TextView) view.findViewById(android.R.id.text1);
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
	
	public View inflateChart(LayoutInflater inflater, final String title, final GraphicalView graph)
		{
		View view = inflater.inflate(R.layout.graphed_results, null);

		// setup the title
		TextView textView = (TextView) view.findViewById(android.R.id.text1);
		textView.setText(title);
		
		// setup the graph
		ViewGroup container = (ViewGroup) view.findViewById(R.id.graphGroup);

		// TODO: This method for getting height is deprecated
		int screenHeight = getWindowManager().getDefaultDisplay().getHeight();
		
		container.addView(graph, 
				new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, screenHeight / 3));
		
		// setup the sharing action
		ImageView share = (ImageView) view.findViewById(R.id.share);
		share.setOnClickListener(new View.OnClickListener()
			{
			public void onClick(View v)
				{
				share(graph.toBitmap(), title, "Shared: histogram of " + title.toLowerCase());
				}
			});

		return view;
		}
	
	public void share(Bitmap bitmap, String title, String subject)
		{
		// the problem with this method is that it adds to your gallery each time you share and can't remove
        //String path = Images.Media.insertImage(getContentResolver(), bitmap, title, null);
        //Uri screenshotUri = Uri.parse(path);
        
		// In the future, I should switch this to getExternalFilesDir
        File file = new File(Environment.getExternalStorageDirectory(), "sms_ling.png");
        try
        	{
        	OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
        	bitmap.compress(CompressFormat.PNG, 100, out);
        	out.close();
        	
            Intent intent = new Intent( android.content.Intent.ACTION_SEND);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + file.getAbsolutePath()));
            intent.setType("image/png");

            intent.putExtra(Intent.EXTRA_SUBJECT, subject);

            startActivity(Intent.createChooser(intent, "Send email..."));
        	}
        catch (IOException e)
        	{
        	error("Unable to share image");
        	}
		}
	
	/**
	 * Build a chart for day-of-the-week histogram
	 * @param c
	 * @param dayHistogram
	 * @return The drawable View.  Be sure to set the height of it or it won't show!
	 */
	public GraphicalView buildDayChart(Context c, int[] dayHistogram)
		{
		/****************** BUILD THE DATA SET **********************/
		XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();

		CategorySeries series = new CategorySeries("Day");
		for (int i = 0; i < dayHistogram.length; i++)
			{
			series.add(dayHistogram[i]);
			}
		dataset.addSeries(series.toXYSeries());
		
		// determine the Y height
		int ymax = 0;
		for (int day : dayHistogram)
			if (day > ymax)
				ymax = day;
		
		ymax *= 1.05;
		
		/******************** BUILD THE RENDERER ********************/
		XYMultipleSeriesRenderer renderer = createBaseChartTheme(0, 8, 0, ymax);

		// set the strings and we're good to go!
	    renderer.setXTitle("Day");
	    renderer.setYTitle("Messages");

		String[] days = new String[] { "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday" };
	    for (int i = 0; i < days.length; i++)
	    	renderer.addXTextLabel(i + 1, days[i].substring(0, 1));

	    final BarChart chart = new BarChart(dataset, renderer, BarChart.Type.DEFAULT);
	    GraphicalView view = new GraphicalView(c, chart);
	    
	    /*
	    view.setOnClickListener(new View.OnClickListener()
			{
			public void onClick(View v)
				{
		    	Intent intent = new Intent(PersonalActivity.this, GraphicalActivity.class);
		    	intent.putExtra(ChartFactory.CHART, chart);
		    	intent.putExtra(ChartFactory.TITLE, "Days of the week");
		    	startActivity(intent);
				}
			});
	    view.setClickable(true);
	    */

		return view;
		}
	
	/**
	 * Does all the parts of renderer setup that don't depend on the actual datapoints, under
	 * the assumption that we're rendering a single data series.  The caller should still call
	 * addXTextLabel and setXTitle, setYTitle.
	 * @return
	 */
	private static XYMultipleSeriesRenderer createBaseChartTheme(int xmin, int xmax, int ymin, int ymax)
		{
		XYMultipleSeriesRenderer renderer = new XYMultipleSeriesRenderer();
		
		// text sizes
		renderer.setAxisTitleTextSize(18);
		renderer.setChartTitleTextSize(20);
		renderer.setLabelsTextSize(14);
		renderer.setLegendTextSize(14);

		// data series settings
		SimpleSeriesRenderer r = new SimpleSeriesRenderer();
		r.setColor(Color.DKGRAY);
		r.setDisplayChartValues(false);
	    r.setGradientEnabled(true);
	    r.setGradientStart(0, Color.rgb(25, 89, 115));
	    r.setGradientStop(ymax, Color.rgb(17, 60, 77));
		renderer.addSeriesRenderer(r);
	    
	    renderer.setOrientation(Orientation.HORIZONTAL);
		renderer.setBarSpacing(0.2f);
		
		// colors
	    renderer.setAxesColor(Color.DKGRAY);
	    renderer.setLabelsColor(Color.DKGRAY);
	    renderer.setBackgroundColor(Color.WHITE);
	    renderer.setMarginsColor(Color.WHITE);
	    renderer.setXLabelsColor(Color.DKGRAY);
	    renderer.setYLabelsColor(0, Color.DKGRAY);
	    
	    // size
	    renderer.setXAxisMin(xmin);
	    renderer.setXAxisMax(xmax);
	    renderer.setYAxisMin(ymin);
	    renderer.setYAxisMax(ymax);
	    
	    renderer.setYLabelsAlign(Align.RIGHT);

	    renderer.setShowAxes(true);
	    renderer.setShowLabels(true);
	    renderer.setShowLegend(false);

	    // tick marks + labels - we don't want any for X cause that will be labels
		renderer.setYLabels(4);
		renderer.setXLabels(0);
		
		// disable interaction
		renderer.setPanEnabled(false, false);
		renderer.setZoomEnabled(false);
		
		renderer.setInScroll(true);

	    return renderer;
		}
	
	/**
	 * Build the chart View for the hour-of-day histogram
	 * @param c
	 * @param timeData
	 * @return The drawable View.  Be sure to set the height of it or it won't show!
	 */
	public GraphicalView buildHourChart(Context c, int[] timeData)
		{
		/****************** BUILD THE DATA SET **********************/
		XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();

		CategorySeries series = new CategorySeries("Day");
		for (int i = 1; i < timeData.length; i++)
			{
			series.add(timeData[i]);
			}
		dataset.addSeries(series.toXYSeries());
		
		int first = 0, last = 24;
		for (int i = 1; i < timeData.length; i++)
			{
			if (first == 0 && timeData[i] > 0)
				first = i;

			if (timeData[i] > 0)
				last = i;
			}
		

		// determine the Y height
		int ymax = 0;
		for (int day : timeData)
			if (day > ymax)
				ymax = day;
		
		ymax *= 1.05;
		
		/******************** BUILD THE RENDERER ********************/
		XYMultipleSeriesRenderer renderer = createBaseChartTheme(first - 1, last + 1, 0, ymax);

		// set the strings and we're good to go!
	    renderer.setXTitle("Hour");
	    renderer.setYTitle("Messages");

	    // TODO: These constant labels won't work for everyone
	    renderer.addXTextLabel(7.5, "8 AM");
	    renderer.addXTextLabel(11.5, "noon"); 
	    renderer.addXTextLabel(17.5, "5 PM"); 
	    renderer.addXTextLabel(21.5, "10 PM");    
	    
	    /**************** BUILD THE VIEW *********************/
	    final BarChart chart = new BarChart(dataset, renderer, BarChart.Type.DEFAULT);
	    GraphicalView view = new GraphicalView(c, chart);
	    
	    /*
	    view.setOnClickListener(new View.OnClickListener()
			{
			public void onClick(View v)
				{
		    	Intent intent = new Intent(PersonalActivity.this, GraphicalActivity.class);
		    	intent.putExtra(ChartFactory.CHART, chart);
		    	intent.putExtra(ChartFactory.TITLE, "Days of the week");
		    	startActivity(intent);
				}
			});
	    view.setClickable(true);
*/
		return view;
		}
	}
