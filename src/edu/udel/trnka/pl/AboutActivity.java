package edu.udel.trnka.pl;

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
		

		/*
        
        
		Account[] accounts = AccountManager.get(this).getAccountsByType("com.google");
		
		Cursor messages = getContentResolver().query(Uri.parse("content://gmail-ls/unread/" + accounts[0]), null, null, null, null);
		if (messages.moveToFirst())
			{
			do
				{
				String[] columns = messages.getColumnNames();
				//about.append(messages.getString(messages.getColumnIndexOrThrow("")))
				} while (messages.moveToNext());
			}
		
		for (Account account : accounts)
			{
			about.append("\n" + account.name);
			}
			*/
		}
    

	}
