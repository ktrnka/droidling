package edu.udel.trnka.pl;

import java.util.ArrayList;
import java.util.HashMap;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

public class HomeActivity extends ListActivity
	{
	// TODO: use strings.xml for localization
	private static final String[] names = { "Personal Stats", "Interpersonal Stats", "Send Feedback", "About" };
	private static final String[] descriptions = { "Analyse sent messages.", "Compare your messages to messages from your friends!", "", "" };
	
	// TODO:  I don't like how this uses parallel arrays.  I'd much rather do something like make an instance that has all this (could I do it by overriding toString in the Acitivites?)
	private static final Class<?>[] activities = { PersonalLingActivity.class, InterpersonalActivity.class, null, AboutActivity.class };
	
	public void onCreate(Bundle savedInstanceState)
		{
		super.onCreate(savedInstanceState);
		
		// build the structure we need to pass to SimpleAdapter
		ArrayList<HashMap<String,String>> fields = new ArrayList<HashMap<String,String>>();
		for (int i = 0; i < names.length; i++)
			{
			HashMap<String,String> item = new HashMap<String,String>();
			item.put("name", names[i]);
			item.put("desc", descriptions[i]);
			fields.add(item);
			}
		
		// simple adapter that's just plain strings
		//ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
		//		android.R.layout.simple_list_item_1, android.R.id.text1, names);
		
		SimpleAdapter adapter = new SimpleAdapter(this, fields,
				android.R.layout.simple_list_item_2,
				new String[] { "name", "desc" },
				new int[] { android.R.id.text1, android.R.id.text2 });
		
		
		setListAdapter(adapter);
		}

	public void onListItemClick(ListView list, View view, int position, long id)
		{
		if (position < activities.length && activities[position] != null)
			{
			// launch the activity if it's found
			Intent intent = new Intent(this, activities[position]);
			startActivity(intent);
			}
		else if (position < names.length && names[position].equals("Send Feedback"))
			{
			// special case for the feedback option
			
			Intent sendIntent = new Intent(Intent.ACTION_SEND);
			sendIntent.setType("message/rfc822");
			sendIntent.putExtra(Intent.EXTRA_EMAIL, new String[] { "keith.trnka@gmail.com" });
			sendIntent.putExtra(Intent.EXTRA_SUBJECT, "Feedback on " + getString(R.string.app_name));
			
			startActivity(Intent.createChooser(sendIntent, "Send email using..."));
			}
		else
			{
			Toast.makeText(getApplicationContext(), "Not Implemented Yet", Toast.LENGTH_SHORT).show();
			}

	
		}


	}