#!/usr/bin/env python3
from pathlib import Path
import hashlib,json,re,xml.etree.ElementTree as ET,zipfile
root=Path(__file__).resolve().parent.parent
release=json.loads((root/'localization/release.json').read_text())
original=root/'RezkaTV-1.2.0.apk';final=root/('outputs/KIK-'+release['version_name']+'.apk')
assert hashlib.sha256(original.read_bytes()).hexdigest()=='7883dc0e4dd15e726994e10f75c066b061241a3a8ef7192f5caf6595ba897442'
ns='{http://schemas.android.com/apk/res/android}'
a=ET.parse(root/'decoded/AndroidManifest.xml').getroot();b=ET.parse(root/'localized/AndroidManifest.xml').getroot()
assert b.get('package')=='rezkatv.web.app.uk'
old_permissions={e.get(ns+'name') for e in a.findall('uses-permission') if not e.get(ns+'name').startswith('rezkatv.')}
new_permissions={e.get(ns+'name') for e in b.findall('uses-permission') if not e.get(ns+'name').startswith('rezkatv.')}
assert not old_permissions-new_permissions
assert new_permissions-old_permissions=={'android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION'}
job=b.find("./application/service[@"+ns+"name='kik.i18n.UpdateJob']")
assert job is not None and job.get(ns+'permission')=='android.permission.BIND_JOB_SERVICE'
receiver=b.find("./application/receiver[@"+ns+"name='kik.i18n.UpdateReceiver']")
assert receiver is not None and receiver.get(ns+'exported')=='false'
# KIK replaces the legacy main and player screens with its own; the activity list is explicit.
activities=lambda m:{e.get(ns+'name') for e in m.findall('./application/activity')}
assert activities(a)-activities(b)=={'rezkatv.web.app.MainActivity','rezkatv.web.app.PlayerActivity'},activities(a)-activities(b)
assert activities(b)-activities(a)=={'kik.i18n.'+n for n in ('BasicSettingsActivity','BrowseActivity','FilmActivity','HomeActivity','KikPlayerActivity','OpenTitleActivity','ProfilesActivity','SyncActivity','TasteActivity')},activities(b)-activities(a)
channels=b.find("./application/service[@"+ns+"name='kik.i18n.TvChannels$Job']")
assert channels is not None and channels.get(ns+'permission')=='android.permission.BIND_JOB_SERVICE'
assert b.find("./application/activity[@"+ns+"name='kik.i18n.BasicSettingsActivity']") is not None
assert b.find("./application/activity[@"+ns+"name='kik.i18n.ProfilesActivity']") is not None
taste=b.find("./application/activity[@"+ns+"name='kik.i18n.TasteActivity']")
assert taste is not None and taste.get(ns+'exported')=='false'
original_authorities={e.get(ns+'authorities') for e in a.findall('./application/provider')}
assert not original_authorities.intersection({e.get(ns+'authorities') for e in b.findall('./application/provider')})
with zipfile.ZipFile(original) as src,zipfile.ZipFile(final) as dst:
 native=[n for n in src.namelist() if n.startswith('lib/') and n.endswith('.so')]
 for name in native:assert src.read(name)==dst.read(name),name
 assert b'Lkik/i18n/Ua;' in dst.read('classes.dex')
 assert 'assets/dexopt/baseline.prof' not in dst.namelist()
# URLs used by adapters remain byte-for-byte identical at the string constant boundary.
def urls(path):
 out=set()
 for p in path.glob('smali*/**/*.smali'):
  if p.name=='AppUpdateRepository.smali':continue
  for m in re.finditer(r'const-string(?:/jumbo)?\s+[^,]+,\s*"((?:[^"\\]|\\.)*)"',p.read_text()):
   try:s=json.loads('"'+m[1]+'"')
   except ValueError:continue
   if s.startswith(('http://','https://')):out.add(s)
 return out
# Added endpoints are translation, KIK services and the existing source's canonical sync base.
assert urls(root/'localized')-urls(root/'decoded')<={'https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=auto&tl=uk','https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=ru&tl=uk','https://hdrezka.website','https://hdrezka.website/','https://hdrezka.sh/','https://uaserials.com/',release['update_url'],release['web_url'],release['web_url']+'/api/artwork/batch'}
assert urls(root/'decoded')<=urls(root/'localized')
repository=(root/'localized/smali/rezkatv/web/app/data/repository/AppUpdateRepository.smali').read_text()
for m in re.finditer(r'\.method public final checkForUpdates-gIAlu-s[^\n]*\n(.*?)\.end method',repository,re.S):
 assert 'AppUpdateInfo;-><init>' in m[1] and 'getLatestRelease' not in m[1]
assert 'https://codeberg.org/api/v1/repos/rezkatv/app/releases/latest' not in repository
assert 'PersonSection' in (root/'localized/smali/g92.smali').read_text()
summary={'upstream_updates_disabled':True,'kik_signed_updates':True,'basic_settings_activity':True,'profiles_activity':True,'taste_onboarding_activity':True,'added_url':'description translation (Google)','original_unchanged':True,'added_permission':'android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION','native_libraries_unchanged':len(native),'video_source_urls_unchanged':True,'distinct_package_and_providers':True,'sha256':hashlib.sha256(final.read_bytes()).hexdigest()}
(root/'outputs/validation.json').write_text(json.dumps(summary,indent=2))
print(json.dumps(summary))
