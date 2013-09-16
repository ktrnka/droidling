package com.github.ktrnka.droidling;

import java.io.IOException;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.fima.cardsui.objects.Card;
import com.github.ktrnka.droidling.helpers.AsyncDrawable;
import com.github.ktrnka.droidling.helpers.BitmapLoaderTask;

public class InterpersonalCard extends Card
	{
	private static final String TAG = "InterpersonalCard";
	// TODO: Investigate whether this will cause memory leaks by preventing GC
	// of the activity.
	private Context activityContext;
	private InterpersonalSingleStats stats;
	private ExtendedApplication application;
	private View.OnClickListener shareListener;

	public InterpersonalCard(String title, InterpersonalSingleStats stats, Context activityContext, ExtendedApplication application)
		{
		super(title, null, 0);

		this.activityContext = activityContext;
		this.stats = stats;
		this.application = application;
		
		shareListener = new View.OnClickListener()
			{
			public void onClick(View v)
				{
				if (InterpersonalCard.this.activityContext == null)
					return;

				String subject = "Shared stats from " + InterpersonalCard.this.activityContext.getString(R.string.app_name);
				String text = "Stats: " + InterpersonalCard.this.title + ":\n" + InterpersonalCard.this.stats.toString(InterpersonalCard.this.activityContext);

				Intent sendIntent = new Intent(Intent.ACTION_SEND);
				sendIntent.setType("text/plain");
				sendIntent.putExtra(Intent.EXTRA_TEXT, text);
				sendIntent.putExtra(Intent.EXTRA_SUBJECT, subject);

				InterpersonalCard.this.activityContext.startActivity(Intent.createChooser(sendIntent, InterpersonalCard.this.activityContext.getString(R.string.share_intent)));
				}
			};
		}

	public int getCardContentId()
		{
		return R.layout.card_interpersonal;
		}
	
	private void apply(final View view)
		{
		Resources res = view.getResources();
		int imageSize = res.getDimensionPixelSize(R.dimen.imagebutton_size);

		((TextView) view.findViewById(R.id.title)).setText(title);
		((TextView) view.findViewById(R.id.mainText)).setText(stats.getFormatted(view.getContext()));

		ImageView contactImageView = (ImageView) view.findViewById(R.id.contactImage);
		if (stats.photoUri != null)
			{
			Log.d(TAG, "Photo uri for " + title + ": " + stats.photoUri);
            try
	            {
	            setImage(contactImageView, view.getContext(), Uri.parse(stats.photoUri), imageSize, imageSize);
	            }
            catch (IOException e)
	            {
	            // TODO Auto-generated catch block
	            e.printStackTrace();
	            }
			}
		else
			{
			Log.d(TAG, "Photo uri for " + title + " is null");
			// temporary hack to work better with card recycling
			try
	            {
	            setImage(contactImageView, view.getContext(), BitmapLoaderTask.packIntoUri(R.drawable.ic_contact_picture), imageSize, imageSize);
	            }
            catch (IOException e)
	            {
	            // TODO Auto-generated catch block
	            e.printStackTrace();
	            }
			}

		
		ImageView shareView = (ImageView) view.findViewById(R.id.share);
		shareView.setOnClickListener(shareListener);
		}
	
	private void setImage(ImageView imageView, Context context, Uri imageUri, int width, int height) throws IOException
		{
		// old code
		//imageView.setImageBitmap(ExtendedApplication.decodeSampledBitmapFromUri(context, imageUri, width, height));
		
		if (!BitmapLoaderTask.cancelPotentialWork(imageView, imageUri))
			{
			BitmapLoaderTask task = new BitmapLoaderTask(imageView, width, height, application);
			AsyncDrawable placeholder = new AsyncDrawable(context.getResources(), null, task);
			imageView.setImageDrawable(placeholder);
			task.execute(imageUri);
			}
		}

	@Override
	public View getCardContent(final Context context)
		{
		View view = LayoutInflater.from(context).inflate(getCardContentId(), null);

		apply(view);

		Log.d("com.github.droidling.InterpersonalCard", "Inflated card for " + title);

		return view;
		}

	@Override
    public boolean convert(final View convertCardView)
	    {
	    View view = convertCardView.findViewById(R.id.cardContentRoot);
	    if (view == null)
	    	{
	    	Log.d(TAG, "Can't find card content root");
	    	return false;
	    	}
	    
	    apply(view);

		Log.d("com.github.droidling.InterpersonalCard", "Reused card for " + title);

	    return true;
	    }

	}
