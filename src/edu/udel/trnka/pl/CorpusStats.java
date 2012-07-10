package edu.udel.trnka.pl;

import static edu.udel.trnka.pl.Tokenizer.isNonword;
import static edu.udel.trnka.pl.Tokenizer.tokenize;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

/**
 * Statistics about a homogeneous corpus, such as a single person's sent or received messages.
 * Contains stats like unigrams, bigrams, trigrams, number of messages, message lengths, etc.
 * @author keith.trnka
 *
 */
public class CorpusStats
	{
	private int messages;
	private int filteredWords;
	private int unfilteredWords;
	private int chars;
	private int filteredWordLength;
	
	private HashMap<String,int[]> unigrams;
	private int unigramTotal;
	
	private HashMap<String,HashMap<String, int[]>> bigrams;
	private int bigramTotal;
	
	private HashMap<String,HashMap<String,HashMap<String, int[]>>> trigrams;
	private int trigramTotal;
	
	/**
	 * parameter for absolute discounting
	 */
	public static final double D = 0.3;
	
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
	
	/**
	 * Unigram probability (smoothed)
	 */
	public double getProb(String word)
		{
		if (unigrams.containsKey(word))
			{
			int count = unigrams.get(word)[0];
			return (count - D) / unigramTotal;
			}
		else
			{
			// we discounted unigrams.size() of them, estimating unigrams.size() unknown words
			return D / unigramTotal;
			}
		}

	/**
	 * Bigram joint probability (smoothed)
	 */
	public double getProb(String word1, String word2)
		{
		if (bigrams.containsKey(word1) && bigrams.get(word1).containsKey(word2))
			{
			int count = bigrams.get(word1).get(word2)[0];
			return (count - D) / bigramTotal;
			}
		else
			{
			// we discounted bigrams.size() of them, estimating bigrams.size() unknown words
			return D / bigramTotal;
			}
		}
	
	/**
	 * Unigram probability (smoothed) minus any counts in subtractThis
	 */
	public double getProb(String word, CorpusStats subtractThis)
		{
		if (unigrams.containsKey(word))
			{
			int count = unigrams.get(word)[0];
			
			if (subtractThis.unigrams.containsKey(word))
				count -= subtractThis.unigrams.get(word)[0];
			
			if (count == 0)
				{
				// we discounted unigrams.size() of them, estimating unigrams.size() unknown words
				return D / (unigramTotal - subtractThis.unigramTotal);
				}
			else
				{
				return (count - D) / (unigramTotal - subtractThis.unigramTotal);
				}
			
			}
		else
			{
			// we discounted unigrams.size() of them, estimating unigrams.size() unknown words
			return D / (unigramTotal - subtractThis.unigramTotal);
			}
		}

	/**
	 * Bigram joint probability (smoothed) minus any counts in subtractThis
	 */
	public double getProb(String word1, String word2, CorpusStats subtractThis)
		{
		if (bigrams.containsKey(word1) && bigrams.get(word1).containsKey(word2))
			{
			int count = bigrams.get(word1).get(word2)[0];
			
			if (subtractThis.bigrams.containsKey(word1) && subtractThis.bigrams.get(word1).containsKey(word2))
				count -= subtractThis.bigrams.get(word1).get(word2)[0];
			
			if (count == 0)
				{
				// we discounted unigrams.size() of them, estimating unigrams.size() unknown words
				return D / (bigramTotal - subtractThis.bigramTotal);
				}
			else
				{
				return (count - D) / (bigramTotal - subtractThis.bigramTotal);
				}
			
			}
		else
			{
			// we discounted unigrams.size() of them, estimating unigrams.size() unknown words
			return D / (bigramTotal - subtractThis.bigramTotal);
			}
		}
	
	/**
	 * Compute Jaccard coefficient over the full (unfiltered) vocabulary, which
	 * is size(intersection)/size(union)
	 * @param other
	 * @return
	 */
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
	
	/**
	 * Assuming that the person measured in a and b are related, determine the interesting
	 * terms that are unique to this association.
	 */
	public static ArrayList<String> computeRelationshipTerms(CorpusStats a, CorpusStats aSuperset, CorpusStats b, CorpusStats bSuperset)
		{
		// find unigrams in both, score them
		final HashMap<String,int[]> candidates = new HashMap<String,int[]>();
		for (String word : a.unigrams.keySet())
			if (b.unigrams.containsKey(word))
				if (!isNonword(word))
					candidates.put(word, new int[] { (int)(100 * Math.log(a.getProb(word) / aSuperset.getProb(word, a))) + (int)(100 * Math.log(b.getProb(word) / bSuperset.getProb(word, b))) });
		
		// find bigrams in both, score them
		for (String prev : a.bigrams.keySet())
			if (b.bigrams.containsKey(prev) && !isNonword(prev))
				for (String word : a.bigrams.get(prev).keySet())
					if (b.bigrams.get(prev).containsKey(word) && !isNonword(word))
						candidates.put(prev + " " + word, 
								new int[] { (int)(100 * Math.log(a.getProb(prev, word) / aSuperset.getProb(prev, word, a))) + (int)(100 * Math.log(b.getProb(prev, word) / bSuperset.getProb(prev, word, b))) });

		ArrayList<String> candidateList = new ArrayList<String>(candidates.keySet());
		Collections.sort(candidateList, new Comparator<String>()
			{
			public int compare(String lhs, String rhs)
				{
				return candidates.get(rhs)[0] - candidates.get(lhs)[0];
				}
			});
		
		// basic ngram folding
		for (int i = 0; i < 10 && i < candidateList.size(); i++)
			{
			String[] tokens = candidateList.get(i).split(" ");
			
			if (tokens.length == 2)
				{
				candidates.get(tokens[0])[0] = 0;
				candidates.get(tokens[1])[0] = 0;
				}
			}
		
		// resort (should be fast - mostly in order)
		Collections.sort(candidateList, new Comparator<String>()
			{
			public int compare(String lhs, String rhs)
				{
				return candidates.get(rhs)[0] - candidates.get(lhs)[0];
				}
			});
		

		// TODO: find an efficient way to remove any elements that have zero or negative score

		return candidateList;
		}
	
	/**
	 * Generate the single most probable message according to the trigram model.
	 * If it's not built from much data, it's probably an exact message.
	 * @return
	 */
	public String generateBestMessage()
		{
		return "";
		}

	/**
	 * Generate N random messages according to the trigram/bigram model.
	 * @param N
	 * @return
	 */
	public ArrayList<String> generateRandomMessages(int N)
		{
		return null;
		}
	
	/**
	 * Find the N most probable messages of the input.
	 * @param N
	 * @return The most probable subset of the messages.
	 */
	public ArrayList<String> pickBest(ArrayList<String> messages, int N)
		{
		return messages;
		}
	}
