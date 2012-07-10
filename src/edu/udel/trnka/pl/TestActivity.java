package edu.udel.trnka.pl;

import java.util.ArrayList;
import java.util.HashMap;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

public class TestActivity extends Activity implements OnItemClickListener
	{
	public void onCreate(Bundle savedInstanceState)
		{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.test);
		
		ListView list = (ListView) findViewById(R.id.testList);
		
		
		//ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
		//		android.R.layout.simple_list_item_1, android.R.id.text1, values);
		
		SimpleAdapter adapter = new SimpleAdapter(this, buildData(), android.R.layout.simple_list_item_2, 
				buildNames(),
				new int[] { android.R.id.text1, android.R.id.text2 } );
		
		list.setAdapter(adapter);
		list.setOnItemClickListener(this);
		}

	public void onItemClick(AdapterView<?> parent, View view, int position, long id)
		{
		Toast.makeText(getApplicationContext(), parent.getItemAtPosition(position).toString(), Toast.LENGTH_LONG).show();
		}
	
	public HashMap<String,String> buildPair(String name, String desc)
		{
		HashMap<String,String> map = new HashMap<String,String>();
		map.put("name", name);
		map.put("desc", desc);
		return map;
		}
	
	private ArrayList<HashMap<String,String>> buildData()
		{
		ArrayList<HashMap<String,String>> data = new ArrayList<HashMap<String,String>>();
		data.add(buildPair("Personal Stats", "Analyse sent messages."));
		data.add(buildPair("Interpersonal Stats", "Analyse sent, received messages.  Compare your messages to your contacts'."));
		data.add(buildPair("About", "Duh."));
		return data;
		}
	
	private String[] buildNames()
		{
		return new String[] { "name", "desc" };
		}
	}
