package com.github.ktrnka.droidling;

import com.actionbarsherlock.app.SherlockActivity;
import com.github.ktrnka.droidling.R;

import android.os.Bundle;
import android.widget.TextView;

/**
 * The activity that describes the app, the author, etc.  Not much code,
 * most of the work is in the XML file.
 * @author keith.trnka
 *
 */
public class AboutLangIDActivity extends SherlockActivity
	{
    public void onCreate(Bundle savedInstanceState)
		{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.about_langid);
		
		// extract the extra
		TextView languagesTextView = (TextView) findViewById(R.id.about_langid_languages);
		String languagesString = getIntent().getStringExtra(LanguageIdentificationActivity.EXTRA_LANGUAGES);
		if (languagesString != null)
			languagesTextView.setText(languagesString);
		}
	}
