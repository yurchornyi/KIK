package kik.i18n;

/**
 * Where a scroll box goes for the focused view, and how it glides there ({@link Glide}). Plain arithmetic, so
 * scripts/FocusScrollCheck.java checks it without a TV.
 */
final class FocusScrollPolicy {
 private FocusScrollPolicy(){}
 /** The glide's ease-out: the same share of what is left in the same time, 90 % of the way in about 180 ms. */
 static final float TAU=80f;
 /** Further behind than this share of the screen (a held key), the glide hurries, so the focus never stays off screen. */
 static final float LAG=0.4f;
 /** The slowest a glide moves, in dp per ms: its last pixels end crisply instead of creeping. */
 static final float FLOOR_DP=0.3f;

 /** Move a TV shelf or the page only when the focused card would leave the visible safe area. */
 static int target(int scroll,int viewport,int before,int after,int itemStart,int itemEnd,int margin){
  if(viewport<=0)return Math.max(0,scroll);
  int start=itemStart-margin-before,end=itemEnd+margin-viewport+after;
  if(start<scroll)return Math.max(0,start);
  if(end>scroll)return Math.max(0,end);
  return Math.max(0,scroll);
 }
 /** As {@link #target}, and on the way back to the start, the very start once the item fits there: the page's top
  *  with the hero, a row's first chips. */
 static int reveal(int scroll,int viewport,int before,int after,int itemStart,int itemEnd,int margin){
  int to=target(scroll,viewport,before,after,itemStart,itemEnd,margin);
  return to<scroll&&itemEnd+margin+after<=viewport?0:to;
 }
 /** A shelf, as on Netflix: the focused card stands {@code anchor} px from the row's edge and the row slides under it,
  *  up to its ends. */
 static int anchor(int itemStart,int anchor,int range){return Math.max(0,Math.min(Math.max(0,range),itemStart-anchor));}
 /**
  * One frame of a glide from {@code at} toward {@code to}, {@code dt} ms after the last one: an ease-out (TAU), never
  * slower than {@code floor} px per ms, and quicker the further it is more than {@code lag} px behind. It never
  * overshoots, and a new {@code to} only changes where it eases to: no jump, no stop.
  */
 static float step(float at,int to,float dt,float floor,float lag){
  float gap=to-at,far=Math.abs(gap);
  if(far<0.5f)return to;
  if(dt<=0)return at;
  float tau=lag>0&&far>lag?TAU*lag/far:TAU;
  float move=Math.max(far*(1f-(float)Math.exp(-dt/tau)),floor*dt);
  return move>=far?to:at+Math.signum(gap)*move;
 }
}
