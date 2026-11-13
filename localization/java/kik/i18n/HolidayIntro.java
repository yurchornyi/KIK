package kik.i18n;
import android.content.Context;
import android.graphics.*;
import android.text.TextPaint;
import android.view.View;
import android.view.animation.AnimationUtils;
import java.util.Calendar;
import java.util.Locale;

/**
 * The holiday's own intro before a video, in place of the KIK clip (web/kik-intro.mp4) on the day: on black the strokes draw
 * in one by one, the holiday's action follows (a sparkler and fireworks, the star, the gift, the heart, the paska, the flag's
 * colours, the spider), a soft glow comes up and the greeting fades in under the mark; «Створено Чорним» below, as in the
 * clip. The mark stands exactly where the clip's does, so the waiting screen after it goes on from the same place. Drawn
 * with HolidayMark's look and at most 90 particles, everything made once; it asks for frames only until its end. The player
 * runs it as it runs the clip (KikPlayerActivity.playIntro).
 */
final class HolidayIntro extends View {
 /** The whole intro, about as long as the clip; then the player's endIntro, as when the clip ends. */
 static final long DURATION_MS=3300;
 /** The settled frame, the greeting in: with the video not ready yet the waiting screen takes over here (the clip's INTRO_HOLD_MS). */
 static final long HOLD_MS=3150;
 private static final float A=HolidayMark.Look.INTRO_ACTION;
 private static final String MADE="СТВОРЕНО ЧОРНИМ";
 private final String id,greeting;private final HolidayMark.Look look;private final HolidayMark.Particles fx;private final Runnable done;
 private final TextPaint greet=new TextPaint(Paint.ANTI_ALIAS_FLAG),credit=new TextPaint(Paint.ANTI_ALIAS_FLAG);private final Paint ring=new Paint(Paint.ANTI_ALIAS_FLAG),half=new Paint(Paint.ANTI_ALIAS_FLAG);
 /** When each particle event fires, in seconds (fire()). */
 private final float[] events;private int next;
 private long start=-1,last=-1;private boolean ended,stopped;
 /** The clip's 1280×720 frame fitted to the view (k pixels per frame pixel, at x0, y0), and the mark's units in it. */
 private float k,x0,y0,unit,left,top;

 HolidayIntro(Context c,String id,Runnable done){
  super(c);this.id=id;this.done=done;look=new HolidayMark.Look(id);fx=new HolidayMark.Particles(c.getResources().getDisplayMetrics().density);
  Calendar now=Calendar.getInstance();greeting=Holidays.greeting(id,now.get(Calendar.YEAR),now.get(Calendar.MONTH)+1).toUpperCase(new Locale("uk"));
  greet.setColor(Holidays.accent(id));greet.setTypeface(Ui.MEDIUM);greet.setTextAlign(Paint.Align.CENTER);greet.setLetterSpacing(.32f);
  credit.setColor(0xff9d9aa6);credit.setTypeface(Ui.MEDIUM);credit.setTextAlign(Paint.Align.CENTER);credit.setLetterSpacing(.2f);
  ring.setStyle(Paint.Style.STROKE);ring.setColor(0xff8a8a90);half.setColor(Color.WHITE);
  events=events(id);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
 }
 /** Milliseconds the intro has been on screen; 0 before its first frame. */
 long elapsed(){return start<0?0:AnimationUtils.currentAnimationTimeMillis()-start;}
 /** No more frames and no end call: the player took it away. */
 void stop(){stopped=true;}
 @Override protected void onDetachedFromWindow(){stopped=true;super.onDetachedFromWindow();}

 @Override protected void onSizeChanged(int w,int h,int ow,int oh){
  super.onSizeChanged(w,h,ow,oh);
  // As the waiting screen fits its picture of the clip's last frame (KikPlayerActivity.placeLogo): the KIK letters span
  // 3.85 frame pixels a unit, centred on (640, 360); the greeting stands on the waiting line's place (486), the credit at 681.
  k=Math.min(w/1280f,h/720f);x0=(w-1280*k)/2;y0=(h-720*k)/2;unit=3.85f*k;left=x0+640*k-51*unit;top=y0+360*k-24*unit;
  greet.setTextSize(20*k);credit.setTextSize(12.5f*k);ring.setStrokeWidth(Math.max(1,1.2f*k));
 }
 @Override protected void onDraw(Canvas c){
  long now=AnimationUtils.currentAnimationTimeMillis();if(start<0){start=now;last=now;}
  float t=(now-start)/1000f,dt=Math.min(.05f,(now-last)/1000f);last=now;
  c.drawColor(Color.BLACK);
  while(next<events.length&&t>=events[next])fire(next++);
  c.save();c.translate(left,top);c.scale(unit,unit);look.draw(c,HolidayMark.Look.INTRO,t);c.restore();
  fx.step(dt,getWidth(),getHeight());fx.draw(c);
  float g=HolidayMark.EASE.at(HolidayMark.span(t,2.35f,.8f));
  if(g>0&&!greeting.isEmpty()){greet.setAlpha(HolidayMark.alpha(g));c.drawText(greeting,getWidth()/2f,y0+486*k+greet.getTextSize()*.35f+8*k*(1-g),greet);}
  c.drawText(MADE,x0+652*k,y0+681*k,credit);
  // The half-dark, half-white circle beside the credit, as in the clip.
  c.drawArc(x0+551*k,y0+668*k,x0+569*k,y0+686*k,-90,180,true,half);c.drawCircle(x0+560*k,y0+677*k,9*k,ring);
  if(stopped)return;
  if(now-start<DURATION_MS)postInvalidateOnAnimation();else if(!ended){ended=true;post(done);}
 }

 /** The times of the preview's particle events, from the holiday's action at 1.15 s. */
 private static float[] events(String id){
  switch(id){
   case Holidays.NEW_YEAR:{float[] e=new float[18];for(int i=0;i<15;i++)e[i]=A+i*.04f;e[15]=A+.6f;e[16]=A+.95f;e[17]=A+1.25f;return e;}
   case Holidays.XMAS:return new float[]{A,A+.7f};
   case Holidays.NICK:return new float[]{A+.2f,A+.9f};
   case Holidays.VALENTINE:return new float[]{A+.95f};
   case Holidays.EASTER:return new float[]{A+1.65f};
   default:return new float[0];
  }
 }
 private void fire(int n){
  switch(id){
   case Holidays.NEW_YEAR:
    // Sparks fall off the sparkler as it climbs the I; then fireworks over the I and either K.
    if(n<15)fx.sparks(x(51),y(40-46*HolidayMark.FUSE.at(Math.min(1,(n+1)*.04f/.6f/.92f))),3);
    else if(n==15)fx.burst(x(51),y(-6.5f),36,HolidayMark.Particles.FIREWORKS[0],230,90,1.4f,1.5f,HolidayMark.Particles.SPARK,0);
    else if(n==16)fx.burst(x(8),y(-40),24,HolidayMark.Particles.FIREWORKS[1],170,80,1.2f,1.3f,HolidayMark.Particles.SPARK,0);
    else fx.burst(x(94),y(-34),24,HolidayMark.Particles.FIREWORKS[3],170,80,1.2f,1.3f,HolidayMark.Particles.SPARK,0);
    break;
   case Holidays.XMAS:if(n==0)fx.snow(14,getWidth(),getHeight(),3);else fx.twinkles(x(51),y(-6),8,0xfff6d37a,0);break;
   case Holidays.NICK:if(n==0)fx.snow(10,getWidth(),getHeight(),3);else fx.burst(x(108),y(29),18,HolidayMark.Burst.GIFT,90,40,.9f,1.2f,HolidayMark.Particles.DOT,0);break;
   case Holidays.VALENTINE:fx.hearts(x(51),y(12),6,HolidayMark.Burst.HEARTS,0);break;
   case Holidays.EASTER:fx.confetti(x(51),y(5),24,HolidayMark.Look.SPRINKLE_COLORS,0);break;
   default:break;
  }
 }
 private float x(float u){return left+u*unit;}
 private float y(float u){return top+u*unit;}
}
