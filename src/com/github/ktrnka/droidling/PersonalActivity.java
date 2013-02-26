package com.github.ktrnka.droidling;

import static com.github.ktrnka.droidling.Tokenizer.isNonword;
import static com.github.ktrnka.droidling.Tokenizer.tokenize;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
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
import java.util.Locale;

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
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class PersonalActivity extends Activity implements OnItemSelectedListener
	{
	public static final int maxPhrases = 50;
	private boolean scanned = false;
	private WordDistribution corpusUnigrams;
	private HashSet<String> smallStopwords;
	private HashSet<String> largeStopwords;
	private DateDistribution dates;

	// constants to tweak the scoring of phrases.  This is probably language-specific and should be extracted to a config.
	public double unigramScale = 0.25;
	public double bigramScale = 0.9;
	public double trigramScale = 1.2;
	public double shortMessageFactor = 1.3;
	public double simplePhraseFactor = 1.6;

	public static HashMap<String, Long> runtime;

	static final int PROGRESS_DIALOG = 0;
	private ProgressDialog progress;
	private static final String TAG = "com.github.ktrnka.droidling.PersonalActivity";
	
	private StringBuilder[] keyPhraseTexts;
	private int previousItemSelected;
	
	private static final int graphBarBottomColor = Color.rgb(25, 89, 115);
	private static final int graphBarTopColor = Color.rgb(17, 60, 77);
	
	public void onCreate(Bundle savedInstanceState)
		{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.simple_scroll);
		
		keyPhraseTexts = new StringBuilder[2];
		keyPhraseTexts[0] = new StringBuilder();
		keyPhraseTexts[1] = new StringBuilder();
		previousItemSelected = 0;
		}

	public void onStart()
		{
		super.onStart();

		if (runtime == null)
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

	/**
	 * Get the scaling factor to apply to fonts.
	 */
	private float getFontScale()
		{
		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);
		return metrics.scaledDensity;
		}
	
	/**
	 * Gets a file like en.unigrams.utf8.txt if it exists in the assets.  If not, returns null.
	 * 
	 * TODO: Basically this is mimicking Resources.  I need to double-check why I chose to
	 * use Assets instead.
	 * 
	 * @param suffix The suffix to append to the language and/or country code.
	 * @return The filename if it exists.  Null if not.
	 */
	private String getLocalizedAsset(String suffix)
		{
		try
			{
			String languageCode2 = Locale.getDefault().getLanguage();
			String filename = languageCode2 + suffix;
	
			String[] assets = getAssets().list("");
			for (String asset : assets)
				{
				if (asset.equals(filename))
					{
					return filename;
					}
				}
			}
		catch (IOException e)
			{
			Log.e(TAG, "getLocalizedAsset failed to list assets");
			}
		
		return null;
		}

	private void loadUnigrams()
		{
		long time = System.currentTimeMillis();

		try
			{
			String unigramFilename = getLocalizedAsset(".unigrams.utf8.txt");
			
			if (unigramFilename != null)
				{
				corpusUnigrams = new WordDistribution(getAssets().open(unigramFilename), false);
				}
			else
				{
				// There isn't a unigram file for this language.
				// TODO: Build a baseline unigram model from the set of received messages.
				corpusUnigrams = null;
				}
			}
		catch (IOException e)
			{
			corpusUnigrams = null;
			Log.e(TAG, "loadUnigrams failed");
			}
		runtime.put("load unigrams", System.currentTimeMillis() - time);
		}

	private void loadStopwords()
		{
		long time = System.currentTimeMillis();
		smallStopwords = new HashSet<String>();
		
		try
			{
			String smallStopwordsFile = getLocalizedAsset(".stopwords.small.utf8.txt");
			
			if (smallStopwordsFile != null)
				{
				BufferedReader in = new BufferedReader(new InputStreamReader(getAssets().open(smallStopwordsFile)), 8192);
				String line;
				while ((line = in.readLine()) != null)
					{
					line = line.trim();
					if (line.length() > 0)
						smallStopwords.add(line.toLowerCase());
					}
				in.close();				
				}
			}
		catch (IOException e)
			{
			Log.e(TAG, "loadStopwords failed for small file");
			}

		largeStopwords = new HashSet<String>();
		try
			{
			String largeStopwordsFile = getLocalizedAsset(".stopwords.medium.utf8.txt");
			
			if (largeStopwordsFile != null)
				{
				BufferedReader in = new BufferedReader(new InputStreamReader(getAssets().open(largeStopwordsFile)), 8192);
				String line;
				while ((line = in.readLine()) != null)
					{
					line = line.trim();
					if (line.length() > 0)
						largeStopwords.add(line.toLowerCase());
					}
				in.close();
				}
			}
		catch (IOException e)
			{
			Log.e(TAG, "loadStopwords failed for large file");
			}

		runtime.put("load stopwords", System.currentTimeMillis() - time);
		}

	// TODO: This code is duplicated in Interpersonal and shouldn't be.
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

	// TODO: This code is duplicated in Interpersonal and shouldn't be.
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
		long time = System.currentTimeMillis();
		ExtendedApplication app = (ExtendedApplication) getApplication();
		if (!app.blockingLoadContacts())
			{
			warning("No contacts found");
			}
		runtime.put("scanning contacts", System.currentTimeMillis() - time);

		// step 2: scan sent messages
		time = System.currentTimeMillis();
		String[] sentColumns = new String[] { Sms.BODY, Sms.DATE, Sms.ADDRESS };
		Cursor messages = getContentResolver().query(Sms.SENT_URI, sentColumns, null, null, null);

		final HashMap<String, int[]> personCounts = new HashMap<String, int[]>();
		
		CorpusStats sentStats = new CorpusStats();

		// full-message distribution (sort messages only)
		final HashMap<String, int[]> shortMessages = new HashMap<String, int[]>();
		int shortMessageTotal = 0;
		final int maxShortMessageLength = 20;

		// segmented phrases distribution (think hacky chunking)
		final HashMap<String, int[]> simplePhrases = new HashMap<String, int[]>();
		int simplePhraseTotal = 0;

		ArrayList<String> simplePhrase = new ArrayList<String>();

		dates = new DateDistribution();
		
		// reusable phrase builder
		StringBuilder reusableBuilder = new StringBuilder();

		if (messages.moveToFirst())
			{
			final int bodyIndex = messages.getColumnIndexOrThrow(Sms.BODY);
			final int dateIndex = messages.getColumnIndexOrThrow(Sms.DATE);
			final int addressIndex = messages.getColumnIndexOrThrow(Sms.ADDRESS);
			
			do
				{
				// TODO: Replace this with truecasing
				String body = messages.getString(bodyIndex).toLowerCase();

				long millis = messages.getLong(dateIndex);
				Date date = new Date(millis);
				dates.add(date);

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
				
				sentStats.train(tokens, body.length());

				// update the simple phrases
				for (String token : tokens)
					{
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
							reusableBuilder.setLength(0);
							reusableBuilder.append(simplePhrase.get(0));
							for (int i = 1; i < simplePhrase.size(); i++)
								{
								reusableBuilder.append(' ');
								reusableBuilder.append(simplePhrase.get(i));
								}

							String phraseString = reusableBuilder.toString();

							if (simplePhrases.containsKey(phraseString))
								simplePhrases.get(phraseString)[0]++;
							else
								simplePhrases.put(phraseString, new int[] { 1 });

							simplePhraseTotal++;
							}
						// flush the phrase
						simplePhrase.clear();
						}
					}

				// figure out the name of the destination, store it in person
				String address = messages.getString(addressIndex);
				
				String displayName = app.lookupContactName(address);
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
			error(getString(R.string.error_no_sent_sms));
			return;
			}
		messages.close();
		runtime.put("scanning sent messages", System.currentTimeMillis() - time);

		time = System.currentTimeMillis();
		// generate candidates
		final HashMap<String, double[]> candidates = new HashMap<String, double[]>();
		
		final HashMap<String,int[]> frequencyCandidates = new HashMap<String,int[]>();

		// unigram candidates
		for (String word : sentStats.unigrams.keySet())
			{
			if (!isNonword(word))
				frequencyCandidates.put(word, new int[] { sentStats.unigrams.get(word)[0] } );
			
			if (!isNonword(word) && !largeStopwords.contains(word))
				candidates.put(
						word,
						new double[] { unigramScale
								* (sentStats.unigrams.get(word)[0] - corpusUnigrams.expectedFrequency(word, sentStats.unigramTotal)) });
			}

		// analyse bigrams
		StringBuilder ngramBuilder = new StringBuilder();
		for (String word1 : sentStats.bigrams.keySet())
			{
			if (isNonword(word1))
				continue;
			
			for (String word2 : sentStats.bigrams.get(word1).keySet())
				{
				if (isNonword(word2))
					continue;
				
				// concatenation with StringBuilder for performance
				ngramBuilder.setLength(0);
				ngramBuilder.append(word1);
				ngramBuilder.append(' ');
				ngramBuilder.append(word2);
				String ngram = ngramBuilder.toString();
				
				frequencyCandidates.put(ngram, new int[] { sentStats.bigrams.get(word1).get(word2)[0] } );

				if (smallStopwords.contains(word1) || smallStopwords.contains(word2))
					continue;

				int freq = sentStats.bigrams.get(word1).get(word2)[0];

				double freqDiff = freq - corpusUnigrams.expectedFrequency(word1, word2, sentStats.bigramTotal);

				candidates.put(ngram, new double[] { bigramScale * freqDiff });
				}
			}

		// analyse trigrams
		for (String word1 : sentStats.trigrams.keySet())
			{
			if (isNonword(word1))
				continue;
			
			for (String word2 : sentStats.trigrams.get(word1).keySet())
				{
				if (isNonword(word2))
					continue;
				
				for (String word3 : sentStats.trigrams.get(word1).get(word2).keySet())
					{
					if (isNonword(word3))
						continue;
					
					// concatenation with StringBuilder for performance
					ngramBuilder.setLength(0);
					ngramBuilder.append(word1);
					ngramBuilder.append(' ');
					ngramBuilder.append(word2);
					ngramBuilder.append(' ');
					ngramBuilder.append(word3);
					String ngram = ngramBuilder.toString();
					
					frequencyCandidates.put(ngram, new int[] { sentStats.trigrams.get(word1).get(word2).get(word3)[0] } );
					
					if (smallStopwords.contains(word1) || smallStopwords.contains(word3))
						continue;

					int freq = sentStats.trigrams.get(word1).get(word2).get(word3)[0];
					double expected = corpusUnigrams.expectedFrequency(word1, word2, word3, sentStats.trigramTotal);

					candidates.put(ngram, new double[] { trigramScale * (freq - expected) });
					}
				}
			}
		
		ArrayList<String> basicPhrases = new ArrayList<String>(frequencyCandidates.keySet());
		Collections.sort(basicPhrases, new Comparator<String>()
			{
			public int compare(String a, String b)
				{
				return Double.compare(frequencyCandidates.get(b)[0], frequencyCandidates.get(a)[0]);
				}
			});
		
		int basicCurrent = 0;
		for (String wordPair : basicPhrases)
			{
			keyPhraseTexts[1].append(wordPair);
			keyPhraseTexts[1].append('\n');

			if (++basicCurrent >= maxPhrases)
				break;
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
					double ratio = sentStats.bigrams.get(words[0]).get(words[1])[0] / (double) sentStats.unigrams.get(words[0])[0];
					double discount = ratio * candidates.get(words[0])[0];

					candidates.get(words[0])[0] -= discount;
					candidates.get(pairs.get(i))[0] += discount;
					
					if (Double.isNaN(ratio) || Double.isNaN(candidates.get(pairs.get(i))[0]))
						Log.e(TAG, "NaN in " + pairs.get(i) + " / " + words[0]);
					}

				// discount from the second word
				if (candidates.containsKey(words[1]))
					{
					double ratio = sentStats.bigrams.get(words[0]).get(words[1])[0] / (double) sentStats.unigrams.get(words[1])[0];
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
				// This doesn't look pretty, but it's much faster than normal +
				ngramBuilder.setLength(0);
				ngramBuilder.append(words[0]);
				ngramBuilder.append(' ');
				ngramBuilder.append(words[1]);
				String first = ngramBuilder.toString();
				
				ngramBuilder.setLength(0);
				ngramBuilder.append(words[1]);
				ngramBuilder.append(' ');
				ngramBuilder.append(words[2]);
				String second = ngramBuilder.toString();

				// discount from the first pair
				if (candidates.containsKey(first))
					{
					double ratio = sentStats.trigrams.get(words[0]).get(words[1]).get(words[2])[0]
							/ (double) sentStats.bigrams.get(words[0]).get(words[1])[0];
					double discount = ratio * candidates.get(first)[0];

					candidates.get(first)[0] -= discount;
					candidates.get(pairs.get(i))[0] += discount;
					if (Double.isNaN(ratio) || Double.isNaN(candidates.get(pairs.get(i))[0]))
						Log.e(TAG, "NaN in " + pairs.get(i) + " / " + first);

					}

				// discount from the second word
				if (candidates.containsKey(second))
					{
					double ratio = sentStats.trigrams.get(words[0]).get(words[1]).get(words[2])[0]
							/ (double) sentStats.bigrams.get(words[1]).get(words[2])[0];
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
		final StringBuilder phraseBuilder = keyPhraseTexts[0];
		int current = 0;
		for (String wordPair : pairs)
			{
			phraseBuilder.append(wordPair);
			phraseBuilder.append('\n');

			if (++current >= maxPhrases)
				break;
			}

		if (phraseBuilder.length() == 0)
			phraseBuilder.append(getString(R.string.no_phrases));

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

			contactBuilder.append(getString(R.string.num_messages_format, person, personCounts.get(person)[0]));
			}

		if (contactBuilder.length() == 0)
			contactBuilder.append(getString(R.string.no_frequent_contacts));

		// build out the general stats
		final StringBuilder statsBuilder = new StringBuilder();

		statsBuilder.append(getString(R.string.num_sent_format, sentStats.messages));
		statsBuilder.append(getString(R.string.num_sent_per_month_format, dates.computeTextsPerMonth()));
		
		statsBuilder.append(getString(R.string.words_per_text_format, sentStats.filteredWords / sentStats.messages));
		statsBuilder.append(getString(R.string.chars_per_text_format, sentStats.chars / sentStats.messages));
		statsBuilder.append(getString(R.string.chars_per_word_format, sentStats.filteredWordLength / (double) sentStats.filteredWords));

		// day of the week histogram
		final int[] dayHist = dates.computeDayOfWeekHistogram();

		// time of day histogram
		final int[] hourHist = dates.computeHourHistogram();

		runtime.put("generating descriptions", System.currentTimeMillis() - time);

		// RUNTIME DISPLAY
		final String runtimeString;
		if (HomeActivity.DEVELOPER_MODE)
			runtimeString = summarizeRuntime();
		else
			runtimeString = null;

		/*************** SHOW IT *******************/
		runOnUiThread(new Runnable()
			{
			@SuppressWarnings("unused")
            public void run()
				{
				ViewGroup parent = (ViewGroup) findViewById(R.id.linear);

				LayoutInflater inflater = getLayoutInflater();

				parent.addView(inflatePhraseResults(inflater, phraseBuilder.toString()));
				parent.addView(inflateResults(inflater, getString(R.string.contacts), contactBuilder.toString()));
				parent.addView(inflateResults(inflater, getString(R.string.stats), statsBuilder.toString()));
				
				GraphicalView dayChart = buildDayChart(PersonalActivity.this, dayHist);
				parent.addView(inflateChart(inflater, getString(R.string.day_of_week), dayChart));
				
				GraphicalView hourChart = buildHourChart(PersonalActivity.this, hourHist);
				parent.addView(inflateChart(inflater, getString(R.string.time_of_day), hourChart));

				if (runtimeString != null)
					parent.addView(inflateResults(inflater, getString(R.string.runtime), runtimeString));
				}
			});
		}

	public static String summarizeRuntime()
		{
		if (runtime == null)
			return null;
		
		StringBuilder computeBuilder = new StringBuilder();
		Formatter f = new Formatter(computeBuilder);
		double totalSeconds = 0;
		for (String unit : runtime.keySet())
			{
			// doesn't really need a localization; it's only for me
			f.format("%s: %.1fs\n", unit, runtime.get(unit) / 1000.0);
			totalSeconds += runtime.get(unit) / 1000.0;
			}
		f.format("Total: %.1fs", totalSeconds);
		return computeBuilder.toString();
		}

	/**
	 * Inflates a R.layout.phrases with the specified details, using
	 * the specified inflater, registers callbacks for the spinner, etc.
	 * 
	 * @param inflater
	 * @param details
	 * @return the inflated view
	 */
	public View inflatePhraseResults(LayoutInflater inflater, final String details)
		{
		View view = inflater.inflate(R.layout.phrases, null);

		TextView textView = (TextView) view.findViewById(android.R.id.text2);
		textView.setText(details);
		
		Spinner spinner = (Spinner) view.findViewById(R.id.spinner1);
		spinner.setOnItemSelectedListener(this);

		ImageView shareView = (ImageView) view.findViewById(R.id.share);
		shareView.setOnClickListener(new View.OnClickListener()
			{
			public void onClick(View v)
				{
				String subject = "Shared stats from " + getString(R.string.app_name);
				String text = "Stats: " + getString(R.string.key_phrases) + ":\n" + details;

				Intent sendIntent = new Intent(Intent.ACTION_SEND);
				sendIntent.setType("text/plain");
				sendIntent.putExtra(Intent.EXTRA_TEXT, text);
				sendIntent.putExtra(Intent.EXTRA_SUBJECT, subject);

				startActivity(Intent.createChooser(sendIntent, "Share with..."));
				}
			});

		return view;
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
		XYMultipleSeriesRenderer renderer = createBaseChartTheme(0, 8, 0, ymax, getFontScale());

		// set the strings and we're good to go!
	    renderer.setXTitle("Day");
	    renderer.setYTitle("Messages");

		String[] days = new String[] { "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday" };
	    for (int i = 0; i < days.length; i++)
	    	renderer.addXTextLabel(i + 1, days[i].substring(0, 1));

	    final BarChart chart = new BarChart(dataset, renderer, BarChart.Type.DEFAULT);
	    GraphicalView view = new GraphicalView(c, chart);

		return view;
		}
	
	/**
	 * Does all the parts of renderer setup that don't depend on the actual datapoints, under
	 * the assumption that we're rendering a single data series.  The caller should still call
	 * addXTextLabel and setXTitle, setYTitle.
	 * @return
	 */
	private static XYMultipleSeriesRenderer createBaseChartTheme(int xmin, int xmax, int ymin, int ymax, float scale)
		{
		XYMultipleSeriesRenderer renderer = new XYMultipleSeriesRenderer();
		
		// text sizes
		renderer.setAxisTitleTextSize(18 * scale);
		renderer.setChartTitleTextSize(20 * scale);
		renderer.setLabelsTextSize(14 * scale);
		renderer.setLegendTextSize(14 * scale);
		
		// a post on StackOverflow suggests the X/Y axis labels stick to margins, so if they aren't big enough it'll render labels on top of other things
		// http://stackoverflow.com/a/12527041/1492373
		// Margin order is { top, left, bottom, right }
		int[] margins = renderer.getMargins();
		// top margin:  This is a total hack; I saw a y-axis label near the top of the axis that had a small amount of the top of the number cutoff and this fixes it.
		margins[0] += 2;
		// left margin:  Also a hack.  I *think* the left margin needs to be enough for the y-axis labels, plus the y-axis title.  But I don't know how to get
		// the pixel width of the y-axis labels (and this point of the code is graph-independent).  It'll take some refactoring to cleanse this unholy mess.
		margins[1] = (int)(3 * renderer.getLabelsTextSize());
		renderer.setMargins(margins);

		// data series settings
		SimpleSeriesRenderer r = new SimpleSeriesRenderer();
		r.setColor(Color.DKGRAY);
		r.setDisplayChartValues(false);
	    r.setGradientEnabled(true);
	    r.setGradientStart(0, graphBarBottomColor);	// start = bottom of the bar
	    r.setGradientStop(ymax, graphBarTopColor);	// this color will be the top of the max height bar
		renderer.addSeriesRenderer(r);
		
	    renderer.setOrientation(Orientation.HORIZONTAL);
		renderer.setBarSpacing(0.2f);
		
		// colors
	    renderer.setAxesColor(Color.DKGRAY);
	    renderer.setLabelsColor(Color.DKGRAY);
	    renderer.setXLabelsColor(Color.DKGRAY);
	    renderer.setYLabelsColor(0, Color.DKGRAY);

	    // there's a bug in achartengine that requires you to set the color portion even with full transparency
	    renderer.setMarginsColor(Color.argb(0, 1, 1, 1));
	    renderer.setBackgroundColor(Color.WHITE);
	    renderer.setApplyBackgroundColor(false);
	    
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
		XYMultipleSeriesRenderer renderer = createBaseChartTheme(first - 1, last + 1, 0, ymax, getFontScale());

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
	    
		return view;
		}

	public void onItemSelected(AdapterView<?> parent, View view, int pos, long id)
		{
		if (pos == previousItemSelected)
			return;
		
		View phrasesView = findViewById(R.id.phrase_layout);
		if (phrasesView != null)
			{
			if (pos < keyPhraseTexts.length)
				{
				TextView textView = (TextView) phrasesView.findViewById(android.R.id.text2);
				if (textView != null)
					{
					textView.setText(keyPhraseTexts[pos]);
					}
				}
			}
		else
			Log.d(TAG, "Can't find phrase_layout");
		
		previousItemSelected = pos;
		}

	public void onNothingSelected(AdapterView<?> arg0)
		{
		}
	}
