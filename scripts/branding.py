"""Single vector wordmark for native screens, launcher icon, TV tile and splash."""
from pathlib import Path
import json,re
ROOT=Path(__file__).resolve().parent.parent
D=ROOT/'localized';brand=json.loads((ROOT/'localization/brand.json').read_text());vw,vh=brand['viewport']
brand['paths']=['M'+','.join(map(str,p[:2]))+''.join('L'+','.join(map(str,p[i:i+2])) for i in range(2,len(p),2)) for p in brand['segments']]
(ROOT/'outputs').mkdir(exist_ok=True)
def vector(width,height,mark_width,bg=False):
 scale=mark_width/vw;x=(width-mark_width)/2;y=(height-vh*scale)/2
 paths=''.join(f'<path android:fillColor="#00000000" android:strokeColor="{brand["color"]}" android:strokeWidth="{brand["stroke"]}" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="{p}"/>' for p in brand['paths'])
 back=f'<path android:fillColor="#0D0D0F" android:pathData="M0,0h{width}v{height}H0Z"/>' if bg else ''
 return f'<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="{width}dp" android:height="{height}dp" android:viewportWidth="{width}" android:viewportHeight="{height}">{back}<group android:translateX="{x}" android:translateY="{y}" android:scaleX="{scale}" android:scaleY="{scale}">{paths}</group></vector>\n'
for filename,w,h,m,bg in [('kik_splash',320,180,150,False)]:
 (D/f'res/drawable/{filename}.xml').write_text(vector(w,h,m,bg))
# Launcher icon and TV banner: the phone icon's style, rendered by scripts/tv-icons.py into localization/.
import shutil
for folder,source,target in [('drawable-xxxhdpi','kik-icon.png','kik_icon.png'),('drawable-xhdpi','kik-banner.png','kik_banner.png')]:
 (D/'res'/folder).mkdir(parents=True,exist_ok=True);shutil.copy(ROOT/'localization'/source,D/'res'/folder/target)
(D/'res/drawable/kik_icon_fg.xml').write_text('<shape xmlns:android="http://schemas.android.com/apk/res/android"><solid android:color="#00000000"/></shape>')
for name in ['ic_launcher','ic_launcher_round']:
 f=D/f'res/mipmap-anydpi-v26/{name}.xml'
 if f.exists():f.write_text('<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@drawable/kik_icon"/><foreground android:drawable="@drawable/kik_icon_fg"/></adaptive-icon>')
 for p in list((D/'res').glob(f'mipmap-*dpi/{name}.png'))+list((D/'res').glob(f'mipmap-*dpi/{name}.webp')):p.unlink();shutil.copy(ROOT/'localization/kik-icon-legacy.png',p.with_suffix('.png'))
p=D/'AndroidManifest.xml';p.write_text(p.read_text().replace('android:banner="@mipmap/ic_banner"','android:banner="@drawable/kik_banner"'))
for p in (D/'res').glob('values*/strings.xml'):p.write_text(re.sub(r'(<string name="app_name">).*?(</string>)',r'\1KIK\2',p.read_text()))
(D/'res/drawable/splash_background.xml').write_text('<layer-list xmlns:android="http://schemas.android.com/apk/res/android"><item><color android:color="#0D0D0F"/></item><item android:gravity="center" android:width="320dp" android:height="180dp" android:drawable="@drawable/kik_splash"/></layer-list>')
commands=[]
for seg in brand['segments']:
 commands.append(f'path.moveTo({seg[0]}f,{seg[1]}f);')
 for i in range(2,len(seg),2):commands.append(f'path.lineTo({seg[i]}f,{seg[i+1]}f);')
java='''package kik.i18n;
import android.content.Context;
import android.graphics.*;
import android.view.View;
/** Generated from localization/brand.json; all visible KIK marks use the same geometry. */
public final class Brand {
 public static View logo(Context c,int width,int height){View v=new Mark(c);v.setLayoutParams(new android.widget.LinearLayout.LayoutParams(Ui.dp(c,width),Ui.dp(c,height)));v.setContentDescription("KIK");return v;}
 private static final class Mark extends View {
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path path=new Path();
  Mark(Context c){super(c);paint.setColor(Ui.RED);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(STROKE_WIDTH);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);COMMANDS}
  @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float scale=Math.min(getWidth()/102f,getHeight()/48f);canvas.save();canvas.translate((getWidth()-102*scale)/2,(getHeight()-48*scale)/2);canvas.scale(scale,scale);canvas.drawPath(path,paint);canvas.restore();}
 }
}
'''.replace('STROKE_WIDTH',str(brand['stroke'])+'f').replace('COMMANDS',''.join(commands))
(ROOT/'localization/java/kik/i18n/Brand.java').write_text(java)
svg=''.join(f'<path d="{p}"/>' for p in brand['paths'])
(ROOT/'outputs/KIK-logo.svg').write_text(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {vw} {vh}"><g fill="none" stroke="{brand["color"]}" stroke-width="{brand["stroke"]}" stroke-linecap="round" stroke-linejoin="round">{svg}</g></svg>')
