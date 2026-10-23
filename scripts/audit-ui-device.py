from pathlib import Path
import subprocess,json,os

os.chdir(Path(__file__).resolve().parents[1])
serial=os.environ.get('KIK_DEVICE','192.168.1.127:5555')
adb=['adb','-s',serial]
out=Path('outputs/ui-audit');(out/'tv').mkdir(parents=True,exist_ok=True)
def run(args,**kw):return subprocess.run(adb+args,check=True,capture_output=True,text=True,timeout=240,**kw)
assert run(['shell','getprop','ro.product.model']).stdout.strip()=='XK10T'
size=run(['shell','wm','size']).stdout;density=run(['shell','wm','density']).stdout
(out/'tv-display-before.json').write_text(json.dumps({'size':size,'density':density}))
failures=[]
try:
 for label,w,h,d in [('1080p-final',1920,1080,320),('720p',1280,720,240),('large-text',1920,1080,480)]:
  print('Testing',label,flush=True)
  run(['shell','wm','size',f'{w}x{h}']);run(['shell','wm','density',str(d)]);run(['shell','input','keyevent','224'])
  result=run(['shell','am','instrument','-w','-r','-e','label',label,'kik.audit/kik.qa.ResponsiveAudit']);(out/f'tv-{label}.log').write_text(result.stdout)
  run(['pull',f'/sdcard/Android/data/kik.app/files/ui-audit/{label}.json',str(out/'tv'/f'{label}.json')])
  report=json.loads((out/'tv'/f'{label}.json').read_text());print(label,'passed=',report['passed'],'checks=',len(report['checks']),flush=True)
  if not report['passed']:failures.append(label)
  for name in ['settings-initial','home-initial','film-initial']:
   try:run(['pull',f'/sdcard/Android/data/kik.app/files/ui-audit/{label}-{name}.png',str(out/'tv'/f'{label}-{name}.png')])
   except subprocess.CalledProcessError:pass
finally:
 for kind,value in [('size',size),('density',density)]:
  override=next((s.split(':',1)[1].strip() for s in value.splitlines() if s.startswith('Override')), 'reset')
  try:run(['shell','wm',kind,override])
  except Exception as e:print('Display restoration needs retry:',e,flush=True)

if failures:raise SystemExit('UI audit failures: '+', '.join(failures))
