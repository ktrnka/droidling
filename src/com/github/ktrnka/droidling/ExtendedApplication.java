/**
 * 
 */
package com.github.ktrnka.droidling;

import java.util.Locale;

import android.app.Application;

/**
 * @author keith.trnka
 * 
 */
public class ExtendedApplication extends Application
	{

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
	 * 
	 * @return true if successful, false if failed (such as timeout)
	 */
	public boolean blockingLoadContacts()
		{
		return true;
		}
	
	private void nonblockingLoadContacts()
		{
		
		}
	
	public boolean blockingLoadUnigrams(Locale locale)
		{
		return true;
		}
	
	private void nonblockingLoadUnigrams()
		{
		
		}

	}
