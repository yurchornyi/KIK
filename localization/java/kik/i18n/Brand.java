package kik.i18n;
import android.content.Context;
import android.graphics.*;
import android.view.View;
/** Generated from localization/brand.json; all visible KIK marks use the same geometry. */
public final class Brand {
 public static View logo(Context c,int width,int height){View v=new Mark(c);v.setLayoutParams(new android.widget.LinearLayout.LayoutParams(Ui.dp(c,width),Ui.dp(c,height)));v.setContentDescription("KIK");return v;}
 private static final class Mark extends View {
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path path=new Path();
  Mark(Context c){super(c);paint.setColor(Ui.RED);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(7.6f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);path.moveTo(8f,8f);path.lineTo(8f,40f);path.moveTo(29f,8f);path.lineTo(8f,24f);path.lineTo(29f,40f);path.moveTo(51f,8f);path.lineTo(51f,40f);path.moveTo(73f,8f);path.lineTo(73f,40f);path.moveTo(94f,8f);path.lineTo(73f,24f);path.lineTo(94f,40f);}
  @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float scale=Math.min(getWidth()/102f,getHeight()/48f);canvas.save();canvas.translate((getWidth()-102*scale)/2,(getHeight()-48*scale)/2);canvas.scale(scale,scale);canvas.drawPath(path,paint);canvas.restore();}
 }
}
