#!/usr/bin/env python3
"""Behavioral overlay on the reproducible Ukrainian decode; no adapter URLs or codecs changed."""
from pathlib import Path
import re,json
ROOT=Path(__file__).resolve().parent.parent
D=ROOT/'localized';changes=[]
release=json.loads((ROOT/'localization/release.json').read_text())

def replace_method(file,name,body):
 p=D/'smali'/file;s=p.read_text()
 pattern=r'(\.method [^\n]* '+re.escape(name)+r'\([^\n]*\n).*?\.end method'
 s,n=re.subn(pattern,lambda m:m[1]+body+'\n.end method',s,flags=re.S)
 assert n==1,(file,name,n)
 p.write_text(s);changes.append(file+':'+name)

def prefix_method(file,name,code):
 p=D/'smali'/file;s=p.read_text()
 pattern=r'(\.method [^\n]* '+re.escape(name)+r'\([^\n]*\n\s*\.locals \d+\n)'
 s,n=re.subn(pattern,lambda m:m[1]+code+'\n',s)
 assert n==1,(file,name,n)
 p.write_text(s);changes.append(file+':'+name)

replace_method('g92.smali','PersonSection','    .locals 0\n    return-void')
for name,reg in [('ActionIconButton-pjcdKXk','p1'),('AppButton-Cq-TASA','p2')]:
 prefix_method('jc0.smali',name,f'''    invoke-static/range {{{reg} .. {reg}}}, Lkik/i18n/UiRuntime;->hideAction(Ljava/lang/String;)Z
    move-result v0
    if-eqz v0, :kik_action_visible
    return-void
    :kik_action_visible''')
# A single rating, preferring IMDb and falling back only when IMDb is absent.
replace_method('nf5.smali','RatingsCompact','''    .locals 9
    invoke-static/range {p0 .. p0}, Lkik/i18n/PlaybackPolicy;->rating(Ljava/lang/Object;)[Ljava/lang/String;
    move-result-object v0
    if-eqz v0, :kik_no_rating
    const/4 v1, 0x0
    aget-object v1, v0, v1
    const/4 v2, 0x1
    aget-object v2, v0, v2
    const/4 v3, 0x2
    aget-object v3, v0, v3
    move-object v0, v1
    move-object v1, v2
    const/4 v2, 0x0
    const/4 v3, 0x0
    const/4 v4, 0x1
    const/4 v5, 0x0
    move-object/from16 v6, p5
    const/4 v7, 0x0
    const/16 v8, 0x8
    invoke-static/range {v0 .. v8}, Lnf5;->RatingBadge(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lj74;ZZLqx0;II)V
    :kik_no_rating
    return-void''')
# Only the detail presentation gets a two-genre limit. The underlying Film model stays intact.
p=D/'smali/g92.smali';s=p.read_text()
pattern=r'(invoke-virtual[^\n]*Lrezkatv/web/app/data/models/Film;->getGenres\(\)Ljava/util/List;[\s\S]*?move-result-object (\w+))'
s,n=re.subn(pattern,lambda m:m[1]+'\n    invoke-static/range {'+m[2]+' .. '+m[2]+'}, Lkik/i18n/PlaybackPolicy;->genres(Ljava/util/List;)Ljava/util/List;\n    move-result-object '+m[2],s)
assert n>0;p.write_text(s);changes.append('detail_genre_limits:'+str(n))
# Remove collection/list tags from the detail genre strip.
p=D/'smali/g92.smali';s=p.read_text()
for getter in ['getInLists','getInSeries']:
 pattern=r'(invoke-virtual[^\n]*Lrezkatv/web/app/data/models/Film;->'+getter+r'\(\)Ljava/util/List;[\s\S]*?move-result-object (\w+))'
 s,n=re.subn(pattern,lambda m:m[1]+'\n    const/16 '+m[2]+', 0x0',s);assert n==1
p.write_text(s)
# Remove comment count badge along with the comments button, without changing stored metadata.
p=D/'smali/n82.smali';s=p.read_text()
pattern=r'(\.method public static final FilmDetailBody\([^\n]*\n.*?)(\.end method)'
def clean_detail(m):
 body=m[1]
 body,n=re.subn(r'(invoke-virtual[^\n]*getCommentsCount\(\)Ljava/lang/Integer;[\s\S]*?move-result-object (\w+))',lambda v:v[1]+'\n    const/16 '+v[2]+', 0x0',body);assert n==3
 marker='    .line 140\n    invoke-static {v8, v5}, Lx76;->o(Lj74;F)Lj74;'
 assert marker in body
 body=body.replace(marker,'    .line 140\n    const/4 v5, 0x0\n    invoke-static {v8, v5}, Lx76;->o(Lj74;F)Lj74;')
 body=body.replace('sget-object v7, Lzo;->g:Ltx5;', 'sget-object v7, Lzo;->a:Lto;')
 return body+m[2]
s,n=re.subn(pattern,clean_detail,s,flags=re.S);assert n==1;p.write_text(s)
# Respect premium access, then choose by language/studio rather than last arbitrary provider order.
replace_method('by4.smali','h','''    .locals 2
    iget-boolean v0, p0, Lby4;->b:Z
    invoke-static {p1, p2, v0}, Lkik/i18n/PlaybackPolicy;->bestVoice(Ljava/lang/String;Ljava/util/List;Z)Ljava/lang/Object;
    move-result-object v1
    check-cast v1, Lrezkatv/web/app/data/models/Voice;
    return-object v1''')
# Retain the original asynchronous loading/commit logic and callback; replace only chooser content.
p=D/'smali/wy4.smali';s=p.read_text()
manual=re.search(r'(\.method private static final PlaybackSelectionDialogV2\$lambda\$54\$1\$0\([^\n]*\n.*?\.end method)',s,re.S)
assert manual
original=manual[1].replace('PlaybackSelectionDialogV2$lambda$54$1$0(', 'PlaybackSelectionDialogV2$lambda$54$1$0$KikManual(',1)
s+='\n'+original+'\n';p.write_text(s)
replace_method('wy4.smali','PlaybackSelectionDialogV2$lambda$54$1$0','''    .locals 8
    move-object/from16 v0, p15
    if-eqz v0, :kik_initial_play
    invoke-static/range {p0 .. p39}, Lwy4;->PlaybackSelectionDialogV2$lambda$54$1$0$KikManual(Ljava/util/List;Lu24;Lf51;Lby4;ZZLjava/lang/String;Ljava/util/Map;Ljava/util/List;ZLjava/util/List;ILrezkatv/web/app/data/models/Voice;ZLyf2;Lrezkatv/web/app/ui/viewmodel/PlayerViewModel;Lhk2;Ljava/lang/String;Lyf2;Ljava/util/List;Ljava/lang/String;Lyf2;Ljava/util/Map;Ljava/util/List;Li05;Lse6;Lyf2;Landroid/content/Context;ZLdk2;Lck2;Lvb4;Lvb4;Lvb4;Lvb4;Lvb4;Lvb4;Lvb4;Lqx0;I)Loe7;
    move-result-object v0
    return-object v0
    :kik_initial_play
    new-instance v0, Ll50;
    move-object/from16 v1, p29
    move-object/from16 v2, p30
    move-object/from16 v3, p2
    move-object/from16 v4, p31
    move-object/from16 v5, p3
    move-object/from16 v6, p27
    invoke-direct/range {v0 .. v6}, Ll50;-><init>(Ldk2;Lck2;Lf51;Lvb4;Lby4;Landroid/content/Context;)V
    move-object v1, v5
    move-object v2, v4
    move/from16 v3, p28
    invoke-static {v0, v1, v2, v3}, Lkik/i18n/UiRuntime;->autoStart(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Z)V
    invoke-static/range {p3 .. p3}, Lkik/i18n/UiRuntime;->playbackStatus(Ljava/lang/Object;)Ljava/lang/String;
    move-result-object v0
    move-object/from16 v1, p38
    const/4 v2, 0x0
    invoke-static {v0, v1, v2}, Lwy4;->h(Ljava/lang/String;Lqx0;I)V
    sget-object v0, Loe7;->a:Loe7;
    return-object v0''')
# A basic native settings screen replaces the nested advanced settings menu.
replace_method('d46.smali','SettingsScreen','''    .locals 0
    invoke-static/range {p1 .. p1}, Lkik/i18n/UiRuntime;->settings(Ljava/lang/Object;)V
    return-void''')
# Capture the live preference state holder so changes also update already-created view models.
p=D/'smali/rezkatv/web/app/ui/viewmodel/SettingsViewModel.smali';s=p.read_text()
pattern=r'(\.method public constructor <init>\(Landroid/app/Application;\)V\n.*?)(\.end method)'
def capture(m):
 body=m[1];assert body.count('return-void')==1
 return body.replace('    return-void','    invoke-static/range {p0 .. p0}, Lkik/i18n/UiRuntime;->captureSettings(Ljava/lang/Object;)V\n    return-void')+m[2]
s,n=re.subn(pattern,capture,s,flags=re.S);assert n==1;p.write_text(s)
replace_method('rezkatv/web/app/ui/viewmodel/SettingsViewModel.smali','F','    .locals 0\n    return-void')
# No torrent source can surface in the built-in player either.
p=D/'smali/i05.smali';s=p.read_text()
pattern=r'(\.method public final i\(Lrezkatv/web/app/data/models/Film;\)Ljava/util/ArrayList;\n.*?)(\.end method)'
def source_filter(m):
 body=re.sub(r'    return-object (\w+)',lambda v:'    invoke-static/range {'+v[1]+' .. '+v[1]+'}, Lkik/i18n/PlaybackPolicy;->sources(Ljava/util/ArrayList;)Ljava/util/ArrayList;\n    move-result-object '+v[1]+'\n    return-object '+v[1],m[1])
 return body+m[2]
s,n=re.subn(pattern,source_filter,s,flags=re.S);assert n==1;p.write_text(s)
# A changed default takes priority over a remembered quality for the same title.
p=D/'smali/i05.smali';s=p.read_text()
pattern=r'(    invoke-static \{(\w+), \w+\}, Lsf5;->x\(Ljava/lang/String;Ljava/util/List;\)I)'
s,n=re.subn(pattern,lambda m:'    invoke-static {}, Lkik/i18n/UiRuntime;->defaultQuality()Ljava/lang/String;\n    move-result-object '+m[2]+'\n'+m[1],s)
assert n==1;p.write_text(s);changes.append('quality_default_over_remembered_title_quality')
# Disable both update network paths and all publication/check triggers.
repo='rezkatv/web/app/data/repository/AppUpdateRepository.smali'
no_update='''    .locals 10
    new-instance v0, Lrezkatv/web/app/data/repository/AppUpdateInfo;
    const/4 v1, 0x0
    const/4 v2, 0x0
    const-string v3, "1.2.2-uk"
    move-object v4, v3
    const/4 v5, 0x0
    const-wide/16 v6, 0x0
    const-string v8, ""
    move-object v9, v8
    invoke-direct/range {v0 .. v9}, Lrezkatv/web/app/data/repository/AppUpdateInfo;-><init>(ZZLjava/lang/String;Ljava/lang/String;Ljava/lang/String;JLjava/lang/String;Ljava/lang/String;)V
    return-object v0'''
p=D/'smali'/repo;s=p.read_text()
pattern=r'(\.method public final checkForUpdates-gIAlu-s\([^\n]*\n).*?\.end method'
s,n=re.subn(pattern,lambda m:m[1]+no_update+'\n.end method',s,flags=re.S);assert n==2;p.write_text(s)
for method in ['access$getLatestRelease','access$getUpdateFromRemoteConfig']:
 replace_method(repo,method,'    .locals 1\n    const/4 v0, 0x0\n    return-object v0')
replace_method('rezkatv/web/app/ui/viewmodel/AppUpdateViewModel.smali','h','    .locals 0\n    return-void')
replace_method('rezkatv/web/app/data/repository/AppUpdateStartupCache.smali','publish','    .locals 0\n    return-void')
replace_method('rezkatv/web/app/notifications/AppUpdateNotificationWorker.smali','d','''    .locals 1
    invoke-static {}, Lco3;->a()Lbo3;
    move-result-object v0
    return-object v0''')
# Initialize preferences before the original repositories/view models are constructed.
p=ROOT/'localization/java/kik/i18n/LocaleBridge.java';s=p.read_text();s=s.replace('  Locale locale =','  UiRuntime.init(context);\n  Locale locale =');p.write_text(s)
# The app cannot install downloaded APK updates any more.
p=D/'AndroidManifest.xml';s=p.read_text();s=s.replace('    <application', '    <uses-permission android:name="android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION"/>\n    <application',1)
activity='    <activity android:name="kik.i18n.BasicSettingsActivity" android:exported="false" android:screenOrientation="landscape" android:taskAffinity="rezkatv.web.app.uk.main" android:theme="@android:style/Theme.Material.NoActionBar"/>\n'
s=s.replace('    </application>',activity+'    <service android:name="kik.i18n.UpdateJob" android:exported="true" android:permission="android.permission.BIND_JOB_SERVICE"/>\n    <receiver android:name="kik.i18n.UpdateReceiver" android:exported="false"><intent-filter><action android:name="android.intent.action.MY_PACKAGE_REPLACED"/></intent-filter></receiver>\n    </application>');p.write_text(s)
p=D/'apktool.yml';s=p.read_text().replace('  versionCode: 1','  versionCode: '+str(release['version_code'])).replace('  versionName: 1.2.0','  versionName: '+release['version_name']);p.write_text(s)
p=D/'smali/gn5.smali';s=p.read_text()
old='const-string v2, "div.b-post__description_text"'
i=s.index(old);j=s.index('move-result-object v27',i)+len('move-result-object v27')
assert s.count(old)==1 and j-i<400
s=s[:j]+'\n    invoke-static/range {v27 .. v27}, Lkik/i18n/DescTranslate;->tr(Ljava/lang/String;)Ljava/lang/String;\n    move-result-object v27'+s[j:]
p.write_text(s);changes.append('gn5.smali:detail_description_uk')
# Catalog descriptions: machine translation to Ukrainian (Google), cached on device.
# Detail pages come from the HTML parser (gn5) or the film disk cache (i72.d).
p=D/'smali/i72.smali';s=p.read_text()
sig='.method public final d(Ljava/lang/String;)Lrezkatv/web/app/data/models/Film;'
assert s.count(sig)==1
s=s.replace(sig,sig.replace(' d(',' d$KikOrig('),1)
s+='''
.method public final d(Ljava/lang/String;)Lrezkatv/web/app/data/models/Film;
    .locals 1
    invoke-virtual {p0, p1}, Li72;->d$KikOrig(Ljava/lang/String;)Lrezkatv/web/app/data/models/Film;
    move-result-object v0
    invoke-static {v0}, Lkik/i18n/DescTranslate;->film(Ljava/lang/Object;)V
    return-object v0
.end method
'''
p.write_text(s);changes.append('i72.smali:d_description_uk')
# Viewer profiles: each profile gets its own progress directory (= watch history and resume points).
p=D/'smali/gu7.smali';s=p.read_text()
s,n=re.subn(r'new-instance v1, Ljava/io/File;\s*(?:\.line \d+\s*)*invoke-virtual \{p0\}, Landroid/content/Context;->getFilesDir\(\)Ljava/io/File;\s*(?:\.line \d+\s*)*move-result-object p0\s*(?:\.line \d+\s*)*const-string v2, "playback_cache"\s*(?:\.line \d+\s*)*invoke-direct \{v1, p0, v2\}, Ljava/io/File;-><init>\(Ljava/io/File;Ljava/lang/String;\)V',
 'invoke-static {p0}, Lkik/i18n/ProfileStore;->progressDir(Landroid/content/Context;)Ljava/io/File;\n    move-result-object v1',s)
assert n==1;p.write_text(s);changes.append('gu7.smali:profile_progress_dir')
p=D/'smali/rezkatv/web/app/MainActivity.smali';s=p.read_text()
s,n=re.subn(r'(\.method public final onCreate\(Landroid/os/Bundle;\)V\n\s*\.locals \d+\n)',r'\1    invoke-static {p0}, Lkik/i18n/ProfileStore;->onLaunch(Landroid/app/Activity;)V\n',s)
assert n==1;p.write_text(s);changes.append('MainActivity.smali:profile_picker')
p=D/'AndroidManifest.xml';s=p.read_text()
s=s.replace('    </application>','    <activity android:name="kik.i18n.HomeActivity" android:exported="true" android:banner="@drawable/kik_banner" android:screenOrientation="landscape" android:taskAffinity="rezkatv.web.app.uk.main" android:theme="@android:style/Theme.Material.NoActionBar"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LEANBACK_LAUNCHER"/></intent-filter></activity>\n    <activity android:name="kik.i18n.BrowseActivity" android:exported="false" android:screenOrientation="landscape" android:taskAffinity="rezkatv.web.app.uk.main" android:windowSoftInputMode="adjustPan" android:theme="@android:style/Theme.Material.NoActionBar"/>\n    <activity android:name="kik.i18n.FilmActivity" android:exported="false" android:screenOrientation="landscape" android:taskAffinity="rezkatv.web.app.uk.main" android:theme="@android:style/Theme.Material.NoActionBar"/>\n    <activity android:name="kik.i18n.KikPlayerActivity" android:exported="false" android:screenOrientation="landscape" android:taskAffinity="rezkatv.web.app.uk.main" android:configChanges="keyboardHidden|orientation|screenSize|screenLayout|smallestScreenSize" android:theme="@android:style/Theme.Material.NoActionBar.Fullscreen"/>\n    <activity android:name="kik.i18n.SyncActivity" android:exported="false" android:screenOrientation="landscape" android:taskAffinity="rezkatv.web.app.uk.main" android:theme="@android:style/Theme.Material.NoActionBar"/>\n    <activity android:name="kik.i18n.TasteActivity" android:exported="false" android:screenOrientation="landscape" android:taskAffinity="rezkatv.web.app.uk.main" android:theme="@android:style/Theme.Material.NoActionBar"/>\n    <activity android:name="kik.i18n.ProfilesActivity" android:exported="false" android:screenOrientation="landscape" android:taskAffinity="rezkatv.web.app.uk.main" android:theme="@android:style/Theme.Material.NoActionBar"/>\n    </application>')
p.write_text(s)
# Resume: one-click start begins at the saved position of the same title/episode for this profile.
p=D/'smali/i05.smali';s=p.read_text()
m=re.search(r'\.method public final F\(Lez4;\)Lez4;\n.*?\.end method',s,re.S);body=m[0]
call='invoke-static/range {v1 .. v30}, Lez4;->a('
i=body.index(call);j=body.index('move-result-object v1',i)+len('move-result-object v1')
assert body.count(call)==1
body2=body[:j]+'\n    invoke-static {v1}, Lkik/i18n/ResumeFix;->apply(Ljava/lang/Object;)V'+body[j:]
s=s.replace(body,body2);p.write_text(s);changes.append('i05.smali:F_resume_saved_position')
# Back from the catalog root returns to the KIK home screen instead of asking to exit.
prefix_method('vm1.smali','ConfirmDialog','''    move-object/from16 v0, p0
    move-object/from16 v1, p3
    invoke-static {v0, v1}, Lkik/i18n/UiRuntime;->exitDialog(Ljava/lang/String;Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :kik_show_dialog
    return-void
    :kik_show_dialog''')
# Netflix-style player controls: the overlay sees remote keys first and attaches on resume.
p=D/'smali/rezkatv/web/app/PlayerActivity.smali';s=p.read_text()
assert 'dispatchKeyEvent' not in s
s+='''
.method public dispatchKeyEvent(Landroid/view/KeyEvent;)Z
    .locals 1
    invoke-static {p0, p1}, Lkik/i18n/PlayerOverlay;->key(Landroid/app/Activity;Landroid/view/KeyEvent;)Z
    move-result v0
    if-eqz v0, :kik_pass
    const/4 v0, 0x1
    return v0
    :kik_pass
    invoke-super {p0, p1}, Lbr0;->dispatchKeyEvent(Landroid/view/KeyEvent;)Z
    move-result v0
    return v0
.end method
'''
p.write_text(s);changes.append('PlayerActivity.smali:dispatchKeyEvent_overlay')
prefix_method('rezkatv/web/app/PlayerActivity.smali','onResume','    invoke-static {p0}, Lkik/i18n/PlayerOverlay;->attach(Landroid/app/Activity;)V')
# Generate all wordmarks from the shared vector source.
exec(compile((ROOT/'scripts/branding.py').read_text(),str(ROOT/'scripts/branding.py'),'exec'))
changes.append('branding:kik_icon_banner_name_splash')
# The KIK home screen is the launcher entry; the catalog activity is opened from it.
p=D/'AndroidManifest.xml';s=p.read_text()
m=re.search(r'<activity [^>]*android:name="rezkatv\.web\.app\.MainActivity"[^>]*>.*?</activity>',s,re.S);block=m[0]
b2=re.sub(r'\s*<intent-filter>\s*<action android:name="android\.intent\.action\.MAIN"/>\s*<category android:name="android\.intent\.category\.(LEANBACK_)?LAUNCHER"/>\s*</intent-filter>','',block)
assert block.count('LAUNCHER')==2 and 'LAUNCHER' not in b2
s=s.replace(block,b2);p.write_text(s);changes.append('manifest:home_is_launcher')
(ROOT/'localization/simplification-summary.json').write_text(json.dumps({'version':release['version_name'],'patches':changes,'trailers':'removed','rating':'IMDb then HDRezka then Kinopoisk; one badge','genres':2,'quality_default':'1080p','player':'internal','updates':'KIK GitHub Releases; upstream disabled','descriptions':'machine-translated to Ukrainian, cached','profiles':'local, separate history and resume'},indent=2))
print('Simplification overlay:',len(changes),'patches')
