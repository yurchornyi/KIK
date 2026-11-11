import kik.i18n.TemporaryPolicy;
/** «Тимчасовий вхід» on a fake clock: closing KIK keeps the visit, an idle hour or 12 hours in all end it. */
public final class TemporaryCheck {
 static void check(boolean v,String what){if(!v)throw new AssertionError(what);}
 public static void main(String[] args){
  final long M=TemporaryPolicy.MINUTE,H=60*M,t0=1_800_000_000_000L;
  check(TemporaryPolicy.IDLE==H&&TemporaryPolicy.LIFETIME==12*H,"an idle hour, 12 hours in all");
  // Signed in at t0 and KIK closed at once: reopening within the hour finds the visit, after it does not.
  check(!TemporaryPolicy.over(t0,t0,t0),"fresh");
  check(!TemporaryPolicy.over(t0+59*M,t0,t0),"closed and reopened after 59 min");
  check(TemporaryPolicy.over(t0+H,t0,t0),"an hour with nobody ends it");
  check(TemporaryPolicy.deadline(t0,t0)==t0+H,"the job wakes an hour after the last use");
  // Use moves the idle deadline, up to 12 hours after the sign-in.
  long used=t0+5*H;
  check(!TemporaryPolicy.over(t0+5*H+50*M,t0,used),"use 50 min ago keeps it");
  check(TemporaryPolicy.deadline(t0,used)==used+H,"use moves the deadline");
  long late=t0+11*H+30*M;
  check(TemporaryPolicy.deadline(t0,late)==t0+12*H,"the lifetime caps the deadline");
  check(!TemporaryPolicy.over(t0+12*H-1,t0,late),"just before 12 hours");
  check(TemporaryPolicy.over(t0+12*H,t0,t0+12*H-M),"12 hours end it even while someone watches");
  // A visit from before 1.31 has no start: its last use stands in.
  check(TemporaryPolicy.deadline(0,used)==used+H&&!TemporaryPolicy.over(used+30*M,0,used)&&TemporaryPolicy.over(used+H,0,used),"no start kept");
  // A use not saved yet: the sign-in itself counts as the last use.
  check(!TemporaryPolicy.over(t0+59*M,t0,0)&&TemporaryPolicy.over(t0+H,t0,0)&&TemporaryPolicy.deadline(t0,0)==t0+H,"no use saved yet");
  // No time at all, or a clock far behind the saved ones, cannot tell how long it was: the visit ends.
  check(TemporaryPolicy.over(t0,0,0)&&TemporaryPolicy.deadline(0,0)==0,"no time kept");
  check(!TemporaryPolicy.over(used-30_000,t0,used),"a time sync a few seconds back");
  check(TemporaryPolicy.over(used-2*M,t0,used),"a clock 2 min behind the last use");
  check(TemporaryPolicy.over(t0-2*M,t0,t0-2*M),"a clock behind the sign-in");
  System.out.println("Temporary sign-in policy checks passed");
 }
}
