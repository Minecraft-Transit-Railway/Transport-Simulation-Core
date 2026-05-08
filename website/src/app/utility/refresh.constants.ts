/**
 * Frontend refresh / polling intervals (milliseconds).
 *
 * Keep these in sync with the matching cache TTLs on the backend — see
 * `SystemMapServlet.STATIONS_AND_ROUTES_CACHE_MILLIS` and `LIVE_DATA_CACHE_MILLIS`. There's
 * no point polling more often than the server is willing to recompute.
 */

/** Long-lived data: the stations / routes graph and per-client metadata. */
export const SLOW_REFRESH_INTERVAL_MILLIS = 30000;

/** Live data: departures, client positions, directions. */
export const LIVE_REFRESH_INTERVAL_MILLIS = 3000;
