package kik.i18n;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.ViewGroup;
import android.widget.ImageView;

/** Original vector characters: no downloads, emoji fonts or bitmap scaling artifacts. */
final class Avatars {
 private static final String[] IDS={"cat","fox","panda","robot","astronaut","owl","dog","ninja","alien","bear","penguin","rabbit"};
 private static final String[] NAMES={"Кіт","Лис","Панда","Робот","Космонавт","Сова","Пес","Ніндзя","Прибулець","Ведмідь","Пінгвін","Кролик"};
 private static final int[] COLORS={0xffb35350,0xff246e78,0xff637cb1,0xff704c91,0xff345488,0xff4b6e68,0xffa27043,0xff79506b,0xff495587,0xff476d83,0xff666497,0xff8b5974};
 static int count(){return IDS.length;}
 static String idAt(int index){return IDS[Math.floorMod(index,IDS.length)];}
 static int index(String id){for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))return i;return -1;}
 static boolean valid(String id){return index(id)>=0;}
 static String fallback(String profile){return idAt(profile==null?0:profile.hashCode());}
 static String label(String id){int i=index(id);return NAMES[i<0?0:i];}
 static ImageView view(Context c,String id,int size){
  ImageView v=new ImageView(c);v.setScaleType(ImageView.ScaleType.FIT_CENTER);v.setPadding(0,0,0,0);v.setFocusable(true);v.setClickable(true);v.setLayoutParams(new ViewGroup.LayoutParams(Ui.dp(c,size),Ui.dp(c,size)));bind(v,id);
  v.setOnFocusChangeListener((view,focused)->Ui.focus(view,focused,1.06f));return v;
 }
 static void bind(ImageView view,String id){view.setImageDrawable(new Art(view.getContext(),id));view.setContentDescription(label(id));}

 static final class Art extends Drawable {
  private static final int INK=0xff202536,WHITE=0xfffffaf2,CREAM=0xffffe6c6,PINK=0xffec9ea8;
  private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final int kind;private final int border;private final Shader background;
  private boolean focused,selected;private int alpha=255;private ColorFilter filter;
  Art(Context c,String id){int i=index(id);kind=i<0?0:i;border=Ui.dp(c,2);background=new LinearGradient(0,0,100,100,COLORS[kind],darken(COLORS[kind]),Shader.TileMode.CLAMP);}
  private static int darken(int color){return Color.rgb((int)(Color.red(color)*.7f),(int)(Color.green(color)*.7f),(int)(Color.blue(color)*.7f));}
  private void fill(int color){p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(color);p.setAlpha(Color.alpha(color)*alpha/255);p.setColorFilter(filter);}
  private void oval(Canvas c,float l,float t,float r,float b,int color){fill(color);c.drawOval(l,t,r,b,p);}
  private void circle(Canvas c,float x,float y,float r,int color){fill(color);c.drawCircle(x,y,r,p);}
  private void box(Canvas c,float l,float t,float r,float b,float radius,int color){fill(color);c.drawRoundRect(l,t,r,b,radius,radius,p);}
  private void poly(Canvas c,int color,float...points){fill(color);Path path=new Path();path.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)path.lineTo(points[i],points[i+1]);path.close();c.drawPath(path,p);}
  private void line(Canvas c,float x,float y,float x2,float y2,int color,float width){fill(color);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);c.drawLine(x,y,x2,y2,p);}
  private void eyes(Canvas c,float y){circle(c,39,y,3.5f,INK);circle(c,61,y,3.5f,INK);circle(c,40,y-1,1,WHITE);circle(c,62,y-1,1,WHITE);}
  private void smile(Canvas c,float y){fill(INK);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.5f);p.setStrokeCap(Paint.Cap.ROUND);c.drawArc(43,y,57,y+10,10,160,false,p);p.setStyle(Paint.Style.FILL);}
  @Override public void draw(Canvas c){
   Rect bounds=getBounds();if(bounds.width()<=0||bounds.height()<=0)return;
   c.save();c.translate(bounds.left,bounds.top);c.scale(bounds.width()/100f,bounds.height()/100f);
   Path clip=new Path();clip.addRoundRect(new RectF(0,0,100,100),19,19,Path.Direction.CW);c.clipPath(clip);
   fill(COLORS[kind]);p.setShader(background);c.drawRect(0,0,100,100,p);p.setShader(null);
   circle(c,13,9,37,0x12ffffff);circle(c,94,101,38,0x10000000);
   switch(kind){
    case 0: // Cat
     poly(c,0xffefc06b,23,43,24,14,45,32);poly(c,0xffefc06b,55,32,76,14,77,43);poly(c,PINK,28,32,28,22,37,32);poly(c,PINK,63,32,72,22,72,32);
     box(c,22,29,78,82,23,0xffefc06b);oval(c,34,56,66,78,CREAM);eyes(c,49);poly(c,INK,46,60,54,60,50,65);smile(c,65);line(c,18,58,33,61,INK,1.8f);line(c,18,66,33,65,INK,1.8f);line(c,67,61,82,58,INK,1.8f);line(c,67,65,82,66,INK,1.8f);break;
    case 1: // Fox
     poly(c,0xffe99455,20,43,17,12,45,32);poly(c,0xffe99455,55,32,83,12,80,43);poly(c,INK,23,29,22,20,32,29);poly(c,INK,68,29,78,20,77,29);
     poly(c,0xffe99455,22,29,78,29,84,54,50,85,16,54);poly(c,CREAM,18,52,41,55,50,77,59,55,82,52,50,85);eyes(c,46);circle(c,50,67,4,INK);break;
    case 2: // Panda
     circle(c,28,28,13,INK);circle(c,72,28,13,INK);circle(c,50,53,31,WHITE);oval(c,28,39,47,60,INK);oval(c,53,39,72,60,INK);circle(c,39,49,3.5f,WHITE);circle(c,61,49,3.5f,WHITE);oval(c,45,61,55,68,INK);smile(c,68);break;
    case 3: // Robot
     line(c,50,17,50,30,WHITE,3);circle(c,50,15,5,0xfff3bd69);box(c,14,43,27,62,5,0xff7cb8c9);box(c,73,43,86,62,5,0xff7cb8c9);box(c,22,28,78,80,14,0xffb9dce2);box(c,29,39,71,62,9,INK);circle(c,39,50,4,0xff8de9d4);circle(c,61,50,4,0xff8de9d4);box(c,40,68,60,73,2,INK);line(c,46,68,46,73,WHITE,1.5f);line(c,54,68,54,73,WHITE,1.5f);break;
    case 4: // Astronaut
     box(c,31,70,69,96,12,WHITE);circle(c,50,47,34,0xffdbe7ed);box(c,14,39,23,61,4,WHITE);box(c,77,39,86,61,4,WHITE);box(c,24,26,76,70,21,INK);circle(c,50,49,19,0xffe8b581);eyes(c,48);smile(c,54);line(c,31,34,44,30,0xff8a9eaf,3);box(c,54,79,63,86,2,0xffe66562);break;
    case 5: // Owl
     poly(c,0xffb2a0d8,20,42,19,16,43,31);poly(c,0xffb2a0d8,57,31,81,16,80,42);circle(c,50,53,31,0xffb2a0d8);oval(c,31,58,69,87,CREAM);circle(c,36,46,14,WHITE);circle(c,64,46,14,WHITE);circle(c,37,46,5,INK);circle(c,63,46,5,INK);poly(c,0xffedb764,44,60,56,60,50,70);break;
    case 6: // Dog
     oval(c,16,25,38,76,0xff755140);oval(c,62,25,84,76,0xff755140);box(c,26,26,74,83,21,CREAM);oval(c,29,38,47,59,0xffa57855);eyes(c,49);oval(c,44,62,56,70,INK);box(c,46,72,54,85,4,PINK);line(c,50,73,50,78,0xffbd6979,1);break;
    case 7: // Ninja
     box(c,29,70,71,96,17,INK);circle(c,50,48,31,INK);poly(c,0xffe385a0,76,41,92,36,86,57);box(c,20,38,80,57,5,0xffe385a0);circle(c,39,47,4,WHITE);circle(c,61,47,4,WHITE);line(c,33,41,43,43,INK,2);line(c,57,43,67,41,INK,2);break;
    case 8: // Alien
     oval(c,24,17,76,84,0xffa7dcb0);c.save();c.rotate(-22,38,48);oval(c,31,37,45,59,INK);c.restore();c.save();c.rotate(22,62,48);oval(c,55,37,69,59,INK);c.restore();smile(c,64);break;
    case 9: // Bear
     circle(c,28,28,13,0xffc6946e);circle(c,72,28,13,0xffc6946e);circle(c,28,28,7,0xff8c604a);circle(c,72,28,7,0xff8c604a);circle(c,50,53,31,0xffc6946e);oval(c,34,55,66,79,CREAM);eyes(c,47);oval(c,44,59,56,67,INK);smile(c,68);break;
    case 10: // Penguin
     oval(c,15,46,33,79,INK);oval(c,67,46,85,79,INK);oval(c,24,15,76,92,INK);oval(c,30,41,70,87,WHITE);circle(c,40,39,9,WHITE);circle(c,60,39,9,WHITE);circle(c,40,39,3,INK);circle(c,60,39,3,INK);poly(c,0xffedb764,44,49,56,49,50,59);oval(c,29,84,48,94,0xffedb764);oval(c,52,84,71,94,0xffedb764);break;
    default: // Rabbit
     oval(c,27,8,43,52,CREAM);oval(c,57,8,73,52,CREAM);oval(c,32,14,38,40,PINK);oval(c,62,14,68,40,PINK);circle(c,50,60,28,CREAM);eyes(c,54);poly(c,PINK,45,66,55,66,50,72);box(c,46,74,54,82,2,WHITE);line(c,50,75,50,81,0xffd4c9ba,1);break;
   }
   if(selected){circle(c,82,82,11,WHITE);line(c,77,82,81,86,INK,2.5f);line(c,81,86,88,78,INK,2.5f);}
   if(focused||selected){float stroke=border*100f/bounds.width(),inset=stroke/2f+1;fill(focused?Ui.RED:WHITE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(stroke);c.drawRoundRect(inset,inset,100-inset,100-inset,17,17,p);p.setStyle(Paint.Style.FILL);}
   c.restore();
  }
  @Override public boolean isStateful(){return true;}
  @Override protected boolean onStateChange(int[] states){boolean f=false,s=false;for(int state:states){if(state==android.R.attr.state_focused||state==android.R.attr.state_pressed)f=true;if(state==android.R.attr.state_selected)s=true;}if(f==focused&&s==selected)return false;focused=f;selected=s;invalidateSelf();return true;}
  @Override public void setAlpha(int alpha){this.alpha=alpha;invalidateSelf();}@Override public void setColorFilter(ColorFilter filter){this.filter=filter;invalidateSelf();}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
}
