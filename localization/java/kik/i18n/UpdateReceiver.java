package kik.i18n;
import android.content.*;
/** Explicit installer callbacks; system-only replacement broadcast. */
public final class UpdateReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent i){AppUpdater.result(c.getApplicationContext(),i);}
}
