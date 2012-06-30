package edu.udel.trnka.pl;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class DescriptionMenuAdapter extends ArrayAdapter<String>
	{
	private String[] names;
	private String[] descriptions;
	private Context c;

	public DescriptionMenuAdapter(Context context, String[] names, String[] descriptions)
		{
		// this super call isn't pretty; the simple_list_item_1 probably isn't necessary, but passing in names is
		// so that the number of list elements is correct
		super(context, android.R.layout.simple_list_item_1, names);

		this.names = names;
		this.descriptions = descriptions;
		c = context;
		}

	@Override
	public View getView(int position, View convertView, ViewGroup parent)
		{
		if (position >= names.length)
			return convertView;
		
		if (descriptions[position] != null && descriptions[position].length() > 0)
			{
			// inflate
			
			if (convertView == null)
				{
				LayoutInflater inflater = (LayoutInflater) c.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				convertView = inflater.inflate(android.R.layout.simple_list_item_2, null);
				}
			
			// fill the data
			TextView text = (TextView) convertView.findViewById(android.R.id.text1);
			if (text != null)
				text.setText(names[position]);
			
			text = (TextView) convertView.findViewById(android.R.id.text2);
			if (text != null)
				text.setText(descriptions[position]);
			}
		else
			{
			// inflate
			
			if (convertView == null)
				{
				LayoutInflater inflater = (LayoutInflater) c.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				convertView = inflater.inflate(android.R.layout.simple_list_item_1, null);
				}
			
			// fill the data
			TextView text = (TextView) convertView.findViewById(android.R.id.text1);
			if (text != null)
				text.setText(names[position]);
			}
		return convertView;
		}
	}
