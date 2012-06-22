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
	private HashMap<String,double[]> probs;
	private double total;

	public WordDistribution()
		{
		probs = new HashMap<String,double[]>();
		total = 0;
		}
	
	public WordDistribution(InputStream fin) throws IOException
		{
		this();
		
		BufferedReader in = new BufferedReader(new InputStreamReader(fin, Charset.forName("UTF-8")));
		String line = in.readLine();
		total = Double.parseDouble(line.trim());
		while ( (line = in.readLine()) != null)
			{
			String[] parts = line.trim().split("\t");
			probs.put(parts[0], new double[] { Double.parseDouble(parts[1]) });
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
				if (probs.containsKey(token))
					probs.get(token)[0]++;
				else
					probs.put(token, new double[] { 1 });
				}
			}
		
		// compute total
		for (double[] value : probs.values())
			total += value[0];
		
		// normalize
		for (double[] value : probs.values())
			value[0] /= total;
		
		
		in.close();
		}
	
	public void save(String filename) throws IOException
		{
		ArrayList<String> words = new ArrayList<String>(probs.keySet());
		Collections.sort(words, new Comparator<String>()
				{
				public int compare(String a, String b)
					{
					return (int)(total * (probs.get(b)[0] - probs.get(a)[0]));
					}
				});
		
		PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(filename), Charset.forName("UTF-8")));
		out.println(total);
		for (String word : words)
			{
			out.println(word + "\t" + probs.get(word)[0]);
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

	public double getSmoothProb(String word)
		{
		if (probs.containsKey(word))
			return probs.get(word)[0];
		else
			return 0.5 / total;
		}

	/**
	 * Train a unigram model from text.
	 * Sadly, I can't run this from the Android project or rather, I don't know how to.
	 * @param args
	 */
	public static void main(String[] args) throws Exception
		{
		WordDistribution dist = new WordDistribution();
		dist.train("C:/Users/keith.trnka/Documents/corpora/enronmobile/enronmobile/mobile_orig_simple.txt");
		dist.save("C:/Users/keith.trnka/workspace/PersonalLinguistics/assets/unigrams.utf8.txt");
		}

	}
