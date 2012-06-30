package edu.udel.trnka.pl;

import android.app.Activity;
import android.os.Bundle;

/**
 * Basic activity to show info about the app/author.
 * @author keith.trnka
 *
 */
public class AboutActivity extends Activity
	{
	public void onCreate(Bundle savedInstanceState)
		{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.about);
		}
	}
