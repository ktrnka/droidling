package com.github.ktrnka.droidling;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Wrapper for the output of PersonalActivity to load/save.
 * 
 * TODO: Serialize in Json instead; easier to debug, easier to upgrade.
 */
public class PersonalStats
	{
	public static final int PHRASE_SORTED = 0;
	public static final int COUNT_SORTED = 1;

	StringBuilder[] keyPhraseTexts;
	StringBuilder contactsDisplay;
	StringBuilder generalDisplay;
	
	int[] dayHistogram;
	int[] hourHistogram;
	
	public PersonalStats()
		{
		keyPhraseTexts = new StringBuilder[2];
		for (int i = 0; i < keyPhraseTexts.length; i++)
			keyPhraseTexts[i] = new StringBuilder();
		
		contactsDisplay = new StringBuilder();
		generalDisplay = new StringBuilder();
		
		dayHistogram = new int[7];
		for (int i = 0; i < dayHistogram.length; i++)
			dayHistogram[i] = 0;
		
		hourHistogram = new int[25];
		for (int i = 0; i < hourHistogram.length; i++)
			hourHistogram[i] = 0;
		}

	public PersonalStats(FileInputStream in) throws IOException
		{
		this();
		readFrom(in);
		}

	private void readFrom(FileInputStream in) throws IOException
	    {
	    DataInputStream dataIn = new DataInputStream(new BufferedInputStream(in));
	    
	    keyPhraseTexts[PHRASE_SORTED].append(dataIn.readUTF());
	    keyPhraseTexts[COUNT_SORTED].append(dataIn.readUTF());
	    contactsDisplay.append(dataIn.readUTF());
	    generalDisplay.append(dataIn.readUTF());

		for (int i = 0; i < dayHistogram.length; i++)
			dayHistogram[i] = dataIn.readInt();
		
		for (int i = 0; i < hourHistogram.length; i++)
			hourHistogram[i] = dataIn.readInt();
	    
	    in.close();
	    }
	
	public void writeTo(FileOutputStream out) throws IOException
		{
		DataOutputStream dataOut = new DataOutputStream(new BufferedOutputStream(out));
		
		dataOut.writeUTF(keyPhraseTexts[PHRASE_SORTED].toString());
		dataOut.writeUTF(keyPhraseTexts[COUNT_SORTED].toString());
		dataOut.writeUTF(contactsDisplay.toString());
		dataOut.writeUTF(generalDisplay.toString());
		
		for (int i = 0; i < dayHistogram.length; i++)
			dataOut.writeInt(dayHistogram[i]);
		
		for (int i = 0; i < hourHistogram.length; i++)
			dataOut.writeInt(hourHistogram[i]);

		dataOut.close();
		}
	}
