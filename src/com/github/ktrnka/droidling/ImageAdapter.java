package com.github.ktrnka.droidling;

import com.github.ktrnka.droidling.helpers.AsyncDrawable;
import com.github.ktrnka.droidling.helpers.BitmapLoaderTask;

import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;


public class ImageAdapter extends BaseAdapter
	{
	private ExtendedApplication application;
	private String[] imageUris;
	private int imageSizePixels;
	
	public ImageAdapter(ExtendedApplication application, String[] imageUris, int imageSizePixels)
		{
		this.application = application;
		this.imageUris = imageUris;
		this.imageSizePixels = imageSizePixels;
		
		for (String uri : imageUris)
		    Log.i("ImageAdapter", "Image URI " + uri);
		}

	public int getCount()
	    {
	    return imageUris.length;
	    }

	public Object getItem(int position)
	    {
	    return null;
	    }

	public long getItemId(int position)
	    {
	    return 0;
	    }

	public View getView(int position, View convertView, ViewGroup parent)
	    {
	    ImageView imageView = (ImageView) convertView;
	    
	    // inflate a view if we can't recycle
	    if (imageView == null)
	    	{
			LayoutInflater inflater = (LayoutInflater) application.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			imageView = (ImageView) inflater.inflate(R.layout.grid_image, null);
			imageView.setScaleType(ScaleType.CENTER_CROP);
	    	}
	    
	    Resources res = application.getResources();

	    // set the image
	    Uri imageUri = Uri.parse(imageUris[position]);
	    Log.i("ImageAdapter", "Setting " + imageUri + ", want " + imageSizePixels + " px");
		if (!BitmapLoaderTask.cancelPotentialWork(imageView, imageUri))
			{
			BitmapLoaderTask task = new BitmapLoaderTask(imageView, imageSizePixels, imageSizePixels, application);
			AsyncDrawable placeholder = new AsyncDrawable(res, null, task);
			imageView.setImageDrawable(placeholder);
			task.execute(imageUri);
			}
	    
	    return imageView;
	    }

	}
