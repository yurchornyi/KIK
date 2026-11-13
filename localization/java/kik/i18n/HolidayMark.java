package kik.i18n;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.view.animation.AnimationUtils;
import android.widget.*;
import java.util.Random;

/**
 * The KIK mark in its holiday look (Holidays) on the day, in the headers of Home, the catalogue and the title page. It stands
 * still all day; on Home a short moment plays when the page comes back after half an hour (play()), then nothing redraws.
 * The same look, timed differently, draws the holiday intro before a video (HolidayIntro). Shapes and timings follow the
 * approved preview, in the 102×48 units of the plain mark (Brand). The projector's GPU is weak: every Path, Paint and Shader
 * is made once, a frame only moves the canvas, and frames are asked for only while something moves.
 */
public final class HolidayMark extends View {
 static final int RED=0xffe50914,ORANGE=0xffff7a1a;
 // The preview's CSS timing functions.
 static final Ease DRAW=new Ease(.6f,0,.3f,1),POP=new Ease(.2f,1.7f,.4f,1),STAR_RISE=new Ease(.2f,1.4f,.4f,1),STAR_SPIN=new Ease(.2f,1.3f,.4f,1),
  DROP_IN=new Ease(.55f,0,1,.45f),DROP_OUT=new Ease(0,.55f,.45f,1),HEART_POP=new Ease(.2f,1.6f,.4f,1),RISE=new Ease(.3f,1.5f,.5f,1),POUR=new Ease(.3f,1.3f,.5f,1),
  SPRINKLE=new Ease(.2f,1.8f,.4f,1),SPIDER=new Ease(.3f,1.35f,.5f,1),FUSE=new Ease(.5f,0,.8f,.6f),EASE=new Ease(.25f,.1f,.25f,1),IN_OUT=new Ease(.42f,0,.58f,1),
  OUT=new Ease(0,0,.58f,1),IN=new Ease(.42f,0,1,1);

 final Look look;
 private boolean playing;private long startAt=-1;
 /** Room of the view's own above and right of the mark's box, for decorations that reach out there (roomInCenteredRow). */
 private int roomTop,roomRight;

 /** The mark for a screen's header: on other days, or with the holidays off, the plain one (Brand). The size is the plain mark's, so the layout never shifts. */
 public static View logo(Context c,int widthDp,int heightDp){
  String id=AppFlags.today(c);if(id==null)return Brand.logo(c,widthDp,heightDp);
  HolidayMark v=new HolidayMark(c,id);v.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c,widthDp),Ui.dp(c,heightDp)));v.setContentDescription("KIK");return v;
 }
 HolidayMark(Context c,String id){super(c);look=new Look(id);}

 /**
  * For a mark in a row that centres it vertically (Home's top bar): the view grows by what its decorations reach above and
  * to the right, and negative margins give that room back, so the row lays out as with the plain mark. The decorations
  * then draw inside the view's own bounds: the row keeps clipping its other children (unclipped, every focus animation in
  * the menu would redraw the whole page) and the moment redraws only this view.
  */
 void roomInCenteredRow(){
  ViewGroup.LayoutParams old=getLayoutParams();if(!(old instanceof LinearLayout.LayoutParams)||old.width<=0||old.height<=0)return;
  float k=Math.min(old.width/102f,old.height/48f);
  // A multiple of 4, so the margins below split it exactly.
  int top=((int)Math.ceil(look.reachTop()*k)+3)/4*4,right=(int)Math.ceil(look.reachRight()*k);if(top==0&&right==0)return;
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(old.width+right,old.height+top);
  // A centred child's top lands at (room−height)/2 + top margin − bottom margin: −¾ and −¼ of the extra height keep the box where the plain mark stands.
  lp.setMargins(0,-(top-top/4),-right,-(top/4));roomTop=top;roomRight=right;setLayoutParams(lp);
 }
 @Override protected void onAttachedToWindow(){
  super.onAttachedToWindow();
  if(look.reachTop()<=0&&look.reachRight()<=0)return;
  // The star, the gift and the spider's thread reach past the mark's box. With room of its own (Home) only the row's padding
  // must let them through; otherwise the parents up to the page stop clipping (on the catalogue and the title page they already have).
  boolean own=roomTop>0||roomRight>0;ViewParent p=getParent();
  for(int n=0;n<4&&p instanceof ViewGroup&&!(p instanceof ScrollView)&&!(p instanceof HorizontalScrollView)&&((ViewGroup)p).getId()!=android.R.id.content;n++,p=p.getParent()){
   ViewGroup g=(ViewGroup)p;g.setClipToPadding(false);if(own)break;g.setClipChildren(false);
  }
 }
 @Override protected void onDetachedFromWindow(){playing=false;super.onDetachedFromWindow();}

 /** Home's moment, at most ~1.8 s; then the still look and no more frames. Nothing when animations are off on the TV. */
 public void play(){if(!Ui.animations()||look.momentLength()<=0)return;playing=true;startAt=-1;invalidate();}
 void stop(){if(!playing)return;playing=false;invalidate();}

 @Override protected void onDraw(Canvas c){
  super.onDraw(c);float w=getWidth()-roomRight,h=getHeight()-roomTop,k=Math.min(w/102f,h/48f);if(k<=0)return;
  c.save();c.translate((w-102*k)/2,roomTop+(h-48*k)/2);c.scale(k,k);
  if(playing){
   long now=AnimationUtils.currentAnimationTimeMillis();if(startAt<0)startAt=now;float t=(now-startAt)/1000f;
   if(t<look.momentLength()){look.draw(c,Look.MOMENT,t);postInvalidateOnAnimation();}else{playing=false;look.draw(c,Look.STILL,0);}
  }else look.draw(c,Look.STILL,0);
  c.restore();
 }
 /** Where a point of the mark (in its units) is in another view's coordinates; false while the mark is not laid out. */
 boolean place(View in,float lx,float ly,float[] out){
  if(!isAttachedToWindow()||getWidth()==0||getHeight()==0)return false;int[] a=new int[2],b=new int[2];getLocationInWindow(a);in.getLocationInWindow(b);
  float w=getWidth()-roomRight,h=getHeight()-roomTop,k=Math.min(w/102f,h/48f);
  out[0]=a[0]-b[0]+(w-102*k)/2+lx*k;out[1]=a[1]-b[1]+roomTop+(h-48*k)/2+ly*k;return true;
 }

 static float span(float t,float start,float length){return t<=start?0:t>=start+length?1:(t-start)/length;}
 static int alpha(float f){return Math.round(255*Math.max(0,Math.min(1,f)));}
 /** A value along CSS-like keyframes: {@code at} the offsets, {@code v} the values, {@code e} each segment's timing (null: linear; one for all). */
 static float key(float p,float[] at,float[] v,Ease[] e){
  int n=at.length-1;if(p<=at[0])return v[0];if(p>=at[n])return v[n];
  int i=0;while(i<n-1&&p>at[i+1])i++;
  float u=(p-at[i])/(at[i+1]-at[i]);Ease ease=e==null?null:e[Math.min(i,e.length-1)];if(ease!=null)u=ease.at(u);
  return v[i]+(v[i+1]-v[i])*u;
 }
 static int mix(int a,int b,float m){
  m=Math.max(0,Math.min(1,m));int ar=(a>>16)&255,ag=(a>>8)&255,ab=a&255,br=(b>>16)&255,bg=(b>>8)&255,bb=b&255;
  return 0xff000000|Math.round(ar+(br-ar)*m)<<16|Math.round(ag+(bg-ag)*m)<<8|Math.round(ab+(bb-ab)*m);
 }

 /** A CSS cubic-bezier timing function, solved without allocating (Newton's steps, bisection if they stall). */
 static final class Ease{
  private final float ax,bx,cx,ay,by,cy;
  Ease(float x1,float y1,float x2,float y2){cx=3*x1;bx=3*(x2-x1)-cx;ax=1-cx-bx;cy=3*y1;by=3*(y2-y1)-cy;ay=1-cy-by;}
  float at(float x){if(x<=0)return 0;if(x>=1)return 1;float s=solve(x);return ((ay*s+by)*s+cy)*s;}
  private float solve(float x){
   float s=x;
   for(int i=0;i<8;i++){float e=((ax*s+bx)*s+cx)*s-x;if(Math.abs(e)<1e-5f)return s;float d=(3*ax*s+2*bx)*s+cx;if(Math.abs(d)<1e-6f)break;s-=e/d;}
   float lo=0,hi=1;s=x;
   for(int i=0;i<24;i++){float v=((ax*s+bx)*s+cx)*s;if(Math.abs(v-x)<1e-5f)break;if(v<x)lo=s;else hi=s;s=(lo+hi)/2;}
   return s;
  }
 }

 /**
  * One holiday's look of the mark, drawn in its 102×48 units: still (all day), the moment (Home) or the intro (before a
  * video), at a time in seconds. Everything it draws with is made here once.
  */
 static final class Look{
  static final int STILL=0,MOMENT=1,INTRO=2;
  /** When the holiday's own action starts: in the intro after the strokes have drawn in, in the moment almost at once. */
  static final float INTRO_ACTION=1.15f,MOMENT_ACTION=.3f;
  /** The spider's thread hangs from far above: off the top of the screen wherever the mark is. */
  private static final float THREAD_TOP=-400;
  /** When each stroke starts drawing in, in the intro (the clip's order: K, its arm, I, K, its arm). */
  private static final float[] DRAW_AT={0,.16f,.34f,.52f,.68f};
  private static final float[] SPRINKLES={47.3f,12.3f,50.6f,8.6f,54.2f,11.2f,56.5f,15.8f,48.5f,17,52.6f,15.2f,45.3f,18.6f,55.2f,19.6f,51,19.9f};
  static final int[] SPRINKLE_COLORS={0xffe50914,0xffffd500,0xff3d8bff,0xff2fbf71,0xffff7ab6,0xffe50914,0xffffd500,0xff3d8bff,0xff2fbf71};
  private static final float[] GLOW_AT={2.4f,2.9f,3.3f},GLOW_V={0,1,.5f},GLOW_A={.3f,.18f,.1f};
  private static final float[] DROP_AT={0,.45f,.62f,.78f,.88f,1},DROP_Y={-70,0,-9,0,-2,0},DROP_SX={1,1.1f,.96f,1.04f,1,1},DROP_SY={1,.88f,1.04f,.96f,1,1};
  private static final float[] FADE_AT={0,.45f,1},FADE_V={0,1,1},LID_AT={0,.4f,1},LID_Y={0,-4,0},LID_R={0,-16,0};
  private static final float[] BEAT_AT={0,.14f,.28f,.42f,.7f,1},BEAT_V={1,1.18f,1,1.1f,1,1};
  private static final float[] HOP_AT={0,.22f,.5f,.75f,1},HOP_Y={0,0,-6,0,0},HOP_SX={1,1.08f,.95f,1.04f,1},HOP_SY={1,.9f,1.06f,.96f,1};
  private static final float[] FLICK_AT={0,.1f,.18f,.3f,.36f,.52f,.58f,1},FLICK_V={1,.12f,1,.25f,1,.5f,1,1};
  private static final Ease[] DROP_E={DROP_IN,DROP_OUT,DROP_IN,null,null},FADE_E={DROP_IN,null},OUT_ALL={OUT},IN_OUT_ALL={IN_OUT};

  final String id;
  private final Path[] strokes=new Path[5];private final PathMeasure[] measures=new PathMeasure[5];private final float[] lengths=new float[5];private final Path seg=new Path();
  private final Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG),glow=new Paint(Paint.ANTI_ALIAS_FLAG),fill=new Paint(Paint.ANTI_ALIAS_FLAG),line=new Paint(Paint.ANTI_ALIAS_FLAG);
  private Paint flag,crust,side,halo,heartPaint;private Path star,heart,bow,body,glaze,shine,legs;private float[] rays;

  Look(String id){
   this.id=id;
   float[][] points={{8,8,8,40},{29,8,8,24,29,40},{51,8,51,40},{73,8,73,40},{94,8,73,24,94,40}};
   for(int i=0;i<5;i++){Path p=new Path();p.moveTo(points[i][0],points[i][1]);for(int j=2;j<points[i].length;j+=2)p.lineTo(points[i][j],points[i][j+1]);strokes[i]=p;measures[i]=new PathMeasure(p,false);lengths[i]=measures[i].getLength();}
   stroke(stroke,7.6f);stroke.setColor(RED);stroke(glow,7.6f);stroke(line,1);fill.setStyle(Paint.Style.FILL);
   switch(id){
    case Holidays.NEW_YEAR:
     rays=new float[32];for(int k=0;k<8;k++){double a=k*Math.PI/4;float r1=k%2==1?2.8f:3.2f,r2=k%2==1?5.2f:7.2f;rays[4*k]=(float)Math.cos(a)*r1;rays[4*k+1]=(float)Math.sin(a)*r1;rays[4*k+2]=(float)Math.cos(a)*r2;rays[4*k+3]=(float)Math.sin(a)*r2;}
     break;
    case Holidays.XMAS:
     star=new Path();star.moveTo(0,-8);star.quadTo(.9f,-.9f,6,0);star.quadTo(.9f,.9f,0,7.5f);star.quadTo(-.9f,.9f,-6,0);star.quadTo(-.9f,-.9f,0,-8);star.close();
     halo=new Paint(Paint.ANTI_ALIAS_FLAG);halo.setShader(new RadialGradient(0,0,10,0x99f6d37a,0x00f6d37a,Shader.TileMode.CLAMP));
     break;
    case Holidays.NICK:
     bow=new Path();bow.moveTo(0,-7.8f);bow.cubicTo(-2.6f,-11.8f,-6,-9.6f,-2.4f,-7.8f);bow.close();bow.moveTo(0,-7.8f);bow.cubicTo(2.6f,-11.8f,6,-9.6f,2.4f,-7.8f);bow.close();
     break;
    case Holidays.VALENTINE:
     heart=heartPath();heartPaint=new Paint(Paint.ANTI_ALIAS_FLAG);heartPaint.setStyle(Paint.Style.FILL_AND_STROKE);heartPaint.setStrokeWidth(5);heartPaint.setStrokeJoin(Paint.Join.ROUND);heartPaint.setColor(RED);
     break;
    case Holidays.EASTER:
     body=new Path();body.moveTo(44.3f,21);body.lineTo(57.7f,21);body.lineTo(57.7f,41.4f);body.quadTo(57.7f,43.6f,55.5f,43.6f);body.lineTo(46.5f,43.6f);body.quadTo(44.3f,43.6f,44.3f,41.4f);body.close();
     glaze=new Path();glaze.moveTo(42.3f,21.5f);glaze.cubicTo(42.3f,9,46,5,51,5);glaze.cubicTo(56,5,59.7f,9,59.7f,21.5f);glaze.quadTo(59,25.6f,58,22.2f);glaze.quadTo(56.6f,23.3f,55.3f,22);glaze.quadTo(54.4f,26.8f,53.2f,22.2f);glaze.quadTo(51.5f,23.4f,49.8f,22.1f);glaze.quadTo(48.7f,25.2f,47.5f,22.1f);glaze.quadTo(45.5f,23.2f,42.3f,21.5f);glaze.close();
     shine=new Path();shine.moveTo(45,12);shine.cubicTo(46,8.4f,48.2f,6.6f,51,6.4f);
     // The crust browns towards the bottom; the sides are shaded, the middle catches a little light.
     crust=new Paint(Paint.ANTI_ALIAS_FLAG);crust.setShader(new LinearGradient(0,21,0,43.6f,0xffe7b064,0xffa9672b,Shader.TileMode.CLAMP));
     side=new Paint(Paint.ANTI_ALIAS_FLAG);side.setShader(new LinearGradient(44.3f,0,57.7f,0,new int[]{0x47000000,0x00000000,0x14ffffff,0x52000000},new float[]{0,.3f,.7f,1},Shader.TileMode.CLAMP));
     break;
    case Holidays.INDEPENDENCE:
     // Blue above the middle line, yellow below it: a hard split, as on the flag.
     flag=new Paint(Paint.ANTI_ALIAS_FLAG);stroke(flag,7.6f);flag.setShader(new LinearGradient(51,4,51,44,new int[]{0xff1f6fe0,0xff1f6fe0,0xffffd500,0xffffd500},new float[]{0,.5f,.5f,1},Shader.TileMode.CLAMP));
     break;
    case Holidays.HALLOWEEN:
     legs=new Path();float[] l={-1.6f,-1.2f,-4.5f,-4.6f,-6.6f,-2.2f,-1.9f,0,-5.2f,-1.6f,-7.2f,1,-1.9f,1,-5,2,-6.4f,4.6f,-1.4f,2,-3.6f,4.2f,-4.4f,6.8f};
     for(int sign=-1;sign<=1;sign+=2)for(int i=0;i<l.length;i+=6){legs.moveTo(sign*l[i],l[i+1]);legs.quadTo(sign*l[i+2],l[i+3],sign*l[i+4],l[i+5]);}
     break;
    default:break;
   }
  }
  private static void stroke(Paint p,float width){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);}
  /** The preview's heart, about 22 units wide around (0, 0.75). */
  static Path heartPath(){
   Path p=new Path();p.moveTo(0,13);p.cubicTo(-7,7.5f,-11,3,-11,-3.5f);p.cubicTo(-11,-8.2f,-7.8f,-11.5f,-4.2f,-11.5f);p.cubicTo(-2,-11.5f,-.7f,-10.2f,0,-8.5f);
   p.cubicTo(.7f,-10.2f,2,-11.5f,4.2f,-11.5f);p.cubicTo(7.8f,-11.5f,11,-8.2f,11,-3.5f);p.cubicTo(11,3,7,7.5f,0,13);p.close();return p;
  }
  /** How long Home's moment lasts, in seconds. */
  float momentLength(){
   switch(id){
    case Holidays.NEW_YEAR:return .85f;case Holidays.XMAS:return 1.6f;case Holidays.NICK:return 1.75f;case Holidays.VALENTINE:return 1.8f;
    case Holidays.EASTER:return 1.45f;case Holidays.INDEPENDENCE:return 1.11f;case Holidays.HALLOWEEN:return 1.3f;default:return 0;
   }
  }
  /** How far the look reaches above the box, in units, the moment included: the spark, the star, the falling gift and spider, the hopping paska. */
  float reachTop(){
   switch(id){case Holidays.NEW_YEAR:return 16;case Holidays.XMAS:return 18;case Holidays.NICK:return 46;case Holidays.EASTER:return 6;case Holidays.HALLOWEEN:return 64;default:return 0;}
  }
  /** How far it reaches right of the box: the gift beside the last K. */
  float reachRight(){return Holidays.NICK.equals(id)?16:0;}

  void draw(Canvas c,int mode,float t){
   stroke.setColor(RED);
   if(mode==INTRO)glow(c,t);
   float a=mode==INTRO?INTRO_ACTION:MOMENT_ACTION;
   switch(id){
    case Holidays.NEW_YEAR:newYear(c,mode,t,a);break;
    case Holidays.XMAS:christmas(c,mode,t,a);break;
    case Holidays.NICK:nicholas(c,mode,t,a);break;
    case Holidays.VALENTINE:valentine(c,mode,t,a);break;
    case Holidays.EASTER:easter(c,mode,t,a);break;
    case Holidays.INDEPENDENCE:independence(c,mode,t,a);break;
    case Holidays.HALLOWEEN:halloween(c,mode,t,a);break;
    default:base(c,mode,t,false);
   }
  }

  // ---------- the strokes ----------
  private void base(Canvas c,int mode,float t,boolean skipI){if(mode==INTRO)drawnIn(c,t,skipI);else strokes(c,stroke,skipI);}
  private void strokes(Canvas c,Paint p,boolean skipI){for(int i=0;i<5;i++)if(!skipI||i!=2)c.drawPath(strokes[i],p);}
  /** The intro's strokes, each drawn in from its start over 0.34 s. */
  private void drawnIn(Canvas c,float t,boolean skipI){for(int i=0;i<5;i++)if(!skipI||i!=2)part(c,i,DRAW.at(span(t,DRAW_AT[i],.34f)),stroke);}
  /** The first fraction of stroke i, cut into the one reused path. */
  private void part(Canvas c,int i,float f,Paint p){if(f<=0)return;if(f>=1){c.drawPath(strokes[i],p);return;}seg.rewind();measures[i].getSegment(0,lengths[i]*f,seg,true);c.drawPath(seg,p);}
  /** The intro's glow around the strokes from 2.4 s: wider, faint passes of the same strokes, no blur on the GPU. */
  private void glow(Canvas c,float t){
   float g=key(t,GLOW_AT,GLOW_V,IN_OUT_ALL);if(g<=0)return;
   int color=Holidays.INDEPENDENCE.equals(id)?0x80ffd500:Holidays.HALLOWEEN.equals(id)?0xb3ff7a1a:0xbfe50914;boolean skipI=Holidays.VALENTINE.equals(id)||Holidays.EASTER.equals(id);
   for(int i=3;i>=1;i--){
    float spread=7.5f*g*i/3f;glow.setColor(color);glow.setAlpha((int)(Color.alpha(color)*g*GLOW_A[i-1]));glow.setStrokeWidth(7.6f+2*spread);strokes(c,glow,skipI);
    if(heart!=null){glow.setStrokeWidth(5+2*spread);c.save();c.translate(51,25);c.drawPath(heart,glow);c.restore();}
   }
  }
  private void paint(int color,int alpha){fill.setColor(color);fill.setAlpha(alpha);}

  // ---------- the holidays ----------
  /** New Year: an eight-ray gold spark over the I. The moment pops it; in the intro a sparkler climbs the I first. */
  private void newYear(Canvas c,int mode,float t,float a){
   base(c,mode,t,false);float k=1;
   if(mode==INTRO){
    float f=span(t,a,.6f);
    if(f>0&&f<1){paint(0xffffe7a3,alpha(f<.1f?f/.1f:f>.92f?(1-f)/.08f:1));c.drawCircle(51,40-46*FUSE.at(Math.min(1,f/.92f)),2.4f,fill);}
    k=POP.at(span(t,a+.58f,.55f));
   }else if(mode==MOMENT)k=POP.at(span(t,a,.55f));
   if(k<=0)return;
   c.save();c.translate(51,-6.5f);c.scale(k,k);line.setColor(0xfff5c451);line.setStrokeWidth(1.7f);c.drawLines(rays,line);paint(0xffffe7a3,255);c.drawCircle(0,0,1.4f,fill);c.restore();
  }
  /** Christmas: a gold four-point star with a soft glow over the I. The moment spins it in; the intro raises it. */
  private void christmas(Canvas c,int mode,float t,float a){
   base(c,mode,t,false);c.save();c.translate(51,-6);
   float glowA=.8f,glowS=1;
   if(mode==MOMENT){float p=(float)Math.sin(Math.PI*span(t,0,1.6f));glowA=.8f+.2f*p;glowS=1+.08f*p;}
   else if(mode==INTRO){float g=EASE.at(span(t,a+.45f,.8f));glowA=g;glowS=.3f+.7f*g;}
   if(glowA>0){c.save();c.scale(glowS,glowS);halo.setAlpha(alpha(glowA));c.drawCircle(0,0,10,halo);c.restore();}
   float s=1,r=0,y=0,o=1;
   if(mode==MOMENT){float e=STAR_SPIN.at(span(t,a,1));s=.5f+.5f*e;r=-90*(1-e);}
   else if(mode==INTRO){float e=STAR_RISE.at(span(t,a,.9f));o=e;s=.2f+.8f*e;r=-45*(1-e);y=12*(1-e);}
   if(o>0&&s>0){c.translate(0,y-.25f);c.rotate(r);c.scale(s,s);c.translate(0,.25f);paint(0xfff6d37a,alpha(o));c.drawPath(star,fill);}
   c.restore();
  }
  /** St Nicholas: a gold gift with a red ribbon right of the last K. It drops in with a bounce, then its lid hops. */
  private void nicholas(Canvas c,int mode,float t,float a){
   base(c,mode,t,false);float y=0,sx=1,sy=1,o=1,ly=0,lr=0;
   if(mode!=STILL){
    float d=span(t,a,1);y=key(d,DROP_AT,DROP_Y,DROP_E);sx=key(d,DROP_AT,DROP_SX,DROP_E);sy=key(d,DROP_AT,DROP_SY,DROP_E);o=key(d,FADE_AT,FADE_V,FADE_E);
    float l=span(t,a+.85f,.6f);ly=key(l,LID_AT,LID_Y,OUT_ALL);lr=key(l,LID_AT,LID_R,OUT_ALL);
   }
   if(o<=0)return;int alpha=alpha(o);
   // Squashed and stretched from its bottom edge.
   c.save();c.translate(108,37.3f+y);c.translate(0,6.3f);c.scale(sx,sy);c.translate(0,-6.3f);
   paint(0xffe9b44c,alpha);c.drawRoundRect(-6.5f,-4.5f,6.5f,6.3f,1.4f,1.4f,fill);paint(RED,alpha);c.drawRect(-.8f,-4.5f,.8f,6.3f,fill);
   // The lid hinges near its left end and hops.
   c.translate(-5.18f,-4+ly);c.rotate(lr);c.translate(5.18f,4);
   paint(0xfff6c964,alpha);c.drawRoundRect(-7.4f,-7.8f,7.4f,-4,1,1,fill);paint(RED,alpha);c.drawRect(-.8f,-7.8f,.8f,-4,fill);c.drawPath(bow,fill);
   c.restore();
  }
  /** Two heartbeats of the given length from t=0, then still. */
  private static float beat(float t,float cycle){if(t<=0||t>=2*cycle)return 1;return key((t%cycle)/cycle,BEAT_AT,BEAT_V,IN_OUT_ALL);}
  /** Valentine's Day: a red heart in place of the I. It beats twice; in the intro the I draws in, collapses and the heart pops out. */
  private void valentine(Canvas c,int mode,float t,float a){
   float s=1;
   if(mode==INTRO){
    for(int i=0;i<5;i++)if(i!=2)part(c,i,DRAW.at(span(t,DRAW_AT[i],.34f)),stroke);
    float out=IN.at(span(t,a,.28f)),f=DRAW.at(span(t,DRAW_AT[2],.34f));
    if(out<1&&f>0){c.save();c.translate(51,24);c.scale(1,1-out);c.translate(-51,-24);stroke.setAlpha(alpha(1-out));part(c,2,f,stroke);stroke.setAlpha(255);c.restore();}
    // The clip is short: the two beats come quicker than on Home.
    s=HEART_POP.at(span(t,a+.12f,.6f))*beat(t-(a+.75f),.6f);
   }else{strokes(c,stroke,true);if(mode==MOMENT)s=beat(t,1.05f);}
   if(s<=0)return;
   c.save();c.translate(51,25.75f);c.scale(s,s);c.translate(0,-.75f);c.drawPath(heart,heartPaint);c.restore();
  }
  /** Easter: a paska in place of the I, its crust shaded, white glaze with drips and nine sprinkles. The moment makes it hop and
   *  the sprinkles pop again; in the intro it rises, the glaze pours and the sprinkles pop. */
  private void easter(Canvas c,int mode,float t,float a){
   base(c,mode,t,true);float y=0,sx=1,sy=1,rise=1,pour=1,glazeA=1;
   if(mode==MOMENT){float h=span(t,a,.8f);y=key(h,HOP_AT,HOP_Y,IN_OUT_ALL);sx=key(h,HOP_AT,HOP_SX,IN_OUT_ALL);sy=key(h,HOP_AT,HOP_SY,IN_OUT_ALL);}
   else if(mode==INTRO){rise=RISE.at(span(t,a,.75f));float p=span(t,a+.5f,.6f);pour=POUR.at(p);glazeA=Math.min(1,p/.25f);}
   c.save();c.translate(51,43.6f+y);c.scale(sx,sy);c.translate(-51,-43.6f);
   if(rise>0){c.save();c.translate(51,43.6f);c.scale(.8f+.2f*rise,rise);c.translate(-51,-43.6f);c.drawPath(body,crust);c.drawPath(body,side);c.restore();}
   if(pour>0&&glazeA>0){
    c.save();c.translate(51,5);c.scale(1,pour);c.translate(-51,-5);paint(0xfff7f2e8,alpha(glazeA));c.drawPath(glaze,fill);
    line.setColor(Color.WHITE);line.setStrokeWidth(.9f);line.setAlpha(alpha(.85f*glazeA));c.drawPath(shine,line);c.restore();
   }
   for(int i=0;i<9;i++){
    float k=1;if(mode==MOMENT)k=SPRINKLE.at(span(t,a+.35f+i*.05f,.4f));else if(mode==INTRO)k=SPRINKLE.at(span(t,a+1.05f+i*.07f,.4f));
    if(k>0){paint(SPRINKLE_COLORS[i],255);c.drawCircle(SPRINKLES[2*i],SPRINKLES[2*i+1],k,fill);}
   }
   c.restore();
  }
  /** Independence Day: the strokes blue above the middle, yellow below. The moment retraces them over dimmed ones; in the
   *  intro they retrace over the red. */
  private void independence(Canvas c,int mode,float t,float a){
   if(mode==STILL){strokes(c,flag,false);return;}
   if(mode==MOMENT){flag.setAlpha(alpha(.28f));strokes(c,flag,false);flag.setAlpha(255);}else drawnIn(c,t,false);
   for(int j=0;j<5;j++)part(c,j,DRAW.at(span(t,a+j*.09f,.45f)),flag);
  }
  /** Halloween: orange strokes and a pale spider on a thread between the first K and the I. It drops on the thread; in the
   *  intro the red neon flickers to orange first. */
  private void halloween(Canvas c,int mode,float t,float a){
   if(mode==INTRO){stroke.setColor(mix(RED,ORANGE,EASE.at(span(t,a+.55f,.45f))));stroke.setAlpha(alpha(key(span(t,a+.1f,.7f),FLICK_AT,FLICK_V,null)));drawnIn(c,t,false);stroke.setColor(RED);}
   else{stroke.setColor(ORANGE);strokes(c,stroke,false);stroke.setColor(RED);}
   // It falls from out of sight: from above Home's top edge in the moment, from above the screen in the intro.
   float e=1,fall=86;if(mode==MOMENT)e=SPIDER.at(span(t,a,1));else if(mode==INTRO){e=SPIDER.at(span(t,a+.7f,1));fall=120;}
   float y=26.5f-fall*(1-e);
   line.setColor(0x73ffffff);line.setStrokeWidth(.5f);if(y-3.5f>THREAD_TOP)c.drawLine(39.5f,THREAD_TOP,39.5f,y-3.5f,line);
   c.save();c.translate(39.5f,y);line.setColor(0xffededf0);line.setStrokeWidth(.8f);c.drawPath(legs,line);paint(0xffededf0,255);c.drawOval(-2.4f,-1.6f,2.4f,4.2f,fill);c.drawCircle(0,-2.1f,1.6f,fill);c.restore();
  }
 }

 /**
  * A fixed pool of at most 90 particles (sparks, dots, twinkles, hearts, confetti, snowflakes, rockets), allocated once and
  * drawn as circles, short lines, rects and the one heart path. Sizes and speeds are the preview's, in dp. A negative age
  * is a delay before the particle starts.
  */
 static final class Particles{
  static final int MAX=90;
  static final byte NONE=0,SPARK=1,DOT=2,TWINKLE=3,HEART=4,CONFETTI=5,FLAKE=6,ROCKET=7;
  static final int[][] FIREWORKS={{0xffffe7a3,0xfff5c451,0xffffffff},{0xffe50914,0xffff8a8a,0xffffe7a3},{0xffffffff,0xffbfd8ff},{0xfff5c451,0xffffffff}};
  private final byte[] type=new byte[MAX];private final int[] color=new int[MAX],set=new int[MAX];
  private final float[] x=new float[MAX],y=new float[MAX],vx=new float[MAX],vy=new float[MAX],g=new float[MAX],drag=new float[MAX],age=new float[MAX],life=new float[MAX],
   size=new float[MAX],rot=new float[MAX],spin=new float[MAX],sway=new float[MAX],amp=new float[MAX],phase=new float[MAX],fade=new float[MAX];
  private final Random random=new Random();private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path heart=Look.heartPath();
  /** Pixels per dp. */
  final float s;
  Particles(float density){s=density;paint.setStrokeCap(Paint.Cap.ROUND);}
  float r(){return random.nextFloat();}
  /** A free slot, or -1 when all 90 fly: the particle is then simply left out. */
  private int add(byte t,float px,float py,float pvx,float pvy,float pg,float pdrag,float plife,float psize,int pcolor,float delay){
   for(int i=0;i<MAX;i++)if(type[i]==NONE){type[i]=t;x[i]=px;y[i]=py;vx[i]=pvx;vy[i]=pvy;g[i]=pg;drag[i]=pdrag;life[i]=plife;size[i]=psize;color[i]=pcolor;age[i]=-delay;rot[i]=0;spin[i]=0;sway[i]=0;amp[i]=0;phase[i]=0;fade[i]=1;set[i]=0;return i;}
   return -1;
  }
  void burst(float px,float py,int n,int[] colors,float speed,float gravity,float lifetime,float sz,byte t,float delay){
   for(int i=0;i<n;i++){double a=i*Math.PI*2/n+r()*.3;float v=speed*s*(.45f+r()*.55f);add(t,px,py,(float)Math.cos(a)*v,(float)Math.sin(a)*v,gravity*s,1.6f,lifetime*(.7f+r()*.45f),sz*(.8f+r()*.5f),colors[i%colors.length],delay);}
  }
  void sparks(float px,float py,int n){
   for(int i=0;i<n;i++){double a=r()*Math.PI*2;float v=(30+r()*60)*s;add(DOT,px,py,(float)Math.cos(a)*v,(float)Math.sin(a)*v-20*s,120*s,2,.35f+r()*.3f,1.1f,r()<.5f?0xffffe7a3:0xffffffff,0);}
  }
  void hearts(float px,float py,int n,int[] colors,float delay){
   for(int i=0;i<n;i++){int j=add(HEART,px+(r()-.5f)*40*s,py,(r()-.5f)*20*s,-(40+r()*30)*s,-6*s,.4f,1.8f+r()*.8f,(5+r()*4)*s,colors[i%colors.length],delay+i*.12f);if(j>=0)phase[j]=r()*6;}
  }
  void confetti(float px,float py,int n,int[] colors,float delay){
   for(int i=0;i<n;i++){double a=-Math.PI/2+(r()-.5f)*2.2f;float v=(80+r()*90)*s;int j=add(CONFETTI,px,py,(float)Math.cos(a)*v,(float)Math.sin(a)*v,170*s,1.4f,1.1f+r()*.5f,(2+r()*1.4f)*s,colors[i%colors.length],delay);if(j>=0){rot[j]=r()*6;spin[j]=(r()-.5f)*14;}}
  }
  void twinkles(float px,float py,int n,int c,float delay){
   for(int i=0;i<n;i++){double a=r()*Math.PI*2;float d=(10+r()*26)*s;add(TWINKLE,px+(float)Math.cos(a)*d,py+(float)Math.sin(a)*d,0,0,0,0,.6f+r()*.7f,1+r()*1.3f,c,delay+r()*.6f);}
  }
  /** Snowflakes over the whole w×h, falling slowly and swaying; they fade in, and out by the end of their life. */
  void snow(int n,float w,float h,float lifetime){
   for(int i=0;i<n;i++){int j=add(FLAKE,r()*w,r()*h,0,(9+r()*12)*s,0,0,lifetime,.9f+r()*1.3f,0xffffffff,0);if(j>=0){sway[j]=.6f+r()*.9f;amp[j]=(6+r()*10)*s;phase[j]=r()*9;fade[j]=.25f+r()*.4f;}}
  }
  /** A rocket from y0 up to ty that bursts at the top in one of the FIREWORKS colour sets. */
  void rocket(float px,float ty,float y0,int colors,float delay,float gravity){
   float dy=Math.max(20*s,y0-ty);int j=add(ROCKET,px,y0,(r()-.5f)*10*s,-(float)Math.sqrt(2*gravity*dy),gravity,0,9,1,0xffffe7a3,delay);if(j>=0)set[j]=colors;
  }
  /** Moves every particle by dt seconds; false once none is left, waiting ones included. */
  boolean step(float dt,float w,float h){
   boolean any=false;
   for(int i=0;i<MAX;i++){
    byte t=type[i];if(t==NONE)continue;
    age[i]+=dt;if(age[i]<0){any=true;continue;}
    if(t==FLAKE){
     y[i]+=vy[i]*dt;x[i]+=(float)Math.sin((age[i]+phase[i])*sway[i])*amp[i]*dt;if(y[i]>h+6*s){y[i]=-6*s;x[i]=r()*w;}
     if(age[i]>=life[i])type[i]=NONE;else any=true;continue;
    }
    float k=(float)Math.exp(-drag[i]*dt);vx[i]*=k;vy[i]*=k;vy[i]+=g[i]*dt;x[i]+=vx[i]*dt;y[i]+=vy[i]*dt;rot[i]+=spin[i]*dt;
    if(t==ROCKET){if(vy[i]>=0){type[i]=NONE;burst(x[i],y[i],16,FIREWORKS[set[i]%FIREWORKS.length],200,70,1.1f,1.4f,SPARK,0);}any=true;continue;}
    if(age[i]>=life[i]){type[i]=NONE;continue;}
    any=true;
   }
   return any;
  }
  void draw(Canvas c){
   for(int i=0;i<MAX;i++){
    byte t=type[i];if(t==NONE||age[i]<0)continue;float k=1-age[i]/life[i];
    switch(t){
     case SPARK:paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,size[i]*s*1.6f));paint.setColor(color[i]);paint.setAlpha(alpha(k*1.5f));c.drawLine(x[i],y[i],x[i]-vx[i]*.05f,y[i]-vy[i]*.05f,paint);break;
     case DOT:paint.setStyle(Paint.Style.FILL);paint.setColor(color[i]);paint.setAlpha(alpha(k*1.5f));c.drawCircle(x[i],y[i],Math.max(.7f*s,size[i]*s*1.3f),paint);break;
     case TWINKLE:{
      float a=(float)Math.sin(age[i]/life[i]*Math.PI),r=Math.max(1,size[i]*s*4*a);
      paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,r*.3f));paint.setColor(color[i]);paint.setAlpha(alpha(a));c.drawLine(x[i]-r,y[i],x[i]+r,y[i],paint);c.drawLine(x[i],y[i]-r,x[i],y[i]+r,paint);break;
     }
     case HEART:{
      float scale=size[i]*2.6f/22;paint.setStyle(Paint.Style.FILL);paint.setColor(color[i]);paint.setAlpha(alpha(Math.min(1,k*1.4f)*.9f));
      c.save();c.translate(x[i]+(float)Math.sin(age[i]*3+phase[i])*4*s,y[i]);c.scale(scale,scale);c.drawPath(heart,paint);c.restore();break;
     }
     case CONFETTI:paint.setStyle(Paint.Style.FILL);paint.setColor(color[i]);paint.setAlpha(alpha(k*1.6f));c.save();c.translate(x[i],y[i]);c.rotate(rot[i]*57.29578f);c.drawRect(-size[i],-size[i]*.45f,size[i],size[i]*.45f,paint);c.restore();break;
     case FLAKE:paint.setStyle(Paint.Style.FILL);paint.setColor(0xffffffff);paint.setAlpha(alpha(fade[i]*Math.min(1,age[i]/.5f)*Math.min(1,life[i]-age[i])));c.drawCircle(x[i],y[i],Math.max(.7f*s,size[i]*s*1.5f),paint);break;
     case ROCKET:paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,1.8f*s));paint.setColor(color[i]);paint.setAlpha(230);c.drawLine(x[i],y[i],x[i]-vx[i]*.06f,y[i]-vy[i]*.06f,paint);break;
     default:break;
    }
   }
  }
 }

 /**
  * Home's particles: a light overlay above the page that exists only while they fly, then takes itself away. Sized to the
  * burst where it can be, so only that corner redraws; the whole page only for snow (at most 15 flakes, 6 s) and fireworks.
  */
 static final class Burst extends View{
  static final int[] GOLD={0xffffe7a3,0xfff5c451,0xffffffff},GIFT={0xfff6d37a,0xffffffff},HEARTS={0xffff4d6d,0xffe50914,0xffff8fa3};
  final Particles particles;private final int ox,oy,width,height;private long last=-1;private boolean stopped,gone;
  private Burst(Context c,int left,int top,int w,int h){super(c);particles=new Particles(c.getResources().getDisplayMetrics().density);ox=left;oy=top;width=w;height=h;setFocusable(false);setClickable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
  /** The overlay over the part of root around (x, y): {@code up} above, {@code down} below, {@code side} to each side. */
  private static Burst around(FrameLayout root,float x,float y,float side,float up,float down){
   int w=root.getWidth(),h=root.getHeight(),l=Math.max(0,Math.round(x-side)),t=Math.max(0,Math.round(y-up)),r=Math.min(w,Math.round(x+side)),b=Math.min(h,Math.round(y+down));
   return r-l<=0||b-t<=0?null:new Burst(root.getContext(),l,t,r-l,b-t);
  }
  /** Home's moment, with the mark's own play(): null when this holiday has none, or there is nothing to place it by. */
  static Burst moment(FrameLayout root,HolidayMark mark,String id,View focused){
   int w=root.getWidth(),h=root.getHeight();if(w==0||h==0)return null;
   float d=root.getResources().getDisplayMetrics().density;float[] at=new float[2];Burst b;
   switch(id){
    case Holidays.NEW_YEAR:
     if(mark==null||!mark.place(root,51,-6.5f,at)||(b=around(root,at[0],at[1],150*d,150*d,170*d))==null)return null;
     b.particles.burst(at[0]-b.ox,at[1]-b.oy,40,GOLD,120,60,1,1.2f,Particles.SPARK,.5f);break;
    case Holidays.XMAS:
     b=new Burst(root.getContext(),0,0,w,h);if(mark!=null&&mark.place(root,51,-6,at))b.particles.twinkles(at[0],at[1],7,0xfff6d37a,.6f);b.particles.snow(15,w,h,6);break;
    case Holidays.NICK:
     b=new Burst(root.getContext(),0,0,w,h);if(mark!=null&&mark.place(root,108,29,at))b.particles.burst(at[0],at[1],14,GIFT,70,40,.8f,1.1f,Particles.DOT,1.2f);b.particles.snow(10,w,h,6);break;
    case Holidays.VALENTINE:{
     // Six small hearts rise from the poster in focus, or from the heart in the mark when no poster is.
     if(focused instanceof FrameLayout&&!(focused instanceof ScrollView)&&!(focused instanceof HorizontalScrollView)&&focused.getWidth()>0&&focused.isAttachedToWindow()){int[] f=new int[2],r=new int[2];focused.getLocationInWindow(f);root.getLocationInWindow(r);at[0]=f[0]-r[0]+focused.getWidth()*focused.getScaleX()/2f;at[1]=f[1]-r[1]+6*d;}
     else if(mark==null||!mark.place(root,51,12,at))return null;
     if((b=around(root,at[0],at[1],100*d,240*d,30*d))==null)return null;
     b.particles.hearts(at[0]-b.ox,at[1]-b.oy,6,HEARTS,.55f);break;
    }
    case Holidays.EASTER:
     if(mark==null||!mark.place(root,51,5,at)||(b=around(root,at[0],at[1],180*d,160*d,220*d))==null)return null;
     b.particles.confetti(at[0]-b.ox,at[1]-b.oy,14,Look.SPRINKLE_COLORS,.7f);break;
    default:return null;
   }
   root.addView(b,b.params());return b;
  }
  /** New Year at midnight: six rockets over the hero, a third of a second apart, bursting gold, red, white and gold-white; about 3 s. */
  static Burst fireworks(FrameLayout root){
   int w=root.getWidth(),h=root.getHeight();if(w==0||h==0)return null;Burst b=new Burst(root.getContext(),0,0,w,h);Particles p=b.particles;
   for(int i=0;i<6;i++)p.rocket(w*(.42f+p.r()*.5f),h*(.1f+p.r()*.28f),h*.82f,i,i*.3f,700*p.s);
   root.addView(b,b.params());return b;
  }
  private FrameLayout.LayoutParams params(){FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(width,height,Gravity.TOP|Gravity.START);lp.leftMargin=ox;lp.topMargin=oy;return lp;}
  @Override protected void onDraw(Canvas c){
   if(gone)return;long now=AnimationUtils.currentAnimationTimeMillis();float dt=last<0?0:Math.min(.05f,(now-last)/1000f);last=now;
   boolean alive=particles.step(dt,getWidth(),getHeight());particles.draw(c);
   if(alive&&!stopped)postInvalidateOnAnimation();else{gone=true;post(this::remove);}
  }
  void stop(){stopped=true;remove();}
  private void remove(){gone=true;ViewParent p=getParent();if(p instanceof ViewGroup)((ViewGroup)p).removeView(this);}
 }

 /** The one thin decoration across the very top of Home on the day; null for a holiday without one. */
 static View topline(Context c,String id){return toplineHeight(id)>0?new Topline(c,id):null;}
 /** Its height in dp: Christmas's garland, Easter's cross-stitch, the flag's two lines. */
 static int toplineHeight(String id){if(Holidays.XMAS.equals(id))return 34;if(Holidays.EASTER.equals(id))return 12;if(Holidays.INDEPENDENCE.equals(id))return 6;return 0;}
 /** Drawn once and never animated: lamps and stitches are a single drawPoints each, so a frame of Home replays only a few operations for it. */
 static final class Topline extends View{
  private final String id;private final float d;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG),dots=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path wire=new Path();
  private float[] lamps=new float[0],red=new float[0],gold=new float[0];
  Topline(Context c,String id){super(c);this.id=id;d=c.getResources().getDisplayMetrics().density;setFocusable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
  @Override protected void onSizeChanged(int w,int h,int ow,int oh){
   super.onSizeChanged(w,h,ow,oh);float width=w/d;
   if(Holidays.XMAS.equals(id)){
    // A hairline wire sagging between hooks every 120 dp, three warm lamps on each span.
    wire.rewind();int spans=0;for(float x0=-60;x0<width;x0+=120)spans++;lamps=new float[spans*6];int k=0;
    for(float x0=-60;x0<width;x0+=120){float cx=x0+60,x1=x0+120,cy=23;wire.moveTo(x0,3);wire.quadTo(cx,cy,x1,3);
     for(int j=1;j<=3;j++){float t=j*.25f,u=1-t;lamps[k++]=u*u*x0+2*u*t*cx+t*t*x1;lamps[k++]=u*u*3+2*u*t*cy+t*t*3+2.6f;}}
   }else if(Holidays.EASTER.equals(id)){
    // Red cross-stitch diamonds with a gold centre, one every 20 dp, each stitch a 2 dp square.
    float[] cross={9,1,7,3,11,3,5,5,13,5,7,7,11,7,9,9};int tiles=(int)Math.ceil(width/20)+1;red=new float[tiles*16];gold=new float[tiles*2];
    for(int n=0;n<tiles;n++){for(int i=0;i<16;i+=2){red[n*16+i]=n*20+cross[i]+1;red[n*16+i+1]=cross[i+1]+1;}gold[n*2]=n*20+10;gold[n*2+1]=6;}
   }
  }
  @Override protected void onDraw(Canvas c){
   c.save();c.scale(d,d);
   if(Holidays.XMAS.equals(id)){
    paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.8f);paint.setColor(0x29ffffff);c.drawPath(wire,paint);
    dots.setStyle(Paint.Style.STROKE);dots.setStrokeCap(Paint.Cap.ROUND);dots.setStrokeWidth(4.6f);dots.setColor(0xd9ffe2a6);c.drawPoints(lamps,dots);
   }else if(Holidays.EASTER.equals(id)){
    dots.setStyle(Paint.Style.STROKE);dots.setStrokeCap(Paint.Cap.SQUARE);dots.setStrokeWidth(2);dots.setColor(0xe6d0122e);c.drawPoints(red,dots);dots.setColor(0xe6f2c14e);c.drawPoints(gold,dots);
   }else if(Holidays.INDEPENDENCE.equals(id)){
    float w=getWidth()/d;paint.setStyle(Paint.Style.FILL);paint.setColor(0xff1f6fe0);c.drawRect(0,0,w,3,paint);paint.setColor(0xffffd500);c.drawRect(0,3,w,6,paint);
   }
   c.restore();
  }
 }
}
