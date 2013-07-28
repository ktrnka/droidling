package com.github.ktrnka.droidling;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import android.content.Context;

public class InterpersonalSingleStats
	{
	String nameText;
	String numSentText;
	String messageLengthText;
	String sharedVocabPercentText;
	String sharedPhrasesText;
	String responseTimeText;
	String bigramGenerationText;
	String trigramGenerationText;

	public String toString(Context context)
		{
		return "";
		}

	public void serialize(DataOutputStream dataOut) throws IOException
	    {
	    dataOut.writeUTF(nameText);
	    dataOut.writeUTF(numSentText);
	    dataOut.writeUTF(messageLengthText);
	    dataOut.writeUTF(sharedVocabPercentText);
	    dataOut.writeUTF(sharedPhrasesText);
	    dataOut.writeUTF(responseTimeText);
	    dataOut.writeUTF(bigramGenerationText);
	    dataOut.writeUTF(trigramGenerationText);
	    }

	public static InterpersonalSingleStats deserialize(DataInputStream dataIn) throws IOException
	    {
	    InterpersonalSingleStats stats = new InterpersonalSingleStats();
	    stats.nameText = dataIn.readUTF();
	    stats.numSentText = dataIn.readUTF();
	    stats.messageLengthText = dataIn.readUTF();
	    stats.sharedVocabPercentText = dataIn.readUTF();
	    stats.sharedPhrasesText = dataIn.readUTF();
	    stats.responseTimeText = dataIn.readUTF();
	    stats.bigramGenerationText = dataIn.readUTF();
	    stats.trigramGenerationText = dataIn.readUTF();
	    
	    return stats;
	    }
	}
