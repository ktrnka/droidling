package com.github.ktrnka.droidling;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Wrapper around the stats from InterpersonalActivity to load/save.
 */
public class LIDStats
	{
	ArrayList<Item> list;
	
	public LIDStats()
		{
		list = new ArrayList<Item>();
		}
	
	public LIDStats(FileInputStream in) throws IOException
		{
		readFrom(in);
		}
	
	private void readFrom(FileInputStream in) throws IOException
	    {
	    DataInputStream dataIn = new DataInputStream(new BufferedInputStream(in));
	    
	    int numItems = dataIn.readInt();
	    list = new ArrayList<Item>(numItems);
	    
	    for (int i = 0; i < numItems; i++)
	    	{
	    	add(dataIn.readUTF(), dataIn.readUTF());
	    	}
	    
	    dataIn.close();
	    }
	
	public void writeTo(FileOutputStream out) throws IOException
		{
		DataOutputStream dataOut = new DataOutputStream(new BufferedOutputStream(out));
		
		dataOut.writeInt(list.size());
		
		for (Item item : list)
			{
			dataOut.writeUTF(item.name.toString());
			dataOut.writeUTF(item.details.toString());
			}
		
		dataOut.close();
		}
	
	public void add(CharSequence name, CharSequence details)
		{
		list.add(new Item(name, details));
		}

	public static class Item
		{
		CharSequence name;
		CharSequence details;
		
		public Item(CharSequence name, CharSequence details)
			{
			this.name = name;
			this.details = details;
			}
		}
	}
