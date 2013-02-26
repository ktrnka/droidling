package com.github.ktrnka.droidling;
import static com.github.ktrnka.droidling.Tokenizer.isNonword;
import static com.github.ktrnka.droidling.Tokenizer.tokenize;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;



public class PersonalStats
	{
	CorpusStats sentStats;
	
	HashMap<String, int[]> shortMessages;
	int shortMessageTotal;
	
	HashMap<String, int[]> simplePhrases;
	int simplePhraseTotal;
	
	DateDistribution dates;
	
	HashMap<String, int[]> personCounts;
	
	private ArrayList<String> simplePhrase;
	
	private final TrainingParameters params;
	private final StringBuilder reusableBuilder;
	
	public PersonalStats(TrainingParameters params)
		{
		this.params = params;
		sentStats = new CorpusStats();
		
		shortMessages = new HashMap<String, int[]>();
		shortMessageTotal = 0;
		
		simplePhrases = new HashMap<String, int[]>();
		simplePhraseTotal = 0;
		
		dates = new DateDistribution();
		
		personCounts = new HashMap<String, int[]>();
		
		simplePhrase = new ArrayList<String>();
		
		reusableBuilder = new StringBuilder();
		}
	
	public void train(String sentTo, String body, Date date)
		{
		updateDate(date);
		updateContacts(sentTo);
		updateMessage(body);
		}

	private void updateMessage(String body)
	    {
	    // update short messages
		if (body.length() <= params.maxShortMessageLength)
			{
			String text = body;

			if (shortMessages.containsKey(text))
				shortMessages.get(text)[0]++;
			else
				shortMessages.put(text, new int[] { 1 });
			shortMessageTotal++;
			}
		
		// tokenize
		ArrayList<String> tokens = tokenize(body);
		
		// normal sent stats
		sentStats.train(tokens, body.length());
		
		// update simple phrases
		simplePhrase.clear();
		for (String token : tokens)
			{
			// simple phrases
			if (!isNonword(token) && !params.smallStopwords.contains(token))
				{
				// add to the phrase
				simplePhrase.add(token);
				}
			else
				{
				if (simplePhrase.size() > 0)
					{
					reusableBuilder.setLength(0);
					reusableBuilder.append(simplePhrase.get(0));
					for (int i = 1; i < simplePhrase.size(); i++)
						{
						reusableBuilder.append(' ');
						reusableBuilder.append(simplePhrase.get(i));
						}

					String phraseString = reusableBuilder.toString();

					if (simplePhrases.containsKey(phraseString))
						simplePhrases.get(phraseString)[0]++;
					else
						simplePhrases.put(phraseString, new int[] { 1 });

					simplePhraseTotal++;
					}
				// flush the phrase
				simplePhrase.clear();
				}
			}
	    }

	private void updateContacts(String sentTo)
	    {
	    if (personCounts.containsKey(sentTo))
	    	personCounts.get(sentTo)[0]++;
	    else
	    	personCounts.put(sentTo, new int[] { 1 });
	    }

	private void updateDate(Date date)
	    {
	    dates.add(date);
	    }
	
	/**
	 * Parameter settings that control the kinds of key phrases
	 * we learn.
	 * @author keith.trnka
	 *
	 */
	public static class TrainingParameters
		{
		public int maxShortMessageLength;
		public HashSet<String> smallStopwords;
		}
	}
