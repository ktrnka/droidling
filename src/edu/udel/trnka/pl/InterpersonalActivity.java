package edu.udel.trnka.pl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
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
	private HashMap<String, Long> runtime;
	private boolean scanned;
	private ArrayList<HashMap<String,String>> listData;

	private static final int PROGRESS_DIALOG = 0;
	private ProgressDialog progress;
	
	private static final String CONTACT_NAME = "contact";
	private static final String DETAILS = "details";
	
	public void onCreate(Bundle savedInstanceState)
		{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.simple_scroll);
		}
	
	public void onStart()
		{
		super.onStart();
		
		runtime = new HashMap<String,Long>();
		
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
		String subject = "Shared stats from " + getString(R.string.app_name);
		String body = "Analysis of SMS messages with " + listData.get(position).get(CONTACT_NAME) + ":\n" + listData.get(position).get(DETAILS);

		Intent sendIntent = new Intent(Intent.ACTION_SEND);
		sendIntent.setType("message/rfc822");
		sendIntent.putExtra(Intent.EXTRA_TEXT, body);
		sendIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
		
		startActivity(Intent.createChooser(sendIntent, getString(R.string.share_intent)));
		}

	private void scanSMS()
		{
		long time = System.currentTimeMillis();
		final HashMap<String, String> contactMap = new HashMap<String, String>();

		/*************** LOAD CONTACTS *******************/
		String numberName = ContactsContract.CommonDataKinds.Phone.NUMBER;
		String labelName = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME;
		String[] phoneLookupProjection = new String[] { numberName, labelName };

		Cursor phones = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
				phoneLookupProjection, null, null, null);

		if (phones.moveToFirst())
			{
			// TODO: Call getColumnIndex once, on the first message, and use the index the whole time (efficiency)
			do
				{
				String number = phones.getString(phones.getColumnIndex(numberName));
				String label = phones.getString(phones.getColumnIndex(labelName));

				String formattedNumber = PhoneNumberUtils.formatNumber(number);
				
				contactMap.put(formattedNumber, label);
				} while (phones.moveToNext());
			}
		else
			{
			warning("No contacts found");
			}
		phones.close();
		runtime.put("buildContactMap", System.currentTimeMillis() - time);
		
		/*************** PROCESS SENT MESSAGES *******************/
		time = System.currentTimeMillis();
		Uri uri = Uri.parse("content://sms/sent");
		Cursor messages = getContentResolver().query(uri, new String[] { "body", "address", "type" }, null, null, null);

		final HashMap<String, int[]> sentCounts = new HashMap<String, int[]>();
		
		
		final HashMap<String,CorpusStats> sentStats = new HashMap<String,CorpusStats>();
		final CorpusStats overallSentStats = new CorpusStats();
		
		if (messages.moveToFirst())
			{
			do
				{
				// figure out the name of the destination, store it in person
				String recipientId = messages.getString(messages.getColumnIndexOrThrow("address"));

				String recipientName = null;

				if (contactMap.containsKey(recipientId))
					recipientName = contactMap.get(recipientId);
				else
					{
					recipientId = PhoneNumberUtils.formatNumber(recipientId);
					if (contactMap.containsKey(recipientId))
						recipientName = contactMap.get(recipientId);
					}

				if (recipientName != null)
					{
					if (sentCounts.containsKey(recipientName))
						sentCounts.get(recipientName)[0]++;
					else
						sentCounts.put(recipientName, new int[] { 1 });
					
					String body = messages.getString(messages.getColumnIndexOrThrow("body"));
					if (!sentStats.containsKey(recipientName))
						sentStats.put(recipientName, new CorpusStats());
					
					sentStats.get(recipientName).train(body);
					overallSentStats.train(body);
					}
				} while (messages.moveToNext());
			}
		else
			{
			error("No sent messages found.");
			messages.close();
			return;
			}
		messages.close();
		runtime.put("processSentTexts", System.currentTimeMillis() - time);
		
		/*************** PROCESS RECEIVED MESSAGES *******************/
		time = System.currentTimeMillis();
		uri = Uri.parse("content://sms/inbox");
		messages = getContentResolver().query(uri, null, null, null, null);

		final HashMap<String, int[]> receivedCounts = new HashMap<String, int[]>();
		final HashMap<String,CorpusStats> receivedStats = new HashMap<String,CorpusStats>();
		
		final CorpusStats overallReceivedStats = new CorpusStats();
		
		if (messages.moveToFirst())
			{
			do
				{
				// figure out the name of the destination, store it in person
				String senderId = messages.getString(messages.getColumnIndexOrThrow("address"));

				String senderName = null;

				if (contactMap.containsKey(senderId))
					senderName = contactMap.get(senderId);
				else
					{
					senderId = PhoneNumberUtils.formatNumber(senderId);
					if (contactMap.containsKey(senderId))
						senderName = contactMap.get(senderId);
					}

				if (senderName != null)
					{
					if (receivedCounts.containsKey(senderName))
						receivedCounts.get(senderName)[0]++;
					else
						receivedCounts.put(senderName, new int[] { 1 });
					
					if (!receivedStats.containsKey(senderName))
						receivedStats.put(senderName, new CorpusStats());
					
					String message = messages.getString(messages.getColumnIndexOrThrow("body"));
					receivedStats.get(senderName).train(message);
					overallReceivedStats.train(message);
					}
				} while (messages.moveToNext());
			}
		else
			{
			error("No received messages found.");
			messages.close();
			return;
			}
		messages.close();
		runtime.put("processReceivedTexts", System.currentTimeMillis() - time);
		
		/*************** PROCESS IN THREADED VIEW ************************/
		// TODO:  switch all processing to use the FULL set of messages with this
		time = System.currentTimeMillis();
		uri = Uri.parse("content://sms");
		messages = getContentResolver().query(uri, new String[] { "address", "date", "type" }, null, null, "date asc");
		
		// mapping of (other person's parsed address) => [ type, date millis ]
		HashMap<String,long[]> previousMessage = new HashMap<String,long[]>();
		
		HashMap<String,ArrayList<long[]>> theirReplyTimes = new HashMap<String,ArrayList<long[]>>();
		HashMap<String,ArrayList<long[]>> yourReplyTimes = new HashMap<String,ArrayList<long[]>>();
		
		if (messages.moveToFirst())
			{
			do
				{
				// get the person's display string
				String person = messages.getString(messages.getColumnIndexOrThrow("address"));
				if (!contactMap.containsKey(PhoneNumberUtils.formatNumber(person)))
					continue;
				
				person = contactMap.get(PhoneNumberUtils.formatNumber(person));
				
				long millis = messages.getLong(messages.getColumnIndexOrThrow("date"));
				int type = messages.getInt(messages.getColumnIndexOrThrow("type"));
				
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
		
		
		
		/*************** ANALYSE, BUILD REPRESENTATION *******************/
		
		// score the contacts for sorting
		final HashMap<String,int[]> scoredContacts = new HashMap<String,int[]>();
		HashSet<String> uniqueContacts = new HashSet<String>(contactMap.values());
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
			Formatter f = new Formatter(details, Locale.US);
			details.append(firstName + " sent " + generateCountText(received, "text", "texts") + "\n");
			f.format("You sent %s (%.1f%% of all sent)\n\n", generateCountText(sent, "text", "texts"), 100 * sent / (double)overallSentStats.getMessages());
			
			//f.format("Received %d words\n", receivedStats.get(contactName).getFilteredWords());
			//f.format("Sent %d words\n\n", sentStats.get(contactName).getFilteredWords());

			// these stats are eerie and boring
			//f.format("Their avg word length: %.1f\n", receivedStats.get(contactName).getWordLength());
			//f.format("Your avg word length: %.1f\n", overallSentStats.getWordLength());
			//f.format("Your avg word length when texting THEM: %.1f\n", sentStats.get(contactName).getWordLength());
			
			// message length
			details.append("Average message length\n");
			f.format(" %s: %.1f words\n", firstName, receivedStats.get(contactName).getWordsPerMessage());
//			f.format("Your avg message length: %.1f\n", overallSentStats.getWordsPerMessage());
			f.format(" You: %.1f words\n\n", sentStats.get(contactName).getWordsPerMessage());
			
			// Jaccard coeffients
			details.append("Shared vocabulary\n");
			f.format(" with texts to them: %.1f%%\n", 100 * sentStats.get(contactName).computeUnigramJaccard(receivedStats.get(contactName)));
			f.format(" with ALL your texts: %.1f%%\n", 100 * overallSentStats.computeUnigramJaccard(receivedStats.get(contactName)));
			
			// figure out the vocabulary overlap
			ArrayList<String> sortedOverlap = CorpusStats.computeRelationshipTerms(receivedStats.get(contactName), overallReceivedStats, sentStats.get(contactName), overallSentStats);
			details.append("\nShared phrases:\n");
			for (int i = 0; i < 10 && i < sortedOverlap.size(); i++)
				details.append(" " + sortedOverlap.get(i) + "\n");
			
			details.append("\nAverage response time:\n");
			// compute stats about the average response time
			if (theirReplyTimes.containsKey(contactName))
				{
				ArrayList<long[]> replies = theirReplyTimes.get(contactName);
				f.format(" %s: %s in %s\n", firstName, formatTime(averageSeconds(replies)), generateCountText(replies.size(), "text", "texts"));
				}
			if (yourReplyTimes.containsKey(contactName))
				{
				ArrayList<long[]> replies = yourReplyTimes.get(contactName);
				f.format(" %s: %s in %s\n", "You", formatTime(averageSeconds(replies)), generateCountText(replies.size(), "text", "texts"));
				}

			item.put(DETAILS, details.toString());
			listData.add(item);
			}
		
		/*************** SHOW IT *******************/
		runOnUiThread(new Runnable()
			{
			public void run()
				{
				for (HashMap<String,String> item : listData)
					{
					ViewGroup parent = (ViewGroup) findViewById(R.id.linear);
					
					LayoutInflater inflater = getLayoutInflater();
					
					// key phrases
					parent.addView(inflateResults(inflater, item.get(CONTACT_NAME), item.get(DETAILS)));
					}
				}
			});
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
