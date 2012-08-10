package edu.udel.trnka.pl;

import java.util.HashMap;
import java.util.HashSet;

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
import android.widget.TextView;
import android.widget.Toast;

public class DiagnosticActivity extends Activity
	{
	static final int PROGRESS_DIALOG = 0;
	private ProgressDialog progress;
	private static final String TAG = "edu.udel.trnka.pl/DiagnosticActivity";

	public void onCreate(Bundle savedInstanceState)
		{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.simple_scroll);
		}

	public void onStart()
		{
		super.onStart();

		showDialog(PROGRESS_DIALOG);

		// run thread with callback to stop progress
		new Thread()
			{
			public void run()
				{
				runDiagnostics();

				dismissDialog(PROGRESS_DIALOG);
				}
			}.start();
		}

	protected Dialog onCreateDialog(int id)
		{
		switch (id)
			{
			case PROGRESS_DIALOG:
				progress = new ProgressDialog(DiagnosticActivity.this);
				progress.setIndeterminate(true);
				progress.setMessage(getString(R.string.loading));
				return progress;
			default:
				return null;
			}
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

	public void runDiagnostics()
		{
		final StringBuilder tableInfo = new StringBuilder();
		
		/********* CONTACTS TEST *************/
		final HashMap<String, String> contactMap = new HashMap<String, String>();

		String numberName = ContactsContract.CommonDataKinds.Phone.NUMBER;
		String labelName = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME;
		String[] phoneLookupProjection = new String[] { numberName, labelName };

		Cursor phones = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
				phoneLookupProjection, null, null, labelName);

		int numContacts = phones.getCount();
		if (phones.moveToFirst())
			{
			int phoneIndex = phones.getColumnIndex(numberName);
			int labelIndex = phones.getColumnIndex(labelName);
			
			tableInfo.append("neither of these should be -1:\n");
			tableInfo.append("\tphoneIndex: " + phoneIndex + "\n");
			tableInfo.append("\tlabelIndex: " + labelIndex);
			
			do
				{
				String number = phones.getString(phoneIndex);
				String label = phones.getString(labelIndex);
				
				String formattedNumber = PhoneNumberUtils.formatNumber(number);
				
				if (number.equals(formattedNumber))
					contactMap.put(number, number + "\n\t" + labelName + ": " + label + 
							"\n\tgetStrippedReversed: " + PhoneNumberUtils.getStrippedReversed(number) +
							"\n\ttoCallerIDMinMatch : " + PhoneNumberUtils.toCallerIDMinMatch (number));
				else
					contactMap.put(number, number + "\n\tformatted: " + formattedNumber + "\n\t" + labelName + ": " + label + 
							"\n\tgetStrippedReversed: " + PhoneNumberUtils.getStrippedReversed(number) +
							"\n\ttoCallerIDMinMatch : " + PhoneNumberUtils.toCallerIDMinMatch (number));

				} while (phones.moveToNext());
			}

		phones.close();

		final StringBuilder contactBuilder = new StringBuilder();
		contactBuilder.append(numContacts + " contacts\n");
		for (String number : contactMap.keySet())
			{
			contactBuilder.append(contactMap.get(number) + "\n");
			}
		
		/*********** MANUAL TESTS ****************/
		final StringBuilder manualBuilder = new StringBuilder();
		manualBuilder.append("544-8411 ?= 732-544-8411: " + PhoneNumberUtils.compare("544-8411", "732-544-8411") + "\n");
		manualBuilder.append("1-732-544-8411 ?= 732-544-8411: " + PhoneNumberUtils.compare("1-732-544-8411", "732-544-8411") + "\n");
		manualBuilder.append("302-544-8411 ?= 732-544-8411: " + PhoneNumberUtils.compare("302-544-8411", "732-544-8411") + "\n");
		manualBuilder.append("17325448411 ?= 732-544-8411: " + PhoneNumberUtils.compare("17325448411", "732-544-8411") + "\n");
		
		
		
		
		/************* SMS TEST ****************/
		Cursor messages = getContentResolver().query(Sms.CONTENT_URI, new String[] { Sms.ADDRESS, Sms.DATE, Sms.TYPE }, null, null, "date asc");

		HashSet<String> addresses = new HashSet<String>();
		final StringBuilder smsBuilder = new StringBuilder();
		smsBuilder.append("SMS stats from " + messages.getCount() + " messages (sent + received)");
		if (messages.moveToFirst())
			{
			int addressIndex = messages.getColumnIndexOrThrow(Sms.ADDRESS);
			do
				{
				// get the person's display string
				String address = messages.getString(addressIndex);
				addresses.add(address);

				} while (messages.moveToNext());
			}
		messages.close();
		
		for (String address : addresses)
			{
			smsBuilder.append("\n" + address);
			}
		
		/************* MMS TEST ****************/
		Uri uri = Uri.parse("content://mms");
		messages = getContentResolver().query(uri, null, null, null, null);

		
		addresses = new HashSet<String>();
		final StringBuilder mmsBuilder = new StringBuilder();
		mmsBuilder.append("MMS stats from " + messages.getCount() + " messages (sent + received)\nNo additional info cause the column names are weird.");
		/*
		if (messages.moveToFirst())
			{
			String [] columns = messages.getColumnNames();
			
			int addressIndex = messages.getColumnIndexOrThrow("address");
			do
				{
				// get the person's display string
				String address = messages.getString(addressIndex);
				addresses.add(address);

				} while (messages.moveToNext());
			}
			*/
		messages.close();
		
		for (String address : addresses)
			{
			mmsBuilder.append("\n" + address);
			}
		
		/*************** SHOW IT *******************/
		runOnUiThread(new Runnable()
			{
			public void run()
				{
				ViewGroup parent = (ViewGroup) findViewById(R.id.linear);

				LayoutInflater inflater = getLayoutInflater();

				//parent.addView(inflateResults(inflater, "Manual Tests", manualBuilder.toString()));
				parent.addView(inflateResults(inflater, "Contact Info", contactBuilder.toString()));
				parent.addView(inflateResults(inflater, "SMS Info", smsBuilder.toString()));
				parent.addView(inflateResults(inflater, "MMS Info", mmsBuilder.toString()));
				parent.addView(inflateResults(inflater, "Table Info", tableInfo.toString()));
				}
			});
		}
	
	public static String phoneCompareTest(String a, String b)
		{
		return a + " ?= " + b + PhoneNumberUtils.compare(a, b) + "\n";
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

	}
