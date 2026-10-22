package kik.i18n;
import android.app.*;
import android.content.*;
import android.util.Log;
import java.io.File;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Local viewer profiles. Each profile owns its playback progress, which is also the watch history. */
public final class ProfileStore {
 public static final String FIRST="default";
 private static String loaded;
 private static boolean launchChecked;
 public static final class Profile{public String id,name,salt,hash,avatar;public boolean tasteSetup=true;}

 private static SharedPreferences prefs(Context c){return c.getApplicationContext().getSharedPreferences("kik_profiles",0);}

 public static List<Profile> list(Context c){
  List<Profile> out=new ArrayList<Profile>();
  try{JSONArray a=new JSONArray(prefs(c).getString("list","[]"));
   for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);Profile p=new Profile();p.id=o.getString("id");p.name=o.getString("name");p.salt=o.optString("salt");p.hash=o.optString("hash");p.avatar=o.optString("avatar",null);p.tasteSetup=o.optBoolean("taste_setup",true);if(!Avatars.valid(p.avatar))p.avatar=Avatars.fallback(p.id);out.add(p);}
  }catch(JSONException e){Log.w("KIK","Profiles",e);}
  return out;
 }
 private static boolean save(Context c,List<Profile> list){
  JSONArray a=new JSONArray();
  try{for(Profile p:list)a.put(new JSONObject().put("id",p.id).put("name",p.name).put("salt",p.salt).put("hash",p.hash).put("avatar",Avatars.valid(p.avatar)?p.avatar:Avatars.fallback(p.id)).put("taste_setup",p.tasteSetup));}catch(JSONException ignored){}
  return prefs(c).edit().putString("list",a.toString()).commit();
 }
 public static String current(Context c){return prefs(c).getString("current",FIRST);}
 public static String loaded(){return loaded;}

 /** Progress directory used by the film cache; the first profile keeps the pre-profile history. */
 public static File progressDir(Context c){
  loaded=current(c);
  File dir=dirOf(c,loaded);
  dir.mkdirs();
  return dir;
 }
 public static File dirOf(Context c,String id){return FIRST.equals(id)?new File(c.getFilesDir(),"playback_cache"):new File(c.getFilesDir(),"profiles/"+id+"/playback_cache");}

 public static Profile create(Context c,String name,String password){return create(c,name,password,null);}
 public static Profile create(Context c,String name,String password,String avatar){
  List<Profile> all=list(c);
  Profile p=new Profile();p.id=all.isEmpty()?FIRST:UUID.randomUUID().toString().substring(0,8);p.name=name.trim();
  p.salt=UUID.randomUUID().toString();p.hash=hash(p.salt,password);p.avatar=Avatars.valid(avatar)?avatar:Avatars.idAt(all.size());
  p.tasteSetup=false;all.add(p);if(!save(c,all))throw new IllegalStateException("Profile could not be saved");return p;
 }
 public static boolean needsTasteSetup(Context c,String id){for(Profile p:list(c))if(p.id.equals(id))return !p.tasteSetup;return false;}
 public static synchronized boolean completeTasteSetup(Context c,String id){if(!id.equals(current(c)))return false;List<Profile> all=list(c);for(Profile p:all)if(p.id.equals(id)){p.tasteSetup=true;return save(c,all);}return false;}
 public static boolean checkPassword(Profile p,String password){return p.hash!=null&&p.hash.equals(hash(p.salt,password));}
 public static void rename(Context c,String id,String name){List<Profile> all=list(c);for(Profile p:all)if(p.id.equals(id))p.name=name.trim();save(c,all);}
 public static void changePassword(Context c,String id,String password){List<Profile> all=list(c);for(Profile p:all)if(p.id.equals(id)){p.salt=UUID.randomUUID().toString();p.hash=hash(p.salt,password);}save(c,all);}
 public static void delete(Context c,String id){
  if(CloudSync.linked(c,id))CloudSync.disconnect(c,id);
  List<Profile> all=list(c);
  for(Iterator<Profile> it=all.iterator();it.hasNext();)if(it.next().id.equals(id))it.remove();
  save(c,all);
  File dir=FIRST.equals(id)?new File(c.getFilesDir(),"playback_cache"):new File(c.getFilesDir(),"profiles/"+id);
  removeTree(dir);
  new android.util.AtomicFile(new File(c.getFilesDir(),"kik_progress_"+id+".json")).delete();
  new android.util.AtomicFile(new File(c.getFilesDir(),"kik_later_"+id+".json")).delete();
  new android.util.AtomicFile(new File(c.getFilesDir(),"kik_voices_"+id+".json")).delete();
  Recommendations.delete(c,id);
  c.getSharedPreferences("kik_search_"+id,0).edit().clear().commit();if(android.os.Build.VERSION.SDK_INT>=24)c.deleteSharedPreferences("kik_search_"+id);
  RatingStore.delete(c,id);
  if(id.equals(current(c)))prefs(c).edit().putString("current",all.isEmpty()?FIRST:all.get(0).id).commit();
 }
 public static String nameOf(Context c,String id){for(Profile p:list(c))if(p.id.equals(id))return p.name;return null;}
 public static String avatarOf(Context c,String id){for(Profile p:list(c))if(p.id.equals(id))return p.avatar;return Avatars.fallback(id);}
 public static boolean setAvatar(Context c,String id,String avatar){if(!Avatars.valid(avatar))return false;List<Profile> all=list(c);for(Profile p:all)if(p.id.equals(id)){p.avatar=avatar;return save(c,all);}return false;}

 /** Switches profile: rebinds the progress storage in place, then reopens the home screen. */
 public static void choose(Activity a,String id){
  prefs(a).edit().putString("current",id).commit();
  picked=true;
  if(id.equals(loaded)&&!list(a).isEmpty()&&!needsTasteSetup(a,id)){a.finish();return;}
  try{
   // Obfuscated names from RezkaTV 1.2.0: i72 is the film/progress cache, gu7.r(Context) creates it.
   java.lang.reflect.Field h=Class.forName("i72").getDeclaredField("h");h.setAccessible(true);h.set(null,null);
   Class.forName("gu7").getMethod("r",Context.class).invoke(null,a.getApplicationContext());
  }catch(Exception e){
   Log.e("KIK","Profile switch",e);
   android.widget.Toast.makeText(a,"Перезапустіть застосунок, щоб відкрити профіль",android.widget.Toast.LENGTH_LONG).show();
   a.finishAffinity();return;
  }
  a.startActivity(new Intent(a,needsTasteSetup(a,id)?TasteActivity.class:HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
 }

 /** "Who is watching" once per app start, when there is a choice to make. */
 public static boolean picked;
 public static void askOnce(Activity home){
  List<Profile> profiles=list(home);
  if(picked&&!profiles.isEmpty()){if(needsTasteSetup(home,current(home)))TasteActivity.open(home,true);return;}picked=true;
  if(profiles.size()!=1)home.startActivity(new Intent(home,ProfilesActivity.class));
  else if(needsTasteSetup(home,current(home)))TasteActivity.open(home,true);
 }
 /** Kept for the catalog activity hook; the home screen is now the launcher entry. */
 public static void onLaunch(Activity a){}

 private static String hash(String salt,String password){
  try{MessageDigest d=MessageDigest.getInstance("SHA-256");byte[] b=d.digest((salt+":"+password).getBytes("UTF-8"));StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format("%02x",x));return s.toString();}
  catch(Exception e){throw new RuntimeException(e);}
 }
 private static void removeTree(File f){if(f.isDirectory()){File[] a=f.listFiles();if(a!=null)for(File c:a)removeTree(c);}f.delete();}
}
