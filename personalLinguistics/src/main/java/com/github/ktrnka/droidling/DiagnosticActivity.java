
package com.github.ktrnka.droidling;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;

import com.fima.cardsui.views.CardUI;

/**
 * An activity for testing, plain and simple.
 *
 * @author keith.trnka
 */
public class DiagnosticActivity extends ActionBarActivity {
    @SuppressWarnings("unused")
    private static final String TAG = "DiagnosticActivity";
    private CardUI mCardView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.cardsui_main);

        // init CardView
        mCardView = (CardUI) findViewById(R.id.cardsview);
        mCardView.setSwipeable(false);

        mCardView.addCard(new ShareableCard("Test Title"));
        mCardView.addCard(new ShareableCard("Test Title", "Test body content"));
        mCardView.addCard(new ShareableCard("Test Title", "Test body content 2\nThis is a newline!"));
        mCardView.addCard(new ShareableCard("Test Title", "Test body content 2\nThis is a newline!"));
        mCardView.addCard(new ShareableCard("Test Title", "Test body content 2\nThis is a newline!"));
        mCardView.addCard(new ShareableCard("Test Title", "Test body content 2\nThis is a newline!"));

        // draw cards
        mCardView.refresh();
    }

}
