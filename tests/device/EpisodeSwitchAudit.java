package kik.qa;
import android.app.*;import android.content.*;import android.os.*;import java.io.*;import java.lang.reflect.*;import java.util.concurrent.Callable;import java.util.function.BooleanSupplier;import kik.i18n.*;
/**
 * The next episode fails to load at once (its only voice turns premium, so Site.stream refuses it without the network):
 * the previous episode's stream must not play under the new episode, and a retry starts at the new episode's own point.
 */
public final class EpisodeSwitchAudit extends Instrumentation {
 Context c;KikPlayerActivity p;String original,list,qa;int checks;
 Object get(Object o,String n)throws Exception{Field f=(o instanceof Class?(Class)o:o.getClass()).getDeclaredField(n);f.setAccessible(true);return f.get(o instanceof Class?null:o);}
 void set(Object o,String n,Object v)throws Exception{Field f=(o instanceof Class?(Class)o:o.getClass()).getDeclaredField(n);f.setAccessible(true);f.set(o instanceof Class?null:o,v);}
 Object call(String n,Class<?>[] types,Object...args)throws Exception{Method m=p.getClass().getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(p,args);}
 <T>T ui(Callable<T> f){Object[] v=new Object[2];runOnMainSync(()->{try{v[0]=f.call();}catch(Throwable e){v[1]=e;}});if(v[1]!=null)throw new RuntimeException((Throwable)v[1]);return (T)v[0];}
 long pos(){return ui(()->(long)p.position());}
 boolean ready(){return ui(()->(Boolean)get(p,"prepared")&&!(Boolean)get(p,"seeking"));}
 boolean await(BooleanSupplier f,int ms){long end=SystemClock.elapsedRealtime()+ms;while(SystemClock.elapsedRealtime()<end){try{if(f.getAsBoolean())return true;}catch(Throwable ignored){}SystemClock.sleep(100);}return false;}
 void check(String n,boolean b){Bundle out=new Bundle();out.putString("stream",(b?"PASS ":"FAIL ")+n+"\n");sendStatus(0,out);if(!b)throw new AssertionError(n);checks++;}
 /** The QA profile played, so the launcher's KIK channels showed it: the viewer's titles go back before the process ends. */
 void republish()throws Exception{c.getSharedPreferences("kik_tv_channels",0).edit().remove("profile").commit();Method m=TvChannels.class.getDeclaredMethod("publish",Context.class,boolean.class);m.setAccessible(true);m.invoke(null,c,true);}
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){boolean passed=false;String error="";c=getTargetContext();original=ProfileStore.current(c);list=c.getSharedPreferences("kik_profiles",0).getString("list","[]");try{
  File clip=new File(c.getExternalFilesDir(null),"player-test.mp4");check("synthetic clip exists",clip.length()>10000);
  qa=ProfileStore.create(c,"QA Episode Switch","Local6789").id;c.getSharedPreferences("kik_profiles",0).edit().putString("current",qa).commit();ProfileStore.completeTasteSetup(c,qa);
  Site.Film f=new Site.Film();f.link="https://uaserials.com/99999999-kik-episode-audit.html";f.id="0";f.title="Episode switch test";f.series=true;f.origin=(String)get(Site.class,"SERVICE");
  Site.Voice v=new Site.Voice();v.id="0";v.name="Synthetic";f.voices.add(v);f.seasons.add("1");for(String n:new String[]{"1","2"}){Site.Episode e=new Site.Episode();e.season="1";e.episode=n;f.episodes.add(e);}((java.util.Map<String,Object>)get(f,"coverage")).put(v.id,f.episodes);
  set(KikPlayerActivity.class,"openingFilm",f);set(KikPlayerActivity.class,"openingVoice",v);
  p=(KikPlayerActivity)startActivitySync(new Intent(c,KikPlayerActivity.class).putExtra("title",f.title).putExtra("season","1").putExtra("episode","1").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  // Episode 1 plays the local clip from 20 s, as if start() had loaded it.
  ui(()->{set(p,"generation",(Integer)get(p,"generation")+1);Site.Stream s=new Site.Stream();s.qualities.put("1080p",clip.toURI().toString());set(p,"stream",s);set(p,"streamKey","0|1|1");set(p,"quality","1080p");set(p,"resumeAt",20000L);set(p,"playWhenReady",true);call("play",new Class[]{});return null;});
  check("episode 1 plays",await(()->ready()&&ui(()->p.playing()),15000));SystemClock.sleep(2500);check("episode 1 moves on",pos()>21000);
  // ⏭ without the intro: the next episode, from its own history.
  ui(()->{v.premium=true;set(p,"episode","2");call("start",new Class[]{boolean.class},true);return null;});
  check("switching forgets episode 1's stream",ui(()->get(p,"stream")==null&&!p.playing()));
  check("episode 2 starts at its own point",ui(()->(Long)get(p,"resumeAt"))<1000);
  check("a failed episode 2 never replays episode 1",!await(()->ready()||ui(()->p.playing()),15000));
  check("episode 2 keeps no progress of episode 1",Progress.episode(c,f.link,"1","2")==null);
  passed=true;
 }catch(Throwable e){error=e.toString();}finally{try{if(p!=null)ui(()->{p.finish();return null;});c.getSharedPreferences("kik_profiles",0).edit().putString("current",original).commit();if(qa!=null)ProfileStore.delete(c,qa);c.getSharedPreferences("kik_profiles",0).edit().putString("list",list).putString("current",original).commit();republish();}catch(Exception e){passed=false;error=e.toString();}Bundle b=new Bundle();b.putString("stream","Episode switch passed="+passed+" checks="+checks+" "+error);finish(passed?-1:0,b);}}
}
