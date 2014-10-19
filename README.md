droidling
=========

SMS Linguistics (Android app)  
https://play.google.com/store/apps/details?id=com.github.ktrnka.droidling

Setup
-Eclipse/ADT from Google Developer website
-In SDK manager make sure that you have API 7 installed
-Clone this project and import droidling into workspace (import as existing Eclipse project)
-Clone https://github.com/ktrnka/cardsui-for-android abd import cardsuilib into workspace (import as existing Eclipse project). Make sure it's set as a library project and that droidling is pointing to it in the build path.
-Download ActionBarSherlock and import existing Android code in the menus. Make sure it's set as library project and point droidling to it.
-You may need to install API 14 (4.0) for ActionBarSherlock

Before ActionBarSherlock is setup, you'll get errors about missing classes from LruCache and other classes in Android support v4. ABS includes that lib so it can't be included as part of droidling.

Dependencies:  
-AChartEngine (included)  
-ActionBarSherlock (not included): http://actionbarsherlock.com/  
-CardsUILib fork (not included): https://github.com/ktrnka/cardsui-for-android  

Todo
-Figure out how to include CardsUILib as a jar. I tried once and issue is that it didn't see the resources from the library.