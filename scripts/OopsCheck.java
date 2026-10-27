import kik.i18n.OopsPolicy;import java.io.IOException;import java.net.*;
/** The TV's «Упс» screens pick the same cause, words and retry waits as the web's (web/oops.test.mjs). */
public final class OopsCheck {
 static void check(boolean v,String what){if(!v)throw new AssertionError(what);}
 public static void main(String[] args){
  check(OopsPolicy.kind(new IOException("x"),false,false).equals(OopsPolicy.OFFLINE),"no network");
  check(OopsPolicy.kind(new IOException(new UnknownHostException("kiktv.pages.dev")),true,false).equals(OopsPolicy.OFFLINE),"unknown host");
  check(OopsPolicy.kind(new ConnectException("refused"),true,false).equals(OopsPolicy.OFFLINE),"refused connection");
  check(OopsPolicy.kind(new IOException(new SocketTimeoutException("read")),true,true).equals(OopsPolicy.SLOW),"timeout");
  check(OopsPolicy.kind(new IOException("Source blocked this IP"),true,true).equals(OopsPolicy.SOURCE),"source paused");
  check(OopsPolicy.kind(new IOException(new OopsPolicy.ServiceError(503,true,"busy")),true,false).equals(OopsPolicy.SOURCE),"503 retry:true");
  check(OopsPolicy.kind(new OopsPolicy.ServiceError(502,false,"bad gateway"),true,false).equals(OopsPolicy.SERVER),"5xx");
  check(OopsPolicy.kind(new OopsPolicy.ServiceError(404,false,"gone"),true,false).equals(OopsPolicy.MISSING),"404");
  check(OopsPolicy.kind(new OopsPolicy.ServiceError(400,false,"bad"),true,false).equals(OopsPolicy.OTHER),"400");
  check(OopsPolicy.kind(null,true,false).equals(OopsPolicy.OTHER),"no error at hand");
  for(String k:new String[]{OopsPolicy.OFFLINE,OopsPolicy.SOURCE,OopsPolicy.SERVER,OopsPolicy.SLOW,OopsPolicy.MISSING,OopsPolicy.OTHER,OopsPolicy.VIDEO,OopsPolicy.VOICE,"unknown"})check(OopsPolicy.copy(k)[0].startsWith("Упс, "),"heading "+k);
  check(OopsPolicy.copy(OopsPolicy.SOURCE)[1].contains("вже працюємо"),"we are on it");
  check(OopsPolicy.auto(OopsPolicy.SOURCE)&&OopsPolicy.auto(OopsPolicy.OFFLINE)&&OopsPolicy.auto(OopsPolicy.SLOW)&&OopsPolicy.auto(OopsPolicy.SERVER)&&OopsPolicy.auto(OopsPolicy.VIDEO),"temporary causes retry");
  check(!OopsPolicy.auto(OopsPolicy.OTHER)&&!OopsPolicy.auto(OopsPolicy.MISSING)&&!OopsPolicy.auto(OopsPolicy.VOICE),"lasting causes wait for the viewer");
  // The same thing failing again waits longer, 10 → 20 → 30 → 60 → 60 s; a quiet 3 minutes or a press starts over.
  long t=1_000_000_000_000L;int[] want={10,20,30,60,60};for(int i=0;i<want.length;i++)check(OopsPolicy.retryWait("film:x",t+i*1000)==want[i],"wait step "+i);
  check(OopsPolicy.retryWait("film:x",t+600_000)==10,"quiet spell");check(OopsPolicy.retryWait("film:y",t)==10,"keys apart");check(OopsPolicy.retryWait("",t)==10&&OopsPolicy.retryWait("",t)==10,"no key");
  OopsPolicy.retryWait("film:z",t);OopsPolicy.retryWait("film:z",t+1);OopsPolicy.forget("film:z");check(OopsPolicy.retryWait("film:z",t+2)==10,"press starts over");
  System.out.println("«Упс» screen causes, words and retry waits passed");
 }
}
