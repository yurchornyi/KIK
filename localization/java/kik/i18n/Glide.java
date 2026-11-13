package kik.i18n;
import android.content.Context;
import android.graphics.Rect;
import android.view.FocusFinder;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.AnimationUtils;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;

/**
 * Scroll boxes for the remote. Android's own ScrollView brings a focused view into view with an instant jump, and its
 * animated scroll turns into a jump as well whenever keys come less than 250 ms apart: on the projector (13.11) a step
 * down to the next row jumped ~300 px in one frame, and quick presses along a shelf jumped a whole card per frame.
 * Here a focus move only says where the box should be (FocusScrollPolicy) and one glide eases there frame by frame;
 * the next press changes where it eases to, without a jump or a stop, so a held key gives one smooth run.
 */
final class Glide {
 private Glide(){}
 private static int moving;
 /** A box on screen is still gliding: the home waits for it before it repaints the hero (both at once drop frames). */
 static boolean moving(){return moving>0;}

 /** The page, top to bottom: the focused row comes into view with its heading, and only when it would leave the screen. */
 static final class Page extends ScrollView{
  private final Motion motion;
  Page(Context c){
   super(c);motion=new Motion(this,true);setVerticalScrollBarEnabled(false);setSmoothScrollingEnabled(false);
   motion.margins(Ui.dp(c,16),Ui.dp(c,24),Ui.dp(c,8));
  }
  /** Glides to {@code y}; {@link #scrollTo} still jumps (a page that opens at its top). */
  void glide(int y){motion.glide(y);}
  @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect r){return motion==null?super.computeScrollDeltaToGetChildRectOnScreen(r):motion.show(r);}
  @Override public boolean arrowScroll(int direction){return motion.arrow(direction);}
  @Override public void scrollTo(int x,int y){if(motion!=null&&(x!=getScrollX()||y!=getScrollY()))motion.moved();super.scrollTo(x,y);}
  @Override protected void onDetachedFromWindow(){motion.stop();super.onDetachedFromWindow();}
 }

 /** A row, left to right: chips and buttons come into view as needed; a shelf keeps the focused card in one place. */
 static final class Row extends HorizontalScrollView{
  private final Motion motion;private boolean shelf;
  Row(Context c){
   super(c);motion=new Motion(this,false);setHorizontalScrollBarEnabled(false);setSmoothScrollingEnabled(false);
   motion.margins(0,0,Ui.dp(c,16));
  }
  /** Netflix's shelf: the focused card's left edge stands {@code at} px from the row's, and ◀ ▶ stop at the row's ends
   *  instead of jumping to a card of another row. */
  Row shelf(int at){shelf=true;motion.anchor=at;return this;}
  void glide(int x){motion.glide(x);}
  @Override public boolean executeKeyEvent(KeyEvent e){
   int k=e.getKeyCode();
   if(shelf&&(k==KeyEvent.KEYCODE_DPAD_LEFT||k==KeyEvent.KEYCODE_DPAD_RIGHT)){if(e.getAction()==KeyEvent.ACTION_DOWN)motion.arrow(k==KeyEvent.KEYCODE_DPAD_LEFT?View.FOCUS_LEFT:View.FOCUS_RIGHT);return true;}
   return super.executeKeyEvent(e);
  }
  @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect r){return motion==null?super.computeScrollDeltaToGetChildRectOnScreen(r):motion.show(r);}
  @Override public boolean arrowScroll(int direction){return motion.arrow(direction);}
  @Override public void scrollTo(int x,int y){if(motion!=null&&(x!=getScrollX()||y!=getScrollY()))motion.moved();super.scrollTo(x,y);}
  @Override protected void onDetachedFromWindow(){motion.stop();super.onDetachedFromWindow();}
 }

 /** One box's glide: where it is (to a fraction of a pixel), where it is going, and the frames in between. */
 private static final class Motion implements Runnable{
  private final ViewGroup box;private final boolean vertical;private final float floor;private final Rect tmp=new Rect();
  /** A shelf's place for the focused card, px from the box's edge; -1 = only bring it into view. */
  int anchor=-1;
  private int before,after,margin;
  private float at;private int to;private boolean running,self;private long last;
  Motion(ViewGroup box,boolean vertical){this.box=box;this.vertical=vertical;floor=FocusScrollPolicy.FLOOR_DP*box.getResources().getDisplayMetrics().density;}
  void margins(int before,int after,int margin){this.before=before;this.after=after;this.margin=margin;}
  private int now(){return vertical?box.getScrollY():box.getScrollX();}
  private int viewport(){return vertical?box.getHeight():box.getWidth();}
  private int range(){
   if(box.getChildCount()==0)return 0;View c=box.getChildAt(0);
   return Math.max(0,vertical?c.getHeight()-(box.getHeight()-box.getPaddingTop()-box.getPaddingBottom()):c.getWidth()-(box.getWidth()-box.getPaddingLeft()-box.getPaddingRight()));
  }
  /** Where the box will stand: the end of the glide in flight, else where it is. */
  private int heading(){return running?to:now();}
  private int room(){return viewport()-before-after-2*margin;}

  /** Android asks to bring {@code r} into view (a focus move, a cursor, a resize): the box glides there and lets
   *  Android itself move nothing. */
  int show(Rect r){
   int start=vertical?r.top:r.left,end=vertical?r.bottom:r.right;
   int[] s=section();
   if(s!=null&&Math.max(end,s[1])-Math.min(start,s[0])<=room()){start=Math.min(start,s[0]);end=Math.max(end,s[1]);}
   glide(anchor>=0?FocusScrollPolicy.anchor(start,anchor,range()):FocusScrollPolicy.reveal(heading(),viewport(),before,after,start,end,margin));
   return 0;
  }
  /** The focused view's part of the box: the largest view around it that still fits on screen, with what sits just
   *  above it and has nothing to focus (a row's heading, a film's title over its buttons). */
  private int[] section(){
   View f=box.findFocus();
   if(f==null||f==box||box.getChildCount()==0||!(box.getChildAt(0) instanceof ViewGroup))return null;
   ViewGroup content=(ViewGroup)box.getChildAt(0);int room=room();int[] best=null;
   for(View v=f;v!=content;){
    int[] e=extent(v);if(e[1]-e[0]>room)break;best=e;
    ViewParent p=v.getParent();
    if(p==content){
     for(int i=content.indexOfChild(v)-1;i>=0;i--){
      View label=content.getChildAt(i);if(label.getVisibility()==View.GONE)continue;
      if(label.getVisibility()!=View.VISIBLE||label.hasFocusable())break;
      int[] l=extent(label);if(best[1]-l[0]>room)break;best=new int[]{l[0],best[1]};
     }
     break;
    }
    if(!(p instanceof View))break;v=(View)p;
   }
   return best;
  }
  private int[] extent(View v){v.getDrawingRect(tmp);box.offsetDescendantRectToMyCoords(v,tmp);return vertical?new int[]{tmp.top,tmp.bottom}:new int[]{tmp.left,tmp.right};}

  /** ▲ ▼ (◀ ▶ in a row): the focus moves to the next view in the box, which then glides into view. With nothing more
   *  to focus that way, the box shows the rest of itself, half a screen at a time (text under the last button). */
  boolean arrow(int direction){
   View current=box.findFocus();if(current==box)current=null;
   View next=FocusFinder.getInstance().findNextFocus(box,current,direction);
   if(next!=null&&(next==current||next.requestFocus(direction)))return true;
   boolean back=direction==View.FOCUS_UP||direction==View.FOCUS_LEFT;
   int from=heading(),by=back?-Math.min(viewport()/2,from):Math.min(viewport()/2,range()-from);
   if(by==0)return false;
   glide(from+by);return true;
  }

  void glide(int target){
   target=Math.max(0,Math.min(range(),target));
   if(!running&&target==now())return;
   if(!Ui.animations()||!box.isAttachedToWindow()){stop();jump(target);return;}
   to=target;
   if(!running){running=true;moving++;at=now();last=AnimationUtils.currentAnimationTimeMillis();box.postOnAnimation(this);}
  }
  @Override public void run(){
   if(!running)return;
   long t=AnimationUtils.currentAnimationTimeMillis();float dt=Math.max(0,Math.min(50,t-last));last=t;
   to=Math.min(to,range());
   at=FocusScrollPolicy.step(at,to,dt,floor,viewport()*FocusScrollPolicy.LAG);
   jump(Math.round(at));
   if(at==to)stop();else box.postOnAnimation(this);
  }
  private void jump(int p){self=true;try{if(vertical)box.scrollTo(box.getScrollX(),p);else box.scrollTo(p,box.getScrollY());}finally{self=false;}}
  /** Someone else moved the box (a page opening at its top): the glide gives way. */
  void moved(){if(!self)stop();}
  void stop(){if(!running)return;running=false;moving--;box.removeCallbacks(this);}
 }
}
