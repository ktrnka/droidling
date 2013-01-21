package com.github.ktrnka.droidling;

import static com.github.ktrnka.droidling.Tokenizer.isNonword;
import static com.github.ktrnka.droidling.Tokenizer.messageEnd;
import static com.github.ktrnka.droidling.Tokenizer.messageStart;
import static com.github.ktrnka.droidling.Tokenizer.nospacePunctPattern;
import static com.github.ktrnka.droidling.Tokenizer.tokenize;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.regex.Matcher;

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
	public HashMap<String, int[]> getUnigrams() {
		return unigrams;
	}

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
	
	/**
	 * Train from the specified text message.
	 * 
	 * TODO:  There are OutOfMemory crash bugs that can be triggered from this in put calls to HashMap when it doubles in size
	 * @param message
	 */
	public void train(String message)
		{
		messages++;
		chars += message.length();
		
		message = message.toLowerCase();
		
		ArrayList<String> tokens = new ArrayList<String>();
		tokens.add(messageStart);
		tokens.addAll(tokenize(message));
		tokens.add(messageEnd);
		
		// update the ngrams!
		String previous = null, ppWord = null;
		for (String token : tokens)
			{
			// TODO: change this to truecasing
			//token = token.toLowerCase();
			
			// unigrams
			if (unigrams.containsKey(token))
				unigrams.get(token)[0]++;
			else
				unigrams.put(token, new int[] { 1 });	// OutOfMemory error here in the put call at HashMap.doubleCapacity
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
						trigrams.put(ppWord, new HashMap<String,HashMap<String, int[]>>());	// OutOfMemory error here in the put call at HashMap.doubleCapacity
					
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
	public String generateBestMessage(boolean useTrigrams)
		{
		ArrayList<String> tokens = new ArrayList<String>();

		String ppWord = null, prev = messageStart;
		while (tokens.size() < 40)
			{
			// load the best trigram (or bigram if none available)
			HashMap<String, int[]> distribution;
			if (ppWord == null || !useTrigrams)
				distribution = bigrams.get(prev);
			else
				{
				HashMap<String,HashMap<String, int[]>> bigramDist = trigrams.get(ppWord);
				distribution = bigramDist.get(prev);
				}
			
			String bestWord = null;
			int bestFreq = 0;
			for (String word : distribution.keySet())
				if (bestFreq == 0 || bestFreq < distribution.get(word)[0])
					{
					bestWord = word;
					bestFreq = distribution.get(word)[0];
					}
			
			if (bestWord.equals(messageEnd))
				break;

			tokens.add(bestWord);
			
			// advance
			ppWord = prev;
			prev = bestWord;
			}
		
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < tokens.size(); i++)
			if (i == 0)
				b.append(tokens.get(i));
			else
				{
				Matcher m = nospacePunctPattern.matcher(tokens.get(i));
				if (!m.matches())
					b.append(" ");

				b.append(tokens.get(i));
				}
		return b.toString();
		}
	
	public String generateRandomMessage(boolean useTrigrams)
		{
		ArrayList<String> tokens = new ArrayList<String>();

		String ppWord = null, prev = messageStart;
		while (tokens.size() < 40)
			{
			// load the best trigram (or bigram if none available)
			HashMap<String, int[]> distribution;
			int total;
			if (ppWord == null || !useTrigrams)
				{
				distribution = bigrams.get(prev);
				total = unigrams.get(prev)[0];
				}
			else
				{
				HashMap<String,HashMap<String, int[]>> bigramDist = trigrams.get(ppWord);
				distribution = bigramDist.get(prev);
				
				total = bigrams.get(ppWord).get(prev)[0];
				}
			
			// generate a rand, scale to total
			int target = (int)(total * Math.random());
			
			int seenFrequency = 0;
			String bestWord = null;
			for (String word : distribution.keySet())
				{
				seenFrequency += distribution.get(word)[0];
				if (seenFrequency > target)
					{
					bestWord = word;
					break;
					}
				}
			
			if (bestWord.equals(messageEnd))
				break;

			tokens.add(bestWord);
			
			// advance
			ppWord = prev;
			prev = bestWord;
			}
		
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < tokens.size(); i++)
			if (i == 0)
				b.append(tokens.get(i));
			else
				{
				Matcher m = nospacePunctPattern.matcher(tokens.get(i));
				if (!m.matches())
					b.append(" ");

				b.append(tokens.get(i));
				}
		return b.toString();
		}
	
	public ArrayList<String> generateRandomMessageTokens(boolean useTrigrams)
		{
		ArrayList<String> tokens = new ArrayList<String>();

		String ppWord = null, prev = messageStart;
		while (tokens.size() < 40)
			{
			// load the best trigram (or bigram if none available)
			HashMap<String, int[]> distribution;
			int total;
			if (ppWord == null || !useTrigrams)
				{
				distribution = bigrams.get(prev);
				total = unigrams.get(prev)[0];
				}
			else
				{
				HashMap<String,HashMap<String, int[]>> bigramDist = trigrams.get(ppWord);
				distribution = bigramDist.get(prev);
				
				total = bigrams.get(ppWord).get(prev)[0];
				}
			
			// generate a rand, scale to total
			int target = (int)(total * Math.random());
			
			int seenFrequency = 0;
			String bestWord = null;
			for (String word : distribution.keySet())
				{
				seenFrequency += distribution.get(word)[0];
				if (seenFrequency > target)
					{
					bestWord = word;
					break;
					}
				}
			
			if (bestWord.equals(messageEnd))
				break;

			tokens.add(bestWord);
			
			// advance
			ppWord = prev;
			prev = bestWord;
			}
		
		return tokens;
		}

	/**
	 * Generate N random messages according to the trigram/bigram model.
	 * @param N
	 * @return
	 */
	public ArrayList<String> generateRandomMessages(int N)
		{
		ArrayList<String> messages = new ArrayList<String>();
		
		for (int i = 0; i < N; i++)
			{
			messages.add(generateRandomMessage(false));
			}
		return messages;
		}
	
	/**
	 * Generate a set of messages of size M, then select the N most probable.
	 * @param N The number of messages you want
	 * @param M The number to pick N from (if you pick too many, you'll likely get similar messages, but too few and they'll be too random)
	 * @return The most probable subset of the messages.
	 */
	public ArrayList<String> generateSemiRandom(int N, int M)
		{
		ArrayList<ArrayList<String>> messages = new ArrayList<ArrayList<String>>();
		
		// generate M
		for (int i = 0; i < M; i++)
			{
			messages.add(generateRandomMessageTokens(false));
			}
		
		// score them all
		
		// sort
		
		// pick the top
		assert(false);
		return null;
		}
	}
