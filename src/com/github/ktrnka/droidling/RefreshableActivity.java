package com.github.ktrnka.droidling;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

/**
 * Superclass for activities that want a menu bar, etc.
 * @author keith.trnka
 *
 */
public abstract class RefreshableActivity extends SherlockActivity
	{
	private Menu optionsMenu;
	private Class<? extends Activity> helpActivityClass;
	private boolean initializeAsLoading = false;

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
		{
		optionsMenu = menu;
		
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.refreshable, menu);
		
		if (initializeAsLoading)
			{
			setRefreshActionButtonState(true);
			}
		else if (hasNewData())
			{
			final MenuItem refreshItem = optionsMenu.findItem(R.id.refreshMenu);
			refreshItem.setIcon(R.drawable.ic_action_refresh_blue);
			}

		return true;
		}
	
	protected void setInitiallyRefreshing(boolean initiallyRefreshing)
		{
		initializeAsLoading = initiallyRefreshing;
		}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
		{
		switch (item.getItemId())
			{
			case R.id.refreshMenu:
				refresh(true);
				break;
			case R.id.helpMenu:
				if (helpActivityClass != null)
					{
					Intent intent = new Intent(this, helpActivityClass);
					startActivity(intent);
					}
				break;
			}

		return false;
		}
	
	public void setRefreshActionButtonState(final boolean refreshing)
		{
		if (optionsMenu != null)
			{
			final MenuItem refreshItem = optionsMenu.findItem(R.id.refreshMenu);
			if (refreshItem != null)
				{
				if (refreshing)
					{
					runOnUiThread(new Thread()
						{
						public void run()
							{
							refreshItem.setActionView(R.layout.actionbar_progress);
							}
						});
					}
				else
					{
					runOnUiThread(new Thread()
						{
						public void run()
							{
							refreshItem.setIcon(R.drawable.ic_action_refresh);
							refreshItem.setActionView(null);
							}
						});
					}
				}
			}
		else
			{
			// If the optionsMenu is null, need to set the state for when it starts up.
			setInitiallyRefreshing(refreshing);
			}
		}
	
	public void warning(final String message)
		{
		runOnUiThread(new Runnable()
			{
			public void run()
				{
				Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
				}
			});
		}

	public void error(final String message)
		{
		runOnUiThread(new Runnable()
			{
			public void run()
				{
				Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
				}
			});
		}
	
	/**
	 * Must be called in onCreate if you want it to work.
	 * @param helpActivityClass
	 */
	protected void setHelpActivity(Class<? extends Activity> helpActivityClass)
		{
		this.helpActivityClass = helpActivityClass;
		}

	protected void setPreference(String name, long longValue)
		{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		SharedPreferences.Editor editor = prefs.edit();
		editor.putLong(name, longValue);
		editor.commit();
		}

	protected void setPreference(String name, int intValue)
		{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt(name, intValue);
		editor.commit();
		}

	/**
	 * 
	 * @return true if there's new data
	 */
	protected abstract boolean hasNewData();
	
	/**
	 * Refresh the data in the display
	 * @param forceRefresh true if it should be built fresh, false if cached data is okay
	 */
	protected abstract void refresh(boolean forceRefresh);
	}
