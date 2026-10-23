package kik.i18n;
import android.util.Log;
import android.content.*;import android.os.*;import android.util.AtomicFile;import java.io.*;import java.net.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicBoolean;import org.json.*;

/** Per-profile cloud binding. Credentials stay in private preferences; only watch data crosses devices. */
public final class CloudSync {
 private static final Handler MAIN=new Handler(Looper.getMainLooper());
 private static final ScheduledExecutorService IO=Executors.newSingleThreadScheduledExecutor(r->new Thread(r,"kik-cloud"));
 private static final AtomicBoolean BUSY=new AtomicBoolean();private static ScheduledFuture<?> pending;private static final Object SCHEDULE_LOCK=new Object();
 private static final java.util.concurrent.atomic.AtomicLong REVISION=new java.util.concurrent.atomic.AtomicLong();
 private static volatile String playingProfile;private static volatile String status="Не підключено";
 static SharedPreferences prefs(Context c){return c.getSharedPreferences("kik_cloud",0);}
 public static boolean linked(Context c,String p){return !prefs(c).getString("token_"+p,"").isEmpty();}
 public static long revision(){return REVISION.get();}
 public static String status(Context c,String p){return linked(c,p)?status:"Не підключено";}
 public static String profileName(Context c,String p){return prefs(c).getString("name_"+p,"");}
 public static void playing(String p,boolean on){if(on)playingProfile=p;else if(Objects.equals(playingProfile,p))playingProfile=null;}
 public static void changed(Context context){Context c=context.getApplicationContext();synchronized(SCHEDULE_LOCK){if(pending!=null)pending.cancel(false);pending=IO.schedule(()->syncAll(c),1500,TimeUnit.MILLISECONDS);}}
 public static void now(Context context){Context c=context.getApplicationContext();IO.execute(()->syncAll(c));}
 public static void bind(Context c,String profile,String token,JSONObject remote)throws Exception{
  if(ProfileStore.nameOf(c,profile)==null||!token.matches("[a-f0-9]{64}")||remote.optString("id").isEmpty())throw new IOException("Invalid cloud binding");
  if(!remote.getString("id").equals(prefs(c).getString("remote_"+profile,"")))stateFile(c,profile).delete();
  if(!prefs(c).edit().putString("token_"+profile,token).putString("remote_"+profile,remote.getString("id")).putString("name_"+profile,remote.getString("name")).remove("offset_"+profile).commit())throw new IOException("Cloud preferences");status="Підключено • синхронізація…";now(c);
 }
 /** Asks the account for its ranking of these candidates; the server applies AI only where the administrator enabled it. */
 static JSONObject recommend(Context context,String profile,JSONArray items){
  Context c=context.getApplicationContext();String token=prefs(c).getString("token_"+profile,""),remote=prefs(c).getString("remote_"+profile,"");if(token.isEmpty()||remote.isEmpty())return null;
  try{return request("/api/recommendations",new JSONObject().put("profile_id",remote).put("items",items),token);}catch(Exception e){Log.w("KIK","Cloud recommendations",e);return null;}
 }
 public static void disconnect(Context context,String profile){Context c=context.getApplicationContext();String token=prefs(c).getString("token_"+profile,"");final boolean keep=shared(c,profile,token);prefs(c).edit().remove("token_"+profile).remove("remote_"+profile).remove("name_"+profile).remove("offset_"+profile).apply();synchronized(CloudSync.class){stateFile(c,profile).delete();}status="Не підключено";if(!keep)IO.execute(()->{try{request("/api/auth/logout",new JSONObject(),token);}catch(Exception ignored){}});}
 public static JSONObject request(String path,JSONObject data,String token)throws Exception{
  if(!path.startsWith("/api/")||path.contains(".."))throw new IOException("Cloud path");
  HttpURLConnection connection=(HttpURLConnection)new URL(UpdateConfig.WEB_ORIGIN+path).openConnection();connection.setConnectTimeout(12000);connection.setReadTimeout(15000);connection.setInstanceFollowRedirects(false);connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json; charset=UTF-8");if(token!=null&&!token.isEmpty())connection.setRequestProperty("Authorization","Bearer "+token);
  try{byte[] body=data.toString().getBytes("UTF-8");connection.setFixedLengthStreamingMode(body.length);try(OutputStream out=connection.getOutputStream()){out.write(body);}int code=connection.getResponseCode();if(code==401)throw new SecurityException("Підключіть профіль повторно");if(code==410)throw new IOException("Код прострочений. Отримайте новий.");if(code!=200&&code!=201)throw new Failure(code,errorText(connection));
   try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1){if(out.size()+n>3*1024*1024)throw new IOException("Cloud response limit");out.write(b,0,n);}return new JSONObject(out.toString("UTF-8"));}
  }finally{connection.disconnect();}
 }
 /** A refused request with the server's own Ukrainian explanation (for the sign-in screens). */
 public static final class Failure extends IOException{public final int code;Failure(int code,String message){super(message.isEmpty()?"Cloud HTTP "+code:message);this.code=code;}}
 private static String errorText(HttpURLConnection connection){try(InputStream in=connection.getErrorStream()){if(in==null)return "";byte[] b=new byte[8192];int n,size=0;while(size<b.length&&(n=in.read(b,size,b.length-size))!=-1)size+=n;return new JSONObject(new String(b,0,size,"UTF-8")).optString("error");}catch(Exception e){return "";}}
 /** Whether another profile or the TV's account still signs in with this session. */
 private static boolean shared(Context c,String profile,String token){if(token.isEmpty())return false;if(token.equals(Account.token(c)))return true;for(ProfileStore.Profile p:ProfileStore.list(c))if(!p.id.equals(profile)&&token.equals(prefs(c).getString("token_"+p.id,"")))return true;return false;}
 static String remoteId(Context c,String profile){return prefs(c).getString("remote_"+profile,"");}
 private static void syncAll(Context c){if(!BUSY.compareAndSet(false,true))return;try{for(ProfileStore.Profile p:ProfileStore.list(c))if(linked(c,p.id))try{sync(c,p.id);}catch(SecurityException e){status="Потрібне повторне підключення профілю";}catch(Exception e){status="Без мережі • зміни збережено на TV";}}finally{BUSY.set(false);}}
 private static AtomicFile stateFile(Context c,String p){return new AtomicFile(new File(c.getFilesDir(),"kik_cloud_state_"+p+".json"));}
 private static JSONObject read(AtomicFile file){try{return new JSONObject(Site.read(file.openRead()));}catch(Exception e){return new JSONObject();}}
 private static void write(AtomicFile file,String text)throws IOException{FileOutputStream out=null;try{out=file.startWrite();out.write(text.getBytes("UTF-8"));file.finishWrite(out);}catch(IOException e){if(out!=null)file.failWrite(out);throw e;}}
 private static String canonical(Object o)throws JSONException{if(o==null||o==JSONObject.NULL)return "null";if(o instanceof JSONObject){JSONObject j=(JSONObject)o;List<String> keys=new ArrayList<>();Iterator<String> it=j.keys();while(it.hasNext())keys.add(it.next());Collections.sort(keys);StringBuilder b=new StringBuilder("{");for(String k:keys){if(b.length()>1)b.append(',');b.append(JSONObject.quote(k)).append(':').append(canonical(j.get(k)));}return b.append('}').toString();}if(o instanceof JSONArray){JSONArray a=(JSONArray)o;StringBuilder b=new StringBuilder("[");for(int i=0;i<a.length();i++){if(i>0)b.append(',');b.append(canonical(a.get(i)));}return b.append(']').toString();}return o instanceof String?JSONObject.quote((String)o):o.toString();}
 private static String titleKey(String link){return Progress.norm(link).split("[?#]",2)[0];}
 private static JSONObject data(JSONObject e)throws Exception{JSONObject d=new JSONObject();for(String key:new String[]{"link","title","poster","season","episode","voice","voiceName"})d.put(key,e.opt(key));for(String key:new String[]{"pos","dur","watched"})d.put(key,Math.max(0,e.optLong(key)));d.put("observed",e.optBoolean("observed"));if(!d.isNull("link"))d.put("link",FreeSources.canonical(titleKey(d.getString("link"))));return d;}
 private static void localRecord(JSONObject records,JSONObject fingerprints,String kind,String key,JSONObject value,long timestamp)throws Exception{
  String id=kind+"\n"+key,fingerprint=canonical(value);JSONObject before=records.optJSONObject(id);if(fingerprint.equals(fingerprints.optString(id))&&before!=null)return;
  long old=before==null?0:before.optLong("updated_at");records.put(id,new JSONObject().put("kind",kind).put("key",key).put("data",value==null?JSONObject.NULL:value).put("updated_at",Math.max(timestamp,old+1)).put("dirty",true));fingerprints.put(id,fingerprint);
 }
 private static void collect(Context c,String p,JSONObject root)throws Exception{
  JSONObject records=root.optJSONObject("records"),fingerprints=root.optJSONObject("fingerprints");if(records==null){records=new JSONObject();root.put("records",records);}if(fingerprints==null){fingerprints=new JSONObject();root.put("fingerprints",fingerprints);}
  long offset=prefs(c).getLong("offset_"+p,0),now=System.currentTimeMillis()+offset;
  JSONObject progress=Progress.cloudData(c,p);Iterator<String> keys=progress.keys();while(keys.hasNext()){JSONObject e=progress.optJSONObject(keys.next());if(e==null||e.optString("link").isEmpty())continue;JSONObject d=data(e);String path=titleKey(e.getString("link")),key=path+"|"+e.optString("season","0").replace("null","0")+":"+e.optString("episode","0").replace("null","0");long timestamp=e.optLong("ts",System.currentTimeMillis())+offset;localRecord(records,fingerprints,"progress",key,d,timestamp);
   JSONObject positions=e.optJSONObject("eps");if(positions!=null){Iterator<String> episodes=positions.keys();while(episodes.hasNext()){String ep=episodes.next(),epKey=path+"|"+ep;if(records.has("progress\n"+epKey)||!ep.matches("[0-9]+:[0-9]+"))continue;JSONArray position=positions.optJSONArray(ep);if(position==null)continue;JSONObject old=data(e);String[] parts=ep.split(":");old.put("season","0".equals(parts[0])?JSONObject.NULL:parts[0]).put("episode","0".equals(parts[1])?JSONObject.NULL:parts[1]).put("pos",position.optLong(0)).put("dur",position.optLong(1)).put("watched",0).put("observed",false);localRecord(records,fingerprints,"progress",epKey,old,Math.max(1,timestamp-1));}}
  }
  Set<String> present=new HashSet<>();JSONArray later=Progress.later(c,p);for(int i=0;i<later.length();i++){JSONObject d=later.optJSONObject(i);if(d==null)continue;String key=titleKey(d.optString("link"));present.add("later\n"+key);JSONObject v=new JSONObject();for(String k:new String[]{"link","title","poster","year","genre"})v.put(k,d.opt(k));v.put("link",FreeSources.canonical(key));localRecord(records,fingerprints,"later",key,v,now);}
  JSONObject taste=read(new AtomicFile(new File(c.getFilesDir(),"kik_taste_"+p+".json"))).optJSONObject("ratings");if(taste!=null){Iterator<String> ratings=taste.keys();while(ratings.hasNext()){JSONObject d=taste.optJSONObject(ratings.next());if(d==null)continue;String key=titleKey(d.optString("link"));present.add("rating\n"+key);JSONObject v=new JSONObject();for(String k:new String[]{"link","title","poster","genre"})v.put(k,d.opt(k));v.put("value",d.optInt("value")).put("link",FreeSources.canonical(key));localRecord(records,fingerprints,"rating",key,v,d.optLong("time",System.currentTimeMillis())+offset);}}
  List<String> removed=new ArrayList<>();Iterator<String> ids=fingerprints.keys();while(ids.hasNext()){String id=ids.next();if((id.startsWith("later\n")||id.startsWith("rating\n"))&&!present.contains(id)){JSONObject old=records.optJSONObject(id);if(old!=null&&!old.isNull("data"))removed.add(id);}}
  for(String id:removed){String[] parts=id.split("\n",2);localRecord(records,fingerprints,parts[0],parts[1],null,now);}
 }
 private static void sync(Context c,String p)throws Exception{
  final String token=prefs(c).getString("token_"+p,""),remote=prefs(c).getString("remote_"+p,"");if(token.isEmpty()||remote.isEmpty())return;status="Синхронізація…";
  if(!prefs(c).contains("offset_"+p)){JSONObject first=request("/api/sync",new JSONObject().put("profile_id",remote).put("source_schema",2).put("records",new JSONArray()),token);prefs(c).edit().putLong("offset_"+p,first.getLong("server_time")-System.currentTimeMillis()).commit();}
  JSONArray dirty=new JSONArray();synchronized(CloudSync.class){JSONObject root=read(stateFile(c,p));collect(c,p,root);JSONObject records=root.getJSONObject("records");Iterator<String> it=records.keys();while(it.hasNext()){JSONObject r=records.getJSONObject(it.next());if(r.optBoolean("dirty"))dirty.put(r);}write(stateFile(c,p),root.toString());}
  JSONObject response=null;for(int at=0;at<Math.max(1,dirty.length());at+=400){JSONArray batch=new JSONArray();for(int i=at;i<Math.min(at+400,dirty.length());i++)batch.put(dirty.get(i));response=request("/api/sync",new JSONObject().put("profile_id",remote).put("source_schema",2).put("records",batch),token);}
  if(!token.equals(prefs(c).getString("token_"+p,"")))return;
  synchronized(CloudSync.class){if(!token.equals(prefs(c).getString("token_"+p,"")))return;JSONObject root=read(stateFile(c,p));collect(c,p,root);JSONObject records=root.getJSONObject("records"),fingerprints=root.getJSONObject("fingerprints");JSONArray incoming=response.getJSONArray("records");
   for(int i=0;i<incoming.length();i++){JSONObject r=incoming.getJSONObject(i);String id=r.getString("kind")+"\n"+r.getString("key");JSONObject old=records.optJSONObject(id);long ts=r.getLong("updated_at");if(old==null||ts>old.optLong("updated_at")||ts==old.optLong("updated_at")&&canonical(r.opt("data")).compareTo(canonical(old.opt("data")))>=0){r.put("dirty",false);records.put(id,r);}}
   if(!Objects.equals(playingProfile,p)){apply(c,p,records);JSONObject fresh=new JSONObject().put("records",records).put("fingerprints",new JSONObject());refreshFingerprints(c,p,fresh);root.put("fingerprints",fresh.getJSONObject("fingerprints"));}
   write(stateFile(c,p),root.toString());
  }status="Історію та прогрес синхронізовано";
 }
 private static void refreshFingerprints(Context c,String p,JSONObject root)throws Exception{
  // Materialize local values using a scratch record map; retain the server timestamps in the real map.
  JSONObject scratch=new JSONObject().put("records",new JSONObject()).put("fingerprints",new JSONObject());collect(c,p,scratch);JSONObject fps=scratch.getJSONObject("fingerprints");JSONObject records=root.getJSONObject("records");Iterator<String> it=records.keys();while(it.hasNext()){String id=it.next();JSONObject r=records.getJSONObject(id);if(r.isNull("data"))fps.put(id,"null");}root.put("fingerprints",fps);
 }
 private static void apply(Context c,String p,JSONObject records)throws Exception{
  JSONObject progress=new JSONObject(),ratings=new JSONObject();JSONArray later=new JSONArray();Map<String,JSONObject> latest=new HashMap<>(),eps=new HashMap<>();long offset=prefs(c).getLong("offset_"+p,0);
  List<JSONObject> list=new ArrayList<>();Iterator<String> it=records.keys();while(it.hasNext())list.add(records.getJSONObject(it.next()));Collections.sort(list,(a,b)->Long.compare(b.optLong("updated_at"),a.optLong("updated_at")));
  for(JSONObject r:list){if(r.isNull("data"))continue;JSONObject d=new JSONObject(r.getJSONObject("data").toString());String kind=r.getString("kind"),key=titleKey(d.optString("link"));if("progress".equals(kind)){if(!latest.containsKey(key)){d.put("ts",r.getLong("updated_at")-offset);latest.put(key,d);}JSONObject positions=eps.get(key);if(positions==null){positions=new JSONObject();eps.put(key,positions);}String s=d.optString("season","0").replace("null","0"),e=d.optString("episode","0").replace("null","0");positions.put(s+":"+e,new JSONArray().put(d.optLong("pos")).put(d.optLong("dur")));}else if("later".equals(kind))later.put(d);else if("rating".equals(kind)){d.put("time",r.getLong("updated_at")-offset);ratings.put(RecommendationRanker.key(d.getString("link")),d);}}
  for(String key:latest.keySet()){JSONObject d=latest.get(key);d.put("eps",eps.get(key));progress.put(key,d);}
  boolean changed=!canonical(Progress.cloudData(c,p)).equals(canonical(progress))||!canonical(Progress.later(c,p)).equals(canonical(later));
  if(changed)Progress.cloudApply(c,p,progress,later);
  synchronized(RatingStore.class){AtomicFile tasteFile=new AtomicFile(new File(c.getFilesDir(),"kik_taste_"+p+".json"));JSONObject taste=read(tasteFile);if(!canonical(taste.optJSONObject("ratings")).equals(canonical(ratings))){taste.put("ratings",ratings);write(tasteFile,taste.toString());changed=true;}}
  if(changed)REVISION.incrementAndGet();
 }
 public static void removedHistory(Context c,String profile,String link){if(!linked(c,profile))return;IO.execute(()->{synchronized(CloudSync.class){try{JSONObject root=read(stateFile(c,profile)),records=root.optJSONObject("records"),fps=root.optJSONObject("fingerprints");if(records==null||fps==null)return;List<String> keys=new ArrayList<>();Iterator<String> it=records.keys();while(it.hasNext()){String id=it.next();if(id.startsWith("progress\n"+titleKey(link)+"|"))keys.add(id.substring(9));}for(String key:keys)localRecord(records,fps,"progress",key,null,System.currentTimeMillis()+prefs(c).getLong("offset_"+profile,0));write(stateFile(c,profile),root.toString());changed(c);}catch(Exception ignored){}}});}
 private CloudSync(){}
}
