package com.github.ktrnka.droidling;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import com.github.ktrnka.droidling.R;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.widget.TextView;

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
		
		// add version string
		PackageManager manager = this.getPackageManager();
		PackageInfo info;
		TextView about = (TextView) findViewById(R.id.about_description);
		try
			{
			info = manager.getPackageInfo(this.getPackageName(), 0);
			about.append("\n\nVersion: " + info.versionName + " (" + info.versionCode + ")");
			}
		catch (NameNotFoundException e)
			{
			about.append("\n\nError found in loading version number");
			}
		
		// load the changelog
		TextView changelog = (TextView) findViewById(R.id.changelog);
		try
			{
			BufferedReader in = new BufferedReader(new InputStreamReader(getAssets().open("changelog.txt")));
			changelog.setText("");
			String line;
			while ( (line = in.readLine()) != null)
				{
				changelog.append(line);
				changelog.append("\n");
				}
			in.close();
			}
		catch (IOException e)
			{
			changelog.setText("Failed to load changelog.");
			}
		}
    

	}
