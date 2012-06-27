package edu.udel.trnka.pl;

import static edu.udel.trnka.pl.Tokenizer.isNonword;
import static edu.udel.trnka.pl.Tokenizer.tokenize;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Statistics about a homogeneous corpus, such as a single person's sent or received messages.
 * @author keith.trnka
 *
 */
public class CorpusStats
	{
	int messages;
	int filteredWords;
	int unfilteredWords;
	int chars;
	int filteredWordLength;
	
	HashMap<String,int[]> unigrams;
	int unigramTotal;
	
	HashMap<String,HashMap<String, int[]>> bigrams;
	int bigramTotal;
	
	HashMap<String,HashMap<String,HashMap<String, int[]>>> trigrams;
	int trigramTotal;
	
	public CorpusStats()
		{
		messages = 0;
		filteredWords = 0;
		unfilteredWords = 0;
		chars = 0;
		filteredWordLength = 0;
		
		unigrams = new HashMap<String,int[]>();
		bigrams = new HashMap<String,HashMap<String, int[]>>();
		trigrams = new HashMap<String,HashMap<String,HashMap<String, int[]>>>();
		
		unigramTotal = 0;
		bigramTotal = 0;
		trigramTotal = 0;
		}
	
	public void train(String message)
		{
		messages++;
		chars += message.length();
		
		ArrayList<String> tokens = tokenize(message);
		
		// update the ngrams!
		String previous = null, ppWord = null;
		for (String token : tokens)
			{
			// TODO: change this to truecasing
			token = token.toLowerCase();
			
			// unigrams
			if (unigrams.containsKey(token))
				unigrams.get(token)[0]++;
			else
				unigrams.put(token, new int[] { 1 });
			unigramTotal++;
			unfilteredWords++;
			
			// filtered unigram stats
			// TODO: compute this from the distribution at the end (faster)
			if (!isNonword(token))
				{
				filteredWords++;
				filteredWordLength += token.length();
				}
			
			// bigrams
			if (previous != null)
				{
				if (!bigrams.containsKey(previous))
					bigrams.put(previous, new HashMap<String, int[]>());
				
				if (!bigrams.get(previous).containsKey(token))
					bigrams.get(previous).put(token, new int[] { 1 });
				else
					bigrams.get(previous).get(token)[0]++;
				bigramTotal++;
				
				// trigrams
				if (ppWord != null)
					{
					if (!trigrams.containsKey(ppWord))
						trigrams.put(ppWord, new HashMap<String,HashMap<String, int[]>>());
					
					HashMap<String,HashMap<String, int[]>> bigramSubdist = trigrams.get(ppWord);
					
					if (!bigramSubdist.containsKey(previous))
						bigramSubdist.put(previous, new HashMap<String, int[]>());
					
					HashMap<String,int[]> dist = bigramSubdist.get(previous);
					
					if (!dist.containsKey(token))
						dist.put(token, new int[] { 1 });
					else
						dist.get(token)[0]++;
					
					trigramTotal++;
					}
				}
			
			// move the history back
			ppWord = previous;
			previous = token;
			}
		}
	
	public int getMessages()
		{
		return messages;
		}

	public double getCharsPerMessage()
		{
		return chars / (double)messages;
		}

	public double getWordsPerMessage()
		{
		return filteredWords / (double)messages;
		}

	public double getWordLength()
		{
		return filteredWordLength / (double) filteredWords;
		}

	public int getFilteredWords()
		{
		return filteredWords;
		}	
	
	public double computeUnigramJaccard(CorpusStats other)
		{
		int intersection = 0;
		int union = 0;
		
		union = unigrams.size();
		for (String word : unigrams.keySet())
			{
			if (other.unigrams.containsKey(word))
				intersection++;
			}
		
		for (String word : other.unigrams.keySet())
			if (!unigrams.containsKey(word))
				union++;
		
		return intersection / (double) union;
		}
	}
