package com.github.ktrnka.droidling;

import com.actionbarsherlock.app.SherlockActivity;
import com.fima.cardsui.views.CardUI;
import com.github.ktrnka.droidling.R;

import android.os.Bundle;

/**
 * An activity for testing, plain and simple.
 * 
 * @author keith.trnka
 *
 */
public class DiagnosticActivity extends SherlockActivity
	{
	private static final String TAG = "com.github.ktrnka.droidling/DiagnosticActivity";
	private CardUI mCardView;

	public void onCreate(Bundle savedInstanceState)
		{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.cardsui_main);

		// init CardView
		mCardView = (CardUI) findViewById(R.id.cardsview);
		mCardView.setSwipeable(false);
		
		mCardView.addCard(new TitledCard("Test Title"));
		mCardView.addCard(new TitledCard("Test Title", "Test body content"));
		mCardView.addCard(new TitledCard("Test Title", "Test body content 2\nThis is a newline!"));
		mCardView.addCard(new TitledCard("Test Title", "Test body content 2\nThis is a newline!"));
		mCardView.addCard(new TitledCard("Test Title", "Test body content 2\nThis is a newline!"));
		mCardView.addCard(new TitledCard("Test Title", "Test body content 2\nThis is a newline!"));

		// draw cards
		mCardView.refresh();
		}

	}
