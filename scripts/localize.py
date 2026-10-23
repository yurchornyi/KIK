#!/usr/bin/env python3
"""Reproducible localization overlay. Keep decoded/ and the original APK unchanged."""
from pathlib import Path
import json,re,shutil,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parent.parent

def table(name):
 out={}
 for line in (ROOT/'localization'/name).read_text().splitlines():
  if line and not line.startswith('#'):
   a,b=line.split('\t',1)
   if a in out and out[a]!=b: raise ValueError('Conflicting key '+a)
   out[a]=b
 return out
exact=table('uk.tsv'); prefixes=table('prefixes.tsv')
exact.update({'\u0417\u0435\u0440\u043a\u0430\u043b\u0430':'\u0414\u0437\u0435\u0440\u043a\u0430\u043b\u0430','\u041f\u0440\u043e\u0432\u0435\u0440\u0438\u0442\u044c \u0432\u0441\u0435':'\u041f\u0435\u0440\u0435\u0432\u0456\u0440\u0438\u0442\u0438 \u0432\u0441\u0456','\u0414\u043e\u0431\u0430\u0432\u0438\u0442\u044c \u0437\u0435\u0440\u043a\u0430\u043b\u043e...':'\u0414\u043e\u0434\u0430\u0442\u0438 \u0434\u0437\u0435\u0440\u043a\u0430\u043b\u043e...','\u0412\u0445\u043e\u0434 \u0432 \u0430\u043a\u043a\u0430\u0443\u043d\u0442':'\u0412\u0445\u0456\u0434 \u0432 \u043e\u0431\u043b\u0456\u043a\u043e\u0432\u0438\u0439 \u0437\u0430\u043f\u0438\u0441','Email \u0438\u043b\u0438 \u043b\u043e\u0433\u0438\u043d':'Email \u0430\u0431\u043e \u043b\u043e\u0433\u0456\u043d','\u2705 \u0412\u044b \u0443\u0441\u043f\u0435\u0448\u043d\u043e \u0432\u043e\u0448\u043b\u0438 \u0432 \u0430\u043a\u043a\u0430\u0443\u043d\u0442.':'\u2705 \u0412\u0438 \u0443\u0441\u043f\u0456\u0448\u043d\u043e \u0432\u0432\u0456\u0439\u0448\u043b\u0438 \u0432 \u043e\u0431\u043b\u0456\u043a\u043e\u0432\u0438\u0439 \u0437\u0430\u043f\u0438\u0441.','\u0423\u0434\u0430\u043b\u0435\u043d\u043d\u043e\u0435 \u0443\u043f\u0440\u0430\u0432\u043b\u0435\u043d\u0438\u0435':'\u0412\u0456\u0434\u0434\u0430\u043b\u0435\u043d\u0435 \u043a\u0435\u0440\u0443\u0432\u0430\u043d\u043d\u044f','\u0410\u043a\u0442\u0438\u0432\u043d\u043e\u0435 \u0437\u0435\u0440\u043a\u0430\u043b\u043e':'\u0410\u043a\u0442\u0438\u0432\u043d\u0435 \u0434\u0437\u0435\u0440\u043a\u0430\u043b\u043e','\u0410\u0432\u0442\u043e\u0440\u0438\u0437\u043e\u0432\u0430\u043d':'\u0410\u0432\u0442\u043e\u0440\u0438\u0437\u043e\u0432\u0430\u043d\u043e','\u041d\u0435 \u0432 \u0441\u0435\u0442\u0438':'\u041f\u043e\u0437\u0430 \u043c\u0435\u0440\u0435\u0436\u0435\u044e'})
suffixes={'" \u0438\u0437 \u0438\u0441\u0442\u043e\u0440\u0438\u0438 \u043f\u043e\u0438\u0441\u043a\u0430?':'\xbb \u0437 \u0456\u0441\u0442\u043e\u0440\u0456\u0457 \u043f\u043e\u0448\u0443\u043a\u0443?','" \u0441\u043e \u0441\u0442\u0430\u0440\u0442\u043e\u0432\u043e\u0439 \u0441\u0442\u0440\u0430\u043d\u0438\u0446\u044b?':'\xbb \u0437\u0456 \u0441\u0442\u0430\u0440\u0442\u043e\u0432\u043e\u0457 \u0441\u0442\u043e\u0440\u0456\u043d\u043a\u0438?','" \u0441\u0442\u0430\u0440\u0442\u043e\u0432\u043e\u0439 \u0441\u0442\u0440\u0430\u043d\u0438\u0446\u0435\u0439?':'\xbb \u0441\u0442\u0430\u0440\u0442\u043e\u0432\u043e\u044e \u0441\u0442\u043e\u0440\u0456\u043d\u043a\u043e\u044e?','"? \u042d\u0442\u043e \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u0435 \u043d\u0435\u043b\u044c\u0437\u044f \u043e\u0442\u043c\u0435\u043d\u0438\u0442\u044c.':'\xbb? \u0426\u044e \u0434\u0456\u044e \u043d\u0435\u043c\u043e\u0436\u043b\u0438\u0432\u043e \u0441\u043a\u0430\u0441\u0443\u0432\u0430\u0442\u0438.',' \u0431\u0443\u0434\u0435\u0442 \u043e\u0442\u043f\u0440\u0430\u0432\u043b\u0435\u043d\u043e \u043f\u0438\u0441\u044c\u043c\u043e \u0441 \u043f\u0440\u043e\u0432\u0435\u0440\u043e\u0447\u043d\u044b\u043c \u043a\u043e\u0434\u043e\u043c.':' \u0431\u0443\u0434\u0435 \u043d\u0430\u0434\u0456\u0441\u043b\u0430\u043d\u043e \u043b\u0438\u0441\u0442 \u0456\u0437 \u043a\u043e\u0434\u043e\u043c \u043f\u0456\u0434\u0442\u0432\u0435\u0440\u0434\u0436\u0435\u043d\u043d\u044f.',' \u043e\u0442\u043f\u0440\u0430\u0432\u043b\u0435\u043d\u043e \u043f\u0438\u0441\u044c\u043c\u043e \u0441 \u043f\u0440\u043e\u0432\u0435\u0440\u043e\u0447\u043d\u044b\u043c \u043a\u043e\u0434\u043e\u043c.':' \u043d\u0430\u0434\u0456\u0441\u043b\u0430\u043d\u043e \u043b\u0438\u0441\u0442 \u0456\u0437 \u043a\u043e\u0434\u043e\u043c \u043f\u0456\u0434\u0442\u0432\u0435\u0440\u0434\u0436\u0435\u043d\u043d\u044f.',' - \u0434\u0430\u0442\u044b \u0432\u044b\u0445\u043e\u0434\u0430 \u0441\u0435\u0440\u0438\u0439 ':' \u2014 \u0434\u0430\u0442\u0438 \u0432\u0438\u0445\u043e\u0434\u0443 \u0441\u0435\u0440\u0456\u0439 ',' \u0437\u0430\u0433\u0440\u0443\u0437\u043e\u043a':' \u0437\u0430\u0432\u0430\u043d\u0442\u0430\u0436\u0435\u043d\u044c',' \u043c\u0438\u043d\u0443\u0442':' \u0445\u0432\u0438\u043b\u0438\u043d',' \u043c\u0438\u043d\u0443\u0442\u044b':' \u0445\u0432\u0438\u043b\u0438\u043d\u0438',' \u0441\u0435\u0440\u0438\u0439':' \u0441\u0435\u0440\u0456\u0439',' \u0441\u0435\u0440\u0438\u044f':' \u0441\u0435\u0440\u0456\u044f',' \u0444\u0430\u0439\u043b\u043e\u0432':' \u0444\u0430\u0439\u043b\u0456\u0432',' \u0440\u0430\u0437\u0432\u0435\u0440\u043d\u0443\u0442\u044c':' \u0440\u043e\u0437\u0433\u043e\u0440\u043d\u0443\u0442\u0438',' \u044d\u043f.)...':' \u0435\u043f.)...',' \u043c\u0438\u043d.':' \u0445\u0432.'}
# Java escaping is intentionally ASCII, including surrogate pairs for supplementary glyphs.
q=lambda s:json.dumps(s,ensure_ascii=True)
java_dir=ROOT/'localization/java/kik/i18n';java_dir.mkdir(parents=True,exist_ok=True)
release=json.loads((ROOT/'localization/release.json').read_text())
APP=release['application_id']
assert re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+',release['repository'])
(java_dir/'UpdateConfig.java').write_text('package kik.i18n;\npublic final class UpdateConfig {\n public static final String REPOSITORY='+json.dumps(release['repository'])+';\n public static final String MANIFEST_URL='+json.dumps(release['update_url'])+';\n public static final String WEB_ORIGIN='+json.dumps(release.get('web_url','https://kiktv.pages.dev'))+';\n private UpdateConfig(){}\n}\n')

entries='\n'.join('    EXACT.put(%s, %s);'%(q(a),q(b)) for a,b in exact.items())
pair=lambda d:'new String[][] {'+','.join('{%s,%s}'%(q(a),q(b)) for a,b in sorted(d.items(),key=lambda v:-len(v[0])))+'}'
(java_dir/'Ua.java').write_text('''package kik.i18n;
import java.util.*;
/** Translates presentation only: never parsers, source IDs, URLs, query values or stored data. */
public final class Ua {
  private static final Map<String,String> EXACT = new HashMap<String,String>();
  static {
'''+entries+'''
  }
  private static final String[][] PREFIX = '''+pair(prefixes)+''';
  private static final String[][] SUFFIX = '''+pair(suffixes)+''';
  private static String tail(String s) {
    for (String[] p : SUFFIX) if (s.endsWith(p[0])) return s.substring(0,s.length()-p[0].length())+p[1];
    return s;
  }
  public static String t(String s) {
    if (s == null || s.length()==0) return s;
    String direct=EXACT.get(s);
    if(direct!=null) return direct;
    String trimmed=s.trim(); direct=EXACT.get(trimmed);
    if(direct!=null) {int start=s.indexOf(trimmed);return s.substring(0,start)+direct+s.substring(start+trimmed.length());}
    for(String[] p:PREFIX) if(s.startsWith(p[0])) return tail(p[1]+s.substring(p[0].length()));
    String result=tail(s);
    result=result.replace("(\u0423\u043a\u0440\u0430\u0438\u043d\u0441\u043a\u0438\u0439)","(\u0443\u043a\u0440\u0430\u0457\u043d\u0441\u044c\u043a\u0430)").replace("(\u0420\u0443\u0441\u0441\u043a\u0438\u0439)","(\u0440\u043e\u0441\u0456\u0439\u0441\u044c\u043a\u0430)").replace("(+\u0441\u0443\u0431\u0442\u0438\u0442\u0440\u044b)","(+\u0441\u0443\u0431\u0442\u0438\u0442\u0440\u0438)");
    result=result.replaceAll("^(\\\\d{4}) \u0433\u043e\u0434\\\\b","$1 \u0440\u0456\u043a");
    // Poster genre/country chips may carry a flag before the label.
    int space=result.indexOf(' ');
    if(space>0 && space<8) {String label=EXACT.get(result.substring(space+1));if(label!=null) result=result.substring(0,space+1)+label;}
    // Catalog text from the site (titles, voice names): machine translation, display only.
    if(result.equals(s)){String mt=TitleTranslate.lookup(s);if(mt!=null)return mt;}
    return result;
  }
  public static CharSequence c(CharSequence s) {return s instanceof String?t((String)s):s;}
}
''')
(java_dir/'LocaleBridge.java').write_text('''package kik.i18n;
import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;
import java.util.Locale;
public final class LocaleBridge {
 @SuppressWarnings("deprecation") public static void init(Context context) {
  Locale locale = new Locale("uk", "UA");
  Locale.setDefault(locale);
  android.content.res.Resources resources=context.getResources();
  Configuration config=new Configuration(resources.getConfiguration());
  config.setLocales(new LocaleList(locale));
  resources.updateConfiguration(config, resources.getDisplayMetrics());
 }
}
''')
# CI compiles the helpers without the private decoded APK.
if '--java-only' in __import__('sys').argv: raise SystemExit
dst=ROOT/'localized'
if dst.exists(): shutil.rmtree(dst)
shutil.copytree(ROOT/'decoded',dst)
# Distinct identity allows the signed localization to coexist with the untouched original.
manifest=dst/'AndroidManifest.xml'
s=manifest.read_text().replace('package="rezkatv.web.app"','package="'+APP+'"')
for suffix in ['.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION','.androidx-startup','.search','.fileprovider','.firebaseinitprovider','.main','.player']:
 s=s.replace('"rezkatv.web.app'+suffix+'"','"'+APP+suffix+'"')
manifest.write_text(s)
for p in dst.glob('res/xml/*.xml'):
 s=p.read_text();new=s.replace('rezkatv.web.app.search',APP+'.search')
 if s!=new:p.write_text(new)
# Translate only literal markup strings used by the local remote-control screen.
html_count=0
for p in dst.glob('smali*/**/*.smali'):
 s=p.read_text();old=s
 def html(m):
  global html_count
  try:v=json.loads('"'+m[2]+'"')
  except ValueError:return m[0]
  if not re.search(r'<(?:html|section|span|div|/div|!DOCTYPE)',v,re.I):return m[0]
  if not re.search('[\u0410-\u042f\u0430-\u044f\u0401\u0451]',v):return m[0]
  for a,b in sorted(exact.items(),key=lambda kv:-len(kv[0])):v=v.replace(a,b)
  v=v.replace('lang="ru"','lang="uk"')
  html_count+=1
  return m[1]+q(v)
 s=re.sub(r'(const-string(?:/jumbo)?\s+[^,]+,\s*)"((?:[^"\\]|\\.)*)"',html,s)
 # Authorities embedded in code must agree with the clone manifest.
 for suffix in ['.fileprovider','.search','.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION']:
  s=s.replace('"rezkatv.web.app'+suffix+'"','"'+APP+suffix+'"')
 if s!=old:p.write_text(s)
# Hook Compose Text entry points, not TextField values or AnnotatedString constructors.
p=dst/'smali/xv6.smali';s=p.read_text();hooks=[]
def hook_text(m):
 body=m[0]
 if m[1]=='Ljava/lang/String;':
  code='\n    invoke-static/range {p0 .. p0}, Lkik/i18n/Ua;->t(Ljava/lang/String;)Ljava/lang/String;\n    move-result-object p0\n'
 else:
  code='\n    invoke-static/range {p0 .. p0}, Lkik/i18n/Annotated;->t(Lyk;)Lyk;\n    move-result-object p0\n'
 hooks.append(body.splitlines()[0])
 # Must insert after annotations/parameter metadata, before first instruction.
 lines=body.splitlines(True); at=1
 while at<len(lines):
  if lines[at].strip().startswith('.locals'):at+=1;break
  at+=1
 while at<len(lines) and (not lines[at].strip() or lines[at].lstrip().startswith(('.annotation','.param'))):
  if lines[at].lstrip().startswith('.annotation'):
   while at<len(lines) and '.end annotation' not in lines[at]:at+=1
   at+=1
  elif lines[at].lstrip().startswith('.param'):at+=1
  else:at+=1
 lines.insert(at,code)
 return ''.join(lines)
s=re.sub(r'\.method public static final Text[^\n]*\((Ljava/lang/String;|Lyk;)[^\n]*\n.*?\.end method',hook_text,s,flags=re.S)
p.write_text(s)
assert len(hooks)==6,hooks
# Semantic labels and pane titles are display-only too.
p=dst/'smali/rz5.smali';s=p.read_text()
for method in ['d','g']:
 pattern=r'(\.method public static final '+method+r'\(Ltz5;Ljava/lang/String;\)V\n\s*\.locals \d+\n)'
 s,n=re.subn(pattern,r'\1\n    invoke-static/range {p1 .. p1}, Lkik/i18n/Ua;->t(Ljava/lang/String;)Ljava/lang/String;\n    move-result-object p1\n',s)
 assert n==1,(method,n)
p.write_text(s)
# Annotated strings retain their ranges; plain unstyled strings can safely be translated.
(dst/'smali/kik/i18n').mkdir(parents=True,exist_ok=True)
(dst/'smali/kik/i18n/Annotated.smali').write_text('''.class public final Lkik/i18n/Annotated;
.super Ljava/lang/Object;
.method public static t(Lyk;)Lyk;
 .locals 3
 if-eqz p0, :done
 iget-object v0, p0, Lyk;->A:Ljava/util/List;
 if-eqz v0, :plain
 invoke-interface {v0}, Ljava/util/List;->isEmpty()Z
 move-result v0
 if-eqz v0, :done
 :plain
 iget-object v0, p0, Lyk;->B:Ljava/lang/String;
 invoke-static {v0}, Lkik/i18n/Ua;->t(Ljava/lang/String;)Ljava/lang/String;
 move-result-object v1
 invoke-virtual {v0,v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
 move-result v0
 if-nez v0, :done
 new-instance v0, Lyk;
 const/4 v2, 0x0
 invoke-direct {v0,v2,v1}, Lyk;-><init>(Ljava/util/List;Ljava/lang/String;)V
 return-object v0
 :done
 return-object p0
.end method
''')
# Locale for platform and bundled Ukrainian resource strings, on every app/activity creation.
locale_hooks=0
for p in (dst/'smali/rezkatv/web/app').glob('*.smali'):
 if p.stem not in ['HDRezkaApp','MainActivity','PlayerActivity']:continue
 s=p.read_text()
 s,n=re.subn(r'(\.method public final onCreate\([^\n]*\n\s*\.locals \d+\n)',r'\1\n    invoke-static/range {p0 .. p0}, Lkik/i18n/LocaleBridge;->init(Landroid/content/Context;)V\n',s)
 locale_hooks+=n;p.write_text(s)
# Native UI helpers, Toasts and notification text. Input contents are never rewritten.
ui_calls=0
pattern=re.compile(r'(?P<indent>\s*)invoke-(?:virtual|static)(?:/range)? \{(?P<registers>[^}]+)\}, L(?:android/widget/(?:Toast|TextView)|android/view/View|android/app/(?:Notification\$Builder|AlertDialog\$Builder)|[^;]*NotificationCompat\$Builder);->(?:makeText\(Landroid/content/Context;Ljava/lang/CharSequence;I\)Landroid/widget/Toast;|(?:setText|setHint|setContentDescription|setContentTitle|setContentText|setTitle|setMessage)\(Ljava/lang/CharSequence;\)[^\n]*)')
for p in dst.glob('smali*/**/*.smali'):
 s=p.read_text()
 def display_call(m):
  global ui_calls
  regs=m['registers']
  if '..' in regs:
   start=regs.split('..')[0].strip();textreg=start[0]+str(int(start[1:])+1)
  else:
   parts=[x.strip() for x in regs.split(',')]
   if len(parts)<2:return m[0]
   textreg=parts[1]
  if int(textreg[1:])>230:return m[0]
  ui_calls+=1
  return '\n    invoke-static/range {'+textreg+' .. '+textreg+'}, Lkik/i18n/Ua;->c(Ljava/lang/CharSequence;)Ljava/lang/CharSequence;\n    move-result-object '+textreg+'\n'+m[0]
 s,n=pattern.subn(display_call,s)
 if n:p.write_text(s)
# Different launcher label, while retaining the original app branding and artwork.
for p in dst.glob('res/values*/strings.xml'):
 s=p.read_text();s=re.sub(r'(<string name="app_name">).*?(</string>)',r'\1RezkaTV UA\2',s);p.write_text(s)
# Remove compiler profile artifacts because bytecode offsets no longer match the original APK.
shutil.rmtree(dst/'assets/dexopt',ignore_errors=True)
summary={'exact_translations':len(exact),'dynamic_prefixes':len(prefixes),'dynamic_suffixes':len(suffixes),'compose_text_hooks':len(hooks),'locale_hooks':locale_hooks,'native_ui_hooks':ui_calls,'html_literals':html_count,'package':APP}
(ROOT/'localization/patch-summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2))
print(json.dumps(summary,ensure_ascii=False))
