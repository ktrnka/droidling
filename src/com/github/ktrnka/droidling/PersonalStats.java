
package com.github.ktrnka.droidling;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.util.Log;

/**
 * Wrapper for the output of PersonalActivity to load/save.
 */
public class PersonalStats {
    public static final int PHRASE_SORTED = 0;
    public static final int COUNT_SORTED = 1;
    private static final String TAG = "PersonalStats";

    StringBuilder[] keyPhraseTexts;
    StringBuilder contactsDisplay;
    StringBuilder generalDisplay;

    int[] dayHistogram;
    int[] hourHistogram;

    public PersonalStats() {
        keyPhraseTexts = new StringBuilder[2];
        for (int i = 0; i < keyPhraseTexts.length; i++)
            keyPhraseTexts[i] = new StringBuilder();

        contactsDisplay = new StringBuilder();
        generalDisplay = new StringBuilder();

        dayHistogram = new int[7];
        for (int i = 0; i < dayHistogram.length; i++)
            dayHistogram[i] = 0;

        hourHistogram = new int[25];
        for (int i = 0; i < hourHistogram.length; i++)
            hourHistogram[i] = 0;
    }

    public PersonalStats(FileInputStream in) throws IOException, JSONException {
        this();
        readFrom(in);
    }

    private void readFrom(FileInputStream in) throws IOException, JSONException {
        long previousTime = System.currentTimeMillis();
        
        // read file into a string
        StringBuilder b = new StringBuilder();
        BufferedReader charIn = new BufferedReader(new InputStreamReader(in));
        String line;
        while ( (line = charIn.readLine()) != null) {
            b.append(line);
        }
        charIn.close();
        
        // parse it
        JSONTokener tok = new JSONTokener(b.toString());
        JSONObject json = (JSONObject) tok.nextValue();
        keyPhraseTexts[PHRASE_SORTED].append(json.getString("phrase_sorted"));
        keyPhraseTexts[COUNT_SORTED].append(json.getString("count_sorted"));
        contactsDisplay.append(json.getString("contacts"));
        generalDisplay.append(json.getString("general"));
        
        JSONArray dayJson = json.getJSONArray("by_day");
        for (int i = 0; i < dayHistogram.length && i < dayJson.length(); i++)
            dayHistogram[i] = dayJson.getInt(i);
        
        JSONArray hourJson = json.getJSONArray("by_hour");
        for (int i = 0; i < hourHistogram.length && i < hourJson.length(); i++)
            hourHistogram[i] = hourJson.getInt(i);
        
        Log.i(TAG, "Read data in " + (System.currentTimeMillis() - previousTime) + " ms");
    }

    public void writeTo(FileOutputStream out) throws IOException, JSONException {
        long previousTime = System.currentTimeMillis();
        
        JSONObject json = new JSONObject();
        json.put("phrase_sorted", keyPhraseTexts[PHRASE_SORTED].toString());
        json.put("count_sorted", keyPhraseTexts[COUNT_SORTED].toString());
        json.put("contacts", contactsDisplay.toString());
        json.put("general", generalDisplay.toString());
        
        JSONArray dayJson = new JSONArray();
        for (int i = 0; i < dayHistogram.length; i++)
            dayJson.put(i, dayHistogram[i]);
        json.put("by_day", dayJson);
        
        JSONArray hourJson = new JSONArray();
        for (int i = 0; i < hourHistogram.length; i++)
            hourJson.put(i, hourHistogram[i]);
        json.put("by_hour", hourJson);
        
        BufferedWriter charOut = new BufferedWriter(new OutputStreamWriter(out));
        charOut.write(json.toString());
        charOut.close();

        Log.i(TAG, "Wrote data in " + (System.currentTimeMillis() - previousTime) + " ms");
    }
}
