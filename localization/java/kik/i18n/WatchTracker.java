package kik.i18n;
/** Counts advancing playback time; seeks, stalls and background gaps are not watch time. */
final class WatchTracker {
 private long clock=-1,position;private boolean playing;
 long sample(long now,long pos,boolean on){long dt=clock<0?0:now-clock,dp=pos-position;long watched=playing&&on&&dt>0&&dt<=2000&&dp>0&&dp<=dt*3+1000?dt:0;clock=now;position=pos;playing=on;return watched;}
 void reset(){clock=-1;playing=false;}
}
