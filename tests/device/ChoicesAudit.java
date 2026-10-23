package kik.qa;
import android.app.*;import android.content.*;import android.os.*;import java.lang.reflect.*;import kik.i18n.*;import org.json.*;
/** Voice, quality and speed picked for one title are kept per profile and travel as the synced «voice» record. */
public final class ChoicesAudit extends Instrumentation {
 int checks;
 void check(String n,boolean b){Bundle out=new Bundle();out.putString("stream",(b?"PASS ":"FAIL ")+n+"\n");sendStatus(0,out);if(!b)throw new AssertionError(n);checks++;}
 Method method(String cls,String n,Class<?>...types)throws Exception{Method m=Class.forName(cls).getDeclaredMethod(n,types);m.setAccessible(true);return m;}
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){boolean passed=false;String error="",qa=null;Context c=getTargetContext();String original=ProfileStore.current(c),list=c.getSharedPreferences("kik_profiles",0).getString("list","[]");try{
  qa=ProfileStore.create(c,"QA Choices","Choices123").id;c.getSharedPreferences("kik_profiles",0).edit().putString("current",qa).commit();
  String link="https://hdrezka.sh/films/drama/0-choices-audit.html";
  check("nothing remembered at first",Progress.choices(c,link)==null&&Progress.voice(c,link)==null);
  Progress.choose(c,link,"238","speed",1.5d);Progress.choose(c,link,"238","quality","720p");
  JSONObject got=Progress.choices(c,link);check("speed and quality remembered",got!=null&&got.optDouble("speed")==1.5&&"720p".equals(got.optString("quality")));
  check("voice playing at the time is kept",  "238".equals(Progress.voice(c,link)));
  Progress.chooseVoice(c,link,"56");got=Progress.choices(c,link);check("a new voice keeps speed and quality","56".equals(Progress.voice(c,link))&&got.optDouble("speed")==1.5&&"720p".equals(got.optString("quality")));
  JSONObject root=new JSONObject();method("kik.i18n.CloudSync","collect",Context.class,String.class,JSONObject.class).invoke(null,c,qa,root);
  JSONObject rec=root.getJSONObject("records").optJSONObject("voice\nfilms/drama/0-choices-audit.html");check("choices go to the cloud as a voice record",rec!=null&&rec.optBoolean("dirty")&&"56".equals(rec.getJSONObject("data").optString("voice"))&&"720p".equals(rec.getJSONObject("data").optString("quality"))&&rec.getJSONObject("data").optDouble("speed")==1.5);
  // A pick made on the web arrives: the TV opens the title with it.
  JSONObject records=root.getJSONObject("records");records.put("voice\nfilms/drama/0-choices-audit.html",new JSONObject().put("kind","voice").put("key","films/drama/0-choices-audit.html").put("updated_at",System.currentTimeMillis()+60000).put("data",new JSONObject().put("link",link).put("voice","110").put("voiceName","Укр").put("quality","480p").put("speed",2)));
  method("kik.i18n.CloudSync","apply",Context.class,String.class,JSONObject.class).invoke(null,c,qa,records);got=Progress.choices(c,link);
  check("web pick applies on TV","110".equals(Progress.voice(c,link))&&"480p".equals(got.optString("quality"))&&got.optDouble("speed")==2);
  passed=true;
 }catch(Throwable e){error=e.toString();}finally{try{c.getSharedPreferences("kik_profiles",0).edit().putString("current",original).commit();if(qa!=null)ProfileStore.delete(c,qa);c.getSharedPreferences("kik_profiles",0).edit().putString("list",list).putString("current",original).commit();}catch(Exception e){passed=false;error=e.toString();}Bundle b=new Bundle();b.putString("stream","Choices passed="+passed+" checks="+checks+" "+error);finish(passed?-1:0,b);}}
}
