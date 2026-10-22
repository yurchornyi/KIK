package kik.i18n;
import android.util.Log;
import java.io.File;
import java.lang.reflect.*;

/** A session started from zero continues from the profile's saved point for the same title and episode. */
public final class ResumeFix {
 public static void apply(Object session) {
  if(session==null)return;
  try{
   Class<?> s=session.getClass();
   Field pos=s.getDeclaredField("o");pos.setAccessible(true);
   if(pos.getLong(session)!=0)return;
   String link=(String)get(session,"a");if(link==null)return;
   Class<?> cache=Class.forName("i72");
   Object store=staticField(cache,"h");if(store==null)return;
   Method file=cache.getMethod("f",String.class),read=cache.getMethod("h",File.class);
   Object progress=read.invoke(null,file.invoke(store,link));
   if(progress==null&&!link.startsWith("/"))progress=read.invoke(null,file.invoke(store,"/"+link));
   if(progress==null)return;
   String season=PlaybackPolicy.string(progress,"getSeason"),episode=PlaybackPolicy.string(progress,"getEpisode");
   if(!same(season,(String)get(session,"c"))||!same(episode,(String)get(session,"d")))return;
   Object ms=PlaybackPolicy.call(progress,"getPositionMs");
   long saved=ms instanceof Long?(Long)ms:0;
   if(saved<5000)return;
   pos.setLong(session,saved);
   Log.d("KIK","Resume from "+saved/1000+"s for "+link);
  }catch(Exception e){Log.w("KIK","Resume position",e);}
 }
 private static boolean same(String a,String b){a=a==null||a.isEmpty()?"0":a;b=b==null||b.isEmpty()?"0":b;return a.equals(b);}
 private static Object get(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 private static Object staticField(Class<?> c,String name)throws Exception{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
}
