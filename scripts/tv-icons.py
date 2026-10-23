#!/usr/bin/env python3
"""TV launcher icon and Android TV banner in the phone icon's style (web/public/icon.svg), rendered by headless Chrome."""
import re,subprocess,sys,tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent
CHROME='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
svg=(ROOT/'web/public/icon.svg').read_text()
defs=re.search(r'<defs>.*</defs>',svg,re.S).group(0)
mark=re.search(r'<g transform="translate\(72\.4 169\.6\) scale\(3\.6\)"[^>]*>(.*)</g></svg>',svg,re.S).group(1)
def page(w,h,scale):
 x,y,k=(w-102*scale)/2,(h-48*scale)/2,scale/3.6
 return ('<!doctype html><html><body style="margin:0"><svg xmlns="http://www.w3.org/2000/svg" width="%s" height="%s" viewBox="0 0 %s %s">'%(w,h,w,h)+defs+
  '<rect width="%s" height="%s" fill="url(#bg)"/><rect width="%s" height="%s" fill="url(#sheen)"/>'%(w,h,w,h)+
  '<ellipse cx="%s" cy="%s" rx="%s" ry="%s" fill="#e50914" opacity=".09" filter="url(#glow)"/>'%(w/2,h/2+6*k,170*k,80*k)+
  '<g transform="translate(%s %s) scale(%s)" fill="none" stroke-width="8.4" stroke-linecap="round" stroke-linejoin="round">%s</g></svg></body></html>'%(x,y,scale,mark))
# Adaptive icon: 108dp at 4x; the mark stays inside the 66dp safe zone. Legacy icon: 48dp at 4x. Banner: 320x180dp at 2x.
for name,w,h,share in [('kik-icon.png',432,432,.58),('kik-icon-legacy.png',192,192,.7),('kik-banner.png',640,360,.5)]:
 with tempfile.NamedTemporaryFile('w',suffix='.html',delete=False) as f:f.write(page(w,h,w*share/102))
 subprocess.run([CHROME,'--headless=new','--disable-gpu','--hide-scrollbars','--window-size=%d,%d'%(w,h),'--screenshot='+str(ROOT/'localization'/name),'file://'+f.name],check=True,capture_output=True)
 Path(f.name).unlink()
 print(name)
