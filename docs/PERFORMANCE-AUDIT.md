# Web and Android TV performance audit

The audit was done on `codex/performance-audit` (2026-10-23, against TV 1.4.3). That branch could no longer be merged, so its fixes were re-applied on top of the 1.5.5 code. Fixes that main already had, or that newer deliberate changes replaced, were not copied.

## In the code

### Web (`web/public`)

- One sync at a time. Changes made during a sync, and a profile picked during it, are sent as soon as it settles. A large offline queue is sent in batches of 200. A failed sync does not retry in a loop: the 30 s timer and the `online` event retry it.
- The page-close beacon stays under 60,000 bytes and sends the last playback position first. Records that do not fit stay queued (`sync-queue.js`).
- Blocked or full `localStorage` never throws into account, settings, rating-prompt or player code (`storage.js`). The sync line says when storage is unavailable.
- Title details: one request per title at a time, fresh for 3 minutes, at most 80 kept. Artwork cache: at most 256 entries. A late poster is not painted into a card that was removed.
- `hls.js` loads only for a stream that needs it. The eager `<script defer>` that a merge re-added to `index.html` has been removed. The download has a 15 s deadline and can be retried after a failure.
- Player: only the most recently picked episode loads. Changing voice, season, episode or quality keeps the player paused if it was paused. Quality changed before the media is ready resumes from the saved position. Closing the player detaches every media callback. Switching profile also cancels a player that is still opening.
- A button whose action fails shows the error as a toast. A profile rename or settings save that finishes after a profile switch does not change the newly picked profile.
- `sources.html` uses the same no-zoom viewport as the app.

### Android TV (`localization/java/kik/i18n`)

- Shelves and the Home page scroll only when the focused card would leave the visible safe area (`FocusScrollPolicy`). Before this, they scrolled on every D-pad press.
- Decoded posters and backdrops are capped at 4 megapixels, even for extreme remote images (`ImageDecodePolicy`). Queued image work is skipped for views that are detached or already show another picture.
- Identical translated labels are not set again. The player's progress bar, play icon and subtitle position change only when their value changes, so there is no layout pass every 500 ms.
- Entrance, focus and player-overlay animations respect the TV's "animations off" setting.
- Search: the pending typed search is cancelled when the screen closes, and stale card references are cleared. Late genre results are not drawn on a closed screen.
- The player keeps the title and voice on its own instance (`openingFilm`/`openingVoice` only hand them over). A later launch cannot rewrite the session that is playing.
- The player handles only its remote keys. Volume, mute, power and other system keys keep their system behaviour.

## Not taken from the audit branch

| Audit change | Why |
| --- | --- |
| TV search after 2 letters / 600 ms | Main deliberately uses 3 letters / 1.1 s for remote typing (246320f). |
| Ctrl+wheel and two-finger `touchmove` cancelling (`viewport.js`) | Main's deliberate no-zoom design already covers touch: viewport `maximum-scale=1,user-scalable=no`, `touch-action:pan-x pan-y` and Safari gesture events. Blocking Ctrl+wheel would also remove desktop browser zoom. |
| Web prepare/seek timers in `setSource`, TV prepare/seek timeouts and the new `failure()` | Main's recovery already covers these: `RecoveryPlan`, restore-seek verification and `armRecovery` on the web, and ExoPlayer stall/freeze detection with `RecoveryPolicy` on TV. |
| Web API timeout, detail and player generations, progressive catalogue, observer disconnect | Already in main. |
| Search width, `AuditRunner` search-clear check | Already in main (`Ui.contentWidth`, idle-state check). |
| Header and 650 px CSS tweaks | Replaced by `responsive.css`. |

## Checks

- `cd web && npm test`: `ui-races.test.mjs` runs the real `app.js` against a small DOM stand-in. It covers the sync queue, profile switch, film cache, episode race, pause preservation and button errors. `runtime.test.mjs` covers storage and the beacon.
- Host Java: `ImageDecodeCheck`, `FocusScrollCheck` (also in CI) and `PlayerCheck` (player keys).
- Device: `KIK_AUDIT_MODE=performance scripts/audit-device.sh` runs `UiPerformanceAudit`. It records frame timings on a 50-key D-pad route on Home, then checks that a typed search is cancelled when the screen closes. `PlayerLocalAudit` checks that a second launch cannot replace the active player's title.

On the audit branch (XGIMI XK10T, 1920×1080, same route), the focus-scroll change cut p95 frame time from 59.4 ms to 44.9 ms. Frames over 32 ms fell from 68% to 18%. These numbers come from one paired run on 1.4.3. They have not been measured on the current build.

## Still to verify on the projector

1. Build one candidate, then run `KIK_AUDIT_MODE=performance` and compare with the numbers above.
2. Run the full suite plus `PlayerLocalAudit` and `RatingPromptAudit`. Both now set `openingFilm` and `openingVoice`.
3. In the player, test volume and mute on the remote, and check that ◀ ▶ OK, Back and the media keys still work, including during the intro.
4. Turn animations off in developer options and check that focus rings and the player controls still appear.
5. On the web, test pause during voice, episode and quality changes, offline then online with many changes, and closing the tab mid-episode followed by a reload.
