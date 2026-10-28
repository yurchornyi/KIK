package kik.i18n;
import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import javax.net.ssl.SSLException;
import org.json.*;

/**
 * Tells the KIK server whether this TV reaches the catalogue mirrors and, if not, how they fail (DNS, timeout, 403…),
 * so the admin panel shows why someone's home screen stays empty. Sends a change at once, the same state every 6 hours.
 */
final class SiteReport {
 private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->new Thread(r,"kik-site-report"));
 private static String sent="";private static long sentAt=-1;

 /** The failure kind the admin sees; null for a request the app cancelled itself. */
 static String kind(IOException e){
  String m=String.valueOf(e.getMessage());
  if(e instanceof UnknownHostException)return "dns";
  if(e instanceof SocketTimeoutException||m.contains("ETIMEDOUT"))return "timeout";
  if(e instanceof InterruptedIOException)return null;
  if(m.contains("reset")||m.contains("ECONNRESET")||m.contains("Broken pipe"))return "reset";
  if(e instanceof SSLException)return "tls";
  if(e instanceof ConnectException||e instanceof NoRouteToHostException)return "connect";
  if(m.startsWith("HTTP "))return "http";
  if(m.contains("bot check"))return "bot";
  return "other";
 }
 /** One mirror's outcome for the detail line, e.g. "rezka.ag dns" or "hdrezka.sh tls CertificateExpiredException". */
 static String detail(String host,String kind,IOException e){
  if("http".equals(kind))return host+" "+e.getMessage();
  if("tls".equals(kind)||"other".equals(kind)){Throwable t=e;while(t.getCause()!=null&&t.getCause()!=t)t=t.getCause();return host+" "+kind+" "+t.getClass().getSimpleName();}
  return host+" "+kind;
 }
 /** bypassed: the provider's DNS blocks the mirror and KIK reached it through DNS-over-HTTPS. */
 static void ok(String host,boolean bypassed){report(bypassed?"doh":"ok",host,"");}
 static void failed(String status,String host,String detail){report(status,host,detail);}
 /** Titles came through KIK's service, as they do since 1.25: this TV no longer asks the mirrors, so their last state would never change. */
 static void service(){report("service",Site.SERVICE,"");}

 private static synchronized void report(String status,String host,String detail){
  String key=status+"|"+detail;long now=SystemClock.elapsedRealtime();
  // A flaky line must not write to the server on every page: at most once a minute, the same state every 6 hours.
  if(sentAt>=0&&(now-sentAt<60_000||key.equals(sent)&&now-sentAt<6*3600_000))return;
  sent=key;sentAt=now;
  IO.execute(()->{
   try{
    Context c=UiRuntime.app;String token=Account.token(c);if(token.isEmpty()){forget();return;}
    CloudSync.request("/api/diagnostics/site",new JSONObject().put("status",status).put("host",host).put("detail",detail).put("app_version",AppUpdater.version(c)),token);
   }catch(Exception e){Log.w("KIK","Site report: "+e);forget();}
  });
 }
 /** Not delivered: send the same state again, still no more than once a minute. */
 private static synchronized void forget(){sent="";}
}
