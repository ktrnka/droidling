package edu.udel.trnka.pl;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

public class DateDistribution
	{
	private ArrayList<Date> dates;
	
	public DateDistribution()
		{
		dates = new ArrayList<Date>();
		}
	
	public void add(Date date)
		{
		dates.add(date);
		}
	
	public Date findMin()
		{
		Date min = null;
		for (Date date : dates)
			if (min == null || min.compareTo(date) > 0)
				min = date;
		
		return min;
		}

	public Date findMax()
		{
		Date max = null;
		for (Date date : dates)
			if (max == null || max.compareTo(date) < 0)
				max = date;
		
		return max;
		}
	
	/**
	 * This is working under the assumption of approximately 30-day months.
	 * @return The average number of texts per 30 days between your first and last text.
	 */
	public double computeTextsPerMonth()
		{
		Date min = findMin();
		Date max = findMax();
		
		long maxTime = max.getTime();
		long minTime = min.getTime();
		
		double monthMs = 1000L * 60 * 60 * 24 * 30;
		
		double months = (maxTime - minTime) / monthMs;
		return dates.size() / months;
		}
	
	public int[] computeDayOfWeekHistogram()
		{
		int[] days = new int[7];
		for (int i = 0; i < days.length; i++)
			days[i] = 0;
		
		Calendar calendar = Calendar.getInstance();
		
		for (Date date : dates)
			{
			calendar.setTime(date);
			days[calendar.get(Calendar.DAY_OF_WEEK) - 1]++;
			}
		
		return days;
		}
	}
