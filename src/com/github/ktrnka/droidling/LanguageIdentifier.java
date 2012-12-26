package com.github.ktrnka.droidling;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Code to load a data file and identify the language of
 * a unigram model.
 * @author keith.trnka
 *
 */
public class LanguageIdentifier
	{
	private ArrayList<LIDModel> models;
	
	public LanguageIdentifier(InputStream in) throws IOException
		{
		BufferedReader textIn = new BufferedReader(new InputStreamReader(in, "UTF-8"));
		
		models = new ArrayList<LIDModel>();
		
		while (true)
			{
			try
				{
				LIDModel model = new LIDModel(textIn);
				models.add(model);
				}
			catch (IOException e)
				{
				// This code is terribly ugly; the LIDModel constructor should really be replaced by a factory method
				break;
				}
			}
		textIn.close();
		}
	
	public Identification identify(HashMap<String,int[]> unigrams)
	{
		Identification ident = new Identification();
		
		for (LIDModel model : models)
			ident.scores.put(model, new double[] { model.score(unigrams) });
		
		return ident;
	}
	
	public class Identification
		{
		public HashMap<LIDModel,double[]> scores;
		
		Identification()
			{
			scores = new HashMap<LIDModel,double[]>();
			}
		
		public String findBest()
			{
			LIDModel best = null;
			for (LIDModel model : scores.keySet())
				{
				if (best == null || scores.get(model)[0] > scores.get(best)[0])
					best = model;
				}
			
			if (best != null)
				return best.languageName;
			
			return "unknown";
			}
		}
	
	public class LIDModel
		{
		/**
		 * Note: Each String has only one character, but it's a String
		 * because the memory overhead isn't much different than Character
		 * and Character would require an additional conversion.
		 */
		private HashSet<String> chars;
		private HashSet<String> discriminativeChars;
		private HashSet<String> charPairs;
		private HashSet<String> words;
		private HashSet<String> discriminativeWords;
		
		public final String languageName;
		public final String languageCode2;
		
		LIDModel(BufferedReader in) throws IOException
			{
			languageCode2 = in.readLine();
			languageName = in.readLine();

			words = new HashSet<String>();
			String[] tokens = in.readLine().split(" ");
			for (String token : tokens)
				words.add(token);
			
			chars = new HashSet<String>();
			tokens = in.readLine().split(" ");
			for (String token : tokens)
				chars.add(token);
			
			charPairs = new HashSet<String>();
			tokens = in.readLine().split("\\|");
			for (String token : tokens)
				charPairs.add(token);
			
			discriminativeWords = new HashSet<String>();
			tokens = in.readLine().split(" ");
			for (String token : tokens)
				discriminativeWords.add(token);
			
			discriminativeChars = new HashSet<String>();
			tokens = in.readLine().split(" ");
			for (String token : tokens)
				discriminativeChars.add(token);
			
			// skip the discriminative charpairs
			in.readLine();
			}
		
		/**
		 * It scores a unigram-based representation of the input
		 * @param unigrams word unigram model
		 * @return a score between zero and one
		 */
		public double score(HashMap<String,int[]> unigrams)
			{
			int wordMatch = 0;
			int discriminativeWordMatch = 0;
			int wordTotal = 0;
			
			int charMatch = 0;
			int discriminativeCharMatch = 0;
			int charTotal = 0;
			
			int charPairMatch = 0;
			int charPairTotal = 0;
			
			for (String word : unigrams.keySet())
				{
				int count = unigrams.get(word)[0];
				if (words.contains(word))
					wordMatch += count;
				if (discriminativeWords.contains(word))
					discriminativeWordMatch += count;
				wordTotal += count;
				
				char previousChar = ' ';
				charTotal += word.length() * count;
				for (int i = 0; i < word.length(); i++)
					{
					char c = word.charAt(i);
					String charString = String.valueOf(c);
					
					// TODO:  It's terrible slow to create and destroy objects just to do a lookup like this.
					if (chars.contains(charString))
						charMatch += count;
					
					if (discriminativeChars.contains(charString))
						discriminativeCharMatch += count;
					
					// TODO:  Oh this is terrible...
					String pair = String.valueOf(previousChar) + charString;
					
					if (charPairs.contains(pair))
						charPairMatch += count;
					}
				}
			
			// prevent divide-by-zero (if these are zero, the numerators are also zero, so the value doesn't matter so long as it's positive nonzero)
			if (wordTotal == 0)
				wordTotal = 1;

			if (charTotal == 0)
				charTotal = 1;
			
			if (charPairTotal == 0)
				charPairTotal = 1;
			
			return (wordMatch / (double)wordTotal + discriminativeWordMatch / (double)wordTotal + charMatch / (double)charTotal + discriminativeCharMatch / (double)charTotal + charPairMatch / (double)charPairTotal) / 5.0;
			}
		}
	}
