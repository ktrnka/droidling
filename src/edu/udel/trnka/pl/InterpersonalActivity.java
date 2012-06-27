package edu.udel.trnka.pl;

import static edu.udel.trnka.pl.Tokenizer.isNonword;
import static edu.udel.trnka.pl.Tokenizer.tokenize;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

import android.app.Activity;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

public class InterpersonalActivity extends ListActivity
	{
	private HashMap<String, Long> runtime;
	private boolean scanned;
	private ArrayList<HashMap<String,String>> data;

	public void onCreate(Bundle savedInstanceState)
		{
		super.onCreate(savedInstanceState);
		//setContentView(R.layout.interpersonal);
		}
	
	public void onStart()
		{
		super.onStart();
		
		runtime = new HashMap<String,Long>();
		
		if (!scanned)
			{
			// start progress
			final ProgressDialog progress = ProgressDialog.show(InterpersonalActivity.this, "", "Scanning...", true);
			
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
	
	public void onListItemClick(ListView list, View view, int position, long id)
		{
		// figure out the text
		String subject = "Shared stats from " + getString(R.string.app_name);
		String body = "Analysis of SMS messages with " + data.get(position).get("contact") + ":\n" + data.get(position).get("details");

		Intent sendIntent = new Intent(Intent.ACTION_SEND);
		sendIntent.setType("message/rfc822");
		sendIntent.putExtra(Intent.EXTRA_TEXT, body);
		sendIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
		
		startActivity(Intent.createChooser(sendIntent, "Share with..."));
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
			do
				{
				String number = phones.getString(phones.getColumnIndex(numberName));
				String label = phones.getString(phones.getColumnIndex(labelName));

				contactMap.put(number, label);
				} while (phones.moveToNext());
			}
		phones.close();
		runtime.put("buildContactMap", System.currentTimeMillis() - time);
		
		/*************** PROCESS SENT MESSAGES *******************/
		time = System.currentTimeMillis();
		Uri uri = Uri.parse("content://sms/sent");
		Cursor messages = getContentResolver().query(uri, new String[] { "body", "address" }, null, null, null);

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
		messages.close();
		runtime.put("processSentTexts", System.currentTimeMillis() - time);
		
		/*************** PROCESS RECEIVED MESSAGES *******************/
		time = System.currentTimeMillis();
		uri = Uri.parse("content://sms/inbox");
		messages = getContentResolver().query(uri, null, null, null, null);

		final HashMap<String, int[]> receivedCounts = new HashMap<String, int[]>();
		final HashMap<String,CorpusStats> receivedStats = new HashMap<String,CorpusStats>();
		
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
					
					receivedStats.get(senderName).train(messages.getString(messages.getColumnIndexOrThrow("body")));
					}
				} while (messages.moveToNext());
			}
		messages.close();
		runtime.put("processReceivedTexts", System.currentTimeMillis() - time);
		
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
		data = new ArrayList<HashMap<String,String>>();
		for (String contactName : contactList)
			{
			int received = 0;
			if (receivedCounts.containsKey(contactName))
				received = receivedCounts.get(contactName)[0];
			
			int sent = 0;
			if (sentCounts.containsKey(contactName))
				sent = sentCounts.get(contactName)[0];
			
			if (sent == 0 || received == 0)
				continue;
			
			HashMap<String,String> item = new HashMap<String,String>();
			item.put("contact", contactName);
			
			StringBuilder details = new StringBuilder();
			Formatter f = new Formatter(details, Locale.US);
			details.append("Received " + received + " texts\n");
			f.format("Sent %d texts (%.1f%% of all sent)\n\n", sent, 100 * sent / (double)overallSentStats.getMessages());
			
			f.format("Received %d words\n", receivedStats.get(contactName).getFilteredWords());
			f.format("Sent %d words\n\n", sentStats.get(contactName).getFilteredWords());

			// these stats are eerie and boring
			//f.format("Their avg word length: %.1f\n", receivedStats.get(contactName).getWordLength());
			//f.format("Your avg word length: %.1f\n", overallSentStats.getWordLength());
			//f.format("Your avg word length when texting THEM: %.1f\n", sentStats.get(contactName).getWordLength());
			
			// message length
			f.format("Their avg message length: %.1f\n", receivedStats.get(contactName).getWordsPerMessage());
			f.format("Your avg message length: %.1f\n", overallSentStats.getWordsPerMessage());
			f.format("Your avg message length when texting THEM: %.1f\n\n", sentStats.get(contactName).getWordsPerMessage());
			
			// Jaccard coeffients
			f.format("Vocabulary overlap with your texts to them: %.1f%%\n", 100 * sentStats.get(contactName).computeUnigramJaccard(receivedStats.get(contactName)));
			f.format("Vocabulary overlap with ALL your texts: %.1f%%\n", 100 * overallSentStats.computeUnigramJaccard(receivedStats.get(contactName)));

			item.put("details", details.toString());
			data.add(item);
			}
		
		
		final SimpleAdapter adapter = new SimpleAdapter(this, data,
				android.R.layout.simple_list_item_2,
				new String[] { "contact", "details" },
				new int[] { android.R.id.text1, android.R.id.text2 });
		
		
		
		
		/*************** SHOW IT *******************/
		runOnUiThread(new Runnable()
			{
			public void run()
				{
				setListAdapter(adapter);
				}
			});
		}
	}
