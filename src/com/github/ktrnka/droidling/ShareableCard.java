package com.github.ktrnka.droidling;

import java.lang.ref.WeakReference;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.fima.cardsui.objects.RecyclableCard;

/**
 * Card with title, description, and share button.
 */
public class ShareableCard extends RecyclableCard
	{
	private View.OnClickListener shareListener;
	
	/**
	 * Trying to avoid circular reference between the Activity to the share button listener
	 */
	private WeakReference<Context> weakContext;

	public ShareableCard(String title)
		{
		super(title);
		}

	public ShareableCard(String title, String text)
		{
		super(title, text, 0);
		}
	
	/**
	 * 
	 * @param title
	 * @param text
	 * @param applicationName
	 * @param context Context used to start the share intent
	 */
	public ShareableCard(String title, String text, CharSequence applicationName, Context context)
		{
		this(title, text);
		
		weakContext = new WeakReference<Context>(context);
		
		shareListener = new ShareListener("Shared stats from " + applicationName, "Stats: " + title + ":\n" + text);
		}

	@Override
	protected int getCardLayoutId()
		{
		return R.layout.card_shareable;
		}
	
	@Override
	protected void applyTo(View view)
		{
		((TextView) view.findViewById(R.id.title)).setText(title);
		((TextView) view.findViewById(R.id.body)).setText(desc);
		
		if (shareListener != null)
			{
			ImageView shareButton = (ImageView) view.findViewById(R.id.share);
			shareButton.setOnClickListener(shareListener);
			}
		}
	
	private class ShareListener implements View.OnClickListener
		{
		private String subject;
		private String body;
		private static final String chooserText = "Share with...";
		
		ShareListener(String subject, String body)
			{
			this.subject = subject;
			this.body = body;
			}

		public void onClick(View view)
			{
			Context context = weakContext.get();
			if (context == null)
				return;
			
			Intent sendIntent = new Intent(Intent.ACTION_SEND);
			sendIntent.setType("text/plain");
			sendIntent.putExtra(Intent.EXTRA_TEXT, body);
			sendIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
			
			context.startActivity(Intent.createChooser(sendIntent, chooserText));
			}
		}

	}
