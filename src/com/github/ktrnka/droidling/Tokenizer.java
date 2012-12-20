package com.github.ktrnka.droidling;

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
	public static final Pattern nospacePunctPattern = Pattern.compile("[,\\.?!:;]");
	
	public static final String messageStart = "<s>";
	public static final String messageEnd = "</s>";
	
	public static boolean isNonword(String word)
		{
		// This is the regular expression version of the code (and Java regex is slow)
		//return nonwordPattern.matcher(word).find();
		
		for (int i = 0; i < word.length(); i++)
			{
			char c = word.charAt(i);
			if (!Character.isLetter(c) && c != '\'')
				{
				return true;
				}
			}
		
		return false;
		}
	
	/**
	 * Regular expression-based anything in Java is slow as shit so I had to write this.
	 * I haven't tested it thoroughly enough yet, seems to help in a minor way.
	 * @param text
	 * @param delim
	 * @return
	 */
	public static String[] split(String text, char delim)
		{
		// count the delims
		int numTokens = 0;
		
		int i = 0;
		while (true)
			{
			// advance while it's a delim
			while (i < text.length() && text.charAt(i) == delim)
				i++;

			if (i == text.length())
				break;
			
			numTokens++;
			
			while (i < text.length() && text.charAt(i) != delim)
				i++;
			}
		
		// tokenize!
		String[] tokens = new String[numTokens];
		i = 0;
		int currentToken = 0;
		int tokenStart = 0;
		while (true)
			{
			// advance while it's a delim
			while (i < text.length() && text.charAt(i) == delim)
				i++;

			if (i == text.length())
				break;
			
			tokenStart = i;
			
			while (i < text.length() && text.charAt(i) != delim)
				i++;
			
			tokens[currentToken++] = text.substring(tokenStart, i);
			}
		
		// return the thing
		return tokens;
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
