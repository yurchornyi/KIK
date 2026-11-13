package kik.i18n;

/**
 * Focused cards stay visible without moving the whole shelf on every D-pad press, and the glide that moves a box
 * (Glide) never jumps: no frame takes most of the way at once, a new press changes course without a stop, and a
 * held key keeps the focus near the screen.
 */
public final class FocusScrollCheck {
 /** The projector: 1920 × 1080 at density 2. */
 static final int HEIGHT=1080,WIDTH=1920;static final float FLOOR=FocusScrollPolicy.FLOOR_DP*2,FRAME=16.7f;

 public static void main(String[] args){
  int checks=0;
  for(int width:new int[]{640,960,1280,1920,3840}){
   int safe=32,card=148,margin=8;
   for(int item=0;item<5;item++){
    int left=safe+item*card,right=left+card;
    int target=FocusScrollPolicy.target(0,width,safe,safe,left,right,margin);
    if(right+margin<=width-safe&&target!=0)throw new AssertionError("Visible card caused a scroll");
    if(target+width-safe<right+margin)throw new AssertionError("Focused card was clipped");
    checks++;
   }
  }
  if(FocusScrollPolicy.target(500,960,32,32,100,248,8)!=60)throw new AssertionError("Focus did not return left");
  if(FocusScrollPolicy.target(0,0,32,32,100,248,8)!=0)throw new AssertionError("Unlaid-out shelf moved");
  checks+=2;

  // The page: a row below the screen comes up just enough; back up to the hero, the page shows its top.
  if(FocusScrollPolicy.reveal(0,HEIGHT,32,48,900,1500,16)!=484)throw new AssertionError("Row below was not brought up just enough");
  if(FocusScrollPolicy.reveal(1200,HEIGHT,32,48,148,748,16)!=0)throw new AssertionError("Back at the hero, the top stayed hidden");
  if(FocusScrollPolicy.reveal(2000,HEIGHT,32,48,1500,1800,16)!=1452)throw new AssertionError("A row higher up overshot to the top");
  if(FocusScrollPolicy.reveal(300,HEIGHT,32,48,400,700,16)!=300)throw new AssertionError("A visible row moved the page");
  checks+=4;
  // The shelf: the focused card stands at the anchor, the row's ends stop it.
  if(FocusScrollPolicy.anchor(64,64,5000)!=0||FocusScrollPolicy.anchor(64+332*3,64,5000)!=996||FocusScrollPolicy.anchor(9000,64,5000)!=5000||FocusScrollPolicy.anchor(500,64,-1)!=0)
   throw new AssertionError("Shelf anchor is wrong");
  checks++;

  // One step down a row (600 px) and along a shelf (one card, 332 px): eased, short, exact.
  checks+=glide(600,HEIGHT,"row");
  checks+=glide(332,WIDTH,"card");
  checks+=glide(-332,WIDTH,"card back");
  checks+=glide(20,WIDTH,"nudge");

  // A frame-dropping projector (25 fps) still ends on time and never overshoots.
  float at=0;int frames=0;
  while(at!=600&&frames<100){float next=FocusScrollPolicy.step(at,600,40,FLOOR,HEIGHT*FocusScrollPolicy.LAG);if(next<at||next>600)throw new AssertionError("Slow frames overshot");at=next;frames++;}
  if(frames*40>450)throw new AssertionError("Slow frames took "+frames*40+" ms");
  checks++;

  // A second press half way: the box keeps moving the same way, no frame jumps, and it ends at the new place.
  at=0;int to=600;float lastMove=0;
  for(int f=0;f<200&&at!=to;f++){
   if(f==6)to=1200;
   float next=FocusScrollPolicy.step(at,to,FRAME,FLOOR,HEIGHT*FocusScrollPolicy.LAG),move=next-at;
   if(move<0)throw new AssertionError("Second press turned the glide back");
   if(f==6&&move<lastMove*0.5f)throw new AssertionError("Second press stalled the glide");
   if(move>Math.max(0.4f*(to-at),FLOOR*FRAME+1))throw new AssertionError("Second press jumped "+move+" px in one frame");
   lastMove=move;at=next;
  }
  if(at!=to)throw new AssertionError("Second press did not end at its row");
  checks++;

  // Changing direction half way (▼ then ▲): it turns back without a jump.
  at=0;to=600;
  for(int f=0;f<200&&!(f>6&&at==to);f++){
   if(f==6)to=0;
   float next=FocusScrollPolicy.step(at,to,FRAME,FLOOR,HEIGHT*FocusScrollPolicy.LAG);
   if(Math.abs(next-at)>Math.max(0.4f*Math.abs(to-at),FLOOR*FRAME+1))throw new AssertionError("Turning back jumped");
   at=next;
  }
  if(at!=0)throw new AssertionError("Turning back did not end at the top");
  checks++;

  // A held key: a row every 50 ms (Android's repeat) for a second. The page runs behind, but not by a screen.
  at=0;to=0;float behind=0;
  for(int f=0;f<200;f++){
   float t=f*FRAME;if(t<1000&&(int)(t/50)!=(int)((t-FRAME)/50))to+=600;
   at=FocusScrollPolicy.step(at,to,FRAME,FLOOR,HEIGHT*FocusScrollPolicy.LAG);behind=Math.max(behind,to-at);
  }
  if(behind>0.6f*HEIGHT)throw new AssertionError("Held key left the focus "+behind+" px behind");
  if(at!=to)throw new AssertionError("Held key did not settle");
  checks++;

  if(FocusScrollPolicy.step(10,10,FRAME,FLOOR,400)!=10||FocusScrollPolicy.step(9.7f,10,FRAME,FLOOR,400)!=10||FocusScrollPolicy.step(0,600,0,FLOOR,400)!=0)
   throw new AssertionError("Glide edge cases are wrong");
  checks++;
  System.out.println("Focus scrolling passed: "+checks+" checks");
 }

 /** One glide of {@code by} px at 60 fps: the first frame takes a visible part but not most of it, every frame moves
  *  the same way and less than the one before it (ease-out) or at the floor, and it lands exactly within 400 ms. */
 private static int glide(int by,int viewport,String what){
  int from=by<0?2000:0,to=from+by;float at=from,last=Float.MAX_VALUE;int frames=0;
  while(at!=to){
   float next=FocusScrollPolicy.step(at,to,FRAME,FLOOR,viewport*FocusScrollPolicy.LAG),move=Math.abs(next-at);
   if(Math.signum(next-at)!=Math.signum(by))throw new AssertionError(what+": moved the wrong way");
   if(frames==0&&Math.abs(by)>100&&(move<0.1f*Math.abs(by)||move>0.4f*Math.abs(by)))throw new AssertionError(what+": first frame moved "+move+" px of "+by);
   if(move>last+0.01f&&move>FLOOR*FRAME+0.01f)throw new AssertionError(what+": sped up at frame "+frames);
   last=move;at=next;frames++;
   if(frames>100)throw new AssertionError(what+": never landed");
  }
  if(frames*FRAME>400)throw new AssertionError(what+": took "+frames*FRAME+" ms");
  return 1;
 }
}
