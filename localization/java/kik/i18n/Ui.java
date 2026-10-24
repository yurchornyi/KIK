package kik.i18n;
import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.ReplacementSpan;
import android.view.*;
import android.view.animation.*;
import android.widget.*;
import java.util.*;

/** Shared look of KIK's native screens: palette, buttons, poster cards and a background worker. */
public final class Ui {
 public static final int BG=0xff0d0d0f,RED=0xffe50914,GREY=0xffa3a3a8,CARD=0xff1b1b1f;
 public static final Typeface MEDIUM=Typeface.create("sans-serif-medium",Typeface.NORMAL);
 public static final android.view.animation.Interpolator EASE=new DecelerateInterpolator(1.6f);
 public static final Handler MAIN=new Handler(Looper.getMainLooper());
 /** Short grace period for missing translations; network services must not hold screen navigation. */
 public static final long TRANSLATE_WAIT=180;

 public static int dp(Context c,float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
 /** Clamp a design width to the usable window; TV density differs across manufacturers. */
 public static int contentWidth(Context c,int maximum,int horizontalPadding){return Math.max(1,Math.min(dp(c,maximum),c.getResources().getDisplayMetrics().widthPixels-dp(c,horizontalPadding)));}
 /** Scroll a focusable row while leaving enough room for the focus ring on every edge. */
 public static HorizontalScrollView horizontal(Context c,LinearLayout row){HorizontalScrollView scroll=new HorizontalScrollView(c);scroll.setHorizontalScrollBarEnabled(false);scroll.setClipToPadding(false);scroll.setClipChildren(false);scroll.setPadding(dp(c,8),dp(c,6),dp(c,8),dp(c,6));row.setClipChildren(false);row.setClipToPadding(false);scroll.addView(row);return scroll;}
 public static void bg(Runnable r){new Thread(r,"kik-ui").start();}

 /** Full-screen dark window with immersive mode; Back on Android 13+ is routed to onBackPressed. */
 public static void screen(final Activity a){
  a.getWindow().setFlags(1024,1024);a.getWindow().getDecorView().setSystemUiVisibility(5894);a.getWindow().setBackgroundDrawable(null);
  if(Build.VERSION.SDK_INT>=33)a.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,a::onBackPressed);
 }
 /** Short, one-shot entrance; animate only small content, never the full-screen backdrop. */
 /** Off when the TV's developer options or accessibility turn animations off; the views then just appear. */
 public static boolean animations(){return Build.VERSION.SDK_INT<26||android.animation.ValueAnimator.areAnimatorsEnabled();}
 public static void enter(View v,int delay){if(!animations()){v.setAlpha(1f);v.setTranslationY(0);return;}v.setAlpha(0f);v.setTranslationY(dp(v.getContext(),10));v.animate().alpha(1f).translationY(0).setStartDelay(delay).setDuration(240).setInterpolator(EASE).start();}
 public static void focus(View v,boolean on,float scale){v.animate().cancel();if(!animations()){v.setScaleX(on?scale:1f);v.setScaleY(on?scale:1f);return;}v.animate().setStartDelay(0).scaleX(on?scale:1f).scaleY(on?scale:1f).setDuration(160).setInterpolator(EASE).start();}
 public static TextView text(Context c,String s,int size,int color){TextView t=new TextView(c);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
 public static TextView bold(Context c,String s,int size){TextView t=text(c,s,size,Color.WHITE);t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
 public static void space(LinearLayout l,int h){l.addView(new View(l.getContext()),new LinearLayout.LayoutParams(1,dp(l.getContext(),h)));}
 public static GradientDrawable shape(int fill,int radius,int stroke,int strokeColor){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(radius);if(stroke>0)d.setStroke(stroke,strokeColor);return d;}
 // Netflix-style dropdowns: a near-black panel with a thin grey edge; items are grey text that turns white on a soft highlight.
 public static GradientDrawable menuPanel(Context c){return shape(0xff141414,dp(c,16),dp(c,1),0x40ffffff);}
 public static TextView menuItem(Context c,String label,boolean chosen){
  TextView t=new TextView(c);t.setText(label);t.setTextSize(16);t.setSingleLine(true);t.setEllipsize(TextUtils.TruncateAt.END);t.setIncludeFontPadding(false);t.setGravity(Gravity.CENTER_VERTICAL);
  t.setTypeface(chosen?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);t.setFocusable(true);t.setClickable(true);t.setPadding(dp(c,16),dp(c,11),dp(c,16),dp(c,11));
  t.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_focused},new int[]{}},new int[]{Color.WHITE,chosen?Color.WHITE:0xffb3b3b3}));
  t.setBackground(states(shape(0,dp(c,12),0,0),shape(0x33ffffff,dp(c,12),0,0)));return t;
 }
 /** The button that opens such a dropdown: black with a thin white edge, white when focused. */
 public static TextView dropButton(Context c,String label){
  TextView t=new TextView(c);t.setText(label+"  ▾");t.setTextSize(16);t.setSingleLine(true);t.setIncludeFontPadding(false);t.setGravity(Gravity.CENTER);t.setFocusable(true);t.setClickable(true);t.setPadding(dp(c,16),0,dp(c,14),0);
  t.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_focused},new int[]{}},new int[]{0xff141414,Color.WHITE}));
  t.setBackground(states(shape(0xcc000000,dp(c,22),dp(c,1),0xb3ffffff),shape(Color.WHITE,dp(c,22),dp(c,1),Color.WHITE)));return t;
 }
 public static StateListDrawable states(Drawable normal,Drawable focused){StateListDrawable s=new StateListDrawable();s.addState(new int[]{android.R.attr.state_focused},focused);s.addState(new int[]{},normal);return s;}
 public static android.content.res.ColorStateList buttonText(boolean primary){return new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{android.R.attr.state_focused},new int[]{android.R.attr.state_selected},new int[]{}},new int[]{GREY,Color.BLACK,Color.WHITE,primary?Color.BLACK:Color.WHITE});}
 public static StateListDrawable buttonBackground(Context c,boolean primary){
  StateListDrawable s=new StateListDrawable();int r=dp(c,24);
  s.addState(new int[]{-android.R.attr.state_enabled},shape(CARD,r,0,0));s.addState(new int[]{android.R.attr.state_focused},shape(Color.WHITE,r,dp(c,2),RED));s.addState(new int[]{android.R.attr.state_selected},shape(RED,r,0,0));s.addState(new int[]{},shape(primary?0xffe8e8e8:0xff333337,r,0,0));return s;
 }
 public static void paintControl(TextView v,boolean focused){v.setBackground(shape(focused?Color.WHITE:0xff333337,dp(v.getContext(),24),focused?dp(v.getContext(),2):0,RED));v.setTextColor(focused?Color.BLACK:Color.WHITE);focus(v,focused,1.04f);}
 /** Compact native dialogs retain Android's choice, keyboard and dismissal behavior. */
 public static android.app.AlertDialog.Builder dialog(Context c){return new android.app.AlertDialog.Builder(c){@Override public android.app.AlertDialog create(){android.app.AlertDialog d=super.create();d.setOnShowListener(x->styleDialog(c,d));return d;}};}
 private static void styleDialog(Context c,android.app.AlertDialog d){
  ListView list=d.getListView();boolean multiple=list!=null&&list.getChoiceMode()==AbsListView.CHOICE_MODE_MULTIPLE;
  Window window=d.getWindow();if(window!=null){
   window.setBackgroundDrawable(shape(CARD,dp(c,20),dp(c,1),0xff37373d));
   window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(0.72f);window.setGravity(Gravity.CENTER);
   int available=Math.max(1,c.getResources().getDisplayMetrics().widthPixels-dp(c,48));
   window.setLayout(Math.min(dp(c,multiple?540:list==null?440:360),available),WindowManager.LayoutParams.WRAP_CONTENT);
  }
  int titleId=c.getResources().getIdentifier("alertTitle","id","android");View title=titleId==0?null:d.findViewById(titleId);
  if(title instanceof TextView){TextView t=(TextView)title;t.setTextColor(Color.WHITE);t.setTextSize(20);t.setTypeface(Typeface.DEFAULT_BOLD);t.setSingleLine(false);t.setMaxLines(2);t.setEllipsize(TextUtils.TruncateAt.END);}
  TextView message=d.findViewById(android.R.id.message);if(message!=null){message.setTextColor(GREY);message.setTextSize(16);message.setLineSpacing(0,1.15f);}
  int dividerId=c.getResources().getIdentifier("titleDivider","id","android");View divider=dividerId==0?null:d.findViewById(dividerId);if(divider!=null)divider.setBackgroundColor(0xff37373d);
  for(int which:new int[]{-1,-2,-3}){Button b=d.getButton(which);if(b==null||b.getVisibility()!=View.VISIBLE)continue;b.setAllCaps(false);b.setTextSize(16);b.setSingleLine(true);b.setTypeface(Typeface.DEFAULT_BOLD);b.setPadding(dp(c,20),0,dp(c,20),0);b.setTextColor(buttonText(which==-1));b.setBackground(buttonBackground(c,which==-1));b.setStateListAnimator(null);b.setOnFocusChangeListener((v,f)->focus(v,f,1.04f));ViewGroup.LayoutParams lp=b.getLayoutParams();lp.height=dp(c,44);if(lp instanceof ViewGroup.MarginLayoutParams){ViewGroup.MarginLayoutParams margins=(ViewGroup.MarginLayoutParams)lp;margins.leftMargin=dp(c,8);margins.topMargin=dp(c,4);margins.bottomMargin=dp(c,4);}b.setLayoutParams(lp);if(b.getParent() instanceof ViewGroup){ViewGroup parent=(ViewGroup)b.getParent();parent.setClipChildren(false);parent.setClipToPadding(false);}}
  if(list!=null){
   list.setPadding(dp(c,12),dp(c,4),dp(c,12),dp(c,12));list.setClipToPadding(true);list.setDivider(new ColorDrawable(Color.TRANSPARENT));list.setDividerHeight(dp(c,4));
   list.setSelector(shape(Color.WHITE,dp(c,12),dp(c,2),RED));list.setDrawSelectorOnTop(false);
   list.setVerticalScrollBarEnabled(true);list.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
   if(list.getAdapter()!=null&&list.getAdapter().getCount()>4){int maxHeight=Math.max(dp(c,52),c.getResources().getDisplayMetrics().heightPixels-dp(c,220));ViewGroup.LayoutParams lp=list.getLayoutParams();lp.height=Math.min(dp(c,list.getAdapter().getCount()*56+16),maxHeight);list.setLayoutParams(lp);}
   list.setOnScrollListener(new AbsListView.OnScrollListener(){public void onScrollStateChanged(AbsListView v,int state){}public void onScroll(AbsListView v,int first,int visible,int total){styleDialogRows(list);}});
   list.post(()->styleDialogRows(list));
  }
 }
 private static final Object DIALOG_ROW_STYLE=new Object();
 private static void styleDialogRows(ListView list){
  Context c=list.getContext();
  for(int i=0;i<list.getChildCount();i++){
   View row=list.getChildAt(i);if(!(row instanceof TextView)||row.getTag()==DIALOG_ROW_STYLE)continue;
   TextView t=(TextView)row;t.setTag(DIALOG_ROW_STYLE);t.setTextSize(16);t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));t.setGravity(Gravity.CENTER_VERTICAL);t.setSingleLine(false);t.setMaxLines(2);t.setEllipsize(TextUtils.TruncateAt.END);t.setMinHeight(dp(c,52));t.setPadding(dp(c,16),dp(c,10),dp(c,16),dp(c,10));
   t.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_selected},new int[]{android.R.attr.state_pressed},new int[]{}},new int[]{Color.BLACK,Color.BLACK,Color.WHITE}));
   if(t instanceof CheckedTextView)((CheckedTextView)t).setCheckMarkTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_selected},new int[]{android.R.attr.state_checked},new int[]{}},new int[]{Color.BLACK,RED,GREY}));
  }
 }

 /** White primary pill or translucent secondary pill, Netflix style. */
 public static Button pill(Context c,String label,boolean primary){
  Button b=new Button(c);b.setText(label);b.setAllCaps(false);b.setSingleLine(true);b.setEllipsize(TextUtils.TruncateAt.END);b.setTextSize(16);b.setTypeface(Typeface.DEFAULT_BOLD);b.setPadding(dp(c,24),0,dp(c,26),0);
  b.setTextColor(buttonText(primary));b.setStateListAnimator(null);b.setBackground(buttonBackground(c,primary));
  b.setOnFocusChangeListener((v,f)->focus(v,f,1.04f));
  return b;
 }
 /** Small selectable chip (filters, seasons, voices). */
 public static TextView chip(Context c,String label){
  TextView t=text(c,label,16,Color.WHITE);t.setSingleLine(true);t.setTypeface(Typeface.DEFAULT_BOLD);t.setMinHeight(dp(c,44));t.setFocusable(true);t.setClickable(true);t.setGravity(Gravity.CENTER);t.setPadding(dp(c,18),dp(c,8),dp(c,18),dp(c,8));
  t.setTextColor(buttonText(false));t.setBackground(buttonBackground(c,false));
  t.setOnFocusChangeListener((v,f)->focus(v,f,1.04f));
  return t;
 }
 public static void select(TextView chip,boolean on){chip.setSelected(on);}
 public static LinearLayout.LayoutParams wrap(Context c,int right){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.setMargins(0,0,dp(c,right),0);return lp;}

 /** Poster card: focus grows it slightly and shows a red ring; title and a subtitle line below. */
 public static View card(final Context c,String title,String sub,String poster,String badge,float progress,final Runnable onFocus,final Runnable onClick){
  LinearLayout box=new LinearLayout(c);box.setOrientation(1);box.setClipChildren(false);box.setClipToPadding(false);box.setPadding(dp(c,6),dp(c,10),dp(c,6),dp(c,10));
  FrameLayout frame=new FrameLayout(c);frame.setFocusable(true);frame.setClickable(true);
  ImageView img=new HomeActivity.PosterView(c,dp(c,8));frame.addView(img,new FrameLayout.LayoutParams(-1,-1));
  final View ring=new View(c);ring.setBackground(shape(0,dp(c,8),dp(c,3),RED));ring.setAlpha(0f);frame.addView(ring,new FrameLayout.LayoutParams(-1,-1));
  if(badge!=null){TextView r=text(c,badge,12,Color.WHITE);r.setPadding(dp(c,6),dp(c,2),dp(c,6),dp(c,2));r.setBackground(shape(0xcc000000,dp(c,4),0,0));FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.END);p.setMargins(0,dp(c,6),dp(c,6),0);frame.addView(r,p);}
  if(progress>0){View track=new View(c);track.setBackgroundColor(0x88ffffff);frame.addView(track,new FrameLayout.LayoutParams(-1,dp(c,4),Gravity.BOTTOM));
   View bar=new View(c);bar.setBackgroundColor(RED);frame.addView(bar,new FrameLayout.LayoutParams((int)(dp(c,136)*Math.min(1f,progress)),dp(c,4),Gravity.BOTTOM|Gravity.START));}
  box.addView(frame,new LinearLayout.LayoutParams(dp(c,136),dp(c,202)));
  final TextView t=text(c,title,14,0xffe6e6ea);t.setSingleLine(true);t.setEllipsize(TextUtils.TruncateAt.END);t.setPadding(dp(c,2),dp(c,8),dp(c,2),0);
  box.addView(t,new LinearLayout.LayoutParams(dp(c,136),-2));
  TextView s=text(c,sub==null?"":sub,12,GREY);s.setSingleLine(true);s.setEllipsize(TextUtils.TruncateAt.END);s.setPadding(dp(c,2),dp(c,2),dp(c,2),0);
  box.addView(s,new LinearLayout.LayoutParams(dp(c,136),-2));
  frame.setContentDescription(title+(sub==null?"":" · "+sub));
  frame.setOnFocusChangeListener((v,f)->{
   focus(v,f,1.045f);ring.animate().alpha(f?1f:0f).setDuration(140).start();
   t.setTextColor(f?Color.WHITE:0xffe6e6ea);if(f&&onFocus!=null)onFocus.run();
  });
  frame.setOnClickListener(v->{if(onClick!=null)onClick.run();});
  HomeActivity.Images.load(poster,img,dp(c,136));
  return box;
 }
 /** Quiet bottom notice instead of a dialog. */
 public static void toast(Activity a,String s){
  ViewGroup root=(ViewGroup)a.findViewById(android.R.id.content);
  final TextView t=text(a,s,16,Color.WHITE);t.setPadding(dp(a,22),dp(a,10),dp(a,22),dp(a,10));t.setBackground(shape(0xe61b1b1f,dp(a,22),0,0));
  FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);lp.bottomMargin=dp(a,40);
  root.addView(t,lp);t.setAlpha(0f);t.animate().alpha(1f).setDuration(200).withEndAction(()->t.animate().alpha(0f).setStartDelay(2200).setDuration(300).withEndAction(()->root.removeView(t)).start()).start();
 }
 public static String time(long ms){long s=Math.max(0,ms)/1000;return s>=3600?String.format(Locale.ROOT,"%d:%02d:%02d",s/3600,s/60%60,s%60):String.format(Locale.ROOT,"%d:%02d",s/60,s%60);}
 public static String join(String... parts){StringBuilder b=new StringBuilder();for(String p:parts)if(p!=null&&!p.isEmpty()){if(b.length()>0)b.append("  •  ");b.append(year(p));}return b.toString();}
 /** The source writes a running series as "2026-..." and a finished one as "2010-2022": show "2026" and "2010–2022". */
 public static String year(String s){if(s==null)return null;java.util.regex.Matcher m=java.util.regex.Pattern.compile("^(\\d{4})\\s*-\\s*(\\d{4}|\\.{3}|…)?$").matcher(s.trim());if(!m.matches())return s;String end=m.group(2);return end==null||!Character.isDigit(end.charAt(0))||end.equals(m.group(1))?m.group(1):m.group(1)+"–"+end;}

 /** The line under a title, as on Netflix: the year bright, the age rating in an outlined badge, the rest softer with
  *  dim dots between, the score last after a gold star. Empty parts are skipped. */
 public static CharSequence facts(Context c,String year,String age,String rating,String... rest){
  SpannableStringBuilder b=new SpannableStringBuilder();boolean badge=age!=null&&!age.isEmpty();
  if(year!=null&&!year.isEmpty()){b.append(year(year));b.setSpan(new ForegroundColorSpan(Color.WHITE),0,b.length(),33);}
  if(badge){if(b.length()>0)b.append("  ");int at=b.length();b.append(age.trim());b.setSpan(new Badge(c),at,b.length(),33);}
  for(String p:rest)if(p!=null&&!p.isEmpty()){gap(b,badge);badge=false;b.append(p);}
  if(rating!=null&&!rating.isEmpty()){gap(b,badge);int at=b.length();b.append("★ ");b.setSpan(new ForegroundColorSpan(0xfff5c518),at,at+1,33);at=b.length();b.append(rating);b.setSpan(new ForegroundColorSpan(Color.WHITE),at,b.length(),33);}
  return b;
 }
 /** The badge frame keeps its neighbours apart by itself; between plain words goes a dim dot. */
 private static void gap(SpannableStringBuilder b,boolean afterBadge){if(b.length()==0)return;b.append("  ");if(afterBadge)return;int at=b.length();b.append("•  ");b.setSpan(new ForegroundColorSpan(0x66ffffff),at,at+1,33);}
 /** The source's "105 мин." reads "1 год 45 хв"; anything else is returned as otherwise. */
 public static String minutes(String s,String otherwise){if(s==null)return otherwise;java.util.regex.Matcher m=java.util.regex.Pattern.compile("^(\\d{1,4})\\s*(мин|хв)\\.?$").matcher(s.trim());if(!m.matches())return otherwise;int n=Integer.parseInt(m.group(1));return n>=60?n/60+" год"+(n%60>0?" "+n%60+" хв":""):n+" хв";}
 /** An age rating in a thin rounded frame: the text a little smaller, centred on the line's digits. */
 static final class Badge extends ReplacementSpan{
  private static final float SCALE=0.8f;private final float pad,radius,stroke;private final Rect glyph=new Rect();private final RectF box=new RectF();
  Badge(Context c){float d=c.getResources().getDisplayMetrics().density;pad=5*d;radius=3*d;stroke=Math.max(1f,d);}
  @Override public int getSize(Paint p,CharSequence t,int s,int e,Paint.FontMetricsInt fm){
   if(fm!=null)p.getFontMetricsInt(fm);
   float size=p.getTextSize();p.setTextSize(size*SCALE);float w=p.measureText(t,s,e);p.setTextSize(size);
   return Math.round(w+2*pad+stroke);
  }
  @Override public void draw(Canvas c,CharSequence t,int s,int e,float x,int top,int y,int bottom,Paint p){
   float size=p.getTextSize(),width=p.getStrokeWidth();int color=p.getColor();Paint.Style style=p.getStyle();
   p.getTextBounds("8",0,1,glyph);float mid=y+(glyph.top+glyph.bottom)/2f;
   p.setTextSize(size*SCALE);p.getTextBounds("8",0,1,glyph);float half=(glyph.bottom-glyph.top)/2f,w=p.measureText(t,s,e),left=x+stroke/2;
   box.set(left,mid-half-pad*0.7f,left+w+2*pad,mid+half+pad*0.7f);
   p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(stroke);p.setColor(0x8cffffff);c.drawRoundRect(box,radius,radius,p);
   p.setStyle(style);p.setStrokeWidth(width);p.setColor(color);c.drawText(t,s,e,left+pad,mid+half,p);p.setTextSize(size);
  }
 }
}
