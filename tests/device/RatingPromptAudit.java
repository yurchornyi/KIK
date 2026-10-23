package kik.qa;
import android.app.*;import android.content.*;import android.graphics.Bitmap;import android.os.*;import android.view.*;import java.io.*;import java.lang.reflect.*;import java.util.concurrent.Callable;import java.util.function.BooleanSupplier;import kik.i18n.*;
/** «Як вам?» in the KIK player, on a generated clip in a temporary profile: shown at the credits, answered with the remote, asked once. */
public final class RatingPromptAudit extends Instrumentation {
 Context c;KikPlayerActivity p;String original,list,qa;int checks;
 Object get(Object o,String n)throws Exception{Field f=(o instanceof Class?(Class)o:o.getClass()).getDeclaredField(n);f.setAccessible(true);return f.get(o instanceof Class?null:o);}
 void set(Object o,String n,Object v)throws Exception{Field f=(o instanceof Class?(Class)o:o.getClass()).getDeclaredField(n);f.setAccessible(true);f.set(o instanceof Class?null:o,v);}
 Object call(String n,Class<?>[] types,Object...args)throws Exception{Method m=p.getClass().getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(p,args);}
 Object store(String n,Class<?>[] types,Object...args)throws Exception{Method m=Class.forName("kik.i18n.RatingStore").getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(null,args);}
 int rating(String link)throws Exception{return (Integer)store("get",new Class[]{Context.class,String.class,String.class},c,qa,link);}
 boolean asked(String link)throws Exception{return (Boolean)store("asked",new Class[]{Context.class,String.class,String.class},c,qa,link);}
 <T>T ui(Callable<T> f){Object[] v=new Object[2];runOnMainSync(()->{try{v[0]=f.call();}catch(Throwable e){v[1]=e;}});if(v[1]!=null)throw new RuntimeException((Throwable)v[1]);return (T)v[0];}
 long pos(){return ui(()->p.position());}
 boolean prompt(){return ui(()->{View a=(View)get(p,"ask");return a!=null&&a.getVisibility()==View.VISIBLE&&(Boolean)get(p,"askOn");});}
 boolean await(BooleanSupplier f,int ms){long end=SystemClock.elapsedRealtime()+ms;while(SystemClock.elapsedRealtime()<end){try{if(f.getAsBoolean())return true;}catch(Throwable ignored){}SystemClock.sleep(100);}return false;}
 void check(String n,boolean b){Bundle out=new Bundle();out.putString("stream",(b?"PASS ":"FAIL ")+n+"\n");sendStatus(0,out);if(!b)throw new AssertionError(n);checks++;}
 void shot(String name){try{Bitmap b=getUiAutomation().takeScreenshot();if(b==null)return;try(FileOutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}}catch(Exception ignored){}}
 /** Re-runs the credits check for the playing title as if the viewer had watched it for `watched` ms in this sitting. */
 void credits(long watched){ui(()->{set(p,"askChecked",false);set(p,"watchedItem",watched);call("maybeAsk",new Class[]{long.class},p.duration()-20_000L);return null;});}
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){boolean passed=false;String error="";c=getTargetContext();original=ProfileStore.current(c);list=c.getSharedPreferences("kik_profiles",0).getString("list","[]");try{
 File clip=new File(c.getExternalFilesDir(null),"rate-test.mp4");check("synthetic 4-minute clip exists",clip.length()>10000);
 qa=ProfileStore.create(c,"QA Rating","Rate6789").id;c.getSharedPreferences("kik_profiles",0).edit().putString("current",qa).commit();ProfileStore.completeTasteSetup(c,qa);
 Site.Film f=new Site.Film();f.link="https://hdrezka.sh/films/drama/1-rating-audit.html";f.id="0";f.title="Перевірка оцінки";f.origin="https://127.0.0.1";Site.Voice v=new Site.Voice();v.id="0";v.name="Synthetic";set(KikPlayerActivity.class,"openingFilm",f);set(KikPlayerActivity.class,"openingVoice",v);
 p=(KikPlayerActivity)startActivitySync(new Intent(c,KikPlayerActivity.class).putExtra("title",f.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
 ui(()->{call("endIntro",new Class[]{});set(p,"generation",(Integer)get(p,"generation")+1);Site.Stream s=new Site.Stream();s.qualities.put("1080p",clip.toURI().toString());set(p,"stream",s);set(p,"quality","1080p");set(p,"resumeAt",0L);set(p,"playWhenReady",true);call("play",new Class[]{});return null;});
 check("clip plays",await(()->ui(()->p.playing()&&p.duration()>200_000),15000));
 // A seek to the end is not watching: no question.
 ui(()->{call("seek",new Class[]{long.class},p.duration()-44_000L-p.position());return null;});SystemClock.sleep(2500);check("no question after a seek to the credits",!prompt());
 credits(25*60_000L);check("question at the credits after real watching",prompt());check("the title is marked asked at once",asked(f.link));
 SystemClock.sleep(700);shot("rate-prompt.png");
 sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);check("▶ moves to «Не подобається»",ui(()->(Integer)get(p,"askFocus"))==1);
 sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);check("OK saves «Не подобається»",rating(f.link)==-1);check("playback never stops",ui(()->p.playing()));
 SystemClock.sleep(400);shot("rate-thanks.png");check("thank-you note hides by itself",await(()->ui(()->((View)get(p,"ask")).getVisibility()!=View.VISIBLE),5000));
 credits(25*60_000L);check("a rated title is not asked again",!prompt());
 // Another title: skipped with Back is still asked only once.
 f.link="https://hdrezka.sh/films/drama/2-rating-audit.html";credits(25*60_000L);check("a new title is asked",prompt());
 sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);check("Back skips without rating",await(()->!prompt(),2000)&&rating(f.link)==0);check("Back keeps the player open",!p.isFinishing());
 credits(25*60_000L);check("a skipped title is not asked again",!prompt());
 f.link="https://hdrezka.sh/films/drama/3-rating-audit.html";credits(60_000L);check("one minute of watching is not enough",!prompt());
 check("still playing after all questions",ui(()->p.playing()));passed=true;
 }catch(Throwable e){error=e.toString();}finally{try{if(p!=null)ui(()->{p.finish();return null;});c.getSharedPreferences("kik_profiles",0).edit().putString("current",original).commit();if(qa!=null)ProfileStore.delete(c,qa);c.getSharedPreferences("kik_profiles",0).edit().putString("list",list).putString("current",original).commit();}catch(Exception e){passed=false;error=e.toString();}Bundle b=new Bundle();b.putString("stream","Rating prompt passed="+passed+" checks="+checks+" "+error);finish(passed?-1:0,b);}}
}
