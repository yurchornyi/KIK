# TMDB artwork

KIK requests artwork through its server at `/api/artwork`. The browser never receives the TMDB token. Catalog and playback continue working when TMDB is not configured or unavailable.

## Production setup

1. In your TMDB account, open **Settings → API** and obtain the **API Read Access Token** (the long token, not the short API key).
2. In Cloudflare, open **Workers & Pages → kik → Settings → Variables and Secrets → Add**.
3. Choose **Secret**, enter the name `TMDB_READ_TOKEN`, paste the token as the value, then save/deploy the secret.
4. Deploy the updated KIK source through the project's normal deployment process. Adding the secret alone does not update an older deployment.
5. Reload KIK. In browser Network tools, `/api/artwork` should return `configured: true` and an artwork object for a matched title. A null result is valid when TMDB has no unambiguous match or suitable images.

Alternatively, from `web/`, run `npx wrangler secret put TMDB_READ_TOKEN` and paste the token at the private prompt. Never put it into `app.js`, `wrangler.jsonc`, a URL, a screenshot, or a chat message.

For local development, copy `.dev.vars.example` to `.dev.vars` and replace the placeholder. The real file is excluded from version control. Do not copy it into `public/`.

## Behavior

- The first page render uses existing posters. Cards near the viewport and the active hero request artwork through `POST /api/artwork/batch` (up to 24 titles per request, one request in flight), so a full home page costs only a few calls against the per-IP API limit. Without a token the first batch answers `configured: false` and the page stops asking.
- Search uses the content type, title/original title, and release/start year. Ambiguous matches and conflicting years keep the existing artwork.
- Hero backgrounds require a landscape image at least 1280 × 720. High-density screens may select the original image; cards use 500 px posters, with 780 px variants for high-density displays.
- If no landscape image is available, a contained poster is shown rather than stretching a thumbnail across the banner.
- Successful matches are cached for seven days, misses for twelve hours, and temporary failures for one minute. Concurrent identical lookups share a request. No profile data or watch history is sent to TMDB.
- TMDB supplies images only; it does not supply playback streams.

## Verification

Run `node --test tmdb.test.mjs` (in `web/`) for credential-free tests with mocked TMDB responses. Run `npm test` for the existing project tests as well. A live TMDB check requires a configured token; never log that token during verification.

## Sources and attribution

- https://developer.themoviedb.org/docs/authentication-application
- https://developer.themoviedb.org/docs/image-basics
- https://developer.themoviedb.org/docs/faq
- https://developers.cloudflare.com/workers/configuration/secrets/

The footer link «Джерела даних» shows the required TMDB notice and a link to themoviedb.org. TMDB's developer API terms apply; commercial use requires the appropriate agreement.
