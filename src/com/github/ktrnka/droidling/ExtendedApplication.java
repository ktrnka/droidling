/**
 * 
 */
package com.github.ktrnka.droidling;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import android.app.Application;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.ContactsContract;
import android.support.v4.util.LruCache;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

/**
 * @author keith.trnka
 * 
 */
public class ExtendedApplication extends Application
	{
	private static final String TAG = "com.github.ktrnka.droidling.ExtendedApplication";
	private HashMap<String,ArrayList<String[]>> contactMap;
	private Runnable contactThread;
	
	public static enum ContactInfo { NAME, PHOTO_URI };
	
	private LruCache<String,Bitmap> bitmapCache;


	public ExtendedApplication()
		{
		super();
		
		int cacheSize = 512; // 512 KB
		bitmapCache = new LruCache<String,Bitmap>(cacheSize)
			{
			protected int sizeOf(String k, Bitmap v)
				{
				return v.getRowBytes() * v.getHeight() / 1024;
				}
			};
		}
	
	public void onCreate()
		{
		// start thread to load contacts
		
		// start thread to load unigrams for the current language
		
		// start thread to load stopwords
		
		// start thread to load LID model
		}

	/**
	 * Load the contacts into an internal data structure.
	 * @return true if successful, false if failed (such as a timeout or the contacts database is empty)
	 */
	public boolean blockingLoadContacts()
		{
		if (contactMap != null)
			return true;
		
		contactMap = new HashMap<String,ArrayList<String[]>>();

		String numberName = ContactsContract.CommonDataKinds.Phone.NUMBER;
		String labelName = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME;
		String idName = ContactsContract.CommonDataKinds.Phone._ID;
		String photoIdName = ContactsContract.CommonDataKinds.Phone.PHOTO_ID;
		String photoUriName = ContactsContract.CommonDataKinds.Phone.PHOTO_URI;
		
		String[] phoneLookupProjection = new String[] { numberName, labelName, idName, photoIdName, photoUriName };

		Cursor phones = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
				phoneLookupProjection, null, null, null);

		if (phones.moveToFirst())
			{
			final int phoneIndex = phones.getColumnIndex(numberName);
			final int labelIndex = phones.getColumnIndex(labelName);
			//final int photoIdIndex = phones.getColumnIndex(photoIdName);
			final int photoUriIndex = phones.getColumnIndex(photoUriName);
			//final int idIndex = phones.getColumnIndex(idName);
			
			do
				{
				String number = phones.getString(phoneIndex);
				String label = phones.getString(labelIndex);
				
				//long photoId = phones.getLong(photoIdIndex);
				String photoUri = phones.getString(photoUriIndex);
				
				if (photoUri != null)
					{
					Log.d(TAG, "Non-null photo uri for " + label + ": " + photoUri);
					}
				else
					{
					Log.d(TAG, "Null photo uri for " + label);
					}

				String minMatch = PhoneNumberUtils.toCallerIDMinMatch(number);
				
				if (contactMap.containsKey(minMatch))
					{
					contactMap.get(minMatch).add(new String[] { number, label, photoUri });
					}
				else
					{
					ArrayList<String[]> matchList = new ArrayList<String[]>();
					matchList.add(new String[] { number, label, photoUri });
					contactMap.put(minMatch, matchList);
					}
				
				} while (phones.moveToNext());
			}
		else
			{
			return false;
			}
		phones.close();
		
		return true;
		}

	/**
	 * The number may be in a very different format than the way it's stored in contacts,
	 * so we need to do a fancy-pants matching.  I tried using the ContentProvider way
	 * of doing this at first but it was much too slow.
	 *
	 * TODO: This function could save some temporary object creation by re-inserting the
	 * contact using the potentially non-standard input parameter or caching in another way.
	 * 
	 * @param number
	 * @return The display name value if found, null if not.
	 */
	public String lookupContactName(String number)
		{
		return lookupContactInfo(number, ContactInfo.NAME);
		}
	
	public String lookupContactInfo(String number, ContactInfo field)
		{
		if (contactMap == null)
			return null;
		
		int fieldIndex = 0;
		switch (field)
		{
		case NAME:
			fieldIndex = 1;
			break;
		case PHOTO_URI:
			fieldIndex = 2;
			break;
		}
		
		String minMatch = PhoneNumberUtils.toCallerIDMinMatch(number);
		
		if (!contactMap.containsKey(minMatch))
			return null;
		
		ArrayList<String[]> matchList = contactMap.get(minMatch);
		if (matchList.size() == 1)
			return matchList.get(0)[fieldIndex];
		
		for (String[] pair : matchList)
			{
			if (PhoneNumberUtils.compare(number, pair[0]))
				return pair[fieldIndex];
			}
		
		return null;
		}

	/**
	 * start processing contacts in another thread and return
	 */
	private void nonblockingLoadContacts()
		{
		
		}
	
	/**
	 * The intention is for this to see if a contact thread is running already
	 * and fail if one is.  If one isn't, it can create the thread and return it.
	 * @return a new thread if one doesn't already exist, null if a thread already exists
	 */
	private synchronized Runnable createContactThread()
		{
		if (contactThread != null)
			return null;
		
		return new Thread()
			{
			public void run()
				{
				blockingLoadContacts();
				contactThread = null;
				}
			};
		}
	
	private boolean blockingLoadUnigrams(Locale locale)
		{
		return true;
		}
	
	private void nonblockingLoadUnigrams()
		{
		
		}
	
	/**
	 * Helper to compute inSampleSize, given an image height/width and target height/width.
	 * Taken from http://developer.android.com/training/displaying-bitmaps/load-bitmap.html
	 * @param options properties of the image
	 * @param reqWidth target width
	 * @param reqHeight target height
	 * @return
	 */
	public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight)
		{
		// Raw height and width of image
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize = 1;

		if (height > reqHeight || width > reqWidth)
			{
			// Calculate ratios of height and width to requested height and
			// width
			// TODO: powers of 2 are faster; might be best to round to nearest power of 2
			final int heightRatio = Math.round((float) height / (float) reqHeight);
			final int widthRatio = Math.round((float) width / (float) reqWidth);
			
			Log.d(TAG, String.format("Suggesting resample from %d x %d to %d x %d", width, height, reqWidth, reqHeight));

			// Choose the smallest ratio as inSampleSize value, this will
			// guarantee
			// a final image with both dimensions larger than or equal to the
			// requested height and width.
			inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
			}
		else
			{
			Log.d(TAG, String.format("Not suggesting resample; at %d x %d, need %d x %d", width, height, reqWidth, reqHeight));
			}

		return inSampleSize;
		}
	
	public Bitmap loadBitmapFromUri(Context context, Uri imageUri, int reqWidth, int reqHeight) throws IOException
		{
		// check the cache
		if (bitmapCache != null)
			{
			Bitmap cachedBitmap = bitmapCache.get(imageUri.toString());
			if (cachedBitmap != null)
				{
				Log.i(TAG, "Image cache hit: " + imageUri.toString());
				return cachedBitmap;
				}
			else
				{
				Log.i(TAG, "Image cache miss: " + imageUri.toString());
				}
			}
		
		InputStream in = context.getContentResolver().openInputStream(imageUri); 
	
		// check dimensions
		final BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeStream(in, null, options);
		in.close();
	
		// Calculate inSampleSize
		options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
	
		// Decode bitmap with inSampleSize set
		options.inJustDecodeBounds = false;
		in = context.getContentResolver().openInputStream(imageUri);           
		Bitmap bitmap = BitmapFactory.decodeStream(in, null, options);
		in.close();
		
		// add to cache
		if (bitmapCache != null)
			{
			bitmapCache.put(imageUri.toString(), bitmap);
			}

		return bitmap;
		}
	
	public Bitmap loadBitmapFromResources(Context context, int drawableId, int reqWidth, int reqHeight)
		{
		// check the cache
		if (bitmapCache != null)
			{
			Bitmap cachedBitmap = bitmapCache.get(String.valueOf(drawableId));
			if (cachedBitmap != null)
				{
				Log.i(TAG, "Image cache hit: " + drawableId);
				return cachedBitmap;
				}
			else
				{
				Log.i(TAG, "Image cache miss: " + drawableId);
				}
			}

		// check dimensions
		final BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeResource(context.getResources(), drawableId, options);

		// Calculate inSampleSize
		options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

		// Decode bitmap with inSampleSize set
		options.inJustDecodeBounds = false;
		Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), drawableId, options);
		
		// add to cache
		if (bitmapCache != null)
			{
			bitmapCache.put(String.valueOf(drawableId), bitmap);
			}
		
		return bitmap;
		}
	}
