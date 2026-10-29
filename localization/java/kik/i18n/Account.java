package kik.i18n;
import android.app.Activity;
import android.content.*;
import org.json.*;

/**
 * The KIK accounts signed in on this TV, each with its own sign-in: a second viewer adds their own email next to the
 * first, and «Хто дивиться?» shows the profiles of all of them. Nobody reaches the home screen without one; a TV whose
 * profiles were paired by code before sign-in existed counts as signed in, so its profiles and history stay as they are.
 */
public final class Account {
 private static SharedPreferences prefs(Context c){return c.getApplicationContext().getSharedPreferences("kik_account",0);}
 /** Every account on this TV as {email, token}, the first signed in first. Before 1.27 the TV kept one in "email" and "token". */
 static JSONArray accounts(Context c){
  String saved=prefs(c).getString("accounts",null);if(saved!=null)try{return new JSONArray(saved);}catch(JSONException ignored){}
  JSONArray a=new JSONArray();String token=prefs(c).getString("token","");
  if(!token.isEmpty())try{a.put(new JSONObject().put("email",prefs(c).getString("email","")).put("token",token));}catch(JSONException ignored){}
  return a;
 }
 private static void store(Context c,JSONArray a){prefs(c).edit().putString("accounts",a.toString()).remove("token").remove("email").commit();}
 /** The current profile's account, else the first one: its sign-in pays for the titles this viewer opens. */
 public static String token(Context c){String own=CloudSync.token(c,ProfileStore.current(c));if(!own.isEmpty())return own;JSONArray a=accounts(c);return a.length()>0?a.optJSONObject(0).optString("token"):"";}
 public static String email(Context c){return emailOf(c,token(c));}
 static String emailOf(Context c,String token){JSONArray a=accounts(c);for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&o.optString("token").equals(token))return o.optString("email");}return "";}
 static boolean has(Context c,String email){JSONArray a=accounts(c);for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&o.optString("email").equalsIgnoreCase(email))return true;}return false;}
 private static boolean accountsWith(Context c,String token){JSONArray a=accounts(c);for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&o.optString("token").equals(token))return true;}return false;}
 /** Signed in = some profile syncs with an account; a session without a chosen profile resumes on the sign-in screen. */
 public static boolean signedIn(Context c){for(ProfileStore.Profile p:ProfileStore.list(c))if(CloudSync.linked(c,p.id))return true;return false;}
 /** Adds an account, or replaces the sign-in of the same email. */
 static void save(Context c,String email,String token){save(c,email,token,false);}
 /** A temporary one («Тимчасовий вхід») leaves the TV by itself: see {@link TemporarySignIn}. */
 static void save(Context c,String email,String token,boolean temporary){
  JSONArray a=accounts(c),out=new JSONArray();boolean placed=false;
  try{JSONObject added=new JSONObject().put("email",email).put("token",token);if(temporary)added.put("temporary",true);
   for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;if(o.optString("email").equalsIgnoreCase(email)){if(!placed)out.put(added);placed=true;}else out.put(o);}
   if(!placed)out.put(added);}catch(JSONException ignored){}
  store(c,out);
 }
 /** The sign-ins added with «Тимчасовий вхід». */
 static java.util.List<String> temporaryTokens(Context c){java.util.List<String> out=new java.util.ArrayList<String>();JSONArray a=accounts(c);for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&o.optBoolean("temporary")&&!o.optString("token").isEmpty())out.add(o.optString("token"));}return out;}
 static boolean temporary(Context c,String token){return !token.isEmpty()&&temporaryTokens(c).contains(token);}
 /** The name the website shows for this TV's sign-ins, such as «XGIMI XK10T». */
 static String deviceName(){String maker=android.os.Build.MANUFACTURER==null?"":android.os.Build.MANUFACTURER.trim(),model=android.os.Build.MODEL==null?"":android.os.Build.MODEL.trim();
  String name=model.toLowerCase(java.util.Locale.ROOT).startsWith(maker.toLowerCase(java.util.Locale.ROOT))?model:(maker+" "+model).trim();return name.isEmpty()?"Android TV":name;}

 /** Sends a signed-out viewer to the sign-in screen instead of the activity that was opening. */
 public static boolean gate(Activity a){
  if(signedIn(a))return false;
  a.startActivity(new Intent(a,SignInActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));a.finish();return true;
 }

 /** The local profile bound to this account profile, if any. */
 static String localFor(Context c,String remote){for(ProfileStore.Profile p:ProfileStore.list(c))if(remote.equals(CloudSync.remoteId(c,p.id)))return p.id;
  for(ProfileStore.Profile p:ProfileStore.list(c))if(!CloudSync.linked(c,p.id)&&remote.equals(CloudSync.pastRemoteId(c,p.id)))return p.id;return null;}

 /**
  * Binds an account profile to a local one: an already bound profile, else the current local profile when it belongs
  * to no account and never did (the history watched before sign-in moves into the account), else a new local profile.
  * A profile another account used before stays out of this one, so one viewer's history never lands in someone else's.
  */
 static String adopt(Context c,JSONObject remote,boolean takeCurrent,String token)throws Exception{
  String id=remote.getString("id"),name=remote.optString("name","Профіль"),avatar=remote.optString("avatar");
  String local=localFor(c,id);
  if(local==null&&takeCurrent){String current=ProfileStore.current(c);if(ProfileStore.nameOf(c,current)!=null&&!CloudSync.linked(c,current)&&CloudSync.pastRemoteId(c,current).isEmpty()){local=current;ProfileStore.rename(c,local,name);if(Avatars.valid(avatar))ProfileStore.setAvatar(c,local,avatar);}}
  if(local==null){local=ProfileStore.create(c,name,"",avatar).id;ProfileStore.skipTasteSetup(c,local);}
  if(!CloudSync.linked(c,local))CloudSync.bind(c,local,token,remote);
  return local;
 }

 /** Signs this TV out of every account: every profile stops syncing, local history stays, and the next start asks to sign in. */
 public static void signOut(Context c){
  JSONArray a=accounts(c);store(c,new JSONArray());
  for(ProfileStore.Profile p:ProfileStore.list(c))if(CloudSync.linked(c,p.id))CloudSync.disconnect(c,p.id);
  for(int i=0;i<a.length();i++){final String token=a.optJSONObject(i)==null?"":a.optJSONObject(i).optString("token");if(!token.isEmpty())logout(token);}
 }
 private static void logout(String token){Ui.bg(()->{try{CloudSync.request("/api/auth/logout",new JSONObject(),token);}catch(Exception ignored){}});}

 /** «Вийти» for one account, saying what happens to its profiles. */
 public static void confirmLeave(Activity a,String token){
  String email=emailOf(a,token);boolean last=!accountsWith(a,token)||accounts(a).length()<=1;
  Ui.dialog(a).setTitle("Вийти з "+(email.isEmpty()?"облікового запису":email)+"?")
   .setMessage(last?"Історія залишиться на TV. Щоб дивитися далі, увійдіть знову поштою або з телефону.":"Профілі цього облікового запису зникнуть з TV, їхня історія збережеться в обліковому записі. Інші облікові записи залишаться.")
   .setNegativeButton("Скасувати",null).setPositiveButton("Вийти",(d,w)->leave(a,token)).show();
 }
 /**
  * Signs one account out. The last one signs the TV out as before; with others left, its profiles leave this TV once
  * their latest changes reached the account, and a viewer who was watching one of them picks again.
  */
 static void leave(Activity a,String token){
  Context c=a.getApplicationContext();
  if(!accountsWith(c,token)||accounts(c).length()<=1){signOut(c);a.startActivity(new Intent(a,SignInActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));return;}
  final boolean watching=token.equals(CloudSync.token(c,ProfileStore.current(c)));Ui.toast(a,"Вихід з облікового запису…");
  CloudSync.flush(c,token,()->{drop(c,token);if(watching)reopen(c);else android.widget.Toast.makeText(c,"Обліковий запис вийшов з TV",android.widget.Toast.LENGTH_SHORT).show();});
 }
 /** Removes the account and its local profiles; the current profile moves to one that still syncs. */
 private static void drop(Context c,String token){
  JSONArray a=accounts(c),kept=new JSONArray();for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&!token.equals(o.optString("token")))kept.put(o);}store(c,kept);
  String current=ProfileStore.current(c);
  for(ProfileStore.Profile p:ProfileStore.list(c))if(token.equals(CloudSync.token(c,p.id)))ProfileStore.delete(c,p.id);
  if(ProfileStore.nameOf(c,current)==null)for(ProfileStore.Profile p:ProfileStore.list(c))if(CloudSync.linked(c,p.id)&&ProfileStore.use(c,p.id))break;
  logout(token);
 }
 /**
  * A temporary sign-in ran out: the account leaves as with «Вийти», without asking. Its latest changes go up first,
  * then its profiles leave this TV even when it was the last account: a visitor's history stays only in their account.
  */
 static void expire(Context context,final String token){
  final Context c=context.getApplicationContext();if(!accountsWith(c,token))return;final String email=emailOf(c,token);
  CloudSync.flush(c,token,()->{if(!accountsWith(c,token))return;boolean watching=token.equals(CloudSync.token(c,ProfileStore.current(c)));drop(c,token);TemporarySignIn.ended(c,email,watching);});
 }
 /** The profile being watched has left: the home screen opens on the new current profile, with «Хто дивиться?» when there is a choice. */
 static void reopen(Context c){
  ProfileStore.rebind(c);Intent home=new Intent(c,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
  if(ProfileStore.list(c).size()>1)c.startActivities(new Intent[]{home,new Intent(c,ProfilesActivity.class)});else c.startActivity(home);
 }
 /**
  * The server no longer accepts this sign-in (access revoked, password reset, signed out on the website). With other
  * accounts left only this one leaves; otherwise the TV signs out and asks again.
  */
 static void revoked(Context context,String token){Context c=context.getApplicationContext();new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{if(!signedIn(c))return;
  boolean watching=token.equals(CloudSync.token(c,ProfileStore.current(c)));
  if(accountsWith(c,token)&&accounts(c).length()>1){drop(c,token);if(watching)reopen(c);return;}
  if(!accountsWith(c,token)){for(ProfileStore.Profile p:ProfileStore.list(c))if(token.equals(CloudSync.token(c,p.id)))CloudSync.disconnect(c,p.id);if(signedIn(c)){if(watching)reopen(c);return;}}
  signOut(c);c.startActivity(new Intent(c,SignInActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));});}
 private Account(){}
}
