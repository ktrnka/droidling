package edu.udel.trnka.pl;

import edu.udel.trnka.pl.R;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

public class HomeActivity extends Activity
	{
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
		{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.home);
		
		final TextView personalText = (TextView) findViewById(R.id.personalTextView);
		personalText.setOnClickListener(new View.OnClickListener()
			{
				
				public void onClick(View v)
					{
					Intent intent = new Intent(HomeActivity.this, PersonalLingActivity.class);
					startActivity(intent);
					}
			});
		
		final TextView aboutText = (TextView) findViewById(R.id.aboutTextView);
		aboutText.setOnClickListener(new View.OnClickListener()
			{
				
				public void onClick(View v)
					{
					Intent intent = new Intent(HomeActivity.this, TestActivity.class);
					startActivity(intent);
					}
			});

		final TextView interpersonalText = (TextView) findViewById(R.id.interpersonalTextView);
		interpersonalText.setOnClickListener(new View.OnClickListener()
			{
				
				public void onClick(View v)
					{
					Intent intent = new Intent(HomeActivity.this, InterpersonalActivity.class);
					startActivity(intent);
					}
			});

		}


	}