package com.github.ktrnka.droidling;

import com.actionbarsherlock.app.SherlockActivity;
import com.github.ktrnka.droidling.R;

import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

/**
 * The activity that describes the app, the author, etc.  Not much code,
 * most of the work is in the XML file.
 * @author keith.trnka
 *
 */
public class AboutInterpersonalActivity extends SherlockActivity
	{
    public void onCreate(Bundle savedInstanceState)
		{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.about_interpersonal);
		
		// these lines are necessary for clickable TextViews with <a href> in the strings
		TextView textView = (TextView) findViewById(R.id.about_shared_vocab);
		textView.setMovementMethod(LinkMovementMethod.getInstance());
		}
	}
