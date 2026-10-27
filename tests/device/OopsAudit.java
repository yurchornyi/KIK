package kik.qa;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import java.io.*;import java.lang.reflect.*;import java.util.*;import java.util.concurrent.Callable;import java.util.function.BooleanSupplier;import org.json.*;import kik.i18n.*;
/**
 * KIK's «Упс» screens on the projector: a title that really fails to load, then each cause on the title page, the catalogue,
 * Home and the player, with screenshots and frame timings while the mark breathes. A disposable profile keeps the owner's
 * history unchanged; the player gets a made-up title through KIK's service, never a burst at HDRezka.
 */
public final class OopsAudit extends Instrumentation {
 Context c;String original,list,qa;int checks;JSONObject result=new JSONObject();File dir;
 static final String LINK="https://hdrezka.sh/films/drama/0-kik-oops-audit.html";
 /** KIK's service answers 400 for a UASerials page that does not exist: a real failure without asking HDRezka. */
 static final String MISSING="https://uaserials.com/999999999-kik-oops-audit.html";
 Object get(Object o,String n)throws Exception{Class<?> k=o instanceof Class?(Class<?>)o:o.getClass();Field f=null;for(;f==null&&k!=null;k=k.getSuperclass())try{f=k.getDeclaredField(n);}catch(NoSuchFieldException ignored){}f.setAccessible(true);return f.get(o instanceof Class?null:o);}
 void set(Object o,String n,Object v)throws Exception{Field f=(o instanceof Class?(Class<?>)o:o.getClass()).getDeclaredField(n);f.setAccessible(true);f.set(o instanceof Class?null:o,v);}
 Object call(Object o,String n,Class<?>[] types,Object... args)throws Exception{Method m=o.getClass().getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(o,args);}
 <T>T ui(Callable<T> f){Object[] v=new Object[2];runOnMainSync(()->{try{v[0]=f.call();}catch(Throwable e){v[1]=e;}});if(v[1]!=null)throw new RuntimeException((Throwable)v[1]);return (T)v[0];}
 boolean await(BooleanSupplier f,int ms){long end=SystemClock.elapsedRealtime()+ms;while(SystemClock.elapsedRealtime()<end){try{if(f.getAsBoolean())return true;}catch(Throwable ignored){}SystemClock.sleep(200);}return false;}
 void check(String n,boolean b){Bundle out=new Bundle();out.putString("stream",(b?"PASS ":"FAIL ")+n+"\n");sendStatus(0,out);try{result.put(n,b);}catch(JSONException ignored){}if(!b)throw new AssertionError(n);checks++;}
 String shell(String cmd){StringBuilder b=new StringBuilder();try(ParcelFileDescriptor out=getUiAutomation().executeShellCommand(cmd);BufferedReader in=new BufferedReader(new InputStreamReader(new ParcelFileDescriptor.AutoCloseInputStream(out)))){String line;while((line=in.readLine())!=null)b.append(line).append('\n');}catch(Exception e){throw new RuntimeException(cmd,e);}return b.toString();}
 void shot(String name){SystemClock.sleep(900);File f=new File(dir,name+".png");try(FileOutputStream out=new FileOutputStream(f)){getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}catch(Exception e){android.util.Log.w("KIK","Oops audit shot",e);}f.setReadable(true,false);}
 /** dumpsys gfxinfo over a few seconds of the screen standing still with its animation: frames, janky share, percentiles. */
 JSONObject frames(String name,int ms)throws Exception{
  shell("dumpsys gfxinfo kik.app reset");SystemClock.sleep(ms);String out=shell("dumpsys gfxinfo kik.app");JSONObject r=new JSONObject();
  for(String key:new String[]{"Total frames rendered","Janky frames","50th percentile","90th percentile","95th percentile","99th percentile","Number Slow UI thread","Number Slow bitmap uploads","Number Slow issue draw commands","Number Frame deadline missed","50th gpu percentile","90th gpu percentile","95th gpu percentile"}){
   java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?m)^\\s*"+java.util.regex.Pattern.quote(key)+": ([^\\n]+)").matcher(out);if(m.find())r.put(key,m.group(1).trim());}
  result.put("frames_"+name,r);try(FileWriter w=new FileWriter(new File(dir,"gfxinfo-"+name+".txt"))){w.write(out);}return r;
 }
 Oops oops(Activity a){try{return (Oops)get(a,"oops");}catch(Exception e){return null;}}
 String kind(Oops o)throws Exception{return (String)get(o,"kind");}
 boolean focused(Oops o,String label){View f=o.findFocus();return f instanceof Button&&label.contentEquals(((Button)f).getText());}
 String status(Oops o)throws Exception{TextView t=(TextView)get(o,"status");return t==null?"":t.getText().toString();}

 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){boolean passed=false;String error="";c=getTargetContext();dir=new File(c.getExternalFilesDir(null),"oops-audit");dir.mkdirs();
  original=ProfileStore.current(c);list=c.getSharedPreferences("kik_profiles",0).getString("list","[]");Activity film=null,browse=null,home=null;KikPlayerActivity player=null;
  try{
   qa=ProfileStore.create(c,"QA Oops","Oops12345").id;c.getSharedPreferences("kik_profiles",0).edit().putString("current",qa).commit();ProfileStore.picked=true;ProfileStore.completeTasteSetup(c,qa);
   // 1. A title that does not exist: the real load fails and the splash replaces the page, the button holds the remote.
   final Activity f=film=startActivitySync(new Intent(c,FilmActivity.class).putExtra("link",MISSING).putExtra("title","Перевірка Упс").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   check("film_splash_after_real_failure",await(()->ui(()->oops(f)!=null&&oops(f).isShown()),45000));
   result.put("film_real_kind",ui(()->kind(oops(f))));
   check("film_button_focused",await(()->ui(()->focused(oops(f),"Спробувати ще")||focused(oops(f),"Спробувати зараз")),3000));
   check("film_page_hidden_under_splash",ui(()->((View)get(f,"page")).getVisibility()==View.INVISIBLE&&((View)get(f,"backdrop")).getVisibility()==View.INVISIBLE));
   shot("film-real");
   // 2. Each cause, as the load would report it. A temporary one counts down from 10 s.
   ui(()->{call(f,"fail",new Class[]{Throwable.class},new IOException(new OopsPolicy.ServiceError(503,true,"Джерело фільмів зараз не відповідає.")));return null;});
   check("film_source_kind",ui(()->OopsPolicy.SOURCE.equals(kind(oops(f)))));
   check("film_source_focus",await(()->ui(()->focused(oops(f),"Спробувати зараз")),3000));
   String first=ui(()->status(oops(f)));check("film_countdown_starts",first.matches("Спробуємо ще раз через \\d+ с"));
   shot("film-source");
   result.put("film_source_frames",frames("film_source",4000));
   String later=ui(()->status(oops(f)));check("film_countdown_runs",!later.equals(first)&&later.startsWith("Спробуємо ще раз через"));
   // OK on the remote tries at once: the splash leaves and the page loads again.
   final Oops pressed=ui(()->oops(f));sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
   check("film_ok_retries",await(()->ui(()->oops(f)!=pressed),3000));
   check("film_retry_fails_again_to_splash",await(()->ui(()->oops(f)!=null&&oops(f).isShown()),45000));
   for(Object[] k:new Object[][]{{"offline",new java.net.UnknownHostException("kiktv.pages.dev"),OopsPolicy.OFFLINE},{"slow",new java.net.SocketTimeoutException("read timed out"),OopsPolicy.SLOW},{"server",new OopsPolicy.ServiceError(502,false,"Bad gateway"),OopsPolicy.SERVER},{"missing",new OopsPolicy.ServiceError(404,false,"Not found"),OopsPolicy.MISSING}}){
    ui(()->{call(f,"fail",new Class[]{Throwable.class},k[1]);return null;});check("film_"+k[0],ui(()->k[2].equals(kind(oops(f)))));shot("film-"+k[0]);}
   ui(()->{f.finish();return null;});film=null;
   // 3. The catalogue's first page: the splash under the heading, the header still reachable.
   final Activity br=browse=startActivitySync(new Intent(c,BrowseActivity.class).putExtra("mode","catalog").putExtra("category","films").putExtra("title","Фільми").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   // After the real first page, so its answer cannot take the splash away again.
   await(()->ui(()->!(Boolean)get(br,"loading")),30000);
   ui(()->{set(br,"failed",true);call(br,"fail",new Class[]{Throwable.class,String.class},new OopsPolicy.ServiceError(503,true,"busy"),null);return null;});
   check("catalogue_splash",ui(()->oops(br)!=null&&oops(br).isShown()&&OopsPolicy.SOURCE.equals(kind(oops(br)))));
   check("catalogue_focus",await(()->ui(()->focused(oops(br),"Спробувати зараз")),3000));
   shot("catalogue-source");
   sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);check("catalogue_header_reachable",ui(()->oops(br).findFocus()==null&&br.getCurrentFocus()!=null));
   ui(()->{br.finish();return null;});browse=null;
   // 4. Home with nothing to show: the splash under the top bar.
   final Activity h=home=startActivitySync(new Intent(c,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   // A TV that is not signed in opens the sign-in screen instead of Home: nothing to check there.
   if(ui(()->get(h,"heroInfo")==null))result.put("home","skipped: not signed in");
   else{
    await(()->ui(()->(Boolean)get(h,"loadedOnce")),30000);SystemClock.sleep(1000);
    ui(()->{call(h,"fail",new Class[]{Throwable.class},new java.net.SocketTimeoutException("read timed out"));return null;});
    check("home_splash",ui(()->oops(h)!=null&&oops(h).isShown()&&OopsPolicy.SLOW.equals(kind(oops(h)))));
    check("home_focus",await(()->ui(()->focused(oops(h),"Спробувати зараз")),3000));
    shot("home-slow");
    ui(()->{call(h,"closeOops",new Class[]{});return null;});}
   ui(()->{h.finish();return null;});home=null;
   // 5. The player: a made-up title through KIK's service; its answer is set aside, then each player failure is shown.
   Site.Film fake=new Site.Film();fake.link=LINK;fake.id="0";fake.title="Перевірка Упс";fake.origin="kik-service";
   Site.Voice v1=new Site.Voice();v1.id="1";v1.name="Українська";Site.Voice v2=new Site.Voice();v2.id="2";v2.name="Оригінал (Original)";fake.voices.add(v1);fake.voices.add(v2);
   set(KikPlayerActivity.class,"openingFilm",fake);set(KikPlayerActivity.class,"openingVoice",v1);
   final KikPlayerActivity p=player=(KikPlayerActivity)startActivitySync(new Intent(c,KikPlayerActivity.class).putExtra("title","Перевірка Упс").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   ui(()->{set(p,"generation",(Integer)get(p,"generation")+1);return null;});
   check("player_intro_over",await(()->ui(()->get(p,"intro")==null),10000));SystemClock.sleep(800);
   // Baseline: KIK's own waiting frame, which already breathes and has passed on this projector.
   result.put("player_wait_frames",frames("player_wait",4000));
   ui(()->{call(p,"failure",new Class[]{String.class,boolean.class},OopsPolicy.SOURCE,true);return null;});
   check("player_splash",ui(()->oops(p)!=null&&oops(p).isShown()));
   check("player_wait_frame_hidden",ui(()->((View)get(p,"wait")).getVisibility()==View.GONE));
   check("player_focus",await(()->ui(()->focused(oops(p),"Спробувати зараз")),3000));
   shot("player-source");
   result.put("player_source_frames",frames("player_source",4000));
   ui(()->{call(p,"failure",new Class[]{String.class,boolean.class},OopsPolicy.VOICE,false);return null;});
   check("player_voice_two_buttons",await(()->ui(()->focused(oops(p),"Спробувати ще")),3000));
   sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);check("player_voice_second_button",ui(()->focused(oops(p),"Інша озвучка")));
   shot("player-voice");
   ui(()->{call(p,"failure",new Class[]{String.class,boolean.class},OopsPolicy.VIDEO,true);return null;});check("player_video",ui(()->OopsPolicy.VIDEO.equals(kind(oops(p)))));
   check("player_video_focus",await(()->ui(()->focused(oops(p),"Спробувати зараз")),3000));shot("player-video");
   // «Інша озвучка» leaves the splash for the controls with the voices open.
   sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
   check("player_other_voice_opens_menu",await(()->ui(()->oops(p)==null&&"voice".equals(get(p,"menu"))),3000));shot("player-voices-menu");
   // Back closes the voices, then the controls, then the player, as anywhere in it.
   for(int i=0;i<3&&!p.isFinishing();i++){sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(400);}
   check("player_back_leaves",await(()->p.isFinishing()||p.isDestroyed(),3000));player=null;
   passed=true;
  }catch(Throwable e){error=e.toString();android.util.Log.w("KIK","Oops audit",e);}
  finally{
   for(Activity a:new Activity[]{film,browse,home,player})if(a!=null)try{ui(()->{a.finish();return null;});}catch(Exception ignored){}
   try{c.getSharedPreferences("kik_profiles",0).edit().putString("current",original).commit();if(qa!=null)ProfileStore.delete(c,qa);c.getSharedPreferences("kik_profiles",0).edit().putString("list",list).putString("current",original).commit();}catch(Exception e){passed=false;error+=" restore: "+e;}
   try{result.put("passed",passed).put("checks",checks).put("error",error);try(FileWriter w=new FileWriter(new File(dir,"oops.json"))){w.write(result.toString(2));}}catch(Exception ignored){}
   Bundle b=new Bundle();b.putString("stream","Oops audit passed="+passed+" checks="+checks+" "+error+"\n");finish(passed?-1:0,b);
  }
 }
}
