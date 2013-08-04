package com.github.ktrnka.droidling;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import android.content.Context;

/**
 * Bundle of all the output strings from analysing communication with one contact.
 */
public class InterpersonalSingleStats
	{
	public static final int seralizationVersion = 1;
	String nameText;
	String numSentText;
	String messageLengthText;
	String sharedVocabPercentText;
	String sharedPhrasesText;
	String responseTimeText;
	String bigramGenerationText;
	String trigramGenerationText;
	String photoUri;

	/**
	 * Generate a single string with all of the stats and appropriate labels for them.
	 * @param context the Android context, to get localized strings
	 */
	public String toString(Context context)
		{
		StringBuilder builder = new StringBuilder();
		
		builder.append(numSentText);
		builder.append("\n\n");
		
		builder.append(context.getString(R.string.average_message_length_header));
		builder.append("\n");
		builder.append(messageLengthText);
		builder.append("\n\n");
		
		builder.append(context.getString(R.string.shared_vocabulary_header));
		builder.append("\n");
		builder.append(sharedVocabPercentText);
		builder.append("\n\n");
		
		builder.append(context.getString(R.string.shared_phrases_title));
		builder.append("\n");
		builder.append(sharedPhrasesText);
		builder.append("\n\n");
		
		builder.append(context.getString(R.string.response_time_title));
		builder.append("\n");
		builder.append(responseTimeText);
		builder.append("\n\n");
		
		builder.append("Random trigram generation");
		builder.append("\n");
		builder.append(trigramGenerationText);
		builder.append("\n\n");
		
		builder.append("Random bigram generation");
		builder.append("\n");
		builder.append(bigramGenerationText);
		
		return builder.toString();
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
	    dataOut.writeUTF(photoUri == null ? "" : photoUri);
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
	    
	    stats.photoUri = dataIn.readUTF();
	    if (stats.photoUri.equals(""))
	    	stats.photoUri = null;
	    
	    return stats;
	    }
	}
