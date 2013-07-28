package com.github.ktrnka.droidling;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.fima.cardsui.objects.Card;

public class TitledCard extends Card {

	public TitledCard(String title){
		super(title);
	}
	
	public TitledCard(String title, String text) {
		super(title, text, 0);
	}

	@Override
	public View getCardContent(Context context) {
		View view = LayoutInflater.from(context).inflate(R.layout.card_ex, null);

		((TextView) view.findViewById(R.id.title)).setText(title);
		((TextView) view.findViewById(R.id.description)).setText(desc);

		return view;
	}

	
	
	
}
