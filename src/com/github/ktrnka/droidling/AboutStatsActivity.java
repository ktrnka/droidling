package com.github.ktrnka.droidling;

import android.app.Activity;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

public class AboutStatsActivity extends Activity
	{
    public void onCreate(Bundle savedInstanceState)
		{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.about_stats);
		
		// these lines are necessary for clickable TextViews with <a href> in the strings
		TextView textView = (TextView) findViewById(R.id.about_key_phrases);
		textView.setMovementMethod(LinkMovementMethod.getInstance());

		textView = (TextView) findViewById(R.id.about_shared_vocab);
		textView.setMovementMethod(LinkMovementMethod.getInstance());
		}
	}
