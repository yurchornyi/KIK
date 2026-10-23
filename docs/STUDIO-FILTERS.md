# Studio and platform filters

The catalogue has monochrome studio marks and a compact filter next to genre and order. The website uses the same dark custom menu for all three controls, including keyboard navigation and a mobile sheet. Rounded controls keep a fixed height and single-line captions; the menu and tooltip show the complete genre name.

Supported groups: Netflix, Apple TV+, HBO, Prime Video, Disney+, Hulu, Paramount+, Warner Bros. and Pixar. A badge describes an original TV network or an exactly matched production company. Regional streaming availability does not identify a producer.

## Catalogue behavior

- A studio selection searches TMDB's complete discover results and resolves candidates against KIK's existing catalogue by name, year and category. It does not filter only the cards currently loaded in the browser.
- Movie company IDs are resolved from exact company names. TV groups use original network IDs; production companies are used for studios without a network.
- Source genre membership is checked against all genres on the matched film page, including secondary genres.
- A request handles five candidates. Explicit continuation keeps empty or duplicate intermediate pages from ending the list. Failed source requests remain retryable without discarding the current page.
- Popularity and release date are available with a studio selection. The source's live viewing order remains available when no studio is selected.
- The selected studio stays in the website URL and can be removed with its small reset chip. Category and genre changes preserve it.

## Metadata and configuration

`TMDB_READ_TOKEN` stays on the server. Both supported TMDB credential formats continue to work. Missing configuration leaves regular browsing available and returns a temporary-unavailable response for studio filtering. Artwork and studio data share one TMDB details request using `append_to_response=images`; the artwork cache namespace is now v3.

Android TV uses the same public catalogue endpoint for studio selections. Poster metadata loads in batches in the background, with weak view references and a bounded cache. It does not block title loading, remote navigation or playback. This change does not publish a Worker or install an APK.

## Validation

- `npm test --prefix web` includes studio identity, name/year matching, secondary genres, pagination, retries and stale-response isolation.
- `KIK_PLAYWRIGHT_MODULE=<playwright module> node web/ui-audit.mjs` checks Chromium and WebKit, single-line genre captions and full menu names, studio selection, reset, combined filters and focus restoration.
- Android helper changes are compiled against API 34. Physical TV navigation and a live provider end-to-end check must be verified before a release.

API references: [TV details](https://developer.themoviedb.org/reference/tv-series-details), [movie details](https://developer.themoviedb.org/reference/movie-details), [TV discovery](https://developer.themoviedb.org/reference/discover-tv), [movie discovery](https://developer.themoviedb.org/reference/discover-movie).
