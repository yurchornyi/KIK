package kik.qa;
import android.app.*;import android.content.*;import android.os.*;import android.widget.*;import java.io.*;import java.lang.reflect.*;import java.util.*;import java.util.concurrent.Callable;import java.util.function.BooleanSupplier;import kik.i18n.*;
/** Uses a generated, non-network clip to isolate decoder state from remote stream availability. */
public final class PlayerLocalAudit extends Instrumentation {
 Context c;KikPlayerActivity p;String original,list,qa;int checks;
 Object get(Object o,String n)throws Exception{Field f=(o instanceof Class?(Class)o:o.getClass()).getDeclaredField(n);f.setAccessible(true);return f.get(o instanceof Class?null:o);}
 void set(Object o,String n,Object v)throws Exception{Field f=(o instanceof Class?(Class)o:o.getClass()).getDeclaredField(n);f.setAccessible(true);f.set(o instanceof Class?null:o,v);}
 Object call(String n,Class<?>[] types,Object...args)throws Exception{Method m=p.getClass().getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(p,args);}
 <T>T ui(Callable<T> f){Object[] v=new Object[2];runOnMainSync(()->{try{v[0]=f.call();}catch(Throwable e){v[1]=e;}});if(v[1]!=null)throw new RuntimeException((Throwable)v[1]);return (T)v[0];}
 KikPlayerActivity video(){return p;}long pos(){return ui(()->(long)video().position());}
 boolean ready(){return ui(()->(Boolean)get(p,"prepared")&&!(Boolean)get(p,"seeking"));}
 boolean await(BooleanSupplier f,int ms){long end=SystemClock.elapsedRealtime()+ms;while(SystemClock.elapsedRealtime()<end){try{if(f.getAsBoolean())return true;}catch(Throwable ignored){}SystemClock.sleep(100);}return false;}
 void check(String n,boolean b){Bundle out=new Bundle();out.putString("stream",(b?"PASS ":"FAIL ")+n+"\n");sendStatus(0,out);if(!b)throw new AssertionError(n);checks++;}
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){boolean passed=false;String error="";c=getTargetContext();original=ProfileStore.current(c);list=c.getSharedPreferences("kik_profiles",0).getString("list","[]");try{
 File clip=new File(c.getExternalFilesDir(null),"player-test.mp4");check("synthetic clip exists",clip.length()>10000);
 qa=ProfileStore.create(c,"QA Local Player","Local6789").id;c.getSharedPreferences("kik_profiles",0).edit().putString("current",qa).commit();ProfileStore.completeTasteSetup(c,qa);
 Site.Film f=new Site.Film();f.link="https://hdrezka.sh/films/drama/0-local-audit.html";f.id="0";f.title="Synthetic decoder test";f.origin="https://127.0.0.1";Site.Voice v=new Site.Voice();v.id="0";v.name="Synthetic";set(KikPlayerActivity.class,"openingFilm",f);set(KikPlayerActivity.class,"openingVoice",v);
 p=(KikPlayerActivity)startActivitySync(new Intent(c,KikPlayerActivity.class).putExtra("title",f.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
 ui(()->{set(p,"generation",(Integer)get(p,"generation")+1);Site.Stream s=new Site.Stream();s.qualities.put("1080p",clip.toURI().toString());s.qualities.put("720p",new File(c.getExternalFilesDir(null),"player-test-alt.mp4").toURI().toString());set(p,"stream",s);set(p,"quality","1080p");set(p,"resumeAt",0L);set(p,"playWhenReady",true);call("play",new Class[]{});return null;});
 check("local video prepared",await(()->ready()&&ui(()->video().playing()),15000));check("local duration",ui(()->video().duration()>90000));ui(()->{set(KikPlayerActivity.class,"openingFilm",new Site.Film());return null;});check("another launch cannot replace active player metadata",ui(()->get(p,"film")==f));long start=pos();SystemClock.sleep(3000);check("continuous local playback",pos()>start+2200);
 ui(()->{call("toggle",new Class[]{});set(p,"speed",1.25f);call("applySpeed",new Class[]{});return null;});long paused=pos();SystemClock.sleep(1000);check("pause with speed",!ui(()->video().playing())&&Math.abs(pos()-paused)<400);
 ui(()->{call("openQualities",new Class[]{});List<Runnable> actions=(List<Runnable>)get(p,"optActions");actions.get(1).run();return null;});
 check("quality switch settles paused",await(()->ready()&&!ui(()->video().playing()),15000));check("quality switch keeps position",Math.abs(pos()-paused)<2200);
 ui(()->{call("seek",new Class[]{long.class},15000L);return null;});check("paused seek settles",await(()->ready()&&!ui(()->video().playing()),15000));check("paused seek position",Math.abs(pos()-paused-15000)<2200);
 ui(()->{call("toggle",new Class[]{});return null;});start=pos();SystemClock.sleep(3000);check("resume after quality and seek",ui(()->video().playing())&&pos()>start+2500);long beforeRecovery=pos();
 ui(()->{call("toggle",new Class[]{});Site.Stream broken=new Site.Stream();String bad=new File(c.getExternalFilesDir(null),"missing-recovery.mp4").toURI().toString();broken.qualities.put("1080p",bad);broken.alternatives.put("1080p",Arrays.asList(bad,clip.toURI().toString()));set(p,"stream",broken);set(p,"quality","1080p");set(p,"resumeAt",beforeRecovery);set(p,"prepared",false);set(p,"playWhenReady",false);((RecoveryPolicy)get(p,"recovery")).reset();call("play",new Class[]{});return null;});
 check("failed media switches automatically to backup",await(()->ready()&&!ui(()->video().playing()),18000));
 check("automatic recovery keeps paused position",Math.abs(pos()-beforeRecovery)<2200);
 check("automatic recovery keeps language and KIK player",ui(()->p.getClass().getName().equals("kik.i18n.KikPlayerActivity")));
 ui(()->{call("toggle",new Class[]{});return null;});start=pos();SystemClock.sleep(3000);check("recovered stream continues playing",ui(()->video().playing())&&pos()>start+2000);passed=true;
 }catch(Throwable e){error=e.toString();}finally{try{if(p!=null)ui(()->{p.finish();return null;});c.getSharedPreferences("kik_profiles",0).edit().putString("current",original).commit();if(qa!=null)ProfileStore.delete(c,qa);c.getSharedPreferences("kik_profiles",0).edit().putString("list",list).putString("current",original).commit();}catch(Exception e){passed=false;error=e.toString();}Bundle b=new Bundle();b.putString("stream","Local player passed="+passed+" checks="+checks+" "+error);finish(passed?-1:0,b);}}
}
