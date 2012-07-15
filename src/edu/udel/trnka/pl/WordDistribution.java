package edu.udel.trnka.pl;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.io.*;
import java.nio.charset.Charset;

import static edu.udel.trnka.pl.Tokenizer.tokenize;

public class WordDistribution
	{
	private int total;
	private HashMap<String,int[]> counts;

	public WordDistribution()
		{
		counts = new HashMap<String,int[]>();
		total = 0;
		}
	
	public WordDistribution(InputStream fin) throws IOException
		{
		this();
		
		BufferedReader in = new BufferedReader(new InputStreamReader(fin, Charset.forName("UTF-8")), 8192);
		String line = in.readLine();
		total = Integer.parseInt(line);
		while ( (line = in.readLine()) != null)
			{
			int tab = line.indexOf('\t');
			if (tab != -1)
				{
				String word = line.substring(0, tab);
				String numeric = line.substring(tab + 1);
				counts.put(word, new int[] { Integer.parseInt(numeric) });
				}
			//String[] parts = line.trim().split("\t");
			//counts.put(parts[0], new int[] { Integer.parseInt(parts[1]) });
			}
		in.close();
		}

	public void train(String filename) throws IOException
		{
		BufferedReader in = new BufferedReader(new FileReader(filename));
		
		// compute counts
		String line;
		while ( (line = in.readLine()) != null)
			{
			// hack for Enron texts
			line = line.replaceFirst(".*\t", "");
			
			ArrayList<String> tokens = tokenize(line);
			
			for (String token : tokens)
				{
				token = token.toLowerCase();
				if (counts.containsKey(token))
					counts.get(token)[0]++;
				else
					counts.put(token, new int[] { 1 });
				}
			}
		
		// compute total
		for (int[] value : counts.values())
			total += value[0];
		
		in.close();
		}
	
	/**
	 * Save to the specified file as a UTF-8 list of one word then frequency per line, with a total at the top
	 * @param filename
	 * @throws IOException
	 */
	public void save(String filename) throws IOException
		{
		ArrayList<String> words = new ArrayList<String>(counts.keySet());
		Collections.sort(words, new Comparator<String>()
				{
				public int compare(String a, String b)
					{
					return counts.get(b)[0] - counts.get(a)[0];
					}
				});
		
		PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(filename), Charset.forName("UTF-8")));
		out.println(total);
		for (String word : words)
			{
			out.println(word + "\t" + counts.get(word)[0]);
			}
		out.close();
		}
	
	public double expectedFrequency(String word1, String word2, double localTotal)
		{
		return expectedProb(word1, word2) * localTotal;
		}
	
	public double expectedProb(String word1, String word2)
		{
		return getSmoothProb(word1) * getSmoothProb(word2);
		}
	
	public double expectedFrequency(String word, double localTotal)
		{
		return getSmoothProb(word) * localTotal;
		}
	
	public double expectedFrequency(String w1, String w2, String w3, double localTotal)
		{
		return getSmoothProb(w1) * getSmoothProb(w2) * getSmoothProb(w3) * localTotal;
		}

	public double getSmoothProb(String word)
		{
		if (counts.containsKey(word))
			return counts.get(word)[0] / (double) total;
		else
			return 0.5 / total;
		}

	/**
	 * Train a unigram model from text.
	 * Sadly, I can't run this from the Eclispe Android project or I don't know how to.
	 * @param args
	 */
	public static void main(String[] args) throws Exception
		{
		WordDistribution dist = new WordDistribution();
		dist.train("C:/Users/keith.trnka/Documents/corpora/enronmobile/enronmobile/mobile_orig_simple.txt");
		dist.save("C:/Users/keith.trnka/workspace/PersonalLinguistics/assets/unigrams.utf8.txt");
		}

	}
