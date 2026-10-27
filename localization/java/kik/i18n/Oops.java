package kik.i18n;
import android.animation.*;
import android.content.Context;
import android.graphics.*;
import android.text.TextUtils;
import android.view.*;
import android.view.animation.*;
import android.widget.*;

/**
 * KIK's «Упс» screen on TV, as on the web (web/public/oops.js; the words and waits are OopsPolicy's): the KIK mark breathing
 * over a soft red glow, what happened in plain words, a focused «Спробувати зараз» for the remote and, while the cause is
 * temporary, a countdown that tries again by itself. It is opaque, never a dim layer over the page: screens hide what lies
 * under it, and only the small mark and the countdown bar redraw, so the projector's weak GPU is spared.
 */
public final class Oops extends LinearLayout {
 /** Load again; pressed is true when the viewer asked for it, false for the countdown. */
 public interface Retry{void again(boolean pressed);}
 private final String kind,key;private final boolean auto;private final Retry retry;private final Button again;
 private TextView status;private View fill;private View glow,mark;private AnimatorSet breathing;
 private int wait,left;private boolean done,waitingForNetwork;

 /** The cause as the viewer should read it: OopsPolicy's kind for this error on this TV. */
 public static String kind(Context c,Throwable e){return OopsPolicy.kind(e,online(c),Site.paused(e));}
 static boolean online(Context c){
  try{android.net.ConnectivityManager cm=(android.net.ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);android.net.NetworkInfo n=cm.getActiveNetworkInfo();return n!=null&&n.isConnected();}catch(Exception e){return true;}
 }

 public Oops(Context c,String kind,String title,String key,Retry retry){this(c,kind,OopsPolicy.auto(kind),title,key,retry,null,null);}
 /** auto=false keeps a temporary cause on the button only (the player stops after 30 tries); other adds a second button. */
 public Oops(Context c,String kind,boolean auto,String title,String key,Retry retry,String otherLabel,final Runnable other){
  super(c);this.kind=kind;this.auto=auto&&retry!=null;this.key=key==null?"":key;this.retry=retry;
  setOrientation(VERTICAL);setGravity(Gravity.CENTER);setBackgroundColor(Ui.BG);setClickable(true);setPadding(dp(40),dp(24),dp(40),dp(24));
  setClipChildren(false);setClipToPadding(false);
  FrameLayout box=new FrameLayout(c);glow=new Glow(c);box.addView(glow,new FrameLayout.LayoutParams(-1,-1));
  mark=Brand.logo(c,150,70);box.addView(mark,new FrameLayout.LayoutParams(dp(150),dp(70),Gravity.CENTER));
  addView(box,new LinearLayout.LayoutParams(dp(270),dp(160)));
  if(title!=null&&!title.isEmpty()){// Upper case by hand: a single-line TextView drops setAllCaps.
   TextView t=Ui.text(c,title.toUpperCase(new java.util.Locale("uk")),13,Ui.GREY);t.setTypeface(Ui.MEDIUM);t.setLetterSpacing(0.08f);t.setSingleLine(true);t.setEllipsize(TextUtils.TruncateAt.END);t.setGravity(Gravity.CENTER);
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(Ui.contentWidth(c,600,80),-2);lp.bottomMargin=dp(8);addView(t,lp);}
  String[] words=OopsPolicy.copy(kind);
  TextView heading=Ui.bold(c,words[0],30);heading.setGravity(Gravity.CENTER);heading.setMaxLines(2);heading.setEllipsize(TextUtils.TruncateAt.END);
  addView(heading,new LinearLayout.LayoutParams(Ui.contentWidth(c,640,80),-2));
  TextView text=Ui.text(c,words[1],17,0xffd0d0d6);text.setGravity(Gravity.CENTER);text.setLineSpacing(0,1.2f);
  LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(Ui.contentWidth(c,560,80),-2);tl.topMargin=dp(10);addView(text,tl);
  LinearLayout buttons=new LinearLayout(c);buttons.setOrientation(HORIZONTAL);buttons.setGravity(Gravity.CENTER);buttons.setClipChildren(false);buttons.setClipToPadding(false);
  // Two buttons are both secondary: on a TV only the focused one may light up, as in KIK's dialogs.
  boolean pair=otherLabel!=null&&other!=null;
  again=Ui.pill(c,this.auto?"Спробувати зараз":"Спробувати ще",!pair);again.setOnClickListener(v->run(true));
  if(retry!=null)buttons.addView(again,new LinearLayout.LayoutParams(-2,dp(48)));
  if(pair){Button b=Ui.pill(c,otherLabel,false);b.setOnClickListener(v->{if(!done)other.run();});LinearLayout.LayoutParams bl=new LinearLayout.LayoutParams(-2,dp(48));bl.leftMargin=dp(12);buttons.addView(b,bl);}
  LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,-2);bp.topMargin=dp(26);if(buttons.getChildCount()>0)addView(buttons,bp);
  if(!this.auto)return;
  // The countdown: a thin red line filling as the next try comes, and the seconds under it.
  FrameLayout bar=new FrameLayout(c);bar.setBackground(Ui.shape(0x1affffff,dp(2),0,0));bar.setClipChildren(true);
  fill=new View(c);fill.setBackground(Ui.shape(Ui.RED,dp(2),0,0));fill.setPivotX(0);fill.setScaleX(0);bar.addView(fill,new FrameLayout.LayoutParams(-1,-1));
  LinearLayout.LayoutParams barLp=new LinearLayout.LayoutParams(dp(200),dp(4));barLp.topMargin=dp(26);addView(bar,barLp);
  // A fixed box: the seconds change every second without laying the screen out again.
  status=Ui.text(c,"",14,Ui.GREY);status.setGravity(Gravity.CENTER);status.setSingleLine(true);
  LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(dp(420),dp(24));sl.topMargin=dp(10);addView(status,sl);
  wait=OopsPolicy.retryWait(this.key,System.currentTimeMillis());left=wait;paint(false);
 }
 private int dp(float n){return Ui.dp(getContext(),n);}

 private final Runnable tick=new Runnable(){public void run(){
  if(done)return;postDelayed(this,1000);
  // Another app in front, or the screen hidden under the intro: the countdown holds.
  if(getWindowVisibility()!=VISIBLE||!isShown())return;
  if(OopsPolicy.OFFLINE.equals(kind)){boolean on=online(getContext());if(!on){waitingForNetwork=true;paint(false);return;}
   // The connection came back: try at once, as the web does on its «online» event.
   if(waitingForNetwork){Oops.this.run(false);return;}}
  if(--left<=0){Oops.this.run(false);return;}
  paint(true);
 }};
 private void paint(boolean animate){
  if(status==null)return;
  if(waitingForNetwork){status.setText("Чекаємо на з’єднання…");fill.animate().cancel();fill.setScaleX(0);return;}
  status.setText("Спробуємо ще раз через "+left+" с");
  float to=1f-(float)left/wait;fill.animate().cancel();
  if(animate&&Ui.animations())fill.animate().scaleX(to).setDuration(1000).setInterpolator(new LinearInterpolator()).start();else fill.setScaleX(to);
 }
 private void run(boolean pressed){
  if(done||retry==null)return;done=true;removeCallbacks(tick);
  if(pressed)OopsPolicy.forget(key);
  if(status!=null){status.setText("Пробуємо ще раз…");fill.animate().cancel();fill.setScaleX(1);}
  retry.again(pressed);
 }

 @Override protected void onAttachedToWindow(){
  super.onAttachedToWindow();
  if(again.getParent()!=null)post(again::requestFocus);
  if(auto&&!done){removeCallbacks(tick);postDelayed(tick,1000);}
  breathe(getWindowVisibility()==VISIBLE&&getVisibility()==VISIBLE);
 }
 @Override protected void onDetachedFromWindow(){removeCallbacks(tick);breathe(false);super.onDetachedFromWindow();}
 @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);breathe(visibility==VISIBLE&&isAttachedToWindow()&&isShown());}
 // Shown later (the player's intro ends): the button takes the remote then.
 @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(!isAttachedToWindow())return;boolean shown=isShown();breathe(shown&&getWindowVisibility()==VISIBLE);if(shown&&again.getParent()!=null)post(again::requestFocus);}
 /**
  * The mark breathes and its glow pulses, 2.8 s a breath as on the web. Both are small hardware layers: each frame only moves
  * them, nothing is painted again. Off while the screen is hidden, and when the TV turns animations off.
  */
 private void breathe(boolean on){
  if(!on||!Ui.animations()){if(breathing!=null){breathing.cancel();breathing=null;glow.setAlpha(1f);mark.setScaleX(1f);mark.setScaleY(1f);glow.setLayerType(LAYER_TYPE_NONE,null);mark.setLayerType(LAYER_TYPE_NONE,null);}return;}
  if(breathing!=null)return;
  glow.setLayerType(LAYER_TYPE_HARDWARE,null);mark.setLayerType(LAYER_TYPE_HARDWARE,null);
  ObjectAnimator g=ObjectAnimator.ofFloat(glow,View.ALPHA,0.5f,1f);
  ObjectAnimator m=ObjectAnimator.ofPropertyValuesHolder(mark,PropertyValuesHolder.ofFloat(View.SCALE_X,1f,1.045f),PropertyValuesHolder.ofFloat(View.SCALE_Y,1f,1.045f));
  for(ObjectAnimator a:new ObjectAnimator[]{g,m}){a.setDuration(1400);a.setRepeatCount(ValueAnimator.INFINITE);a.setRepeatMode(ValueAnimator.REVERSE);a.setInterpolator(new AccelerateDecelerateInterpolator());}
  breathing=new AnimatorSet();breathing.playTogether(g,m);breathing.start();
 }
 /** The soft red glow behind the mark: an ellipse fading from KIK red to nothing, like the web's radial gradient. */
 private static final class Glow extends View {
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  Glow(Context c){super(c);}
  @Override protected void onSizeChanged(int w,int h,int ow,int oh){paint.setShader(new RadialGradient(w/2f,w/2f,w/2f,0x47e50914,0x00e50914,Shader.TileMode.CLAMP));}
  @Override protected void onDraw(Canvas canvas){int w=getWidth(),h=getHeight();if(w==0||h==0)return;canvas.save();canvas.scale(1f,(float)h/w);canvas.drawCircle(w/2f,w/2f,w/2f,paint);canvas.restore();}
 }
}
