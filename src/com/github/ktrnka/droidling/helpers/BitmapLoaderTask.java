package com.github.ktrnka.droidling.helpers;

import java.io.IOException;
import java.lang.ref.WeakReference;

import com.github.ktrnka.droidling.ExtendedApplication;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.widget.ImageView;

/**
 * Helper class load and scale images async.  Based on http://developer.android.com/training/displaying-bitmaps/process-bitmap.html
 */
public class BitmapLoaderTask extends AsyncTask<Uri, Void, Bitmap>
	{
	private static final String RESOURCE_SCHEME = "droidlingresources";
	private final WeakReference<ImageView> imageViewReference;
	private Uri imageUri;
	private int widthPixels;
	private int heightPixels;
	private ExtendedApplication application;
	
	public BitmapLoaderTask(ImageView imageView, int widthPixels, int heightPixels, ExtendedApplication application)
		{
		imageViewReference = new WeakReference<ImageView>(imageView);
		this.widthPixels = widthPixels;
		this.heightPixels = heightPixels;
		this.application = application;
		}

	@Override
    protected Bitmap doInBackground(Uri... params)
	    {
	    imageUri = params[0];
	    
	    if (imageViewReference != null && application != null)
	    	{
	    	ImageView imageView = imageViewReference.get();
		    try
	            {
	            if (imageUri.getScheme().equals(RESOURCE_SCHEME))
	            	{
	            	return application.loadBitmapFromResources(imageView.getContext(), Integer.parseInt(imageUri.getSchemeSpecificPart()), widthPixels, heightPixels);
	            	}
	            else
	            	{
	            	return application.loadBitmapFromUri(imageView.getContext(), imageUri, widthPixels, heightPixels);
	            	}
	            }
            catch (IOException e)
	            {
	            return null;
	            }
	    	}
	    
	    return null;
	    }
	
	/**
	 * Check if we should avoid potential work.  This function should
	 * be run on the UI thread for synchronization.
	 * @param imageView the ImageView we're going to populate
	 * @param imageUri the Uri of the image we're going to populate
	 * @return true if the potential work should be avoided, false otherwise
	 */
	public static boolean cancelPotentialWork(ImageView imageView, Uri imageUri)
	    {
	    if (imageView == null)
	    	return false;
	    
	    AsyncDrawable drawable;
    	Drawable plainDrawable = imageView.getDrawable();
    	if (plainDrawable instanceof AsyncDrawable)
    		drawable = (AsyncDrawable) plainDrawable;
    	else
    		return false;
	    
	    BitmapLoaderTask task = drawable.getBitmapLoaderTask();
	    if (task == null)
	    	return false;

	    // already loading this data
	    if (task.imageUri != null && task.imageUri.equals(imageUri))
	    	{
	    	return true;
	    	}
	    // loading something else - kill it
	    else
	    	{
	    	task.cancel(true);
	    	return false;
	    	}
	    }

	public static Uri packIntoUri(int drawableId)
		{
		return Uri.fromParts(RESOURCE_SCHEME, String.valueOf(drawableId), null);
		}
	
	@Override
	protected void onPostExecute(Bitmap bitmap)
		{
		if (!isCancelled() && imageViewReference != null && bitmap != null)
			{
			ImageView imageView = imageViewReference.get();
			if (imageView != null)
				imageView.setImageBitmap(bitmap);
			}
		}

	}
