package kik.qa;
import android.app.*;import android.content.*;import android.os.*;import java.io.*;import java.lang.reflect.*;import java.util.concurrent.Callable;import java.util.function.BooleanSupplier;import kik.i18n.*;

/**
 * «Однакова гучність фільмів» in the real player: a quiet clip (−30 dB) and a loud one (−10 dB), pushed to
 * /data/local/tmp by scripts/audit-device.sh, come out of KIK's audio chain at one level; with the switch off the
 * sound is left alone. Runs in a QA profile; the viewer's profile, switch and launcher channels are put back.
 */
public final class LoudnessAudit extends Instrumentation {
 Context c;KikPlayerActivity p;String original,list,qa;int checks;
 Object get(Object o,String n)throws Exception{Field f=(o instanceof Class?(Class)o:o.getClass()).getDeclaredField(n);f.setAccessible(true);return f.get(o instanceof Class?null:o);}
 void set(Object o,String n,Object v)throws Exception{Field f=(o instanceof Class?(Class)o:o.getClass()).getDeclaredField(n);f.setAccessible(true);f.set(o instanceof Class?null:o,v);}
 Object call(String n,Class<?>[] types,Object...args)throws Exception{Method m=p.getClass().getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(p,args);}
 <T>T ui(Callable<T> f){Object[] v=new Object[2];runOnMainSync(()->{try{v[0]=f.call();}catch(Throwable e){v[1]=e;}});if(v[1]!=null)throw new RuntimeException((Throwable)v[1]);return (T)v[0];}
 boolean await(BooleanSupplier f,int ms){long end=SystemClock.elapsedRealtime()+ms;while(SystemClock.elapsedRealtime()<end){try{if(f.getAsBoolean())return true;}catch(Throwable ignored){}SystemClock.sleep(100);}return false;}
 void check(String n,boolean b){Bundle out=new Bundle();out.putString("stream",(b?"PASS ":"FAIL ")+n+"\n");sendStatus(0,out);if(!b)throw new AssertionError(n);checks++;}
 void note(String s){Bundle out=new Bundle();out.putString("stream","  "+s+"\n");sendStatus(0,out);}
 File copy(String name)throws IOException{File from=new File("/data/local/tmp/"+name),to=new File(c.getExternalFilesDir(null),name);try(InputStream in=new FileInputStream(from);OutputStream out=new FileOutputStream(to)){byte[] b=new byte[65536];int n;while((n=in.read(b))>0)out.write(b,0,n);}return to;}
 /** Plays a clip for nine seconds and returns {gain, output level} in dB as Loudness saw them, or null with the switch off. */
 double[] play(File clip)throws Exception{
  Site.Film f=new Site.Film();f.link="https://hdrezka.sh/films/drama/0-loudness-audit.html";f.id="0";f.title="Loudness test";f.origin="https://127.0.0.1";Site.Voice v=new Site.Voice();v.id="0";v.name="Synthetic";set(KikPlayerActivity.class,"openingFilm",f);set(KikPlayerActivity.class,"openingVoice",v);
  p=(KikPlayerActivity)startActivitySync(new Intent(c,KikPlayerActivity.class).putExtra("title",f.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  ui(()->{set(p,"generation",(Integer)get(p,"generation")+1);Site.Stream s=new Site.Stream();s.qualities.put("1080p",clip.toURI().toString());set(p,"stream",s);set(p,"quality","1080p");set(p,"resumeAt",0L);set(p,"playWhenReady",true);call("play",new Class[]{});return null;});
  check(clip.getName()+" plays",await(()->ui(()->p.playing()),15000));SystemClock.sleep(9000);
  double[] seen=ui(()->{Loudness l=(Loudness)get(p,"loudness");return l==null?null:new double[]{l.gainDb(),l.outputDb()};});
  ui(()->{p.finish();return null;});SystemClock.sleep(1500);p=null;return seen;
 }
 void republish()throws Exception{c.getSharedPreferences("kik_tv_channels",0).edit().remove("profile").commit();Method m=TvChannels.class.getDeclaredMethod("publish",Context.class,boolean.class);m.setAccessible(true);m.invoke(null,c,true);}
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){boolean passed=false;String error="";c=getTargetContext();SharedPreferences settings=c.getSharedPreferences("settings",0);boolean had=settings.contains(Loudness.PREF),was=settings.getBoolean(Loudness.PREF,true);
  original=ProfileStore.current(c);list=c.getSharedPreferences("kik_profiles",0).getString("list","[]");try{
  File quiet=copy("loudness-quiet.mp4"),loud=copy("loudness-loud.mp4");
  qa=ProfileStore.create(c,"QA Loudness","Local6789").id;c.getSharedPreferences("kik_profiles",0).edit().putString("current",qa).commit();ProfileStore.completeTasteSetup(c,qa);
  settings.edit().putBoolean(Loudness.PREF,true).commit();
  double[] q=play(quiet),l=play(loud);
  check("the player's sound passes through Loudness",q!=null&&l!=null);
  note(String.format(java.util.Locale.ROOT,"quiet clip: gain %+.1f dB, out %.1f dB; loud clip: gain %+.1f dB, out %.1f dB",q[0],q[1],l[0],l[1]));
  check("the quiet film is lifted",q[0]>6);check("the loud film is turned down",l[0]<-5);
  check("both come out within 3 dB of each other (they were 20 dB apart)",Math.abs(q[1]-l[1])<=3);
  settings.edit().putBoolean(Loudness.PREF,false).commit();
  check("with the switch off the sound is left alone",play(quiet)==null);
  passed=true;
 }catch(Throwable e){error=e.toString();}finally{try{if(p!=null)ui(()->{p.finish();return null;});if(had)settings.edit().putBoolean(Loudness.PREF,was).commit();else settings.edit().remove(Loudness.PREF).commit();
   c.getSharedPreferences("kik_profiles",0).edit().putString("current",original).commit();if(qa!=null)ProfileStore.delete(c,qa);c.getSharedPreferences("kik_profiles",0).edit().putString("list",list).putString("current",original).commit();republish();
   new File(c.getExternalFilesDir(null),"loudness-quiet.mp4").delete();new File(c.getExternalFilesDir(null),"loudness-loud.mp4").delete();}catch(Exception e){passed=false;error=e.toString();}
  }Bundle b=new Bundle();b.putString("stream","Loudness passed="+passed+" checks="+checks+" "+error);finish(passed?-1:0,b);}
}
