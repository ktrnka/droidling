package com.github.ktrnka.droidling;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;

import org.achartengine.GraphicalView;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.fima.cardsui.objects.RecyclableCard;

/**
 * Plain card that has a title and description.
 */
public class GraphCard extends RecyclableCard
	{
	private GraphicalView graphView;
	private View.OnClickListener shareListener;
	
	/**
	 * Trying to avoid circular reference between the Activity to the share button listener
	 */
	private WeakReference<Context> weakContext;

	public GraphCard(String title, GraphicalView graphView, String applicationName, Context shareContext)
		{
		super(title, null, 0);
		
		this.graphView = graphView;
		
		weakContext = new WeakReference<Context>(shareContext);
		shareListener = new ShareListener("Shared histogram from " + applicationName);
		}
	
	@Override
    protected int getCardLayoutId()
	    {
	    return R.layout.card_graph;
	    }

	@Override
    protected void applyTo(View convertView)
		{
		((TextView) convertView.findViewById(R.id.title)).setText(title);

		// setup the graph
		ViewGroup container = (ViewGroup) convertView.findViewById(R.id.graphGroup);

		// TODO: This method for getting height is deprecated
		// Maybe I should set a target aspect ratio for the graph and trigger from the width.
		WindowManager wm = (WindowManager) convertView.getContext().getSystemService(Context.WINDOW_SERVICE);
		Display display = wm.getDefaultDisplay();
		int screenHeight = display.getHeight();
		
		container.removeAllViews();
		ViewGroup oldParent = (ViewGroup)graphView.getParent();
		if (oldParent != null)
			oldParent.removeView(graphView);

		container.addView(graphView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, screenHeight / 3));
		
		if (shareListener != null)
			{
			ImageView shareButton = (ImageView) convertView.findViewById(R.id.share);
			shareButton.setOnClickListener(shareListener);
			}
		}

	private class ShareListener implements View.OnClickListener
		{
		private String subject;
		private static final String chooserText = "Share with...";
		private static final String FILENAME = "sms_ling.png";

		ShareListener(String subject)
			{
			this.subject = subject;
			}

		@TargetApi(Build.VERSION_CODES.FROYO)
		public void onClick(View view)
			{
			if (weakContext == null)
				return;
			
			Context context = weakContext.get();
			if (context == null)
				return;
			
			File file;
			if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO)
				{
				file = new File(context.getExternalFilesDir(null), FILENAME);
				Log.i("GraphCard", "Sharing using external files dir new API");
				}
			else
				{
				Log.i("GraphCard", "Sharing using external old API");
				String packageName = context.getPackageName();
				File externalRoot = Environment.getExternalStorageDirectory();
				File externalDir = new File(externalRoot.getAbsolutePath() + "/Android/data/" + packageName + "/files");
				
				if (!externalDir.exists())
					{
					if (!externalDir.mkdirs())
						{
						Log.e("GraphCard", "Failed to create storage directory for " + externalDir);
						}
					}
				
				file = new File(externalDir, FILENAME);
				}

	        try
	        	{
	        	OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
	        	graphView.toBitmap().compress(CompressFormat.PNG, 100, out);
	        	out.close();
	        	
	            Intent intent = new Intent( android.content.Intent.ACTION_SEND);
	            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	            intent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + file.getAbsolutePath()));
	            intent.setType("image/png");

	            intent.putExtra(Intent.EXTRA_SUBJECT, subject);

	            context.startActivity(Intent.createChooser(intent, chooserText));
	        	}
	        catch (IOException e)
	        	{
	        	Log.e("GraphCard", "Failed to share: " + e.toString());
	        	}
			}
		}
	}
