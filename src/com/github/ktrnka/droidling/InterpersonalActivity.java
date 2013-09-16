package com.github.ktrnka.droidling;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;

import com.fima.cardsui.views.CardUI;
import com.github.ktrnka.droidling.InterpersonalStats.Item;
import com.github.ktrnka.droidling.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * An Activity for analysing relationships to your contacts.
 * @author keith.trnka
 *
 */
public class InterpersonalActivity extends RefreshableActivity
	{
	private boolean scanned;
	private CardUI mCardView;

	public static final String TAG = "InterpersonalActivity";
	private static final String CONTACT_NAME = "contact";
	private static final String DISPLAY_FILENAME = "InterpersonalActivity.cache";

	public static final String SENT_MESSAGE_LOOP_KEY = "InterpersonalActivity: scanning sent messages";
	public static final String RECEIVED_MESSAGE_LOOP_KEY = "InterpersonalActivity: scanning received messages";
	public static final String THREADED_MESSAGE_LOOP_KEY = "InterpersonalActivity: scanning threaded messages";
	public static final String LOAD_CONTACTS_KEY = "InterpersonalActivity: loading contacts";
	public static final String SELECT_CANDIDATES_KEY = "InterpersonalActivity: finding the best candidates";
	public static final String SAVE_DISPLAY_KEY = "InterpersonalActivity: caching results";
	
	public static final String[] PROFILING_KEY_ORDER = { LOAD_CONTACTS_KEY, SENT_MESSAGE_LOOP_KEY, RECEIVED_MESSAGE_LOOP_KEY, THREADED_MESSAGE_LOOP_KEY, SELECT_CANDIDATES_KEY, SAVE_DISPLAY_KEY };

	private static final String PROCESSED_MESSAGES = "InterpersonalActivity.processedMessages";
	
	private InterpersonalStats displayStats;

	public void onCreate(Bundle savedInstanceState)
		{
		super.onCreate(savedInstanceState);
		setHelpActivity(AboutInterpersonalActivity.class);
		
		// old layout code
		// setContentView(R.layout.simple_scroll);
		
		// cards UI test
		setContentView(R.layout.cardsui_main);
		mCardView = (CardUI) findViewById(R.id.cardsview);
		mCardView.setSwipeable(false);
		
		// draw empty list to start?
		mCardView.refresh();
		}
	
	public void onStart()
		{
		super.onStart();
				
		if (!scanned)
			refresh(false);
		}

	protected void refresh(final boolean forceRefresh)
	    {
	    setRefreshActionButtonState(true);
		new Thread()
	    	{
	    	public void run()
	    		{
	    		buildInterpersonalDisplay(forceRefresh);
	    	    setRefreshActionButtonState(false);
	    		}
	    	}.start();
	    scanned = true;
	    }

	protected void buildInterpersonalDisplay(boolean forceRebuild)
	    {
	    if (forceRebuild)
	    	{
	    	scanSMS();
	    	}
	    else
	    	{
	    	try
	            {
	            displayStats = new InterpersonalStats(openFileInput(DISPLAY_FILENAME));
	            displayStats.cacheStrings(this);
	            }
            catch (IOException e)
	            {
	            scanSMS();
	            }
	    	}

	    showDisplay();
	    }

	private void scanSMS()
		{
		displayStats = new InterpersonalStats();
		
		long time = System.currentTimeMillis();

		/*************** LOAD CONTACTS *******************/
		ExtendedApplication app = (ExtendedApplication) getApplication();
		if (!app.blockingLoadContacts())
			{
			warning("No contacts found");
			}
		setPreference(LOAD_CONTACTS_KEY, System.currentTimeMillis() - time);
		
		/*************** PROCESS SENT MESSAGES *******************/
		time = System.currentTimeMillis();
		Cursor messages = getContentResolver().query(Sms.SENT_URI, new String[] { Sms.BODY, Sms.ADDRESS }, null, null, null);

		final HashMap<String, int[]> sentCounts = new HashMap<String, int[]>();
		
		
		final HashMap<String,CorpusStats> sentStats = new HashMap<String,CorpusStats>();
		final CorpusStats overallSentStats = new CorpusStats();
		
		final HashMap<String,String> contactPhotoUris = new HashMap<String,String>();
		
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
					if (!contactPhotoUris.containsKey(recipientName))
						contactPhotoUris.put(recipientName, app.lookupContactInfo(recipientId, ExtendedApplication.ContactInfo.PHOTO_URI));

					if (sentCounts.containsKey(recipientName))
						sentCounts.get(recipientName)[0]++;
					else
						sentCounts.put(recipientName, new int[] { 1 });
					
					String body = messages.getString(bodyIndex);
					if (!sentStats.containsKey(recipientName))
						sentStats.put(recipientName, new CorpusStats());
					
					try
						{
						sentStats.get(recipientName).train(body);
						overallSentStats.train(body);
						}
					catch (OutOfMemoryError e)
						{
						throw new Error("Overall sent messages proccessed: " + overallSentStats.getMessages() + "\nOverall percent long words: " + overallSentStats.getPercentLongWords(), e);
						}
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
		setPreference(SENT_MESSAGE_LOOP_KEY, System.currentTimeMillis() - time);
		
		/*************** PROCESS RECEIVED MESSAGES *******************/
		time = System.currentTimeMillis();
		messages = getContentResolver().query(Sms.RECEIVED_URI, new String[] { Sms.BODY, Sms.ADDRESS }, null, null, null);

		final HashMap<String, int[]> receivedCounts = new HashMap<String, int[]>();
		final HashMap<String,CorpusStats> receivedStats = new HashMap<String,CorpusStats>();
		
		final CorpusStats overallReceivedStats = new CorpusStats();
		
		if (messages.moveToFirst())
			{
			final int addressIndex = messages.getColumnIndexOrThrow(Sms.ADDRESS);
			final int bodyIndex = messages.getColumnIndexOrThrow(Sms.BODY);
			
			do
				{
				// figure out the name of the destination, store it in person
				String senderId = messages.getString(addressIndex);

				String senderName = app.lookupContactName(senderId);

				if (senderName != null)
					{
					if (!contactPhotoUris.containsKey(senderName))
						contactPhotoUris.put(senderName, app.lookupContactInfo(senderName, ExtendedApplication.ContactInfo.PHOTO_URI));

					if (receivedCounts.containsKey(senderName))
						receivedCounts.get(senderName)[0]++;
					else
						receivedCounts.put(senderName, new int[] { 1 });
					
					if (!receivedStats.containsKey(senderName))
						receivedStats.put(senderName, new CorpusStats());
					
					String message = messages.getString(bodyIndex);
					
					try
						{
						receivedStats.get(senderName).train(message);
						overallReceivedStats.train(message);
						}
					catch (OutOfMemoryError e)
						{
						throw new Error("Overall received messages proccessed: " + overallReceivedStats.getMessages() + "\nOverall percent long words: " + overallReceivedStats.getPercentLongWords(), e);
						}
					}
				} while (messages.moveToNext());
			}
		else
			{
			error(getString(R.string.error_no_received_sms));
			messages.close();
			return;
			}
		messages.close();
		setPreference(RECEIVED_MESSAGE_LOOP_KEY, System.currentTimeMillis() - time);
		
		/*************** PROCESS IN THREADED VIEW ************************/
		// TODO:  switch all processing to use the FULL set of messages with this
		time = System.currentTimeMillis();
		messages = getContentResolver().query(Sms.CONTENT_URI, new String[] { Sms.ADDRESS, Sms.DATE, Sms.TYPE }, null, null, "date asc");
		int numMessages = messages.getCount();
		
		// mapping of (other person's parsed address) => [ type, date millis ]
		HashMap<String,long[]> previousMessage = new HashMap<String,long[]>();
		
		HashMap<String,ArrayList<long[]>> theirReplyTimes = new HashMap<String,ArrayList<long[]>>();
		HashMap<String,ArrayList<long[]>> yourReplyTimes = new HashMap<String,ArrayList<long[]>>();
		
		if (messages.moveToFirst())
			{
			final int addressIndex = messages.getColumnIndexOrThrow(Sms.ADDRESS);
			final int dateIndex = messages.getColumnIndexOrThrow(Sms.DATE);
			final int typeIndex = messages.getColumnIndexOrThrow(Sms.TYPE);
			
			do
				{
				// get the person's display string
				String person = messages.getString(addressIndex);
				
				person = app.lookupContactName(person);
				if (person == null)
					continue;

				long millis = messages.getLong(dateIndex);
				int type = messages.getInt(typeIndex);
				
				// skip unknown message types (drafts, etc?)
				if (type != 1 && type != 2)
					continue;
				
				// figure out the time diff if possible
				if (previousMessage.containsKey(person) && previousMessage.get(person)[0] != type)
					{
					// then treat it as a reply!
					long diff = millis - previousMessage.get(person)[1];
					
					// responses within an hour
					if (diff < 60l * 60 * 1000)
						{
						if (type == 1)
							{
							// received message
							if (!theirReplyTimes.containsKey(person))
								theirReplyTimes.put(person, new ArrayList<long[]>());
							
							theirReplyTimes.get(person).add(new long[] { diff });
							}
						else
							{
							// sent message
							if (!yourReplyTimes.containsKey(person))
								yourReplyTimes.put(person, new ArrayList<long[]>());
							
							yourReplyTimes.get(person).add(new long[] { diff });
							}
						}
					}
				
				// update our tracking listData structure
				if (!previousMessage.containsKey(person))
					previousMessage.put(person, new long[] { type, millis });
				else
					{
					previousMessage.get(person)[0] = type;
					previousMessage.get(person)[1] = millis;
					}
				} while (messages.moveToNext());

			}
		else
			{
			warning("Unable to scan all messages for response time.");
			}
		messages.close();
		setPreference(THREADED_MESSAGE_LOOP_KEY, System.currentTimeMillis() - time);

		
		
		/*************** ANALYSE, BUILD REPRESENTATION *******************/
		time = System.currentTimeMillis();
		// score the contacts for sorting
		final HashMap<String,int[]> scoredContacts = new HashMap<String,int[]>();
		HashSet<String> uniqueContacts = new HashSet<String>(sentStats.keySet());
		uniqueContacts.addAll(sentStats.keySet());
		for (String contact : uniqueContacts)
			{
			int score = 0;
			
			if (sentStats.containsKey(contact))
				score = sentStats.get(contact).getMessages();
			
			if (receivedStats.containsKey(contact))
				score += receivedStats.get(contact).getMessages();
			
			if (score > 0)
				scoredContacts.put(contact, new int[] { score });
			}
		
		ArrayList<String> contactList = new ArrayList<String>(scoredContacts.keySet());
		Collections.sort(contactList, new Comparator<String>()
			{
			public int compare(String lhs, String rhs)
				{
				return scoredContacts.get(rhs)[0] - scoredContacts.get(lhs)[0];
				}
			});
		
		for (String contactName : contactList)
			{
			InterpersonalSingleStats stats = new InterpersonalSingleStats();
			String firstName = extractPersonalName(contactName);
			
			stats.nameText = firstName;
			
			stats.photoUri = contactPhotoUris.get(contactName);
			
			int received = 0;
			if (receivedCounts.containsKey(contactName))
				received = receivedCounts.get(contactName)[0];
			
			int sent = 0;
			if (sentCounts.containsKey(contactName))
				sent = sentCounts.get(contactName)[0];
			
			// only summarize relationships with SOME symmetry
			if (sent == 0 || received == 0)
				continue;
			
			HashMap<String,String> item = new HashMap<String,String>();
			item.put(CONTACT_NAME, contactName);
			
			StringBuilder details = new StringBuilder();
			Formatter f = new Formatter(details);
			details.append(firstName + " sent " + generateCountText(received, "text", "texts") + "\n");
			f.format("You sent %s (%.1f%% of all sent)", generateCountText(sent, "text", "texts"), 100 * sent / (double)overallSentStats.getMessages());
			
			stats.numSentText = details.toString();
			
			// message length
			details.setLength(0);
			details.append(getString(R.string.their_message_length, firstName, receivedStats.get(contactName).getWordsPerMessage()));
			details.append('\n');
			details.append(getString(R.string.your_message_length, sentStats.get(contactName).getWordsPerMessage()));
			stats.messageLengthText = details.toString();
			
			// Jaccard coeffients
			details.setLength(0);
			details.append(getString(R.string.shared_with_them, 100 * sentStats.get(contactName).computeUnigramJaccard(receivedStats.get(contactName))));
			details.append('\n');
			details.append(getString(R.string.shared_with_all, 100 * overallSentStats.computeUnigramJaccard(receivedStats.get(contactName))));
			stats.sharedVocabPercentText = details.toString();
			
			// figure out the vocabulary overlap
			details.setLength(0);
			ArrayList<String> sortedOverlap = CorpusStats.computeRelationshipTerms(receivedStats.get(contactName), overallReceivedStats, sentStats.get(contactName), overallSentStats);
			if (sortedOverlap.size() == 0)
				{
				details.append(getString(R.string.none));
				}
			else
				{
				for (int i = 0; i < 10 && i < sortedOverlap.size(); i++)
					details.append(sortedOverlap.get(i) + "\n");
				details.replace(details.length() - 1, details.length(), "");
				}
			stats.sharedPhrasesText = details.toString();
			
			// compute stats about the average response time
			details.setLength(0);
			if (theirReplyTimes.containsKey(contactName))
				{
				ArrayList<long[]> replies = theirReplyTimes.get(contactName);
				f.format("%s: %s in %s\n", firstName, formatTime(averageSeconds(replies)), generateCountText(replies.size(), "text", "texts"));
				}
			else
				{
				f.format(getString(R.string.not_enough_replies) + "\n", firstName);
				}

			if (yourReplyTimes.containsKey(contactName))
				{
				ArrayList<long[]> replies = yourReplyTimes.get(contactName);
				f.format("%s: %s in %s", "You", formatTime(averageSeconds(replies)), generateCountText(replies.size(), "text", "texts"));
				}
			else
				{
				f.format(getString(R.string.not_enough_replies), "you");
				}
			stats.responseTimeText = details.toString();

			details.setLength(0);
			f.format("%s: %s\n", firstName, receivedStats.get(contactName).generateRandomMessage(true));
			f.format("You: %s", sentStats.get(contactName).generateRandomMessage(true));
			stats.bigramGenerationText = details.toString();

			details.setLength(0);
			f.format("%s: %s\n", firstName, receivedStats.get(contactName).generateRandomMessage(false));
			f.format("You: %s", sentStats.get(contactName).generateRandomMessage(false));
			stats.trigramGenerationText = details.toString();

			displayStats.add(contactName, stats);
			}
		displayStats.cacheStrings(this);
		setPreference(SELECT_CANDIDATES_KEY, System.currentTimeMillis() - time);

		time = System.currentTimeMillis();
		try
	        {
	        displayStats.writeTo(openFileOutput(DISPLAY_FILENAME, Context.MODE_PRIVATE));
	        }
        catch (IOException e)
	        {
	        Log.e(TAG, "Failed to save displayStats");
	        Log.e(TAG, Log.getStackTraceString(e));
	        }
		setPreference(SAVE_DISPLAY_KEY, System.currentTimeMillis() - time);
		
		setPreference(PROCESSED_MESSAGES, numMessages);
		
		showDisplay();
		}

	/**
	 * Inflate a bunch of views to fill out the list
	 */
	private void showDisplay()
	    {
		runOnUiThread(new Runnable()
			{
			public void run()
				{
				mCardView.clearCards();
				ExtendedApplication application = (ExtendedApplication) getApplication();

				for (Item item : displayStats.list)
					{
					mCardView.addCard(new InterpersonalCard(item.name.toString(), item.details, InterpersonalActivity.this, application));
					Log.d("com.github.droidling.InterpersonalActivity", "Adding item for " + item.name);
					}
				

				if (MainActivity.DEVELOPER_MODE)
					mCardView.addCard(new ShareableCard(getString(R.string.runtime), MainActivity.summarizeRuntime(getApplicationContext(), PROFILING_KEY_ORDER)));

				mCardView.refresh();
				}
			});
	    }
	
	public static double averageSeconds(ArrayList<long[]> list)
		{
		if (list.size() == 0)
			return 0;

		long total = 0;
		for (long[] ms : list)
			total += ms[0];
		
		return total / (1000.0 * list.size());
		}
	
	public static String formatTime(double sec)
		{
		if (sec < 60)
			{
			Formatter f = new Formatter();
			f.format("%d sec", (int)(sec + 0.5));
			return f.toString();
			}
		else
			{
			Formatter f = new Formatter();
			f.format("%d min", (int)(sec / 60));
			return f.toString() + ", " + formatTime(sec % 60);
			}
		}
	
	public static String extractPersonalName(String displayName)
		{
		String[] tokens = displayName.split(" ");
		
		return tokens[0];
		}
	
	public static String generateCountText(int number, String singular, String plural)
		{
		if (number == 1)
			return number + " " + singular;
		else
			return number + " " + plural;
		}

	@Override
    protected boolean hasNewData()
	    {
	    Cursor messages = getContentResolver().query(Sms.CONTENT_URI, new String[] { Sms.ADDRESS }, null, null, null);
	    int numMessages = messages.getCount();
	    messages.close();
	    
	    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		if (prefs.getInt(PROCESSED_MESSAGES, 0) != numMessages)
			return true;
	    
	    return false;
	    }
	}
