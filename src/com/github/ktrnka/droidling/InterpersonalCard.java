package com.github.ktrnka.droidling;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.fima.cardsui.objects.Card;

public class InterpersonalCard extends Card {
	// TODO: Investigate whether this will cause memory leaks by preventing GC of the activity.
	private Context activityContext;
	private InterpersonalSingleStats stats;
	
	public InterpersonalCard(String title, InterpersonalSingleStats stats, Context activityContext) {
		super(title, null, 0);
		
		this.activityContext = activityContext;
		
		this.stats = stats;
	}

	@Override
	public View getCardContent(Context context) {
	/*
		View view = LayoutInflater.from(context).inflate(R.layout.card_ex, null);
	
		((TextView) view.findViewById(R.id.title)).setText(title);
		((TextView) view.findViewById(R.id.description)).setText(stats.numSentText);
	
		return view;
		*/
		View view = LayoutInflater.from(context).inflate(R.layout.card_interpersonal2, null);

		((TextView) view.findViewById(R.id.title)).setText(title);
		((TextView) view.findViewById(R.id.numSent)).setText(stats.numSentText);
		((TextView) view.findViewById(R.id.averageMessageLength)).setText(stats.messageLengthText);
		((TextView) view.findViewById(R.id.sharedVocab)).setText(stats.sharedVocabPercentText);
		((TextView) view.findViewById(R.id.sharedPhrases)).setText(stats.sharedPhrasesText);
		((TextView) view.findViewById(R.id.responseTime)).setText(stats.responseTimeText);
		((TextView) view.findViewById(R.id.bigramGeneration)).setText(stats.bigramGenerationText);
		((TextView) view.findViewById(R.id.trigramGeneration)).setText(stats.trigramGenerationText);
		
		ImageView shareView = (ImageView) view.findViewById(R.id.share);
		shareView.setOnClickListener(new View.OnClickListener()
			{
			public void onClick(View v)
				{
				if (activityContext == null)
					return;
				
				String subject = "Shared stats from " + activityContext.getString(R.string.app_name);
				// TODO: There's no longer a valid single desc field
				String text = "Stats: " + title + ":\n" + desc;
				
				Intent sendIntent = new Intent(Intent.ACTION_SEND);
				sendIntent.setType("text/plain");
				sendIntent.putExtra(Intent.EXTRA_TEXT, text);
				sendIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
				
				activityContext.startActivity(Intent.createChooser(sendIntent, activityContext.getString(R.string.share_intent)));
				}
			});
		
		Log.d("com.github.droidling.InterpersonalCard", "Inflated card for " + title);

		return view;
	}

	
	
	
}
