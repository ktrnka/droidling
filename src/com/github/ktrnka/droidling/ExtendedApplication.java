/**
 * 
 */
package com.github.ktrnka.droidling;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import android.app.Application;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;

/**
 * @author keith.trnka
 * 
 */
public class ExtendedApplication extends Application
	{
	private HashMap<String,ArrayList<String[]>> contactMap;
	private Runnable contactThread;

	/**
	 * 
	 */
	public ExtendedApplication()
		{
		super();
		}
	
	public void onCreate()
		{
		// start thread to load contacts
		
		// start thread to load unigrams for the current language
		
		// start thread to load stopwords
		
		// start thread to load LID model
		}

	/**
	 * Load the contacts into an internal data structure.
	 * @return true if successful, false if failed (such as a timeout or the contacts database is empty)
	 */
	public boolean blockingLoadContacts()
		{
		if (contactMap != null)
			return true;
		
		contactMap = new HashMap<String,ArrayList<String[]>>();

		String numberName = ContactsContract.CommonDataKinds.Phone.NUMBER;
		String labelName = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME;
		String[] phoneLookupProjection = new String[] { numberName, labelName };

		Cursor phones = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
				phoneLookupProjection, null, null, null);

		if (phones.moveToFirst())
			{
			int phoneIndex = phones.getColumnIndex(numberName);
			int labelIndex = phones.getColumnIndex(labelName);
			
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
			return false;
			}
		phones.close();
		
		return true;
		}

	/**
	 * The number may be in a very different format than the way it's stored in contacts,
	 * so we need to do a fancy-pants matching.  I tried using the ContentProvider way
	 * of doing this at first but it was much too slow.
	 *
	 * TODO: This function could save some temporary object creation by re-inserting the
	 * contact using the potentially non-standard input parameter or caching in another way.
	 * 
	 * @param number
	 * @return The display name value if found, null if not.
	 */
	public String lookupContactName(String number)
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

	/**
	 * start processing contacts in another thread and return
	 */
	private void nonblockingLoadContacts()
		{
		
		}
	
	/**
	 * The intention is for this to see if a contact thread is running already
	 * and fail if one is.  If one isn't, it can create the thread and return it.
	 * @return a new thread if one doesn't already exist, null if a thread already exists
	 */
	private synchronized Runnable createContactThread()
		{
		if (contactThread != null)
			return null;
		
		return new Thread()
			{
			public void run()
				{
				blockingLoadContacts();
				contactThread = null;
				}
			};
		}
	
	private boolean blockingLoadUnigrams(Locale locale)
		{
		return true;
		}
	
	private void nonblockingLoadUnigrams()
		{
		
		}

	}
