package com.github.ktrnka.droidling;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
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
	
	/**
	 * Languages at or below this score will be deemed "unknown".
	 */
	public static final double UNKNOWN_LANGUAGE_THRESHOLD = 0.1;
	
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
	
	public ArrayList<String> getSupportedLanguages()
		{
		ArrayList<String> languages = new ArrayList<String>();
		
		for (LIDModel model : models)
			{
			languages.add(model.languageName);
			}
		
		Collections.sort(languages);
		
		return languages;
		}
	
	public Identification identify(HashMap<String,int[]> unigrams)
		{
		Identification ident = new Identification();
		ident.setUnigrams(unigrams);
		
		for (LIDModel model : models)
			ident.scores.put(model, new double[] { model.score(unigrams, null, null) });
		
		return ident;
		}
	
	/*
	 * compute a unique int version of this char pair
	 */
	public static final int hash(char a, char b)
		{
		return a * 0x10000 + b;
		}
	
	public class Identification
		{
		public HashMap<LIDModel,double[]> scores;
		
		/**
		 * The unigram model that this identification is for.
		 */
		private HashMap<String,int[]> unigrams;
		
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
			
			if (best != null && scores.get(best)[0] > UNKNOWN_LANGUAGE_THRESHOLD)
				return best.languageName;
			
			return "unknown";
			}

		/**
		 * List the top 3 most likely languages in a string.
		 * 
		 * TODO:  This needs work to support localization.
		 * @return
		 */
		public String describeTopN()
			{
			final ArrayList<LIDModel> modelList = new ArrayList<LIDModel>(scores.keySet());
			
			Collections.sort(modelList, new Comparator<LIDModel>()
				{
				public int compare(LIDModel a, LIDModel b)
					{
					return Double.compare(scores.get(b)[0], scores.get(a)[0]);
					}
				}
				);
			
			Formatter f = new Formatter();
			f.format("Probably %s (score %.2f)\n", modelList.get(0).languageName, scores.get(modelList.get(0))[0]);
			f.format("2nd choice %s (score %.2f)\n", modelList.get(1).languageName, scores.get(modelList.get(1))[0]);
			f.format("3rd choice %s (score %.2f)\n", modelList.get(2).languageName, scores.get(modelList.get(2))[0]);
			return f.toString();
			}
		
		/**
		 * Produce a textual explanation of this particular identification.  It compares
		 * the most likely language to the second most likely language.
		 * 
		 * TODO: This function needs localization support.
		 * TODO: This code is huge and needs some simplification/refactoring.
		 * @return textual description
		 */
		public String explain()
			{
			// sort models to find top 2
			final ArrayList<LIDModel> modelList = new ArrayList<LIDModel>(scores.keySet());
			
			Collections.sort(modelList, new Comparator<LIDModel>()
				{
				public int compare(LIDModel a, LIDModel b)
					{
					return Double.compare(scores.get(b)[0], scores.get(a)[0]);
					}
				}
				);
			
			// re-score them with instrumentation
			final HashMap<String,int[]> topWordFeatures = new HashMap<String,int[]>();
			final HashMap<String,int[]> topCharFeatures = new HashMap<String,int[]>();
			
			modelList.get(0).score(unigrams, topWordFeatures, topCharFeatures);

			HashMap<String,int[]> secondWordFeatures = new HashMap<String,int[]>();
			HashMap<String,int[]> secondCharFeatures = new HashMap<String,int[]>();
			
			modelList.get(1).score(unigrams, secondWordFeatures, secondCharFeatures);
			
			// convert the features into diff-features
			for (String word : topWordFeatures.keySet())
				{
				if (secondWordFeatures.containsKey(word))
					{
					topWordFeatures.get(word)[0] -= secondWordFeatures.get(word)[0];
					}
				}
			for (String chars : topCharFeatures.keySet())
				{
				if (secondCharFeatures.containsKey(chars))
					{
					topCharFeatures.get(chars)[0] -= secondCharFeatures.get(chars)[0];
					}
				}
			
			// make a list and sort it!
			final ArrayList<String> bestWords = new ArrayList<String>(topWordFeatures.keySet());
			Collections.sort(bestWords, new Comparator<String>()
				{
				public int compare(String a, String b)
					{
					return topWordFeatures.get(b)[0] - topWordFeatures.get(a)[0];
					}
				});
			
			StringBuilder wordBuilder = new StringBuilder();
			
			// if there are some good words
			if (bestWords.size() > 0 && topWordFeatures.get(bestWords.get(0))[0] > 0)
				{
				for (int i = 0; i < bestWords.size() && i < 3; i++)
					{
					if (topWordFeatures.get(bestWords.get(i))[0] <= 0)
						break;
					
					if (i > 0)
						wordBuilder.append(", ");
					
					wordBuilder.append(bestWords.get(i));
					}
				}
			
			final ArrayList<String> bestChars = new ArrayList<String>(topCharFeatures.keySet());
			Collections.sort(bestChars, new Comparator<String>()
				{
				public int compare(String a, String b)
					{
					return topCharFeatures.get(b)[0] - topCharFeatures.get(a)[0];
					}
				});

			StringBuilder singleCharBuilder = new StringBuilder();
			StringBuilder charPairBuilder = new StringBuilder();
			StringBuilder startsWithBuilder = new StringBuilder();
			StringBuilder endsWithBuilder = new StringBuilder();

			if (bestChars.size() > 0 && topCharFeatures.get(bestChars.get(0))[0] > 0)
				{
				int found = 0;
				for (int i = 0; i < bestWords.size() && found < 3; i++)
					{
					// filter to single-char strings
					if (bestChars.get(i).length() != 1)
						continue;

					if (topCharFeatures.get(bestChars.get(i))[0] <= 0)
						break;
					
					if (found > 0)
						singleCharBuilder.append(", ");
					
					singleCharBuilder.append(bestChars.get(i));
					found++;
					}

				found = 0;
				for (int i = 0; i < bestChars.size() && found < 3; i++)
					{
					String pair = bestChars.get(i);
					// filter to the two-char without spaces
					if (pair.length() != 2 || pair.startsWith(" ") || pair.endsWith(" "))
						continue;

					if (topCharFeatures.get(pair)[0] <= 0)
						break;
					
					if (found > 0)
						charPairBuilder.append(", ");
					
					charPairBuilder.append(pair);
					found++;
					}

				found = 0;
				for (int i = 0; i < bestChars.size() && found < 3; i++)
					{
					String pair = bestChars.get(i);

					// filter to "starts with"
					if (pair.length() != 2 || !pair.startsWith(" "))
						continue;

					if (topCharFeatures.get(pair)[0] <= 0)
						break;
					
					if (found > 0)
						startsWithBuilder.append(", ");
					
					startsWithBuilder.append(pair.charAt(1));
					found++;
					}

				found = 0;
				for (int i = 0; i < bestChars.size() && found < 3; i++)
					{
					String pair = bestChars.get(i);

					// filter to "starts with"
					if (pair.length() != 2 || !pair.endsWith(" "))
						continue;

					if (topCharFeatures.get(pair)[0] <= 0)
						break;
					
					if (found > 0)
						endsWithBuilder.append(", ");
					
					endsWithBuilder.append(pair.charAt(0));
					found++;
					}

				}
			
			// compose the individual strings
			StringBuilder overallBuilder = new StringBuilder();
			
			if (wordBuilder.length() > 0)
				{
				overallBuilder.append("Common words: ");
				overallBuilder.append(wordBuilder);
				overallBuilder.append("\n");
				}
			
			if (singleCharBuilder.length() > 0)
				{
				overallBuilder.append("Common letters: ");
				overallBuilder.append(singleCharBuilder);
				overallBuilder.append("\n");
				}
			
			if (charPairBuilder.length() > 0)
				{
				overallBuilder.append("Common letter pairs: ");
				overallBuilder.append(charPairBuilder);
				overallBuilder.append("\n");
				}
			
			if (startsWithBuilder.length() > 0)
				{
				overallBuilder.append("Words starting with: ");
				overallBuilder.append(startsWithBuilder);
				overallBuilder.append("\n");
				}
			
			if (endsWithBuilder.length() > 0)
				{
				overallBuilder.append("Words ending with: ");
				overallBuilder.append(endsWithBuilder);
				overallBuilder.append("\n");
				}
			
			return overallBuilder.toString();
			}

		public HashMap<String,int[]> getUnigrams()
			{
			return unigrams;
			}

		public void setUnigrams(HashMap<String,int[]> unigrams)
			{
			this.unigrams = unigrams;
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
		private char[] charArray;
		
		private HashSet<String> discriminativeChars;
		private char[] discriminativeCharArray;
		
		private HashSet<String> charPairs;
		private int[] charPairCodes;
		
		private HashSet<String> words;
		private HashSet<String> discriminativeWords;
		
		public final String languageName;
		public final String languageCode2;
		
		LIDModel(BufferedReader in) throws IOException
			{
			languageCode2 = in.readLine();
			if (languageCode2 == null)
				throw new EOFException();
			
			languageName = in.readLine();
			if (languageName == null)
				throw new EOFException();

			words = new HashSet<String>();
			String[] tokens = in.readLine().split(" ");
			for (String token : tokens)
				words.add(token);
			
			chars = new HashSet<String>();
			tokens = in.readLine().split(" ");
			for (String token : tokens)
				chars.add(token);

			// the sorted array form
			charArray = new char[chars.size()];
			for (int i = 0; i < tokens.length; i++)
				charArray[i] = tokens[i].charAt(0);
			Arrays.sort(charArray);
			
			charPairs = new HashSet<String>();
			tokens = in.readLine().split("\\|");
			for (String token : tokens)
				if (token.length() == 2)
					charPairs.add(token);
			
			charPairCodes = new int[charPairs.size()];
			for (int i = 0, j = 0; j < tokens.length; j++)
				if (tokens[j].length() == 2)
					charPairCodes[i++] = hash(tokens[j].charAt(0), tokens[j].charAt(1));
			Arrays.sort(charPairCodes);
				
				
			discriminativeWords = new HashSet<String>();
			tokens = in.readLine().split(" ");
			for (String token : tokens)
				discriminativeWords.add(token);
			
			discriminativeChars = new HashSet<String>();
			tokens = in.readLine().split(" ");
			for (String token : tokens)
				if (token.length() > 0)
					discriminativeChars.add(token);
			
			discriminativeCharArray = new char[discriminativeChars.size()];
			int i = 0;
			for (int j = 0; j < tokens.length; j++)
				if (tokens[j].length() > 0)
					discriminativeCharArray[i++] = tokens[j].charAt(0);
			Arrays.sort(discriminativeCharArray);
			
			// skip the discriminative charpairs; they don't seem to help
			in.readLine();
			}
		
		/**
		 * It scores a unigram-based representation of the input
		 * @param unigrams word unigram model
		 * @param wordFeatureValues place to store word feature values (optional)
		 * @param charFeatureValues place to store character features values (optional) 
		 * @return a score between zero and one
		 */
		public double score(HashMap<String,int[]> unigrams, HashMap<String,int[]> wordFeatureValues, HashMap<String,int[]> charFeatureValues)
			{
			int wordMatch = 0;
			int discriminativeWordMatch = 0;
			int wordTotal = 0;
			
			int charMatch = 0;
			int discriminativeCharMatch = 0;
			int charTotal = 0;
			
			int charPairMatch = 0;
			int charPairTotal = 0;
			
			StringBuilder scratch = new StringBuilder();
			
			for (String word : unigrams.keySet())
				{
				String lowercaseWord = word.toLowerCase();
				
				int count = unigrams.get(word)[0];
				if (words.contains(lowercaseWord))
					{
					wordMatch += count;
					
					if (wordFeatureValues != null)
						{
						if (!wordFeatureValues.containsKey(lowercaseWord))
							{
							wordFeatureValues.put(lowercaseWord, new int[] { 1 });
							}
						else
							{
							wordFeatureValues.get(lowercaseWord)[0]++;
							}
						}
					}
				if (discriminativeWords.contains(lowercaseWord))
					{
					discriminativeWordMatch += count;
					
					if (wordFeatureValues != null)
						{
						if (!wordFeatureValues.containsKey(lowercaseWord))
							{
							wordFeatureValues.put(lowercaseWord, new int[] { 1 });
							}
						else
							{
							wordFeatureValues.get(lowercaseWord)[0]++;
							}
						}
					}
				wordTotal += count;
				
				char previousChar = ' ';
				charTotal += lowercaseWord.length() * count;
				for (int i = 0; i < lowercaseWord.length(); i++)
					{
					char c = lowercaseWord.charAt(i);

					// we only care about normal letters; not all the LID models have numbers or punctuation (which can skew it)
					if (Character.isLetter(c))
						{
						// this var is optionally set if we're tracing the feature values
						String charString = null;
						if (charFeatureValues != null)
							charString = String.valueOf(c);
						
						if (Arrays.binarySearch(charArray, c) >= 0)
							{
							charMatch += count;
							
							if (charFeatureValues != null)
								{
								if (!charFeatureValues.containsKey(charString))
									{
									charFeatureValues.put(charString, new int[] { 1 });
									}
								else
									{
									charFeatureValues.get(charString)[0]++;
									}
								}
							}
						if (Arrays.binarySearch(discriminativeCharArray, c) >= 0)
							{
							discriminativeCharMatch += count;
							
							if (charFeatureValues != null)
								{
								if (!charFeatureValues.containsKey(charString))
									{
									charFeatureValues.put(charString, new int[] { 1 });
									}
								else
									{
									charFeatureValues.get(charString)[0]++;
									}
								}
							}
						charTotal += count;
						
						if (Arrays.binarySearch(charPairCodes, hash(previousChar, c)) >= 0)
							{
							charPairMatch += count;
							
							if (charFeatureValues != null)
								{
								scratch.setLength(0);
								scratch.append(previousChar);
								scratch.append(c);
								String pair = scratch.toString();

								if (!charFeatureValues.containsKey(pair))
									{
									charFeatureValues.put(pair, new int[] { 1 });
									}
								else
									{
									charFeatureValues.get(pair)[0]++;
									}
								}
							}
						
						// TODO:  Oh this is terrible...
						/*
						scratch.setLength(0);
						scratch.append(previousChar);
						scratch.append(c);
						String pair = scratch.toString(); //String.valueOf(previousChar) + String.valueOf(c);
						
						if (charPairs.contains(pair))
							{
							charPairMatch += count;
							
							if (charFeatureValues != null)
								{
								if (!charFeatureValues.containsKey(pair))
									{
									charFeatureValues.put(pair, new int[] { 1 });
									}
								else
									{
									charFeatureValues.get(pair)[0]++;
									}
								}
							}
						*/
						charPairTotal += count;
						}
					
					previousChar = c;
					}
				
				if (Arrays.binarySearch(charPairCodes, hash(previousChar, ' ')) >= 0)
					{
					charPairMatch += count;
					
					if (charFeatureValues != null)
						{
						scratch.setLength(0);
						scratch.append(previousChar);
						scratch.append(' ');
						String pair = scratch.toString();
	
						if (!charFeatureValues.containsKey(pair))
							{
							charFeatureValues.put(pair, new int[] { 1 });
							}
						else
							{
							charFeatureValues.get(pair)[0]++;
							}
						}
					}

				
				// the end of the word
				// TODO:  Oh this is terrible...
				/*
				String pair = String.valueOf(previousChar) + ' ';
				
				if (charPairs.contains(pair))
					{
					charPairMatch += count;
					
					if (charFeatureValues != null)
						{
						if (!charFeatureValues.containsKey(pair))
							{
							charFeatureValues.put(pair, new int[] { 1 });
							}
						else
							{
							charFeatureValues.get(pair)[0]++;
							}
						}
					}
				*/
				charPairTotal += count;
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
