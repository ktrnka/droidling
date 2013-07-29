package com.github.ktrnka.droidling;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.fima.cardsui.objects.Card;

public class InterpersonalCard extends Card
	{
	// TODO: Investigate whether this will cause memory leaks by preventing GC
	// of the activity.
	private Context activityContext;
	private InterpersonalSingleStats stats;

	public InterpersonalCard(String title, InterpersonalSingleStats stats, Context activityContext)
		{
		super(title, null, 0);

		this.activityContext = activityContext;

		this.stats = stats;
		}

	public int getCardContentId()
		{
		return R.layout.card_interpersonal2;
		}

	@Override
	public View getCardContent(final Context context)
		{
		View view = LayoutInflater.from(context).inflate(getCardContentId(), null);

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
				String text = "Stats: " + title + ":\n" + stats.toString(context);

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

	@Override
    public boolean convert(final View convertCardView)
	    {
	    View view = convertCardView.findViewById(R.id.cardContentRoot);
	    if (view == null)
	    	{
	    	Log.d("com.github.ktrnka.droidling.InterpersonalCard", "Can't find card content root");
	    	return false;
	    	}
	    
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
				String text = "Stats: " + title + ":\n" + stats.toString(convertCardView.getContext());

				Intent sendIntent = new Intent(Intent.ACTION_SEND);
				sendIntent.setType("text/plain");
				sendIntent.putExtra(Intent.EXTRA_TEXT, text);
				sendIntent.putExtra(Intent.EXTRA_SUBJECT, subject);

				activityContext.startActivity(Intent.createChooser(sendIntent, activityContext.getString(R.string.share_intent)));
				}
			});

		Log.d("com.github.droidling.InterpersonalCard", "Reused card for " + title);

	    return true;
	    }

	}
