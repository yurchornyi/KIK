#!/usr/bin/env python3
"""Publish a complete signed release. APKs and private signing material never enter Git."""
import argparse, hashlib, json, os, pathlib, re, subprocess, tempfile

ROOT=pathlib.Path(__file__).resolve().parent.parent
def run(args, **kw):
    return subprocess.check_output([str(x) for x in args], cwd=ROOT, text=True, **kw).strip()
def metadata(apk, config):
    sdk=pathlib.Path(os.environ.get('KIK_ANDROID_SDK','/opt/homebrew/share/android-commandlinetools'))
    bt=sdk/'build-tools/34.0.0'
    env=os.environ.copy();env['JAVA_HOME']=os.environ.get('KIK_JAVA_HOME','/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home')
    certs=run([bt/'apksigner','verify','--print-certs',apk],env=env)
    hashes=re.findall(r'Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]{64})',certs)
    pinned=(ROOT/'localization/signing-certificate.sha256').read_text().strip()
    if hashes != [pinned]:raise SystemExit('APK signing certificate differs from the KIK release key')
    badging=run([bt/'aapt','dump','badging',apk])
    fields=dict(re.findall(r"(\w+)='([^']*)'",badging.splitlines()[0]))
    for k,v in [('name',config['application_id']),('versionCode',str(config['version_code'])),('versionName',config['version_name'])]:
        if fields.get(k)!=v:raise SystemExit('APK/config mismatch: '+k)
    minimum=int(re.search(r"^sdkVersion:'(\d+)'",badging,re.M)[1])
    if minimum!=config['min_sdk']:raise SystemExit('Minimum SDK mismatch')
    if not 0<apk.stat().st_size<=96*1024*1024:raise SystemExit('Unexpected APK size')
    tag='v'+config['version_name']
    return dict(schema=1,application_id=config['application_id'],version_code=config['version_code'],version_name=config['version_name'],min_sdk=minimum,
                apk_name=apk.name,apk_url=f"https://github.com/{config['repository']}/releases/download/{tag}/{apk.name}",size_bytes=apk.stat().st_size,
                sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),signer_sha256=pinned)
def main():
    p=argparse.ArgumentParser();p.add_argument('--publish',action='store_true');p.add_argument('--notes-file',type=pathlib.Path);args=p.parse_args()
    config=json.loads((ROOT/'localization/release.json').read_text());apk=ROOT/f"outputs/KIK-{config['version_name']}.apk"
    data=metadata(apk,config);dest=ROOT/'outputs/release';dest.mkdir(parents=True,exist_ok=True)
    manifest=dest/'update.json';manifest.write_text(json.dumps(data,indent=2)+'\n')
    print(json.dumps(data,indent=2))
    if not args.publish:return
    if args.notes_file is None or not args.notes_file.is_file():raise SystemExit('--notes-file is required for publication')
    if run(['git','status','--porcelain']):raise SystemExit('Commit source changes before publishing')
    repo=config['repository'];tag='v'+config['version_name']
    remote=run(['gh','repo','view',repo,'--json','nameWithOwner,visibility']);info=json.loads(remote)
    if info['nameWithOwner'].lower()!=repo.lower() or info['visibility']!='PUBLIC':raise SystemExit('Anonymous updates require the configured public repository')
    # Create a draft first: latest never points to a release with missing assets.
    existing=subprocess.run(['gh','release','view',tag,'--repo',repo],cwd=ROOT,capture_output=True)
    if existing.returncode==0:raise SystemExit('Release already exists; choose a new version instead of replacing assets')
    commit=run(['git','rev-parse','HEAD'])
    run(['gh','release','create',tag,'--repo',repo,'--target',commit,'--draft','--title','KIK '+config['version_name'],'--notes-file',args.notes_file,apk,manifest])
    print(run(['gh','release','edit',tag,'--repo',repo,'--draft=false','--latest']))
    print(f'https://github.com/{repo}/releases/tag/{tag}')
if __name__=='__main__':main()
