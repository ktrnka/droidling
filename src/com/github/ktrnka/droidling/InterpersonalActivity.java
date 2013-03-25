package com.github.ktrnka.droidling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;

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
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * An Activity for analysing relationships to your contacts.
 * @author keith.trnka
 *
 */
public class InterpersonalActivity extends Activity
	{
	private boolean scanned;
	private ArrayList<HashMap<String,String>> listData;

	private static final int PROGRESS_DIALOG = 0;
	private ProgressDialog progress;
	
	private static final String CONTACT_NAME = "contact";
	private static final String DETAILS = "details";

	public static final String SENT_MESSAGE_LOOP_KEY = "InterpersonalActivity: scanning sent messages";
	public static final String RECEIVED_MESSAGE_LOOP_KEY = "InterpersonalActivity: scanning received messages";
	public static final String THREADED_MESSAGE_LOOP_KEY = "InterpersonalActivity: scanning threaded messages";
	public static final String LOAD_CONTACTS_KEY = "InterpersonalActivity: loading contacts";
	public static final String SELECT_CANDIDATES_KEY = "InterpersonalActivity: finding the best candidates";
	
	public static final String[] PROFILING_KEY_ORDER = { LOAD_CONTACTS_KEY, SENT_MESSAGE_LOOP_KEY, RECEIVED_MESSAGE_LOOP_KEY, THREADED_MESSAGE_LOOP_KEY, SELECT_CANDIDATES_KEY };

	public void onCreate(Bundle savedInstanceState)
		{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.simple_scroll);
		}
	
	public void onStart()
		{
		super.onStart();
				
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
				progress = new ProgressDialog(InterpersonalActivity.this);
				progress.setIndeterminate(true);
				progress.setMessage(getString(R.string.loading));
				return progress;
			default:
				return null;
			}
		}
	
	public void onListItemClick(ListView list, View view, int position, long id)
		{
		// figure out the text
		String subject = getString(R.string.shared_stats_subject_format, getString(R.string.app_name));
		String body = getString(R.string.interpersonal_share_body_format, listData.get(position).get(CONTACT_NAME)) + "\n" + listData.get(position).get(DETAILS);

		Intent sendIntent = new Intent(Intent.ACTION_SEND);
		sendIntent.setType("message/rfc822");
		sendIntent.putExtra(Intent.EXTRA_TEXT, body);
		sendIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
		
		startActivity(Intent.createChooser(sendIntent, getString(R.string.share_intent)));
		}

	private void scanSMS()
		{
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
		
		// build the results into a list for SimpleAdapter
		listData = new ArrayList<HashMap<String,String>>();
		for (String contactName : contactList)
			{
			String firstName = extractPersonalName(contactName);
			
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
			f.format("You sent %s (%.1f%% of all sent)\n\n", generateCountText(sent, "text", "texts"), 100 * sent / (double)overallSentStats.getMessages());
			
			//f.format("Received %d words\n", receivedStats.get(contactName).getFilteredWords());
			//f.format("Sent %d words\n\n", sentStats.get(contactName).getFilteredWords());

			// these stats are eerie and boring
			//f.format("Their avg word length: %.1f\n", receivedStats.get(contactName).getWordLength());
			//f.format("Your avg word length: %.1f\n", overallSentStats.getWordLength());
			//f.format("Your avg word length when texting THEM: %.1f\n", sentStats.get(contactName).getWordLength());
			
			// message length
			details.append(getString(R.string.average_message_length_header) + "\n");
			details.append(' ');
			details.append(getString(R.string.their_message_length, firstName, receivedStats.get(contactName).getWordsPerMessage()));
			details.append('\n');
//			f.format(" %s: %.1f words\n", firstName, receivedStats.get(contactName).getWordsPerMessage());
//			f.format("Your avg message length: %.1f\n", overallSentStats.getWordsPerMessage());
			details.append(' ');
			details.append(getString(R.string.your_message_length, sentStats.get(contactName).getWordsPerMessage()));
			details.append("\n\n");
//			f.format(" You: %.1f words\n\n", sentStats.get(contactName).getWordsPerMessage());
			
			// Jaccard coeffients
			details.append(getString(R.string.shared_vocabulary_header));
			details.append('\n');
//			details.append("Shared vocabulary (Jaccard)\n");
			details.append(' ');
			details.append(getString(R.string.shared_with_them, 100 * sentStats.get(contactName).computeUnigramJaccard(receivedStats.get(contactName))));
			details.append('\n');
//			f.format(" with texts to them: %.1f%%\n", 100 * sentStats.get(contactName).computeUnigramJaccard(receivedStats.get(contactName)));
			details.append(' ');
			details.append(getString(R.string.shared_with_all, 100 * overallSentStats.computeUnigramJaccard(receivedStats.get(contactName))));
			details.append('\n');
//			f.format(" with ALL your texts: %.1f%%\n", 100 * overallSentStats.computeUnigramJaccard(receivedStats.get(contactName)));
			
			// figure out the vocabulary overlap
			ArrayList<String> sortedOverlap = CorpusStats.computeRelationshipTerms(receivedStats.get(contactName), overallReceivedStats, sentStats.get(contactName), overallSentStats);
			details.append("\n" + getString(R.string.shared_phrases_title) + ":\n");
			if (sortedOverlap.size() == 0)
				{
				details.append(" " + getString(R.string.none));
				details.append("\n");
				}
			else
				{
				for (int i = 0; i < 10 && i < sortedOverlap.size(); i++)
					details.append(" " + sortedOverlap.get(i) + "\n");
				}
			
			details.append("\n" + getString(R.string.response_time_title) + ":\n");
			// compute stats about the average response time
			if (theirReplyTimes.containsKey(contactName))
				{
				ArrayList<long[]> replies = theirReplyTimes.get(contactName);
				f.format(" %s: %s in %s\n", firstName, formatTime(averageSeconds(replies)), generateCountText(replies.size(), "text", "texts"));
				}
			else
				{
				f.format(" " + getString(R.string.not_enough_replies) + "\n", firstName);
				}

			if (yourReplyTimes.containsKey(contactName))
				{
				ArrayList<long[]> replies = yourReplyTimes.get(contactName);
				f.format(" %s: %s in %s\n", "You", formatTime(averageSeconds(replies)), generateCountText(replies.size(), "text", "texts"));
				}
			else
				{
				f.format(" " + getString(R.string.not_enough_replies) + "\n", "you");
				}
			
			// generate the single most likely full messages
			/*
			details.append("\nBest trigram generation\n");
			f.format(" %s: %s\n", firstName, receivedStats.get(contactName).generateBestMessage(true));
			f.format(" You: %s\n", sentStats.get(contactName).generateBestMessage(true));

			details.append("\nBest bigram generation\n");
			f.format(" %s: %s\n", firstName, receivedStats.get(contactName).generateBestMessage(false));
			f.format(" You: %s\n", sentStats.get(contactName).generateBestMessage(false));
			*/

			details.append("\nRandom trigram generation\n");
			f.format(" %s: %s\n", firstName, receivedStats.get(contactName).generateRandomMessage(true));
			f.format(" You: %s\n", sentStats.get(contactName).generateRandomMessage(true));

			details.append("\nRandom bigram generation\n");
			f.format(" %s: %s\n", firstName, receivedStats.get(contactName).generateRandomMessage(false));
			f.format(" You: %s\n", sentStats.get(contactName).generateRandomMessage(false));

			item.put(DETAILS, details.toString());
			listData.add(item);
			}
		setPreference(SELECT_CANDIDATES_KEY, System.currentTimeMillis() - time);

		
		/*************** SHOW IT *******************/
		runOnUiThread(new Runnable()
			{
			public void run()
				{
				ViewGroup parent = (ViewGroup) findViewById(R.id.linear);
				
				LayoutInflater inflater = getLayoutInflater();

				for (HashMap<String,String> item : listData)
					{
					// key phrases
					parent.addView(inflateResults(inflater, item.get(CONTACT_NAME), item.get(DETAILS)));
					}
				
				if (HomeActivity.DEVELOPER_MODE)
					parent.addView(inflateResults(inflater, getString(R.string.runtime), HomeActivity.summarizeRuntime(getApplicationContext(), PROFILING_KEY_ORDER)));
				}
			});
		}

	private void setPreference(String name, long longValue)
		{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		SharedPreferences.Editor editor = prefs.edit();
		editor.putLong(name, longValue);
		editor.commit();
		}
	
	public View inflateResults(LayoutInflater inflater, final String title, final String details)
		{
		// contacts
		View view = inflater.inflate(R.layout.result,
				null);
	
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
				
				startActivity(Intent.createChooser(sendIntent, getString(R.string.share_intent)));
				}
			});
		
		return view;
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
	
	public static String generateCountText(int number, String singular, String plural)
		{
		if (number == 1)
			return number + " " + singular;
		else
			return number + " " + plural;
		}
	}
