
package com.github.ktrnka.droidling;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;

/**
 * Basic activity to show info about the app/author.
 * 
 * @author keith.trnka
 */
public class AboutActivity extends SherlockActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.about_app);

        // add version string
        PackageManager manager = this.getPackageManager();
        PackageInfo info;
        TextView version = (TextView) findViewById(R.id.about_version);
        try {
            info = manager.getPackageInfo(this.getPackageName(), 0);
            version.setText("Version " + info.versionName);
        } catch (NameNotFoundException e) {
            version.setText("Version: error found in loading version number");
        }

        // these lines are necessary for clickable TextViews with <a href> in
        // the strings
        TextView textView = (TextView) findViewById(R.id.about_author);
        textView.setMovementMethod(LinkMovementMethod.getInstance());

        textView = (TextView) findViewById(R.id.about_other_credits);
        textView.setMovementMethod(LinkMovementMethod.getInstance());

        textView = (TextView) findViewById(R.id.about_library_credits);
        textView.setMovementMethod(LinkMovementMethod.getInstance());

        // load the changelog
        TextView changelog = (TextView) findViewById(R.id.changelog);
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(getAssets().open(
                    "changelog.txt")), 8192);
            changelog.setText("");
            String line;
            while ((line = in.readLine()) != null) {
                changelog.append(line);
                changelog.append("\n");
            }
            in.close();
        } catch (IOException e) {
            changelog.setText("Failed to load changelog.");
        }
    }

}
