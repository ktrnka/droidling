
package com.github.ktrnka.droidling;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

/**
 * A collection of Dates, which we can use to compute various stats such as date
 * range, day-of-week histogram, time-of-day histogram, etc.
 * 
 * @author keith.trnka
 */
public class DateDistribution {
    private ArrayList<Date> dates;

    public DateDistribution() {
        dates = new ArrayList<Date>();
    }

    public void add(Date date) {
        dates.add(date);
    }

    public Date findMin() {
        Date min = null;
        for (Date date : dates)
            if (min == null || min.compareTo(date) > 0)
                min = date;

        return min;
    }

    public Date findMax() {
        Date max = null;
        for (Date date : dates)
            if (max == null || max.compareTo(date) < 0)
                max = date;

        return max;
    }

    /**
     * This is working under the assumption of approximately 30-day months.
     * 
     * @return The average number of texts per 30 days between your first and
     *         last text. If you have only one text or less than 30 days' worth,
     *         it'll return the total so far.
     */
    public double computeTextsPerMonth() {
        Date min = findMin();
        Date max = findMax();

        long maxTime = max.getTime();
        long minTime = min.getTime();

        double monthMs = 1000L * 60 * 60 * 24 * 30;

        double months = (maxTime - minTime) / monthMs;

        // safety from crazy numbers
        if (months < 1)
            return dates.size();

        return dates.size() / months;
    }

    /**
     * Builds a day-of-week histogram, with Sunday as index 0 and Saturday as
     * index 6.
     */
    public int[] computeDayOfWeekHistogram() {
        int[] days = new int[7];
        for (int i = 0; i < days.length; i++)
            days[i] = 0;

        Calendar calendar = Calendar.getInstance();

        for (Date date : dates) {
            calendar.setTime(date);
            days[calendar.get(Calendar.DAY_OF_WEEK) - 1]++;
        }

        return days;
    }

    /**
     * Compute a histogram mapping the hour to the number of texts in that hour.
     * Uses 24-hour format, starting from 1 (return array is size 25). TODO:
     * There's a bug in this function cause Java returns a 0-23 value and I
     * didn't realize it.
     */
    public int[] computeHourHistogram() {
        int[] hours = new int[25];
        for (int i = 0; i < hours.length; i++)
            hours[i] = 0;

        Calendar calendar = Calendar.getInstance();

        for (Date date : dates) {
            calendar.setTime(date);
            hours[calendar.get(Calendar.HOUR_OF_DAY)]++;
        }

        return hours;
    }
}
