#!/usr/bin/env python3
"""Phone and browser icons from web/public/icon.svg, rendered by headless Chrome: apple-touch-icon.png (180, what an
iPhone shows on the home screen and in the Dynamic Island), icon-192.png and icon-512.png, and icon-512-maskable.png
with the mark shrunk into the round safe zone (the middle 80 %) that Android crops a maskable icon to."""
import re,subprocess,tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent
PUBLIC=ROOT/'web/public'
CHROME='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
svg=(PUBLIC/'icon.svg').read_text().strip()
place=re.search(r'<g transform="translate\(([\d.]+) ([\d.]+)\) scale\(([\d.]+)\)"',svg)
x,y,k=map(float,place.groups())
def scaled(to):
 """The same icon with the 102 x 48 mark at another scale, kept on the same centre."""
 cx,cy=x+51*k,y+24*k
 return svg.replace(place.group(0),'<g transform="translate(%g %g) scale(%g)"'%(cx-51*to,cy-24*to,to))
for name,size,source in [('apple-touch-icon.png',180,svg),('icon-192.png',192,svg),('icon-512.png',512,svg),('icon-512-maskable.png',512,scaled(3.4))]:
 html='<!doctype html><html><body style="margin:0"><svg width="%d" height="%d"%s</body></html>'%(size,size,source[4:])
 with tempfile.NamedTemporaryFile('w',suffix='.html',delete=False) as f:f.write(html)
 subprocess.run([CHROME,'--headless=new','--disable-gpu','--hide-scrollbars','--force-device-scale-factor=1','--window-size=%d,%d'%(size,size),'--screenshot='+str(PUBLIC/name),'file://'+f.name],check=True,capture_output=True)
 Path(f.name).unlink()
 print(name)
