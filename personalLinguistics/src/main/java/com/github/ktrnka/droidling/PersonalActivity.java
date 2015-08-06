
package com.github.ktrnka.droidling;

import static com.github.ktrnka.droidling.Tokenizer.isNonword;
import static com.github.ktrnka.droidling.Tokenizer.tokenize;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
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
import org.json.JSONException;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Color;
import android.graphics.Paint.Align;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
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

import com.fima.cardsui.views.CardUI;
import com.github.ktrnka.droidling.helpers.Util;

public class PersonalActivity extends RefreshableActivity implements OnItemSelectedListener {
    public static final int maxPhrases = 50;
    private boolean scanned = false;

    /**
     * Unigrams from a background corpus for the locale language. May be null if
     * none available.
     */
    private WordDistribution corpusUnigrams;
    private HashSet<String> smallStopwords;
    private HashSet<String> largeStopwords;
    private DateDistribution dates;

    // constants to tweak the scoring of phrases. This is probably
    // language-specific and should be extracted to a config.
    public static final double unigramScale = 0.25;
    public static final double bigramScale = 0.9;
    public static final double trigramScale = 1.2;
    public static final double shortMessageFactor = 1.3;
    public static final double simplePhraseFactor = 1.6;

    private static final String TAG = "PersonalActivity";

    /**
     * the string or near-string stats to display
     */
    private PersonalStats displayStats;

    /**
     * which of the key phrase sortings in displayStats to display
     */
    private int displayPhraseIndex;

    private static final int graphBarBottomColor = Color.rgb(25, 89, 115);
    private static final int graphBarTopColor = Color.rgb(17, 60, 77);

    public static final String MESSAGE_LOOP_KEY = "PersonalActivity: scanning messages";
    public static final String LOAD_UNIGRAMS_KEY = "PersonalActivity: loading unigrams";
    public static final String LOAD_STOPWORDS_KEY = "PersonalActivity: loading stopwords";
    public static final String LOAD_CONTACTS_KEY = "PersonalActivity: loading contacts";
    public static final String SELECT_CANDIDATES_KEY = "PersonalActivity: finding the best candidates";
    public static final String GENERATE_DESCRIPTIONS_KEY = "PersonalActivity: generating descriptions";
    public static final String SAVE_DISPLAY_KEY = "PersonalActivity: caching results";

    public static final String[] PROFILING_KEY_ORDER = {
            LOAD_UNIGRAMS_KEY, LOAD_STOPWORDS_KEY, LOAD_CONTACTS_KEY, MESSAGE_LOOP_KEY,
            SELECT_CANDIDATES_KEY, GENERATE_DESCRIPTIONS_KEY, SAVE_DISPLAY_KEY
    };

    public static final boolean LOG_PHRASES = false;
    private static final String DISPLAY_FILENAME = "PersonalActivity.cache";
    private static final String PROCESSED_SENT_MESSAGES = "PersonalActivity.processedMessages";

    private File logFile;
    private CardUI mCardView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHelpActivity(AboutPersonalActivity.class);

        // setContentView(R.layout.simple_scroll);
        // cards UI test
        setContentView(R.layout.cardsui_main);
        mCardView = (CardUI) findViewById(R.id.cardsview);
        mCardView.setSwipeable(false);

        // draw empty list to start?
        mCardView.refresh();

        displayPhraseIndex = 0;
    }

    @Override
    public void onStart() {
        super.onStart();

        // prevent rescanning on tabbing back to the app or something
        if (!scanned)
            refresh(false);
    }

    @Override
    protected void refresh(final boolean forceRefresh) {
        new Thread() {
            @Override
            public void run() {
                setRefreshActionButtonState(true);
                buildPersonalStats(forceRefresh);
                setRefreshActionButtonState(false);
            }
        }.start();
        scanned = true;
    }

    protected void buildPersonalStats(boolean computeFresh) {
        if (computeFresh) {
            scanSMS();
        }
        else {
            try {
                displayStats = new PersonalStats(openFileInput(DISPLAY_FILENAME));
            } catch (IOException e) {
                scanSMS();
            } catch (JSONException e) {
                scanSMS();
            }
        }

        showStats();
    }

    @Override
    public boolean hasNewData() {
        String[] sentColumns = new String[] {
                Sms.BODY, Sms.DATE, Sms.ADDRESS
        };
        Cursor messages = getContentResolver().query(Sms.SENT_URI, sentColumns, null, null, null);
        int numMessages = messages.getCount();
        messages.close();

        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());
        if (prefs.getInt(PROCESSED_SENT_MESSAGES, 0) != numMessages)
            return true;

        return false;
    }

    /**
     * Get the scaling factor to apply to fonts.
     */
    private float getFontScale() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        return metrics.scaledDensity;
    }

    /**
     * Gets a file like en.unigrams.utf8.txt if it exists in the assets. If not,
     * returns null. TODO: Basically this is mimicking Resources. I need to
     * double-check why I chose to use Assets instead.
     * 
     * @param suffix The suffix to append to the language and/or country code.
     * @return The filename if it exists. Null if not.
     */
    private String getLocalizedAsset(String suffix) {
        try {
            String languageCode2 = Locale.getDefault().getLanguage();
            String filename = languageCode2 + suffix;

            String[] assets = getAssets().list("");
            for (String asset : assets) {
                if (asset.equals(filename)) {
                    return filename;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "getLocalizedAsset failed to list assets");
        }

        return null;
    }

    private void loadUnigrams() {
        long time = System.currentTimeMillis();

        try {
            String unigramFilename = getLocalizedAsset(".unigrams.utf8.txt");

            if (unigramFilename != null) {
                corpusUnigrams = new WordDistribution(getAssets().open(unigramFilename), false);
            }
            else {
                // There isn't a unigram file for this language.
                // TODO: Build a baseline unigram model from the set of received
                // messages.
                corpusUnigrams = null;
            }
        } catch (IOException e) {
            corpusUnigrams = null;
            Log.e(TAG, "loadUnigrams failed");
        }
        setPreference(LOAD_UNIGRAMS_KEY, System.currentTimeMillis() - time);
    }

    private void loadStopwords() {
        long time = System.currentTimeMillis();
        smallStopwords = new HashSet<String>();

        try {
            String smallStopwordsFile = getLocalizedAsset(".stopwords.small.utf8.txt");

            if (smallStopwordsFile != null) {
                BufferedReader in = new BufferedReader(new InputStreamReader(getAssets().open(
                        smallStopwordsFile)), 8192);
                String line;
                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    if (line.length() > 0)
                        smallStopwords.add(line.toLowerCase(Locale.getDefault()));
                }
                in.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "loadStopwords failed for small file");
        }

        largeStopwords = new HashSet<String>();
        try {
            String largeStopwordsFile = getLocalizedAsset(".stopwords.medium.utf8.txt");

            if (largeStopwordsFile != null) {
                BufferedReader in = new BufferedReader(new InputStreamReader(getAssets().open(
                        largeStopwordsFile)), 8192);
                String line;
                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    if (line.length() > 0)
                        // TODO: This should use the locale closest to the
                        // stopword language. We only have stopwords for English
                        // right now though.
                        largeStopwords.add(line.toLowerCase(Locale.ENGLISH));
                }
                in.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "loadStopwords failed for large file");
        }

        setPreference(LOAD_STOPWORDS_KEY, System.currentTimeMillis() - time);
    }

    public void scanSMS() {
        loadUnigrams();
        loadStopwords();

        displayStats = new PersonalStats();

        // step 1: scan contacts, build a mapping of contact number to name
        long time = System.currentTimeMillis();
        ExtendedApplication app = (ExtendedApplication) getApplication();
        if (!app.blockingLoadContacts()) {
            warning("No contacts found");
        }
        setPreference(LOAD_CONTACTS_KEY, System.currentTimeMillis() - time);

        // step 2: scan sent messages
        time = System.currentTimeMillis();
        String[] sentColumns = new String[] {
                Sms.BODY, Sms.DATE, Sms.ADDRESS
        };
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
        int numMessages = messages.getCount();

        if (messages.moveToFirst()) {
            final int bodyIndex = messages.getColumnIndexOrThrow(Sms.BODY);
            final int dateIndex = messages.getColumnIndexOrThrow(Sms.DATE);
            final int addressIndex = messages.getColumnIndexOrThrow(Sms.ADDRESS);

            do {
                // TODO: Replace this with truecasing
                String body = messages.getString(bodyIndex).toLowerCase(Locale.getDefault());

                long millis = messages.getLong(dateIndex);
                Date date = new Date(millis);
                dates.add(date);

                // handle the simple message thing
                if (body.length() <= maxShortMessageLength) {
                    String text = body;

                    if (shortMessages.containsKey(text))
                        shortMessages.get(text)[0]++;
                    else
                        shortMessages.put(text, new int[] {
                            1
                        });
                    shortMessageTotal++;
                }

                ArrayList<String> tokens = tokenize(body);

                // clear out the simplePhrase sequence
                simplePhrase.clear();

                sentStats.train(tokens, body.length());

                // update the simple phrases
                for (String token : tokens) {
                    // simple phrases
                    if (!isNonword(token) && !smallStopwords.contains(token)) {
                        // add to the phrase
                        simplePhrase.add(token);
                    }
                    else {
                        if (simplePhrase.size() > 0) {
                            reusableBuilder.setLength(0);
                            reusableBuilder.append(simplePhrase.get(0));
                            for (int i = 1; i < simplePhrase.size(); i++) {
                                reusableBuilder.append(' ');
                                reusableBuilder.append(simplePhrase.get(i));
                            }

                            String phraseString = reusableBuilder.toString();

                            if (simplePhrases.containsKey(phraseString))
                                simplePhrases.get(phraseString)[0]++;
                            else
                                simplePhrases.put(phraseString, new int[] {
                                    1
                                });

                            simplePhraseTotal++;
                        }
                        // flush the phrase
                        simplePhrase.clear();
                    }
                }

                // figure out the name of the destination, store it in person
                String address = messages.getString(addressIndex);

                String displayName = app.lookupContactName(address);
                if (displayName != null) {
                    if (personCounts.containsKey(displayName))
                        personCounts.get(displayName)[0]++;
                    else
                        personCounts.put(displayName, new int[] { 1 });
                }
            } while (messages.moveToNext());
        }
        else {
            messages.close();
            error(getString(R.string.error_no_sent_sms));
            return;
        }
        messages.close();

        setPreference(MESSAGE_LOOP_KEY, System.currentTimeMillis() - time);

        time = System.currentTimeMillis();
        // generate candidates
        final HashMap<String, double[]> candidates = new HashMap<String, double[]>();

        final HashMap<String, int[]> frequencyCandidates = new HashMap<String, int[]>();

        // unigram candidates
        for (String word : sentStats.unigrams.keySet()) {
            if (!isNonword(word))
                frequencyCandidates.put(word, new int[] {
                    sentStats.unigrams.get(word)[0]
                });

            if (!isNonword(word) && !largeStopwords.contains(word)) {
                double expected = 0;
                if (corpusUnigrams != null)
                    expected = corpusUnigrams.expectedFrequency(word, sentStats.unigramTotal);

                candidates.put(
                        word,
                        new double[] {
                            unigramScale * (sentStats.unigrams.get(word)[0] - expected)
                        });
            }
        }

        // analyse bigrams
        StringBuilder ngramBuilder = new StringBuilder();
        for (String word1 : sentStats.bigrams.keySet()) {
            if (isNonword(word1))
                continue;

            for (String word2 : sentStats.bigrams.get(word1).keySet()) {
                if (isNonword(word2))
                    continue;

                // concatenation with StringBuilder for performance
                ngramBuilder.setLength(0);
                ngramBuilder.append(word1);
                ngramBuilder.append(' ');
                ngramBuilder.append(word2);
                String ngram = ngramBuilder.toString();

                frequencyCandidates.put(ngram, new int[] {
                    sentStats.bigrams.get(word1).get(word2)[0]
                });

                if (smallStopwords.contains(word1) || smallStopwords.contains(word2))
                    continue;

                int freq = sentStats.bigrams.get(word1).get(word2)[0];

                double expected = 0;
                if (corpusUnigrams != null)
                    expected = corpusUnigrams
                            .expectedFrequency(word1, word2, sentStats.bigramTotal);

                candidates.put(ngram, new double[] {
                    bigramScale * (freq - expected)
                });
            }
        }

        // analyse trigrams
        for (String word1 : sentStats.trigrams.keySet()) {
            if (isNonword(word1))
                continue;

            for (String word2 : sentStats.trigrams.get(word1).keySet()) {
                if (isNonword(word2))
                    continue;

                for (String word3 : sentStats.trigrams.get(word1).get(word2).keySet()) {
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

                    frequencyCandidates.put(ngram, new int[] {
                        sentStats.trigrams.get(word1).get(word2).get(word3)[0]
                    });

                    if (smallStopwords.contains(word1) || smallStopwords.contains(word3))
                        continue;

                    int freq = sentStats.trigrams.get(word1).get(word2).get(word3)[0];

                    double expected = 0;
                    if (corpusUnigrams != null)
                        corpusUnigrams.expectedFrequency(word1, word2, word3,
                                sentStats.trigramTotal);

                    candidates.put(ngram, new double[] {
                        trigramScale * (freq - expected)
                    });
                }
            }
        }

        ArrayList<String> basicPhrases = new ArrayList<String>(frequencyCandidates.keySet());
        Collections.sort(basicPhrases, new Comparator<String>() {
            public int compare(String a, String b) {
                return Double.compare(frequencyCandidates.get(b)[0], frequencyCandidates.get(a)[0]);
            }
        });

        int basicCurrent = 0;
        for (String wordPair : basicPhrases) {
            displayStats.keyPhraseTexts[PersonalStats.COUNT_SORTED].append(wordPair);
            displayStats.keyPhraseTexts[PersonalStats.COUNT_SORTED].append('\n');

            if (++basicCurrent >= maxPhrases)
                break;
        }

        // adjust candidates based on phrases, etc
        for (String candidate : candidates.keySet()) {
            if (simplePhrases.containsKey(candidate))
                candidates.get(candidate)[0] *= simplePhraseFactor
                        * Math.log(simplePhrases.get(candidate)[0]);

            if (shortMessages.containsKey(candidate))
                candidates.get(candidate)[0] *= shortMessageFactor
                        * Math.log(shortMessages.get(candidate)[0]);
        }

        // sort candidate pairs
        ArrayList<String> pairs = new ArrayList<String>(candidates.keySet());
        Collections.sort(pairs, new Comparator<String>() {
            public int compare(String a, String b) {
                return Double.compare(candidates.get(b)[0], candidates.get(a)[0]);
            }
        });

        if (LOG_PHRASES) {
            logFile = new File(Environment.getExternalStorageDirectory(), "sms_phrase_log.txt");
            PrintWriter scoresOut;
            try {
                scoresOut = new PrintWriter(new FileWriter(logFile));
                logCandidateFeatures(sentStats, shortMessages, simplePhrases, candidates, scoresOut);
                scoresOut.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to write to log: " + e);
            }
        }

        mergeSimilarPhrases(sentStats, candidates, pairs);

        // resort candidate pairs
        Collections.sort(pairs, new Comparator<String>() {
            public int compare(String a, String b) {
                return Double.compare(candidates.get(b)[0], candidates.get(a)[0]);
            }
        });

        setPreference(SELECT_CANDIDATES_KEY, System.currentTimeMillis() - time);

        time = System.currentTimeMillis();

        /*********************** BUILD THE STRINGS ************************/

        // KEY PHRASE DISPLAY
        final StringBuilder phraseBuilder = displayStats.keyPhraseTexts[PersonalStats.PHRASE_SORTED];
        int current = 0;
        for (String wordPair : pairs) {
            phraseBuilder.append(wordPair);
            phraseBuilder.append('\n');

            if (++current >= maxPhrases)
                break;
        }

        if (phraseBuilder.length() == 0)
            phraseBuilder.append(getString(R.string.no_phrases));

        // CONTACT DISPLAY
        ArrayList<String> people = new ArrayList<String>(personCounts.keySet());
        Collections.sort(people, new Comparator<String>() {
            public int compare(String a, String b) {
                return personCounts.get(b)[0] - personCounts.get(a)[0];
            }
        });

        for (String person : people) {
            if (personCounts.get(person)[0] <= 1)
                break;

            displayStats.contactsDisplay.append(getString(R.string.num_messages_format, person,
                    personCounts.get(person)[0]));
        }

        if (displayStats.contactsDisplay.length() == 0)
            displayStats.contactsDisplay.append(getString(R.string.no_frequent_contacts));

        // build out the general stats
        displayStats.generalDisplay.append(getString(R.string.num_sent_format, sentStats.messages));
        displayStats.generalDisplay.append(getString(R.string.num_sent_per_month_format,
                dates.computeTextsPerMonth()));

        displayStats.generalDisplay.append(getString(R.string.words_per_text_format,
                sentStats.filteredWords / sentStats.messages));
        displayStats.generalDisplay.append(getString(R.string.chars_per_text_format,
                sentStats.chars / sentStats.messages));
        displayStats.generalDisplay.append(getString(R.string.chars_per_word_format,
                sentStats.filteredWordLength / (double) sentStats.filteredWords));

        // day of the week histogram
        displayStats.dayHistogram = dates.computeDayOfWeekHistogram();

        // time of day histogram
        displayStats.hourHistogram = dates.computeHourHistogram();

        // clean up some of the strings for newlines
        Util.strip(displayStats.contactsDisplay, '\n');
        Util.strip(displayStats.generalDisplay, '\n');
        for (StringBuilder b : displayStats.keyPhraseTexts)
            Util.strip(b, '\n');

        setPreference(GENERATE_DESCRIPTIONS_KEY, System.currentTimeMillis() - time);

        time = System.currentTimeMillis();
        try {
            displayStats.writeTo(openFileOutput(DISPLAY_FILENAME, Context.MODE_PRIVATE));
        } catch (IOException e) {
            Log.e(TAG, "Failed to save displayStats");
            Log.e(TAG, Log.getStackTraceString(e));
        } catch (JSONException e) {
            Log.e(TAG, "Failed to save displayStats");
            Log.e(TAG, Log.getStackTraceString(e));
        }
        setPreference(SAVE_DISPLAY_KEY, System.currentTimeMillis() - time);
        setPreference(PROCESSED_SENT_MESSAGES, numMessages);

        showStats();
    }

    /**
     * show the stats in the UI
     */
    private void showStats() {
        // RUNTIME DISPLAY
        final String runtimeString;
        if (MainActivity.DEVELOPER_MODE)
            runtimeString = MainActivity.summarizeRuntime(getApplicationContext(),
                    PROFILING_KEY_ORDER);
        else
            runtimeString = null;

        runOnUiThread(new Runnable() {
            public void run() {
                mCardView.clearCards();

                String appName = getString(R.string.app_name);
                Context shareContext = PersonalActivity.this;

                mCardView.addCard(new ShareableCard(getString(R.string.key_phrases),
                        displayStats.keyPhraseTexts[displayPhraseIndex].toString(), appName,
                        shareContext));
                mCardView.addCard(new ShareableCard(getString(R.string.contacts),
                        displayStats.contactsDisplay.toString(), appName, shareContext));
                mCardView.addCard(new ShareableCard(getString(R.string.stats),
                        displayStats.generalDisplay.toString(), appName, shareContext));

                GraphicalView dayChart = buildDayChart(PersonalActivity.this,
                        displayStats.dayHistogram);
                mCardView.addCard(new GraphCard(getString(R.string.day_of_week), dayChart, appName,
                        shareContext));

                GraphicalView hourChart = buildHourChart(PersonalActivity.this,
                        displayStats.hourHistogram);
                mCardView.addCard(new GraphCard(getString(R.string.time_of_day), hourChart,
                        appName, shareContext));

                if (runtimeString != null)
                    mCardView.addCard(new ShareableCard(getString(R.string.runtime), runtimeString,
                            appName, shareContext));

                mCardView.refresh();

                if (LOG_PHRASES) {
                    Intent intent = new Intent(android.content.Intent.ACTION_SEND);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.putExtra(Intent.EXTRA_STREAM,
                            Uri.parse("file://" + logFile.getAbsolutePath()));
                    intent.setType("text/plain");

                    intent.putExtra(Intent.EXTRA_SUBJECT, "Logged phrases");

                    startActivity(Intent.createChooser(intent, "Send email..."));
                }
            }
        });
    }

    private void logCandidateFeatures(CorpusStats sentStats, HashMap<String, int[]> shortMessages,
            HashMap<String, int[]> simplePhrases, HashMap<String, double[]> combinedScores,
            PrintWriter scoresOut) {
        StringBuilder ngramBuilder = new StringBuilder();

        char sep = '\t';
        // header
        scoresOut.println("Phrase\tFrequency\tExpected Frequency\tHas Nonwords\tStopword Start\tStopword End\tSimple Phrase Count\tShort Message Count\tCombined Score");

        // unigrams
        for (String word : sentStats.unigrams.keySet()) {
            scoresOut.print(word);
            scoresOut.print(sep);
            scoresOut.print(sentStats.unigrams.get(word)[0]);
            scoresOut.print(sep);
            scoresOut.print(corpusUnigrams == null ? 0 : corpusUnigrams.expectedFrequency(word,
                    sentStats.unigramTotal));
            scoresOut.print(sep);
            scoresOut.print(isNonword(word) ? 1 : 0);
            scoresOut.print(sep);
            scoresOut.print(largeStopwords.contains(word) ? 1 : 0);
            scoresOut.print(sep);
            scoresOut.print(largeStopwords.contains(word) ? 1 : 0);
            scoresOut.print(sep);
            scoresOut.print(simplePhrases.containsKey(word) ? simplePhrases.get(word)[0] : 0);
            scoresOut.print(sep);
            scoresOut.print(shortMessages.containsKey(word) ? shortMessages.get(word)[0] : 0);
            scoresOut.print(sep);
            scoresOut.print(combinedScores.containsKey(word) ? combinedScores.get(word)[0] : 0);
            scoresOut.println();
        }

        for (String word1 : sentStats.bigrams.keySet()) {
            for (String word2 : sentStats.bigrams.get(word1).keySet()) {
                ngramBuilder.setLength(0);
                ngramBuilder.append(word1);
                ngramBuilder.append(' ');
                ngramBuilder.append(word2);

                scoresOut.print(ngramBuilder);
                scoresOut.print(sep);
                scoresOut.print(sentStats.bigrams.get(word1).get(word2)[0]);
                scoresOut.print(sep);
                scoresOut.print(corpusUnigrams == null ? 0 : corpusUnigrams.expectedFrequency(
                        word1, word2, sentStats.bigramTotal));
                scoresOut.print(sep);
                scoresOut.print(isNonword(word1) || isNonword(word2) ? 1 : 0);
                scoresOut.print(sep);
                scoresOut.print(smallStopwords.contains(word1) ? 1 : 0);
                scoresOut.print(sep);
                scoresOut.print(smallStopwords.contains(word2) ? 1 : 0);
                scoresOut.print(sep);
                scoresOut.print(simplePhrases.containsKey(ngramBuilder.toString()) ? simplePhrases
                        .get(ngramBuilder.toString())[0] : 0);
                scoresOut.print(sep);
                scoresOut.print(shortMessages.containsKey(ngramBuilder.toString()) ? shortMessages
                        .get(ngramBuilder.toString())[0] : 0);
                scoresOut.print(sep);
                scoresOut
                        .print(combinedScores.containsKey(ngramBuilder.toString()) ? combinedScores
                                .get(ngramBuilder.toString())[0] : 0);
                scoresOut.println();
            }
        }

        for (String word1 : sentStats.trigrams.keySet()) {
            for (String word2 : sentStats.trigrams.get(word1).keySet()) {
                for (String word3 : sentStats.trigrams.get(word1).get(word2).keySet()) {
                    ngramBuilder.setLength(0);
                    ngramBuilder.append(word1);
                    ngramBuilder.append(' ');
                    ngramBuilder.append(word2);
                    ngramBuilder.append(' ');
                    ngramBuilder.append(word3);

                    scoresOut.print(ngramBuilder);
                    scoresOut.print(sep);
                    scoresOut.print(sentStats.trigrams.get(word1).get(word2).get(word3)[0]);
                    scoresOut.print(sep);
                    scoresOut.print(corpusUnigrams == null ? 0 : corpusUnigrams.expectedFrequency(
                            word1, word2, word3, sentStats.trigramTotal));
                    scoresOut.print(sep);
                    scoresOut.print(isNonword(word1) || isNonword(word2) || isNonword(word3) ? 1
                            : 0);
                    scoresOut.print(sep);
                    scoresOut.print(smallStopwords.contains(word1) ? 1 : 0);
                    scoresOut.print(sep);
                    scoresOut.print(smallStopwords.contains(word3) ? 1 : 0);
                    scoresOut.print(sep);
                    scoresOut
                            .print(simplePhrases.containsKey(ngramBuilder.toString()) ? simplePhrases
                                    .get(ngramBuilder.toString())[0] : 0);
                    scoresOut.print(sep);
                    scoresOut
                            .print(shortMessages.containsKey(ngramBuilder.toString()) ? shortMessages
                                    .get(ngramBuilder.toString())[0] : 0);
                    scoresOut.print(sep);
                    scoresOut
                            .print(combinedScores.containsKey(ngramBuilder.toString()) ? combinedScores
                                    .get(ngramBuilder.toString())[0] : 0);
                    scoresOut.println();
                }
            }
        }
    }

    private void mergeSimilarPhrases(CorpusStats sentStats, HashMap<String, double[]> candidates,
            ArrayList<String> sortedCandidates) {
        StringBuilder ngramBuilder = new StringBuilder();
        // fold unigrams into bigrams (top K bigrams only)
        for (int i = 0; i < sortedCandidates.size() && i <= maxPhrases * 2; i++) {
            String[] words = sortedCandidates.get(i).split(" ");

            if (words.length == 2) {
                // discount from the first word
                if (candidates.containsKey(words[0])) {
                    double ratio = sentStats.bigrams.get(words[0]).get(words[1])[0]
                            / (double) sentStats.unigrams.get(words[0])[0];
                    double discount = ratio * candidates.get(words[0])[0];

                    candidates.get(words[0])[0] -= discount;
                    candidates.get(sortedCandidates.get(i))[0] += discount;

                    if (Double.isNaN(ratio)
                            || Double.isNaN(candidates.get(sortedCandidates.get(i))[0]))
                        Log.e(TAG, "NaN in " + sortedCandidates.get(i) + " / " + words[0]);
                }

                // discount from the second word
                if (candidates.containsKey(words[1])) {
                    double ratio = sentStats.bigrams.get(words[0]).get(words[1])[0]
                            / (double) sentStats.unigrams.get(words[1])[0];
                    double discount = ratio * candidates.get(words[1])[0];

                    candidates.get(words[1])[0] -= discount;
                    candidates.get(sortedCandidates.get(i))[0] += discount;
                    if (Double.isNaN(ratio)
                            || Double.isNaN(candidates.get(sortedCandidates.get(i))[0]))
                        Log.e(TAG, "NaN in " + sortedCandidates.get(i) + " / " + words[1]);
                }
            }
        }

        // fold bigrams into trigrams (top K trigrams only)
        for (int i = 0; i < sortedCandidates.size() && i <= maxPhrases * 2; i++) {
            String[] words = sortedCandidates.get(i).split(" ");

            if (words.length == 3) {
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
                if (candidates.containsKey(first)) {
                    double ratio = sentStats.trigrams.get(words[0]).get(words[1]).get(words[2])[0]
                            / (double) sentStats.bigrams.get(words[0]).get(words[1])[0];
                    double discount = ratio * candidates.get(first)[0];

                    candidates.get(first)[0] -= discount;
                    candidates.get(sortedCandidates.get(i))[0] += discount;
                    if (Double.isNaN(ratio)
                            || Double.isNaN(candidates.get(sortedCandidates.get(i))[0]))
                        Log.e(TAG, "NaN in " + sortedCandidates.get(i) + " / " + first);

                }

                // discount from the second word
                if (candidates.containsKey(second)) {
                    double ratio = sentStats.trigrams.get(words[0]).get(words[1]).get(words[2])[0]
                            / (double) sentStats.bigrams.get(words[1]).get(words[2])[0];
                    double discount = ratio * candidates.get(second)[0];

                    candidates.get(second)[0] -= discount;
                    candidates.get(sortedCandidates.get(i))[0] += discount;

                    if (Double.isNaN(ratio)
                            || Double.isNaN(candidates.get(sortedCandidates.get(i))[0]))
                        Log.e(TAG, "NaN in " + sortedCandidates.get(i) + " / " + second);
                }
            }
        }
    }

    /**
     * Inflates a R.layout.phrases with the specified details, using the
     * specified inflater, registers callbacks for the spinner, etc.
     * 
     * @param inflater
     * @param details
     * @return the inflated view
     */
    public View inflatePhraseResults(LayoutInflater inflater, final CharSequence details) {
        View view = inflater.inflate(R.layout.results_phrases, null);

        TextView textView = (TextView) view.findViewById(android.R.id.text2);
        textView.setText(details);

        Spinner spinner = (Spinner) view.findViewById(R.id.spinner1);
        spinner.setOnItemSelectedListener(this);

        ImageView shareView = (ImageView) view.findViewById(R.id.share);
        shareView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
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

    public View inflateChart(LayoutInflater inflater, final CharSequence title,
            final GraphicalView graph) {
        View view = inflater.inflate(R.layout.results_graphed, null);

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
        share.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                share(graph.toBitmap(), title, "Shared: histogram of "
                        + title.toString().toLowerCase(Locale.getDefault()));
            }
        });

        return view;
    }

    public void share(Bitmap bitmap, CharSequence title, CharSequence subject) {
        // In the future, I should switch this to getExternalFilesDir
        File file = new File(Environment.getExternalStorageDirectory(), "sms_ling.png");
        try {
            OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
            bitmap.compress(CompressFormat.PNG, 100, out);
            out.close();

            Intent intent = new Intent(android.content.Intent.ACTION_SEND);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + file.getAbsolutePath()));
            intent.setType("image/png");

            intent.putExtra(Intent.EXTRA_SUBJECT, subject);

            startActivity(Intent.createChooser(intent, "Send email..."));
        } catch (IOException e) {
            error("Unable to share image");
        }
    }

    /**
     * Build a chart for day-of-the-week histogram
     * 
     * @param c
     * @param dayHistogram
     * @return The drawable View. Be sure to set the height of it or it won't
     *         show!
     */
    public GraphicalView buildDayChart(Context c, int[] dayHistogram) {
        /****************** BUILD THE DATA SET **********************/
        XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();

        CategorySeries series = new CategorySeries("Day");
        for (int i = 0; i < dayHistogram.length; i++) {
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

        String[] days = new String[] {
                "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
        };
        for (int i = 0; i < days.length; i++)
            renderer.addXTextLabel(i + 1, days[i].substring(0, 1));

        final BarChart chart = new BarChart(dataset, renderer, BarChart.Type.DEFAULT);
        GraphicalView view = new GraphicalView(c, chart);

        return view;
    }

    /**
     * Does all the parts of renderer setup that don't depend on the actual
     * datapoints, under the assumption that we're rendering a single data
     * series. The caller should still call addXTextLabel and setXTitle,
     * setYTitle.
     * 
     * @return
     */
    private static XYMultipleSeriesRenderer createBaseChartTheme(int xmin, int xmax, int ymin,
            int ymax, float scale) {
        XYMultipleSeriesRenderer renderer = new XYMultipleSeriesRenderer();

        // text sizes
        renderer.setAxisTitleTextSize(18 * scale);
        renderer.setChartTitleTextSize(20 * scale);
        renderer.setLabelsTextSize(14 * scale);
        renderer.setLegendTextSize(14 * scale);

        // a post on StackOverflow suggests the X/Y axis labels stick to
        // margins, so if they aren't big enough it'll render labels on top of
        // other things
        // http://stackoverflow.com/a/12527041/1492373
        // Margin order is { top, left, bottom, right }
        int[] margins = renderer.getMargins();
        // top margin: This is a total hack; I saw a y-axis label near the top
        // of the axis that had a small amount of the top of the number cutoff
        // and this fixes it.
        margins[0] += 2;
        // left margin: Also a hack. I *think* the left margin needs to be
        // enough for the y-axis labels, plus the y-axis title. But I don't know
        // how to get
        // the pixel width of the y-axis labels (and this point of the code is
        // graph-independent). It'll take some refactoring to cleanse this
        // unholy mess.
        margins[1] = (int) (3 * renderer.getLabelsTextSize());
        renderer.setMargins(margins);

        // data series settings
        SimpleSeriesRenderer r = new SimpleSeriesRenderer();
        r.setColor(Color.DKGRAY);
        r.setDisplayChartValues(false);
        r.setGradientEnabled(true);
        r.setGradientStart(0, graphBarBottomColor); // start = bottom of the bar
        r.setGradientStop(ymax, graphBarTopColor); // this color will be the top
                                                   // of the max height bar
        renderer.addSeriesRenderer(r);

        renderer.setOrientation(Orientation.HORIZONTAL);
        renderer.setBarSpacing(0.2f);

        // colors
        renderer.setAxesColor(Color.DKGRAY);
        renderer.setLabelsColor(Color.DKGRAY);
        renderer.setXLabelsColor(Color.DKGRAY);
        renderer.setYLabelsColor(0, Color.DKGRAY);

        // there's a bug in achartengine that requires you to set the color
        // portion even with full transparency
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

        // tick marks + labels - we don't want any for X cause that will be
        // labels
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
     * 
     * @param c
     * @param timeData
     * @return The drawable View. Be sure to set the height of it or it won't
     *         show!
     */
    public GraphicalView buildHourChart(Context c, int[] timeData) {
        /****************** BUILD THE DATA SET **********************/
        XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();

        CategorySeries series = new CategorySeries("Day");
        for (int i = 1; i < timeData.length; i++) {
            series.add(timeData[i]);
        }
        dataset.addSeries(series.toXYSeries());

        int first = 0, last = 24;
        for (int i = 1; i < timeData.length; i++) {
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
        XYMultipleSeriesRenderer renderer = createBaseChartTheme(first - 1, last + 1, 0, ymax,
                getFontScale());

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

    /**
     * Handle the spinner for selecting phrases by phraseness or count
     */
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        if (pos == displayPhraseIndex)
            return;

        View phrasesView = findViewById(R.id.phrase_layout);
        if (phrasesView != null) {
            if (pos < displayStats.keyPhraseTexts.length) {
                TextView textView = (TextView) phrasesView.findViewById(android.R.id.text2);
                if (textView != null) {
                    textView.setText(displayStats.keyPhraseTexts[pos]);
                }
            }
        }
        else
            Log.d(TAG, "Can't find phrase_layout");

        displayPhraseIndex = pos;
    }

    public void onNothingSelected(AdapterView<?> arg0) {
    }
}
