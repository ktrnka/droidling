package com.github.ktrnka.droidling.helpers;

import java.lang.ref.WeakReference;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;

/**
 * Placeholder Drawable that retains a reference to
 * the worker thread that's loading the bitmap.
 */
public class AsyncDrawable extends BitmapDrawable
	{
	private final WeakReference<BitmapLoaderTask> taskReference;
	
	public AsyncDrawable(Resources res, Bitmap bitmap, BitmapLoaderTask loaderTask)
		{
		super(res, bitmap);
		taskReference = new WeakReference<BitmapLoaderTask>(loaderTask);
		}
	
	public BitmapLoaderTask getBitmapLoaderTask()
		{
		if (taskReference == null)
			return null;
		
		return taskReference.get();
		}
	}
