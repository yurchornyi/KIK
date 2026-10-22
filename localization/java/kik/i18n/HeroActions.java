package kik.i18n;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;

/** The three hero actions use a compact face, consistent vector icons and a separate TV focus ring. */
final class HeroActions {
 static final int PLAY=0,ADD=1,CHECK=2,INFO=3,RETRY=4;
 static Button button(Context c,String label,int icon,boolean primary){
  Button b=new Button(c);b.setAllCaps(false);b.setSingleLine(true);b.setTextSize(16);b.setTypeface(primary?Typeface.DEFAULT_BOLD:Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setIncludeFontPadding(false);b.setGravity(Gravity.CENTER);b.setLetterSpacing(0.01f);
  b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(Ui.dp(c,48));b.setMinimumHeight(Ui.dp(c,48));b.setCompoundDrawablePadding(Ui.dp(c,9));b.setStateListAnimator(null);
  b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{Ui.GREY,primary?0xff16161b:0xfff5f5f7}));
  StateListDrawable states=new StateListDrawable();
  states.addState(new int[]{-android.R.attr.state_enabled},face(c,false,false,false,true));
  states.addState(new int[]{android.R.attr.state_focused,android.R.attr.state_selected},face(c,primary,true,true,false));
  states.addState(new int[]{android.R.attr.state_focused},face(c,primary,true,false,false));
  states.addState(new int[]{android.R.attr.state_selected},face(c,primary,false,true,false));
  states.addState(new int[]{},face(c,primary,false,false,false));
  b.setBackground(new RippleDrawable(ColorStateList.valueOf(primary?0x19000000:0x24ffffff),states,Ui.shape(Color.WHITE,Ui.dp(c,22),0,0)));
  b.setPadding(Ui.dp(c,22),0,Ui.dp(c,22),0);
  label(b,label,icon);b.setOnFocusChangeListener((v,on)->focus(v,on));return b;
 }
 static void label(Button b,String text,int icon){
  b.setText(text);Icon drawable=new Icon(icon,b.getTextColors());int size=Ui.dp(b.getContext(),20);drawable.setBounds(0,0,size,size);b.setCompoundDrawablesRelative(drawable,null,null,null);
 }
 static void focus(View v,boolean on){v.animate().cancel();v.animate().setStartDelay(0).scaleX(on?1.035f:1f).scaleY(on?1.035f:1f).translationY(on?-Ui.dp(v.getContext(),1):0).setDuration(160).setInterpolator(Ui.EASE).start();}
 private static Drawable face(Context c,boolean primary,boolean focused,boolean selected,boolean disabled){
  int top,bottom,border;
  if(disabled){top=bottom=0xff242429;border=0xff35353b;}
  else if(primary){top=focused?0xffffffff:0xfff7f7fa;bottom=focused?0xffeeeeF2:0xffdedee4;border=0xfffafafa;}
  else if(selected){top=focused?0xff613440:0xff432b34;bottom=focused?0xff432a33:0xff302128;border=0xff88515c;}
  else{top=focused?0xff4b4b55:0xf23a3a43;bottom=focused?0xff34343e:0xf226262e;border=focused?0xff777781:0xff55555f;}
  GradientDrawable fill=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{top,bottom});fill.setCornerRadius(Ui.dp(c,18));fill.setStroke(Ui.dp(c,1),border);
  LayerDrawable layers=new LayerDrawable(new Drawable[]{Ui.shape(0,Ui.dp(c,22),focused?Ui.dp(c,2):0,Ui.RED),fill});int inset=Ui.dp(c,3);layers.setLayerInset(1,inset,inset,inset,inset);return layers;
 }
 private static final class Icon extends Drawable {
  private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final int kind;private final ColorStateList colors;private int alpha=255;
  Icon(int kind,ColorStateList colors){this.kind=kind;this.colors=colors;p.setColor(colors.getDefaultColor());p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);}
  @Override public void draw(Canvas c){
   Rect bounds=getBounds();c.save();c.translate(bounds.left,bounds.top);c.scale(bounds.width()/24f,bounds.height()/24f);p.setAlpha(alpha);p.setStrokeWidth(2);p.setStyle(Paint.Style.STROKE);
   if(kind==PLAY){p.setStyle(Paint.Style.FILL);Path play=new Path();play.moveTo(7,4);play.lineTo(20,12);play.lineTo(7,20);play.close();c.drawPath(play,p);}
   else if(kind==ADD){c.drawLine(5,12,19,12,p);c.drawLine(12,5,12,19,p);}
   else if(kind==CHECK){Path check=new Path();check.moveTo(5,12);check.lineTo(10,17);check.lineTo(20,7);c.drawPath(check,p);}
   else if(kind==INFO){c.drawCircle(12,12,9,p);c.drawLine(12,11,12,17,p);p.setStyle(Paint.Style.FILL);c.drawCircle(12,7.5f,1.2f,p);}
   else{c.drawArc(4,4,20,20,-35,285,false,p);Path arrow=new Path();arrow.moveTo(16,4);arrow.lineTo(20,4);arrow.lineTo(20,8);c.drawPath(arrow,p);}
   c.restore();
  }
  @Override public boolean isStateful(){return true;}
  @Override protected boolean onStateChange(int[] states){int color=colors.getColorForState(states,colors.getDefaultColor());if(color==p.getColor())return false;p.setColor(color);invalidateSelf();return true;}
  @Override public void setAlpha(int alpha){this.alpha=alpha;invalidateSelf();}@Override public void setColorFilter(ColorFilter filter){p.setColorFilter(filter);invalidateSelf();}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
}
