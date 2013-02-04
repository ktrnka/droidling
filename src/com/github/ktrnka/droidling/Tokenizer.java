package com.github.ktrnka.droidling;

import java.util.ArrayList;
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
	
	/**
	 * Check if a word isn't appropriate to list in the various displays.  Examples
	 * include numeric tokens and such.
	 * 
	 * TODO: This isn't appropriate for many languages that have hyphenated words.
	 * It's also inappropriate to filter out a trigram like "3rd and Seneca"
	 */
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

	/**
	 * Tokenize a message into words.
	 * TODO: This function is responsible for a huge percentage of runtime.
	 * @param in the text message (or longer)
	 * @return list of tokens
	 */
	public static ArrayList<String> tokenize(String in)
		{
		ArrayList<String> tokens = new ArrayList<String>();
		
		String[] split = wordPattern.split(in);
		for (String token : split)
			{
			if (token.length() == 0)
				continue;
			
			// find first word char (add all the tokens along the way)
			int firstWordCharIndex;
			for (firstWordCharIndex = 0; firstWordCharIndex < token.length() && !isWordChar(token.charAt(firstWordCharIndex)); firstWordCharIndex++)
				{
				tokens.add(String.valueOf(token.charAt(firstWordCharIndex)));
				}
			
			// find last word char
			int lastWordCharIndex;
			for (lastWordCharIndex = token.length() - 1; lastWordCharIndex >= firstWordCharIndex && !isWordChar(token.charAt(lastWordCharIndex)); lastWordCharIndex--)
				{
				}
			
			// avoid object creation via substr if possible
			if (firstWordCharIndex > 0 || lastWordCharIndex < token.length() - 1)
				{
				if (firstWordCharIndex < token.length())
					{
					tokens.add(token.substring(firstWordCharIndex, lastWordCharIndex + 1));
					
					for (int i = lastWordCharIndex + 1; i < token.length(); i++)
						tokens.add(String.valueOf(token.charAt(i)));
					}
				}
			else
				{
				tokens.add(token);
				}
			}
		return tokens;
		}
	
	public static final boolean isWordChar(char c)
		{
		return Character.isLetterOrDigit(c) || c == '@';
		}

	}
