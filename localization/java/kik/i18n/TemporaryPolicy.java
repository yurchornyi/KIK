package kik.i18n;

/**
 * When a «Тимчасовий вхід» ends, as plain arithmetic on wall-clock times so a check can fake the clock. Closing KIK,
 * standby and a restart of the TV end nothing by themselves: the visit ends after {@link #IDLE} with nobody using KIK
 * (no remote key on its screens, nothing playing), or {@link #LIFETIME} after the sign-in, whichever comes first.
 * The server keeps a TV sign-in for 90 days of use, far longer, so the TV itself signs the server session out then.
 */
public final class TemporaryPolicy {
 public static final long MINUTE=60_000L,IDLE=60*MINUTE,LIFETIME=12*60*MINUTE;
 /** How far the clock may step back (a time sync) before the elapsed time counts as unknown. */
 static final long SKEW=MINUTE;

 /** The sign-in's start; a visit begun before 1.31 kept no start, so its last use stands in for it. */
 static long since(long since,long active){return since>0?since:active;}
 /** When the visit runs out if nobody uses KIK meanwhile; 0 when its times are unknown. */
 public static long deadline(long since,long active){
  long start=since(since,active);if(active<=0||start<=0)return 0;
  return Math.min(active+IDLE,start+LIFETIME);
 }
 /**
  * Whether the visit is over at {@code now}. Unknown times end it, and so does a clock far behind the saved ones (a TV
  * that booted with a wrong date): how long it was cannot be told.
  */
 public static boolean over(long now,long since,long active){
  long start=since(since,active),due=deadline(since,active);if(due==0)return true;
  if(now<active-SKEW||now<start-SKEW)return true;
  return now>=due;
 }
 private TemporaryPolicy(){}
}
