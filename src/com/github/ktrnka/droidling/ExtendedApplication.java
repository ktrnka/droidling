/**
 * 
 */
package com.github.ktrnka.droidling;

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

	}
