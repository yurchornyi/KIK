package kik.qa;
import android.app.*;import android.content.*;import android.os.*;import java.lang.reflect.*;import java.net.*;import java.util.*;import java.util.concurrent.Callable;import java.util.function.BooleanSupplier;import kik.i18n.*;
/** A real film plays with plain HTTP refused and KIK's own requests signed; a disposable profile keeps the owner's history unchanged. */
public final class NetworkAudit extends Instrumentation {
 Context c;String original,list,qa;KikPlayerActivity p;int checks;
 Object get(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 <T>T ui(Callable<T> f){Object[] v=new Object[2];runOnMainSync(()->{try{v[0]=f.call();}catch(Throwable e){v[1]=e;}});if(v[1]!=null)throw new RuntimeException((Throwable)v[1]);return (T)v[0];}
 boolean await(BooleanSupplier f,int ms){long end=SystemClock.elapsedRealtime()+ms;while(SystemClock.elapsedRealtime()<end){try{if(f.getAsBoolean())return true;}catch(Throwable ignored){}SystemClock.sleep(200);}return false;}
 void check(String n,boolean b){Bundle out=new Bundle();out.putString("stream",(b?"PASS ":"FAIL ")+n+"\n");sendStatus(0,out);if(!b)throw new AssertionError(n);checks++;}
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){boolean passed=false;String error="";c=getTargetContext();for(ProfileStore.Profile x:ProfileStore.list(c))if("QA Network".equals(x.name))ProfileStore.delete(c,x.id);original=ProfileStore.current(c);
  if(ProfileStore.nameOf(c,original)==null&&!ProfileStore.list(c).isEmpty()){original=ProfileStore.list(c).get(0).id;c.getSharedPreferences("kik_profiles",0).edit().putString("current",original).commit();}list=c.getSharedPreferences("kik_profiles",0).getString("list","[]");try{
  check("plain HTTP is refused",!android.security.NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("example.com"));
  HttpURLConnection probe=(HttpURLConnection)new URL(UpdateConfig.WEB_ORIGIN+"/api/health").openConnection();
  Method authorize=CloudSync.class.getDeclaredMethod("authorize",HttpURLConnection.class);authorize.setAccessible(true);authorize.invoke(null,probe);
  check("KIK requests carry this TV's sign-in",!Account.signedIn(c)||String.valueOf(probe.getRequestProperty("Authorization")).matches("Bearer [a-f0-9]{64}"));probe.disconnect();
  qa=ProfileStore.create(c,"QA Network","Network6789").id;c.getSharedPreferences("kik_profiles",0).edit().putString("current",qa).commit();ProfileStore.completeTasteSetup(c,qa);
  List<Site.Card> cards=Site.search("Один вдома",1);Site.Card card=null;for(Site.Card x:cards)if(x.link.contains("/films/")){card=x;break;}check("catalogue reachable over HTTPS",card!=null);
  Site.Film f=Site.film(card.link);Site.Voice voice=null;for(Site.Voice v:(List<Site.Voice>)get(f,"voices"))if(!v.premium&&!v.ads){voice=v;break;}check("free voice found",voice!=null);
  Field film=KikPlayerActivity.class.getDeclaredField("openingFilm"),track=KikPlayerActivity.class.getDeclaredField("openingVoice");film.setAccessible(true);track.setAccessible(true);film.set(null,f);track.set(null,voice);
  p=(KikPlayerActivity)startActivitySync(new Intent(c,KikPlayerActivity.class).putExtra("title",f.title).putExtra("poster",f.poster).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));check("player opened",p!=null);
  check("real film starts playing",await(()->ui(()->(Boolean)get(p,"prepared")&&p.playing()),45000));
  long at=ui(()->p.position());SystemClock.sleep(4000);check("playback advances",ui(()->p.position())>at+2500);passed=true;
 }catch(Throwable e){error=e.toString();}finally{try{if(p!=null)ui(()->{p.finish();return null;});SystemClock.sleep(1500);if(qa!=null)ProfileStore.delete(c,qa);c.getSharedPreferences("kik_profiles",0).edit().putString("list",list).putString("current",original).commit();}catch(Exception e){passed=false;error=e.toString();}Bundle b=new Bundle();b.putString("stream",(passed?"Network audit passed: "+checks:"Network audit failed: "+error)+"\n");finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,b);}}
}
