package kik.i18n;
import java.io.IOException;
import java.util.*;

/**
 * What a loading error means to the viewer, the same as the web's «Упс» screens (web/public/oops.js): the words for each
 * cause, whether KIK tries again by itself, and how long it waits. No Android here, so scripts/check-tv.sh can test it.
 */
public final class OopsPolicy {
 public static final String OFFLINE="offline",SOURCE="source",SERVER="server",SLOW="slow",MISSING="missing",OTHER="other",
  // The player's own: a stream that would not load after every recovery step, and a voice without video for this episode.
  VIDEO="video",VOICE="voice";
 private static final Map<String,String[]> COPY=new HashMap<String,String[]>();
 static{
  COPY.put(OFFLINE,new String[]{"Упс, немає зв’язку","Перевірте інтернет. KIK продовжить сам, щойно з’єднання повернеться."});
  COPY.put(SOURCE,new String[]{"Упс, джерело фільмів мовчить","Ми вже працюємо над цим. KIK сам спробує ще раз — нічого натискати не треба."});
  COPY.put(SERVER,new String[]{"Упс, KIK на хвилинку недоступний","Ми вже працюємо над цим. KIK сам спробує ще раз за мить."});
  COPY.put(SLOW,new String[]{"Упс, щось довго","Відповідь затримується. Ми вже працюємо над цим і спробуємо ще раз."});
  COPY.put(MISSING,new String[]{"Упс, такого тут немає","Можливо, цю назву прибрали з джерела. Пошукайте інший фільм."});
  COPY.put(OTHER,new String[]{"Упс, щось пішло не так","Ми вже працюємо над цим. Спробуйте ще раз за мить."});
  COPY.put(VIDEO,new String[]{"Упс, відео не вантажиться","Ми вже працюємо над цим. KIK сам спробує ще раз — нічого натискати не треба."});
  COPY.put(VOICE,new String[]{"Упс, ця озвучка зараз не грає","Ми вже працюємо над цим. Виберіть іншу озвучку або спробуйте ще раз."});
 }
 /** Seconds before each automatic retry of the same thing; the last step repeats. */
 public static final int[] RETRY_STEPS={10,20,30,60};
 private static final long QUIET_MS=180_000L;
 private static final LinkedHashMap<String,long[]> ATTEMPTS=new LinkedHashMap<String,long[]>();

 /** KIK's own service said no: its HTTP status, and retry=true when the film source is busy or refusing (503 «try again»). */
 public static final class ServiceError extends IOException {
  public final int status;public final boolean retry;
  public ServiceError(int status,boolean retry,String message){super(message);this.status=status;this.retry=retry;}
 }

 /** The cause, in the web's order: no connection, a slow answer, the film source, KIK itself, a missing title, anything else. */
 public static String kind(Throwable e,boolean online,boolean sourcePaused){
  if(!online)return OFFLINE;
  for(Throwable t=e;t!=null;t=t.getCause())if(t instanceof java.net.UnknownHostException||t instanceof java.net.ConnectException||t instanceof java.net.NoRouteToHostException)return OFFLINE;
  for(Throwable t=e;t!=null;t=t.getCause())if(t instanceof java.net.SocketTimeoutException)return SLOW;
  if(sourcePaused)return SOURCE;
  for(Throwable t=e;t!=null;t=t.getCause())if(t instanceof ServiceError){ServiceError s=(ServiceError)t;
   if(s.retry)return SOURCE;if(s.status>=500)return SERVER;if(s.status==404)return MISSING;}
  return OTHER;
 }
 /** Temporary causes: the screen counts down and tries again by itself. */
 public static boolean auto(String kind){return OFFLINE.equals(kind)||SOURCE.equals(kind)||SERVER.equals(kind)||SLOW.equals(kind)||VIDEO.equals(kind);}
 /** Heading («Упс, …») and the line under it. */
 public static String[] copy(String kind){String[] c=COPY.get(kind);return c==null?COPY.get(OTHER):c;}
 /** The wait before the next automatic retry of `key`: longer while the same thing keeps failing, from the start after a quiet 3 minutes. */
 public static synchronized int retryWait(String key,long now){
  long[] last=key==null||key.isEmpty()?null:ATTEMPTS.get(key);int n=last!=null&&now-last[1]<QUIET_MS?(int)last[0]+1:0;
  if(key!=null&&!key.isEmpty()){ATTEMPTS.remove(key);ATTEMPTS.put(key,new long[]{n,now});if(ATTEMPTS.size()>50)ATTEMPTS.remove(ATTEMPTS.keySet().iterator().next());}
  return RETRY_STEPS[Math.min(n,RETRY_STEPS.length-1)];
 }
 /** The viewer pressed «Спробувати зараз»: if that fails too, the countdown starts over from the shortest wait. */
 public static synchronized void forget(String key){if(key!=null)ATTEMPTS.remove(key);}
 private OopsPolicy(){}
}
