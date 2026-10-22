# TMDB artwork

KIK requests artwork through its server at `/api/artwork`. The browser never receives the TMDB token. Catalog and playback continue working when TMDB is not configured or unavailable.

## Production setup

1. In your TMDB account, open **Settings → API** and obtain an **API Read Access Token** or the v3 **API key**. Both formats are accepted; a pasted `Bearer ` prefix is also accepted.
2. In Cloudflare, open **Workers & Pages → kik → Settings → Variables and Secrets → Add**.
3. Choose **Secret**, enter the name `TMDB_READ_TOKEN`, paste the token as the value, then save/deploy the secret.
4. Deploy the updated KIK source through the project's normal deployment process. Adding the secret alone does not update an older deployment.
5. Reload KIK. In browser Network tools, `/api/artwork` should return `configured: true` and an artwork object for a matched title. A null result is valid when TMDB has no unambiguous match or suitable images.

Alternatively, from `web/`, run `npx wrangler secret put TMDB_READ_TOKEN` and paste the token at the private prompt. Never put it into `app.js`, `wrangler.jsonc`, a URL, a screenshot, or a chat message.

Local development without a key reuses the deployed KIK public artwork endpoint. Only validated catalogue identity fields are forwarded; browser cookies, authorization headers and profile fields are excluded. This fallback only runs on loopback hosts. To use a separate local TMDB credential, copy `.dev.vars.example` to `.dev.vars` and replace the placeholder. The real file is excluded from version control. Do not copy it into `public/`.

## Behavior

- The first page render uses existing posters. Cards near the viewport and the active hero request artwork through `POST /api/artwork/batch` (up to 12 titles per client batch, one request in flight), so a full home page costs only a few calls against the per-IP API limit. Without a token the first batch answers `configured: false` and the page stops asking.
- Search uses the content type, title/original title, and release/start year. Each query requests translations in the query language, so an English international title can match a Korean original even when the catalogue uses a Russian name. A second search without the strict year filter still enforces the one-year date tolerance to account for regional release dates. Ambiguous matches and conflicting years keep the existing artwork. If the hero has no match, its original title is read from the film details and tried once, in the background.
- Hero backgrounds require a landscape image at least 1280 × 720. High-density screens may select the original image; cards use 500 px posters, with 780 px and original-resolution variants for high-density displays. Selected TMDB posters must be at least 500 × 750.
- The hero uses only verified landscape artwork at least 1280 × 720; a vertical poster is never used as its background. The dark gradient stays in place while the wide image loads or when no matching landscape image exists. Hero requests start before batches of cards. Visible cards without a TMDB match load the full film-page poster through a queue of two background requests; film requests are shared with details and the hero. Image load handlers are registered before their URLs, including cached responses.
- Artwork cache v2 avoids reusing old misses after credential handling changed. Successful matches are cached for seven days, misses for twelve hours, and temporary failures for one minute. Concurrent identical lookups share a request. No profile data or watch history is sent to TMDB.
- TMDB supplies images only; it does not supply playback streams.

## Verification

Run `node --test tmdb.test.mjs` (in `web/`) for credential-free tests with mocked TMDB responses. Run `npm test` for the existing project tests as well. A live TMDB check requires a configured token; never log that token during verification.

## Sources and attribution

- https://developer.themoviedb.org/docs/authentication-application
- https://developer.themoviedb.org/docs/image-basics
- https://developer.themoviedb.org/docs/faq
- https://developers.cloudflare.com/workers/configuration/secrets/

The footer link «Джерела даних» shows the required TMDB notice and a link to themoviedb.org. TMDB's developer API terms apply; commercial use requires the appropriate agreement.
