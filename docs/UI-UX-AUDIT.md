# Web and Android TV UI audit — 2026-10-23

The fixes are on `codex/ui-ux-audit` in an isolated worktree. Existing unfinished player/ad work in the original checkout was preserved. Web changes have been built locally; this audit did not publish a production Web deployment. The updated TV APK was installed on the existing XGIMI XK10T package.

## Changes

- TV settings now use two balanced columns of cards: playback/catalogue and profile/updates/about. Small logical windows use a single column. Controls share consistent widths, spacing and focus treatment. The automatic-update switch is contained within its card instead of a fixed 670 dp row.
- Quality and country choices use compact grids. Long profile names fit within the profile card. Update status displays the installed version rather than a persisted version from an older installation.
- TV search, hero text, title descriptions, profile forms and sync controls fit the available width. Navigation, catalogue filters and long action rows scroll with the remote rather than running past the screen edge.
- Cached TV home and title pages render before translation requests. Remaining bounded translation waits were reduced from 2,500 ms to 180 ms; cached lookups avoid creating unnecessary worker threads.
- Web navigation fits tablet split view, phones and desktop widths. Hero titles have bounded line counts, portrait/landscape spacing is responsive, touch controls have at least 44 px targets, and mobile grids preserve readable labels.
- Player controls use complete rows. On phones audio spans the first row, season/episode share the next, and quality/speed share the last. WebKit's long native select content cannot create horizontal dialog scrolling.
- Dialog close controls remain reachable while scrolling. Inputs, account names, admin emails, profile names and source credits wrap or truncate within their containers. Reduced-motion settings skip the intro.
- Catalogue sections appear as their requests finish. A slow series endpoint does not hold back films or the first usable home shelf. Loading, empty and network-error states are distinct; failed catalogue/search requests offer retry.
- Background sync and recommendations do not rebuild a focused or scrolled page. Search URLs preserve the query through Back/reload, and late title responses cannot replace another modal.
- HLS is loaded only when a non-native HLS stream is opened. The initial document no longer requests the 619,692-byte uncompressed media dependency. iOS native HLS does not need it.

## Web validation

`web/ui-audit.mjs` serves the actual frontend with deterministic API fixtures. It does not mutate production accounts or depend on third-party catalogue availability.

Chromium and WebKit each covered 14 viewports: 320×568, 375×667, 390×844, 430×932, 667×375, 768×1024, 820×1180, 1024×768, 1180×820, 1280×720, 1440×900, 1920×1080, 2560×1440 and 3840×2160. Touch, DPR 2, landscape and reduced motion are included. This is viewport/browser emulation, not testing on physical iPhones or iPads.

The final run passed **530 checks with zero failures**, including home, films, series, list, history, settings, admin, profiles, login/register, search/results, pairing, countries, avatars, taste, details, film/series player, sources, loading and offline states. Checks cover document/dialog overflow, header collisions, incomplete player rows, browser errors, focus preservation, stale responses, search restoration and progressive loading.

Local fixture measurements, useful for repeatable UI regression comparisons rather than production network promises:

| Metric | Chromium | WebKit |
| --- | ---: | ---: |
| Median home ready | 167 ms | 548 ms |
| Median tab switch | 42 ms | 59 ms |
| First shelf with series request delayed 3 seconds | 157 ms | 575 ms |

All **56 existing Web unit tests** pass, and the Web asset build passes. Read-only checks of the existing production service returned HTTP 200: home 236 ms total and film catalogue 1,017 ms total. Those are single samples of the existing deployment, not post-deployment benchmarks.

## TV validation and evidence

The connected device was reidentified as XGIMI XK10T, with a physical 1920×1080 display and density 320. Instrumentation uses a disposable profile and restores the original selection. Display overrides are restored after testing.

`ResponsiveAudit` checks real measured controls, remote focus reachability, long names, screen readiness and captures before/after focus traversal. The settings redesign separately passed 33 on-device checks. The full 1080p and 720p runs each passed 159 checks. The final larger-text run passed 179 checks, including catalogue filters: **497 successful on-device UI checks** in total. The larger-text run initially exposed a fixed-width search input; it was clamped before the successful rerun.

The generated local 1080p/720p decoder test passed 14 checks covering advancing video, pause, speed, quality switching, paused seek and automatic stream recovery. The local clips are four minutes long so they are not mistaken for the source’s short premium previews. The live-source loading test passed: cached home 2,190 ms, first title 2,071 ms, subsequent title opens 934/963 ms, player prepared 3,450 ms, and confirmed advancing playback. This proves the tested title/device path, not every third-party stream.

The projector caps the effective app surface at 1920×1080 when a 3840×2160 override is requested. Consequently, the high-density test is a large-text test, not proof of native 4K rendering. Web's 3840×2160 viewport was tested separately.

Local evidence is in `outputs/ui-audit/`: final browser `report.json`, screenshots, device JSON/logs, loading measurements, decoder tests and APK validation. The signed APK is `outputs/KIK-1.5.4-uk.apk`; `installed-before.apk` is the exact previously installed APK for rollback. The test APK includes the already present local player overlay so the UI installation does not remove that ongoing work. Those unrelated player changes are excluded from this branch.

APK signature, alignment, original APK integrity, native libraries, package identity and source URLs are checked. The original checkout's updated validation policy is used for the assembled APK because its existing player overlay adds the ad-break API endpoint.

## Reproduce

From the repository root:

```sh
npm --prefix web test
npm --prefix web run build
```

With Playwright and its Chromium/WebKit browsers available:

```sh
# KIK_PLAYWRIGHT_MODULE may point to an existing Playwright installation.
KIK_PLAYWRIGHT_MODULE=/absolute/path/to/playwright npm --prefix web run test:ui
```

For the signed, installed TV APK and local signing tools:

```sh
export KIK_DEVICE=192.168.1.127:5555
KIK_AUDIT_BUILD_ONLY=1 bash scripts/audit-device.sh
adb -s "$KIK_DEVICE" install -r work/KIK-audit.apk
KIK_DEVICE=192.168.1.127:5555 python3 scripts/audit-ui-device.py
adb -s "$KIK_DEVICE" uninstall kik.audit
```

The matrix script targets the verified XK10T model, tests 1080p, 720p and larger text, and restores previous display overrides in `finally`. Reports record effective size/density so a requested mode is never mistaken for the actual app surface.
