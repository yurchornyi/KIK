package kik.i18n;

/** Move a TV shelf or the page only when the focused card would leave the visible safe area. */
final class FocusScrollPolicy {
 private FocusScrollPolicy(){}
 static int target(int scroll,int viewport,int before,int after,int itemStart,int itemEnd,int margin){
  if(viewport<=0)return Math.max(0,scroll);
  int start=itemStart-margin-before,end=itemEnd+margin-viewport+after;
  if(start<scroll)return Math.max(0,start);
  if(end>scroll)return Math.max(0,end);
  return Math.max(0,scroll);
 }
}
