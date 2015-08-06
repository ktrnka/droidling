
package com.github.ktrnka.droidling;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;

/**
 * The activity that describes the app, the author, etc. Not much code, most of
 * the work is in the XML file.
 * 
 * @author keith.trnka
 */
public class AboutLangIDActivity extends ActionBarActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.about_langid);
    }
}
