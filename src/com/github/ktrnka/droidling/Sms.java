
package com.github.ktrnka.droidling;

import android.net.Uri;

/**
 * Separate the SMS provider info from the rest. Hopefully someday I can move
 * towards having a class do generic SMS access, whether from the normal Android
 * store or Google Voice, etc.
 * 
 * @author keith.trnka
 */
public class Sms {
    public static final String BODY = "body";
    public static final String DATE = "date";
    public static final String ADDRESS = "address";
    public static final String TYPE = "type";

    public static final Uri CONTENT_URI = Uri.parse("content://sms");
    public static final Uri SENT_URI = Uri.parse("content://sms/sent");
    public static final Uri RECEIVED_URI = Uri.parse("content://sms/inbox");
}
