package com.github.ktrnka.droidling.helpers;

public class Util
	{
	/**
	 * Strip one character from the end of the string if present.
	 * @param builder
	 * @param c
	 */
	public static void strip(StringBuilder builder, char c)
		{
		if (builder.length() == 0)
			return;
		
		if (builder.charAt(builder.length() - 1) == c)
			builder.setLength(builder.length() - 1);
		}
	}
