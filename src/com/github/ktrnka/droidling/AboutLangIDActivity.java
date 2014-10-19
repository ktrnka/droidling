
package com.github.ktrnka.droidling;

import android.os.Bundle;

import com.actionbarsherlock.app.SherlockActivity;

/**
 * The activity that describes the app, the author, etc. Not much code, most of
 * the work is in the XML file.
 * 
 * @author keith.trnka
 */
public class AboutLangIDActivity extends SherlockActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.about_langid);
    }
}
