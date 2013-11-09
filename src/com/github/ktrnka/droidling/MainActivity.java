package com.github.ktrnka.droidling;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Formatter;

import com.actionbarsherlock.app.SherlockActivity;
import com.github.ktrnka.droidling.R;
import com.github.ktrnka.droidling.helpers.AsyncDrawable;
import com.github.ktrnka.droidling.helpers.BitmapLoaderTask;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Profile;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

public class MainActivity extends SherlockActivity
	{
	public static final String PACKAGE_NAME = "com.github.ktrnka.droidling";
	
	public static final boolean DEVELOPER_MODE = false;
	
	public static final String TAG = "MainActivity";
	
	private static final long VERSION_NOT_FOUND = -1;
		
	public void onCreate(Bundle savedInstanceState)
		{
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.main_activity);
		
		checkShowWhatsNew();

		loadProfilePhoto();
		loadContactPhotos();
		}
	
	public void onStart()
		{
		super.onStart();
		}

	// TODO: run this as an asynctask
	private void loadContactPhotos()
	    {

	    TableLayout photoTable = (TableLayout) findViewById(R.id.interpersonalTable);
	    if (photoTable == null)
	    	{
	    	Log.e(TAG, "Failed to find interpersonal table");
	    	return;
	    	}
	    
	    photoTable.setOnClickListener(new OnClickListener()
			{
			public void onClick(View v)
	            {
	            Intent intent = new Intent(MainActivity.this, InterpersonalActivity.class);
				startActivity(intent);
	            }
			});
	    
	    final int COLUMNS = 3;
	    final int ROWS = 3;
	    int desiredImages = COLUMNS * ROWS;
	    String[] photoUris = new String[desiredImages];

	    int numImages = loadContactPhotoUris(photoUris);
	    
	    Resources res = getResources();
		int imageSize = res.getDimensionPixelSize(R.dimen.home_imagebutton_small_size);
	    
	    int photoIndex = 0;
	    ImageAdapter adapter = new ImageAdapter((ExtendedApplication) getApplication(), photoUris, imageSize);
	    for (int row = 0; row < ROWS; row++)
	    	{
	    	TableRow tableRow = new TableRow(this);
	    	for (int col = 0; col < COLUMNS; col++)
	    		{
	    		if (photoIndex >= numImages)
	    			break;
	    		
	    		View photoView = adapter.getView(photoIndex, null, tableRow);
	    		
	    		TableRow.LayoutParams params = new TableRow.LayoutParams();
	    		params.width = imageSize;
	    		params.height = imageSize;
	    		
	    		tableRow.addView(photoView, params);
	    		photoIndex++;
	    		}
	    	photoTable.addView(tableRow);
	    	}
	    }

	/**
	 * 
	 * @param photoUris array to populate, assumed non-null
	 * @return number of URIs loaded into photoUris
	 */
	private int loadContactPhotoUris(String[] photoUris)
	    {
	    int numImages = 0;
	    
	    /*
	    if (ExtendedApplication.DEMO_MODE) {
	    	int i;
	    	for (i = 0; i < photoUris.length && i < ExtendedApplication.demoResources.length; i++)
	    		{
	    		photoUris[i] = BitmapLoaderTask.packIntoUri(ExtendedApplication.demoResources[i]).toString();
	    		}
	    	
	    	return i;
	    }
	    */
	    
		if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
			{
		    final String[] projection = new String[] { Contacts._ID, Contacts.DISPLAY_NAME, Contacts.PHOTO_ID, Contacts.PHOTO_URI, Contacts.PHOTO_THUMBNAIL_URI, Contacts.TIMES_CONTACTED };
		    final String selection = Contacts.PHOTO_URI + "!=? AND " + Contacts.TIMES_CONTACTED + ">?";
		    final String[] selectionArgs = new String[]{ "null", "0" };
		    final Cursor cursor = getContentResolver().query(Contacts.CONTENT_URI, projection, selection, selectionArgs, Contacts.TIMES_CONTACTED + " DESC");
		    
		    if (cursor.moveToFirst())
		    	{
		    	final int DISPLAY_COL = cursor.getColumnIndex(Contacts.DISPLAY_NAME);
		    	final int PHOTO_COL = cursor.getColumnIndex(Contacts.PHOTO_URI);
		    	final int PHOTO_THUMB_COL = cursor.getColumnIndex(Contacts.PHOTO_THUMBNAIL_URI);
		    	final int PHOTO_ID_COL = cursor.getColumnIndex(Contacts.PHOTO_ID);
		    	
		    	do {
		    		String photoUri = cursor.getString(PHOTO_COL);
		    		
		    		int photoId = cursor.getInt(PHOTO_ID_COL);
		    		String thumbUri = cursor.getString(PHOTO_THUMB_COL);
		    		String name = cursor.getString(DISPLAY_COL);
		    		
		    		//Log.i(TAG, String.format("loadContactPhotoUris(%s): ID=%d, full URI=%s, thumb=%s", name, photoId, photoUri, thumbUri));
		    		
		    		photoUris[numImages++] = photoUri;
		    		if (numImages >= photoUris.length)
		    			break;
	
		    		} while (cursor.moveToNext());
		    	}
		    cursor.close();
			}
		else
			{
			// basic query without the honeycomb stuff
		    final String[] projection = new String[] { Contacts._ID, Contacts.DISPLAY_NAME, Contacts.PHOTO_ID, Contacts.TIMES_CONTACTED };
		    final String selection = Contacts.TIMES_CONTACTED + ">?";
		    final String[] selectionArgs = new String[]{ "0" };
		    final ContentResolver cr = getContentResolver();
		    final Cursor cursor = cr.query(Contacts.CONTENT_URI, projection, selection, selectionArgs, Contacts.TIMES_CONTACTED + " DESC");

		    if (cursor.moveToFirst())
		    	{
		    	final int ID_COL = cursor.getColumnIndex(Contacts._ID);
		    	final int DISPLAY_COL = cursor.getColumnIndex(Contacts.DISPLAY_NAME);
		    	final int PHOTO_ID_COL = cursor.getColumnIndex(Contacts.PHOTO_ID);
		    	
		    	do {
		    		int contactId = cursor.getInt(ID_COL);
		    		int photoId = cursor.getInt(PHOTO_ID_COL);
		    		String name = cursor.getString(DISPLAY_COL);
		    		
		    		Uri contactPhotoUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
	    			InputStream input = ContactsContract.Contacts.openContactPhotoInputStream(cr, contactPhotoUri);
		    		//Log.i(TAG, String.format("loadContactPhotoUris(%s): ID=%d, PHOTO_ID=%s, InputStream=%s, URI=%s", name, contactId, photoId, (input == null ? "null" : "non-null"), contactPhotoUri.toString()));

		    		if (input == null)
		    			continue;
		    		
		    		try
	                    {
	                    input.close();
	                    }
                    catch (IOException e)
	                    {
	                    Log.e(TAG, "Failed to close InputStream", e);
	                    }
		    		
		    		photoUris[numImages++] = contactPhotoUri.toString();
		    		if (numImages >= photoUris.length)
		    			break;
	
		    		} while (cursor.moveToNext());
		    	}
		    cursor.close();
			}
	    
	    return numImages;
	    }

	@SuppressLint("NewApi")
	private void loadProfilePhoto()
	    {
	    final String TAG = MainActivity.TAG + ".loadProfilePhoto()";
	    
		ImageView profileButton = (ImageView) findViewById(R.id.personalImageButton);
		if (profileButton == null)
			{
			Log.e(TAG, "Failed to find ImageButton");
			return;
			}
		
		profileButton.setOnClickListener(new OnClickListener()
			{
			public void onClick(View v)
	            {
	            Intent intent = new Intent(MainActivity.this, PersonalActivity.class);
				startActivity(intent);
	            }
			});
		
		if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH)
			return;

		Resources res = getResources();
		int imageSize = res.getDimensionPixelSize(R.dimen.home_imagebutton_size);

	    final String[] projection = new String[]{ Profile._ID, Profile.DISPLAY_NAME_PRIMARY, Profile.PHOTO_URI, Profile.PHOTO_THUMBNAIL_URI };
	    final Cursor cursor = getContentResolver().query(Profile.CONTENT_URI, projection, null, null, null);
	    //Log.v(TAG, "Number of profile entries: " + cursor.getCount());
	    if (cursor.moveToFirst())
	    	{
	    	final int PHOTO_URI_COL = cursor.getColumnIndex(Profile.PHOTO_URI);
	    	
	    	String photoUri = cursor.getString(PHOTO_URI_COL);
	    	if (photoUri == null)
	    		{
	    		Log.e(TAG, "Profile photo URI is null!");
	    		cursor.close();
	    		return;
	    		}
	    	
			//Log.v(TAG, "Photo uri: " + photoUri);
            try
	            {
	            setImage(profileButton, this, Uri.parse(photoUri), imageSize, imageSize);
	            }
            catch (IOException e)
	            {
	            Log.e(TAG, "IOException in setting image", e);
	            }
	    	}
	    cursor.close();
	    }
	
	private void setImage(ImageView imageView, Context context, Uri imageUri, int width, int height) throws IOException
		{
		if (!BitmapLoaderTask.cancelPotentialWork(imageView, imageUri))
			{
			BitmapLoaderTask task = new BitmapLoaderTask(imageView, width, height, (ExtendedApplication) getApplication());
			AsyncDrawable placeholder = new AsyncDrawable(context.getResources(), null, task);
			imageView.setImageDrawable(placeholder);
			task.execute(imageUri);
			}
		}

	/**
	 * Check if this is first install, app update, normal load and show
	 * What's New dialog if appropriate.
	 */
	private void checkShowWhatsNew()
	    {
	    try
			{
			PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_META_DATA);
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
			
			long lastRunVersion = prefs.getLong("lastRunVersionCode", VERSION_NOT_FOUND);
			
			// show the what's new dialog - updates only, not first install
			if (lastRunVersion != VERSION_NOT_FOUND && lastRunVersion < packageInfo.versionCode)
				showWhatsNew();

			// update the stored version - new install and updates
			if (lastRunVersion != packageInfo.versionCode)
				{
				// update the preference
				SharedPreferences.Editor editor = prefs.edit();
				editor.putLong("lastRunVersionCode", packageInfo.versionCode);
				editor.commit();
				}
			}
		catch (PackageManager.NameNotFoundException exc)
			{
			// This error shouldn't be possible; I don't know an accurate way to test it.
			Log.e(TAG, "PackageManager lookup failed");
			Log.e(TAG, Log.getStackTraceString(exc));
			}
	    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
		{
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.new_help, menu);
		return true;
		}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
		{
		Intent intent;
		switch (item.getItemId())
			{
			case R.id.helpMenu:
				intent = new Intent(this, AboutActivity.class);
				startActivity(intent);
				break;
			case R.id.rateMenu:
				rateApp();
				break;
			case R.id.feedbackMenu:
				sendFeedback();
				break;
			case R.id.lidMenu:
				intent = new Intent(this, LanguageIdentificationActivity.class);
				startActivity(intent);
				break;
			default:
				Log.e(TAG, "Undefined menu item selected");
			}
		return false;
		}

	/**
	 * Show the What's New dialog.
	 */
	private void showWhatsNew()
		{
		new AsyncTask<Void,Void,CharSequence>()
			{
			@Override
            protected CharSequence doInBackground(Void... params)
                {
                StringBuilder builder = new StringBuilder();
				try
					{
	                BufferedReader in = new BufferedReader(new InputStreamReader(getAssets().open("changelog.txt")));
					String line;
					
					while ((line = in.readLine()) != null && !isCancelled())
						{
						builder.append(line);
						builder.append('\n');
						}
					in.close();
					}
				catch (IOException e)
					{
					return null;
					}
				
				return builder;
                }
			
			@Override
			protected void onPostExecute(CharSequence result)
				{
				if (isCancelled() || result == null)
					return;
				
				AlertDialog.Builder alertBuilder = new AlertDialog.Builder(MainActivity.this);
				alertBuilder.setTitle("What's New");
				alertBuilder.setMessage(result);
				alertBuilder.setIcon(android.R.drawable.ic_menu_help);

				alertBuilder.setPositiveButton("Close", null);
				alertBuilder.show();
				}
			
			}.execute();
		}
	
	private void rateApp()
		{
		this.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + PACKAGE_NAME)));
		}

	/**
	 * Email feedback to the development account.  Note that most of the strings
	 * aren't from strings.xml, because we don't need to localize them (cause I need to be able to read them)
	 */
	public void sendFeedback()
		{
		// special case for the feedback option
		Intent sendIntent = new Intent(Intent.ACTION_SEND);
		sendIntent.setType("message/rfc822");
		sendIntent.putExtra(Intent.EXTRA_EMAIL, new String[] { getString(R.string.developer_email) });
		sendIntent.putExtra(Intent.EXTRA_SUBJECT, "Feedback on " + getString(R.string.app_name));
		
		// read the config and make it pretty
		Configuration config = getResources().getConfiguration();
		StringBuilder configBuilder = new StringBuilder();

		configBuilder.append("Locale: " + config.locale.toString() + "\n");
		
		configBuilder.append("Size: ");
		switch (config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK)
		{
		case Configuration.SCREENLAYOUT_SIZE_SMALL:
			configBuilder.append("small\n");
			break;
		case Configuration.SCREENLAYOUT_SIZE_NORMAL:
			configBuilder.append("normal\n");
			break;
		case Configuration.SCREENLAYOUT_SIZE_LARGE:
			configBuilder.append("large\n");
			break;
		default:
			configBuilder.append("unknown (" + config.screenLayout + ")\n");
			break;
		}
		
		configBuilder.append("Model: " + android.os.Build.MODEL + "\n");
		
		configBuilder.append("Android version " + android.os.Build.VERSION.RELEASE + "\n");
		configBuilder.append("SDK version " + android.os.Build.VERSION.SDK_INT + "\n\n");
		
		// application information
		try
			{
			PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_META_DATA);
			configBuilder.append(getString(packageInfo.applicationInfo.labelRes) + " Version " + packageInfo.versionName + " (" + packageInfo.versionCode + ")\n");
			}
		catch (PackageManager.NameNotFoundException exc)
			{
			configBuilder.append("Version unknown\n");
			}
		
		String profiling = summarizeRuntime(getApplicationContext(), PersonalActivity.PROFILING_KEY_ORDER);
		if (profiling != null)
			{
			configBuilder.append("\nPersonal Stats Runtime Profiling (Last Run):\n");
			configBuilder.append(profiling);
			}

		profiling = summarizeRuntime(getApplicationContext(), InterpersonalActivity.PROFILING_KEY_ORDER);
		if (profiling != null)
			{
			configBuilder.append("\n\nInterpersonal Stats Runtime Profiling (Last Run):\n");
			configBuilder.append(profiling);
			}

		profiling = summarizeRuntime(getApplicationContext(), LanguageIdentificationActivity.PROFILING_KEY_ORDER);
		if (profiling != null)
			{
			configBuilder.append("\n\nLanguage Identification Runtime Profiling (Last Run):\n");
			configBuilder.append(profiling);
			}

		sendIntent.putExtra(Intent.EXTRA_TEXT, configBuilder.toString());
		
		startActivity(Intent.createChooser(sendIntent, getString(R.string.send_email_with)));
		}

	public static String summarizeRuntime(Context context, String[] PROFILING_KEYS)
		{
		StringBuilder computeBuilder = new StringBuilder();
		Formatter f = new Formatter(computeBuilder);
		double totalSeconds = 0;
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

		for (String key : PROFILING_KEYS)
			{
			long value = prefs.getLong(key, -1);
			
			key = key.replaceAll(".*:\\s*", "");
			
			// doesn't really need a localization; it's only for me
			f.format("%s: %.1fs\n", key, value / 1000.0);
			totalSeconds += value / 1000.0;
			}
		f.format("Total: %.1fs", totalSeconds);
		return computeBuilder.toString();
		}
	}
