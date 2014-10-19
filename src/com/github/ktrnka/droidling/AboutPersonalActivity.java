
package com.github.ktrnka.droidling;

import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;

/**
 * The activity that describes the app, the author, etc. Not much code, most of
 * the work is in the XML file.
 * 
 * @author keith.trnka
 */
public class AboutPersonalActivity extends SherlockActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.about_personal);

        // these lines are necessary for clickable TextViews with <a href> in
        // the strings
        TextView textView = (TextView) findViewById(R.id.about_key_phrases);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
    }
}
