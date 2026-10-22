package kik.i18n;
import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.util.Log;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * Netflix-style controls over the app's player. Remote keys are handled here; playback is driven through
 * the media3 player held by PlaybackService. "Озвучка і якість" hands the remote to the app's own panel.
 *
 * Obfuscated media3 names in RezkaTV 1.2.0 (Player = v05): K0() position, getDuration(), L(long) seekTo,
 * S(boolean) setPlayWhenReady, m() getPlayWhenReady, b(float) setPlaybackSpeed, getPlaybackParameters().a speed.
 * PlaybackService.T.L.A is the player, PlaybackService.T.n().g holds the session (ez4: q film, c season, d episode).
 */
public final class PlayerOverlay {
 private static final int RED=0xffe50914;
 private static final float[] SPEEDS={0.75f,1f,1.25f,1.5f,2f};
 private static final long HIDE_MS=4000,PASS_MS=12000;
 private static final WeakHashMap<Activity,PlayerOverlay> ALL=new WeakHashMap<Activity,PlayerOverlay>();

 private final Activity act;private final FrameLayout root;private ViewGroup content;private View app;private final Handler main=new Handler(Looper.getMainLooper());
 private LinearLayout top,bottom,buttons,speedRow;private TextView title,sub,timeLeft,timeRight,flash,speedButton;
 private View played,track,knob;private final List<TextView> controls=new ArrayList<TextView>();
 private int focus=1;private boolean visible,progressFocus,passThrough,speedOpen;private long lastPass;
 private final Runnable hide=()->show(false),tick=new Runnable(){public void run(){update();if(visible||flash.getAlpha()>0)main.postDelayed(this,500);}};

 private int dp(float n){return Math.round(n*act.getResources().getDisplayMetrics().density);}

 public static void attach(Activity a){
  if(ALL.containsKey(a))return;
  try{ALL.put(a,new PlayerOverlay(a));Log.d("KIK","Player overlay attached");}catch(Exception e){Log.e("KIK","Player overlay",e);}
 }

 /** @return true when the key is consumed by the overlay. */
 public static boolean key(Activity a,KeyEvent e){
  PlayerOverlay o=ALL.get(a);
  if(o==null){attach(a);o=ALL.get(a);}
  return o!=null&&o.onKey(e);
 }

 private PlayerOverlay(Activity a){
  act=a;
  // Compose consumes remote keys at the pre-IME stage, so the overlay holds focus and sees them first.
  root=new FrameLayout(a){
   @Override public boolean dispatchKeyEventPreIme(KeyEvent e){return onKey(e)||super.dispatchKeyEventPreIme(e);}
  };
  content=(ViewGroup)a.findViewById(android.R.id.content);
  app=content.getChildCount()>0?content.getChildAt(0):null;
  content.addView(root,new FrameLayout.LayoutParams(-1,-1));
  root.setFocusable(true);root.setFocusableInTouchMode(true);
  grab();
  root.getViewTreeObserver().addOnGlobalFocusChangeListener((was,now)->{if(!passThrough&&now!=root)main.post(this::grab);});
  // Top: title over a fade.
  top=new LinearLayout(a);top.setOrientation(1);top.setPadding(dp(48),dp(28),dp(48),dp(60));
  top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xcc000000,0x00000000}));
  title=text("",30,Color.WHITE);title.setTypeface(Typeface.DEFAULT_BOLD);sub=text("",18,0xffbdbdbd);
  top.addView(title);top.addView(sub);
  root.addView(top,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
  // Bottom: progress and buttons.
  bottom=new LinearLayout(a);bottom.setOrientation(1);bottom.setPadding(dp(48),dp(90),dp(48),dp(28));
  bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,new int[]{0xf2000000,0xb3000000,0x00000000}));
  LinearLayout bar=new LinearLayout(a);bar.setOrientation(0);bar.setGravity(Gravity.CENTER_VERTICAL);
  timeLeft=text("0:00",18,Color.WHITE);timeRight=text("0:00",18,0xffbdbdbd);
  FrameLayout tr=new FrameLayout(a);
  track=new View(a);track.setBackground(round(0x55ffffff,dp(2)));tr.addView(track,new FrameLayout.LayoutParams(-1,dp(6),Gravity.CENTER_VERTICAL));
  played=new View(a);played.setBackground(round(RED,dp(3)));tr.addView(played,new FrameLayout.LayoutParams(0,dp(6),Gravity.CENTER_VERTICAL|Gravity.START));
  knob=new View(a);GradientDrawable kd=new GradientDrawable();kd.setShape(GradientDrawable.OVAL);kd.setColor(RED);kd.setStroke(dp(2),Color.WHITE);knob.setBackground(kd);
  tr.addView(knob,new FrameLayout.LayoutParams(dp(16),dp(16),Gravity.CENTER_VERTICAL|Gravity.START));
  bar.addView(timeLeft,new LinearLayout.LayoutParams(dp(96),-2));
  LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(0,dp(18),1);bar.addView(tr,tl);
  timeRight.setGravity(Gravity.END);bar.addView(timeRight,new LinearLayout.LayoutParams(dp(96),-2));
  bottom.addView(bar);
  buttons=new LinearLayout(a);buttons.setOrientation(0);buttons.setGravity(Gravity.CENTER_VERTICAL);buttons.setPadding(0,dp(14),0,0);
  addControl("⟲10",()->seek(-10000));
  addControl("❚❚",()->toggle());
  addControl("10⟳",()->seek(10000));
  View gap=new View(a);buttons.addView(gap,new LinearLayout.LayoutParams(0,1,1));
  speedButton=addControl("Швидкість 1x",()->openSpeed(!speedOpen));
  addControl("Озвучка і якість",()->passThrough());
  bottom.addView(buttons);
  speedRow=new LinearLayout(a);speedRow.setOrientation(0);speedRow.setGravity(Gravity.END);speedRow.setPadding(0,dp(10),0,0);speedRow.setVisibility(View.GONE);
  for(float s:SPEEDS){TextView t=chip(label(s));speedRow.addView(t);}
  bottom.addView(speedRow);
  root.addView(bottom,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
  // Centre flash for seek / play-pause while controls are hidden.
  flash=text("",44,Color.WHITE);flash.setTypeface(Typeface.DEFAULT_BOLD);flash.setGravity(Gravity.CENTER);flash.setPadding(dp(26),dp(14),dp(26),dp(14));
  flash.setBackground(round(0x99000000,dp(40)));flash.setAlpha(0);
  root.addView(flash,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));
  top.setAlpha(0);bottom.setAlpha(0);top.setVisibility(View.GONE);bottom.setVisibility(View.GONE);
 }

 private boolean onKey(KeyEvent e){
  int k=e.getKeyCode();
  if(passThrough){
   if(SystemClock.elapsedRealtime()-lastPass>PASS_MS)endPass();
   else{lastPass=SystemClock.elapsedRealtime();if(k==KeyEvent.KEYCODE_BACK&&e.getAction()==KeyEvent.ACTION_UP)main.postDelayed(this::endPass,150);return false;}
  }
  boolean known=k==KeyEvent.KEYCODE_DPAD_LEFT||k==KeyEvent.KEYCODE_DPAD_RIGHT||k==KeyEvent.KEYCODE_DPAD_UP||k==KeyEvent.KEYCODE_DPAD_DOWN
   ||k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER||k==KeyEvent.KEYCODE_SPACE||k==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
   ||k==KeyEvent.KEYCODE_MEDIA_PLAY||k==KeyEvent.KEYCODE_MEDIA_PAUSE||k==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD||k==KeyEvent.KEYCODE_MEDIA_REWIND||k==KeyEvent.KEYCODE_BACK;
  if(!known)return false;
  if(player()==null){Log.w("KIK","Overlay: player not found");return false;}
  if(k==KeyEvent.KEYCODE_BACK){
   if(!visible)return false;
   if(e.getAction()==KeyEvent.ACTION_UP){if(speedOpen)openSpeed(false);else show(false);}
   return true;
  }
  if(e.getAction()!=KeyEvent.ACTION_DOWN)return true;
  switch(k){
   case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:case KeyEvent.KEYCODE_SPACE:toggle();if(!visible)pulse(playing()?"▶":"❚❚");else show(true);return true;
   case KeyEvent.KEYCODE_MEDIA_PLAY:set(true);return true;
   case KeyEvent.KEYCODE_MEDIA_PAUSE:set(false);return true;
   case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:seek(10000);hint(true);return true;
   case KeyEvent.KEYCODE_MEDIA_REWIND:seek(-10000);hint(false);return true;
  }
  if(!visible){
   if(k==KeyEvent.KEYCODE_DPAD_LEFT){seek(-10000);hint(false);return true;}
   if(k==KeyEvent.KEYCODE_DPAD_RIGHT){seek(10000);hint(true);return true;}
   if(k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER){toggle();pulse(playing()?"▶":"❚❚");show(true);return true;}
   focus=1;progressFocus=false;show(true);return true;
  }
  show(true);
  if(speedOpen){
   int i=speedIndex();
   if(k==KeyEvent.KEYCODE_DPAD_LEFT)i=Math.max(0,i-1);
   else if(k==KeyEvent.KEYCODE_DPAD_RIGHT)i=Math.min(SPEEDS.length-1,i+1);
   else if(k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER||k==KeyEvent.KEYCODE_DPAD_UP){openSpeed(false);return true;}
   speed(SPEEDS[i]);paint();return true;
  }
  if(progressFocus){
   if(k==KeyEvent.KEYCODE_DPAD_LEFT)seek(-10000);
   else if(k==KeyEvent.KEYCODE_DPAD_RIGHT)seek(10000);
   else if(k==KeyEvent.KEYCODE_DPAD_DOWN){progressFocus=false;}
   else if(k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER)toggle();
   paint();update();return true;
  }
  if(k==KeyEvent.KEYCODE_DPAD_LEFT)focus=Math.max(0,focus-1);
  else if(k==KeyEvent.KEYCODE_DPAD_RIGHT)focus=Math.min(controls.size()-1,focus+1);
  else if(k==KeyEvent.KEYCODE_DPAD_UP)progressFocus=true;
  else if(k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER)((Runnable)controls.get(focus).getTag()).run();
  paint();update();return true;
 }

 // ---------- playback ----------
 private Object player(){
  try{Class<?> s=Class.forName("rezkatv.web.app.playback.PlaybackService");Object svc=get(s,null,"T");if(svc==null)return null;Object holder=get(svc.getClass(),svc,"L");return holder==null?null:get(holder.getClass(),holder,"A");}
  catch(Exception e){Log.w("KIK","Overlay player: "+e);return null;}
 }
 private Object call(String name,Class<?>[] types,Object... args){
  Object p=player();if(p==null)return null;
  try{Method m=find(p.getClass(),name,types);m.setAccessible(true);return m.invoke(p,args);}catch(Exception e){Log.w("KIK","Player "+name+": "+e);return null;}
 }
 private long position(){Object v=call("K0",new Class<?>[0]);return v instanceof Long?(Long)v:0;}
 private long duration(){Object v=call("getDuration",new Class<?>[0]);long d=v instanceof Long?(Long)v:0;return d<0?0:d;}
 private boolean playing(){return Boolean.TRUE.equals(call("m",new Class<?>[0]));}
 private void set(boolean play){call("S",new Class<?>[]{boolean.class},play);update();}
 private void toggle(){set(!playing());}
 private void seek(long delta){long d=duration();long t=Math.max(0,position()+delta);if(d>0)t=Math.min(d-1000,t);call("L",new Class<?>[]{long.class},t);update();}
 private float currentSpeed(){Object pp=call("getPlaybackParameters",new Class<?>[0]);try{return pp==null?1f:((Number)get(pp.getClass(),pp,"a")).floatValue();}catch(Exception e){return 1f;}}
 private void speed(float s){call("b",new Class<?>[]{float.class},s);}
 private int speedIndex(){float c=currentSpeed();int best=1;for(int i=0;i<SPEEDS.length;i++)if(Math.abs(SPEEDS[i]-c)<Math.abs(SPEEDS[best]-c))best=i;return best;}

 private void passThrough(){
  show(false);passThrough=true;lastPass=SystemClock.elapsedRealtime();
  root.setFocusable(false);
  if(app instanceof ViewGroup)((ViewGroup)app).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
  if(app!=null){app.setFocusable(true);app.requestFocus();}
  // Show the app's own control panel, which hosts voice, quality, subtitles and episodes.
  main.postDelayed(()->{long t=SystemClock.uptimeMillis();View target=app!=null?app:root;
   target.dispatchKeyEvent(new KeyEvent(t,t,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN,0));
   target.dispatchKeyEvent(new KeyEvent(t,t+40,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_DOWN,0));},250);
 }
 private void endPass(){passThrough=false;grab();}
 /** Keeps remote focus on the overlay: the app's Compose tree may not take it while we are in charge. */
 private void grab(){
  if(app instanceof ViewGroup)((ViewGroup)app).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
  if(app!=null)app.setFocusable(false);
  root.setFocusable(true);root.setFocusableInTouchMode(true);root.requestFocus();
 }

 // ---------- view ----------
 private void show(boolean on){
  main.removeCallbacks(hide);
  if(on){main.postDelayed(hide,HIDE_MS);if(!visible){visible=true;fade(top,true);fade(bottom,true);title();main.removeCallbacks(tick);main.post(tick);}paint();return;}
  if(!visible)return;visible=false;speedOpen=false;speedRow.setVisibility(View.GONE);fade(top,false);fade(bottom,false);
 }
 private void fade(final View v,boolean on){
  v.animate().cancel();
  if(on){v.setVisibility(View.VISIBLE);v.setTranslationY(v==top?-dp(12):dp(12));v.animate().alpha(1).translationY(0).setDuration(320).setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();}
  else v.animate().alpha(0).translationY(v==top?-dp(12):dp(12)).setDuration(360).setInterpolator(new android.view.animation.AccelerateInterpolator(1.5f)).withEndAction(()->v.setVisibility(View.GONE)).start();
 }
 private void openSpeed(boolean on){speedOpen=on;speedRow.setVisibility(on?View.VISIBLE:View.GONE);paint();}
 private void hint(boolean forward){pulse(forward?"10 »":"« 10");}
 private void pulse(String s){
  flash.setText(s);flash.animate().cancel();flash.setScaleX(0.9f);flash.setScaleY(0.9f);
  flash.animate().alpha(1).scaleX(1).scaleY(1).setDuration(220).setInterpolator(new android.view.animation.OvershootInterpolator(1.6f)).withEndAction(()->flash.animate().alpha(0).setStartDelay(500).setDuration(300).start()).start();
  if(!visible){main.removeCallbacks(tick);main.post(tick);}
 }
 private void update(){
  long p=position(),d=duration();
  timeLeft.setText(time(p));timeRight.setText(d>0?"-"+time(Math.max(0,d-p)):"");
  View parent=(View)played.getParent();int w=parent.getWidth();
  ViewGroup.LayoutParams lp=played.getLayoutParams();lp.width=d>0&&w>0?(int)(w*Math.min(1f,(float)p/d)):0;played.setLayoutParams(lp);
  knob.setTranslationX(Math.max(0,lp.width-dp(8)));
  controls.get(1).setText(playing()?"❚❚":"▶");
  speedButton.setText("Швидкість "+label(currentSpeed()));
  if(visible)paint();
 }
 private void paint(){
  for(int i=0;i<controls.size();i++){boolean f=!progressFocus&&!speedOpen&&i==focus;TextView t=controls.get(i);
   t.setBackground(f?round(Color.WHITE,dp(28)):round(0x00000000,dp(28)));t.setTextColor(f?Color.BLACK:0xffe8e8e8);t.animate().scaleX(f?1.1f:1f).scaleY(f?1.1f:1f).setDuration(200).setInterpolator(new android.view.animation.OvershootInterpolator(1.4f)).start();}
  knob.animate().scaleX(progressFocus?1.6f:1f).scaleY(progressFocus?1.6f:1f).setDuration(180).start();
  track.animate().scaleY(progressFocus?1.5f:1f).setDuration(180).start();played.animate().scaleY(progressFocus?1.5f:1f).setDuration(180).start();
  int si=speedIndex();
  for(int i=0;i<speedRow.getChildCount();i++){TextView t=(TextView)speedRow.getChildAt(i);boolean f=speedOpen&&i==si;t.setBackground(f?round(RED,dp(16)):round(0x33ffffff,dp(16)));}
 }
 private void title(){
  try{
   Class<?> s=Class.forName("rezkatv.web.app.playback.PlaybackService");Object svc=get(s,null,"T");
   Object mgr=find(svc.getClass(),"n",new Class<?>[0]).invoke(svc);Object flow=get(mgr.getClass(),mgr,"g");
   Object session=find(flow.getClass(),"getValue",new Class<?>[0]).invoke(flow);
   Object film=get(session.getClass(),session,"q");String t=film==null?"":String.valueOf(PlaybackPolicy.call(film,"getTitle"));
   String m=Ua.t(t);title.setText(m);
   String season=(String)get(session.getClass(),session,"c"),episode=(String)get(session.getClass(),session,"d");
   sub.setText(season!=null&&episode!=null&&!"0".equals(season)?"Сезон "+season+" · Серія "+episode:"");
  }catch(Exception e){Log.w("KIK","Player title",e);}
 }
 private TextView addControl(String label,Runnable r){
  boolean icon=label.length()<=4;
  TextView t=text(label,icon?20:19,Color.WHITE);t.setTypeface(Typeface.DEFAULT_BOLD);t.setGravity(Gravity.CENTER);t.setPadding(icon?0:dp(24),0,icon?0:dp(24),0);t.setTag(r);
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(icon?dp(64):-2,dp(56));lp.setMargins(0,0,dp(14),0);
  buttons.addView(t,lp);controls.add(t);return t;
 }
 private TextView chip(String s){TextView t=text(s,19,Color.WHITE);t.setPadding(dp(20),dp(8),dp(20),dp(8));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.setMargins(dp(8),0,0,0);t.setLayoutParams(lp);return t;}
 private TextView text(String s,int size,int color){TextView t=new TextView(act);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setShadowLayer(6,0,1,0x99000000);return t;}
 private static GradientDrawable round(int c,int r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(r);return d;}
 private static String label(float s){return (s==(int)s?String.valueOf((int)s):String.valueOf(s))+"x";}
 private static String time(long ms){long s=ms/1000;return s>=3600?String.format(Locale.ROOT,"%d:%02d:%02d",s/3600,s/60%60,s%60):String.format(Locale.ROOT,"%d:%02d",s/60,s%60);}
 private static Object get(Class<?> c,Object o,String name)throws Exception{for(Class<?> k=c;k!=null;k=k.getSuperclass())try{Field f=k.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
 private static Method find(Class<?> c,String name,Class<?>[] types)throws NoSuchMethodException{for(Class<?> k=c;k!=null;k=k.getSuperclass())try{return k.getDeclaredMethod(name,types);}catch(NoSuchMethodException ignored){}return c.getMethod(name,types);}
}
