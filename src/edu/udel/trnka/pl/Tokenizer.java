package edu.udel.trnka.pl;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Tokenizer
	{
	public static final Pattern wordPattern = Pattern.compile("\\s+");
	public static final Pattern frontMatter = Pattern.compile("([^\\w@]+)([\\w@].*)");
	public static final Pattern backMatter = Pattern.compile("(.*\\w)(\\W+)");
	public static final Pattern endsInAbbreviation = Pattern.compile(".*(Mr|Mrs|Dr|Jr|Ms|Prof|Sr|dept|Univ|Inc|Ltd|Co|Corp|Mt|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Oct|Nov|Dec|Sept|vs|etc|no)\\.");
	public static final Pattern sentencePattern = Pattern.compile("(?<=[\\.?!][^\\w\\s]?)\\s+(?![a-z])");
	public static final Pattern nonwordPattern = Pattern.compile("[^\\w']|<s>|</s>");
	
	public static boolean isNonword(String word)
		{
		return nonwordPattern.matcher(word).find();
		}
	
	public static ArrayList<String> tokenize(String in)
		{
		ArrayList<String> tokens = new ArrayList<String>();
		
		String[] split = wordPattern.split(in);
		for (String token : split)
			{
			if (token.length() == 0)
				continue;
			
			Matcher m = frontMatter.matcher(token);
			if (m.matches())
				{
				// explode group 1
				String front = m.group(1);
				for (int i = 0; i < front.length(); i++)
					tokens.add(String.valueOf(front.charAt(i)));
				
				// continue processing group 2
				token = m.group(2);
				}

			m = backMatter.matcher(token);
			if (m.matches())
				{
				// save group 1
				tokens.add(m.group(1));
				
				// explode group 2
				String back = m.group(2);
				for (int i = 0; i < back.length(); i++)
					tokens.add(String.valueOf(back.charAt(i)));

				}
			else 
				{
				tokens.add(token);
				}
			}
		return tokens;
		}

	}
