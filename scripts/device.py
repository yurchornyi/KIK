#!/usr/bin/env python3
from pathlib import Path
import subprocess,sys,xml.etree.ElementTree as E
ROOT=Path(__file__).resolve().parent.parent
ADB='/opt/homebrew/bin/adb'
DEVICE='192.168.1.127:5555'
def run(*args):return subprocess.run([ADB,'-s',DEVICE,*args],check=True,capture_output=True,text=True,timeout=25).stdout
command=sys.argv[1]
if command=='dump':
 name=sys.argv[2]
 run('shell','rm','-f','/sdcard/kik-window.xml')
 result=run('shell','uiautomator','dump','/sdcard/kik-window.xml')
 if 'dumped' not in result:raise RuntimeError(result)
 path=ROOT/'work'/name
 run('pull','/sdcard/kik-window.xml',str(path))
 for n in E.parse(path).iter('node'):
  label=n.get('text') or n.get('content-desc')
  if label:print(label,n.get('bounds'))
elif command=='tap':print(run('shell','input','tap',sys.argv[2],sys.argv[3]))
elif command=='key':print(run('shell','input','keyevent',sys.argv[2]))
elif command=='screenshot':
 path=ROOT/'work'/sys.argv[2]
 run('shell','screencap','-p','/sdcard/kik-proof.png');run('pull','/sdcard/kik-proof.png',str(path));print(str(path))
