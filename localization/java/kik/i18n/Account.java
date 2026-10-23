package kik.i18n;
import android.app.Activity;
import android.content.*;
import org.json.*;

/**
 * The KIK account signed in on this TV. Nobody reaches the home screen without one; a TV whose profiles were paired
 * by code before sign-in existed counts as signed in, so its profiles and history stay as they are.
 */
public final class Account {
 private static SharedPreferences prefs(Context c){return c.getApplicationContext().getSharedPreferences("kik_account",0);}
 public static String token(Context c){return prefs(c).getString("token","");}
 public static String email(Context c){return prefs(c).getString("email","");}
 /** Signed in = some profile syncs with an account; a session without a chosen profile resumes on the sign-in screen. */
 public static boolean signedIn(Context c){for(ProfileStore.Profile p:ProfileStore.list(c))if(CloudSync.linked(c,p.id))return true;return false;}
 static void save(Context c,String email,String token){prefs(c).edit().putString("email",email).putString("token",token).commit();}

 /** Sends a signed-out viewer to the sign-in screen instead of the activity that was opening. */
 public static boolean gate(Activity a){
  if(signedIn(a))return false;
  a.startActivity(new Intent(a,SignInActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));a.finish();return true;
 }

 /** The local profile bound to this account profile, if any. */
 static String localFor(Context c,String remote){for(ProfileStore.Profile p:ProfileStore.list(c))if(remote.equals(CloudSync.remoteId(c,p.id)))return p.id;return null;}

 /**
  * Binds an account profile to a local one: an already bound profile, else the current local profile when it belongs
  * to no account (the history watched before sign-in moves into the account), else a new local profile.
  */
 static String adopt(Context c,JSONObject remote,boolean takeCurrent)throws Exception{
  String id=remote.getString("id"),name=remote.optString("name","Профіль"),avatar=remote.optString("avatar");
  String local=localFor(c,id);
  if(local==null&&takeCurrent){String current=ProfileStore.current(c);if(ProfileStore.nameOf(c,current)!=null&&!CloudSync.linked(c,current)){local=current;ProfileStore.rename(c,local,name);if(Avatars.valid(avatar))ProfileStore.setAvatar(c,local,avatar);}}
  if(local==null){local=ProfileStore.create(c,name,"",avatar).id;ProfileStore.skipTasteSetup(c,local);}
  if(!CloudSync.linked(c,local))CloudSync.bind(c,local,token(c),remote);
  return local;
 }

 /** Signs this TV out: every profile stops syncing, local history stays, and the next start asks to sign in. */
 public static void signOut(Context c){
  String token=token(c);prefs(c).edit().remove("token").commit();
  for(ProfileStore.Profile p:ProfileStore.list(c))if(CloudSync.linked(c,p.id))CloudSync.disconnect(c,p.id);
  if(!token.isEmpty())Ui.bg(()->{try{CloudSync.request("/api/auth/logout",new JSONObject(),token);}catch(Exception ignored){}});
 }
 private Account(){}
}
