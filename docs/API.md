# HTTP API

> Reference for every endpoint exposed by the embedded Jetty webserver. Routes are
> registered in [`Main.java`](../src/main/java/org/mtr/core/Main.java) and dispatched
> through [`ServletBase`](../src/main/java/org/mtr/core/servlet/ServletBase.java). For the
> in-process message-queue API used by embedded hosts, see
> [ARCHITECTURE.md § Operations and the message queue](ARCHITECTURE.md#operations-and-the-message-queue).

## Conventions

- Both `GET` and `POST` are accepted on every endpoint; `doGet` simply delegates to
  `doPost`. Bodies, when present, are JSON.
- All responses are JSON, served with `Content-Type: application/json` and
  `Access-Control-Allow-Origin: *`.
- Every response is wrapped in the standard envelope:

  ```json
  {
    "status": 200,
    "text": "OK",
    "currentTime": 1715000000000,
    "data": { ... }
  }
  ```

  `status` matches the HTTP status code; `text` is the reason phrase; `data` is the
  endpoint-specific payload (or `null` for fire-and-forget endpoints).

- Endpoints are dimension-scoped via the `dimension` query parameter (a 0-based index into
  the `dimensions...` argument list passed at startup). Pass `dimensions=all` to fan out
  across every loaded dimension; in that case the response always returns `200 OK` with no
  payload because each dimension has been processed asynchronously.
- An out-of-range `dimension` value yields `400 Bad Request` with reason
  `"Bad Request - Invalid Dimension"`.

## `GET /` — Dashboard

Serves the bundled Angular UI. Any unknown path under `/` falls through to
`MainWebServlet`, which looks the file up in `WebserverResources` (the generated bundle of
the `website/` build output).

## `/mtr/api/map/*` — System Map API

Implemented by [`SystemMapServlet`](../src/main/java/org/mtr/core/servlet/SystemMapServlet.java).

Common query parameters:

| Name         | Type   | Description                                                         |
|--------------|--------|---------------------------------------------------------------------|
| `dimension`  | int    | 0-based dimension index. Default `0`.                               |
| `dimensions` | string | When set to `all`, runs the request against every loaded dimension. |

Endpoints:

### `GET /mtr/api/map/stations-and-routes`

Returns the static stations / routes graph for the dimension.

- **Cache**: per-dimension, `STATIONS_AND_ROUTES_CACHE_MILLIS` = 30 s.
- **Body**: none.
- **Response data**: a `StationAndRoutes` payload — list of stations (with hex id, name,
  colour, area, connecting routes) and list of routes (with hex id, name, colour, type and
  ordered platform list). The TypeScript shape lives in
  `website/src/app/entity/generated/map/`.

### `GET /mtr/api/map/departures`

Live next-departure information per platform per route.

- **Cache**: per-dimension, `LIVE_DATA_CACHE_MILLIS` = 3 s.
- **Response data**: a `Departures` payload — `currentTime` plus a map of
  `routeHexId → platformId → millisecond timestamps` for upcoming departures.

### `POST /mtr/api/map/arrivals`

On-demand arrivals query for a specific set of stops / platforms.

- **Cache**: none (computed per request).
- **Body**: an `ArrivalsRequest` JSON object — see
  `org.mtr.core.operation.ArrivalsRequest`.
- **Response data**: an `ArrivalsResponse` listing predicted arrivals, sorted by time.
  Each `ArrivalResponse` includes `passengerCount` (realtime onboard passengers for the
  serving vehicle, or `0` when unknown / no concrete vehicle).

### `GET /mtr/api/map/clients`

Live positions of every connected player and which station / route / platform they're at or
riding.

- **Cache**: per-dimension, `LIVE_DATA_CACHE_MILLIS` = 3 s.
- **Response data**: a `Clients` payload — `currentTime` plus a list of `Client` records
  (uuid, display name, x/z coordinates, current station hex id, current route / station /
  next-station hex ids if riding).
- The display name comes from `Main.CLIENT_NAME_RESOLVER`; if the embedding host hasn't
  installed one, names will be empty strings.

### `POST /mtr/api/map/directions`

Path-finding query — "how do I get from A to B".

- **Body**: a `DirectionsRequest` JSON object — see
  `org.mtr.core.map.DirectionsRequest`.
- **Response data**: a `DirectionsResponse` listing one or more route segments (rides plus
  walking transfers). The request is queued on the simulator's `directionsFinder`, so
  responses are asynchronous from the caller's perspective but synchronous from the
  client's (the connection is held open until the result is ready).
- **Realtime filtering**: routes currently marked jammed by the simulator are excluded from
  directions results (same filter used by passenger replanning).

## `/oba/api/where/*` — OneBusAway-compatible API

Implemented by [`OBAServlet`](../src/main/java/org/mtr/core/servlet/OBAServlet.java). The
endpoint names follow the
[OneBusAway "where" REST API](https://developer.onebusaway.org/api/where) so existing
OneBusAway client libraries work against this server unchanged.

### Implemented endpoints

| Endpoint                                | Description                                                                                  |
|-----------------------------------------|----------------------------------------------------------------------------------------------|
| `agencies-with-coverage`                | List of agencies (one per dimension).                                                        |
| `agency`                                | Details for a single agency.                                                                 |
| `arrivals-and-departures-for-stop/{id}` | Upcoming arrivals/departures at a station (the `id` segment after the slash is the stop id). |
| `stops-for-location`                    | Stations within a query bounding box / radius.                                               |
| `trip-details/{id}`                     | Vehicle/trip detail for a given trip id.                                                     |

### Stub endpoints (currently return `{}`)

These endpoints are wired through `OBAServlet` to satisfy clients that probe the full OBA
surface, but they return an empty JSON object. Treat them as **not implemented** — relying
on a non-empty response is a bug.

`arrival-and-departure-for-stop`, `arrivals-and-departures-for-location`, `block`,
`cancel-alarm`, `current-time`, `register-alarm-for-arrival-and-departure-at-stop`,
`report-problem-with-stop`, `report-problem-with-trip`, `route-ids-for-agency`, `route`,
`routes-for-agency`, `routes-for-location`, `schedule-for-route`, `schedule-for-stop`,
`shape`, `stop-ids-for-agency`, `stop`, `stops-for-route`, `trip-for-vehicle`, `trip`,
`trips-for-location`, `trips-for-route`, `vehicles-for-agency`.

Unknown endpoint names yield `404 Not Found` (for both `/mtr/api/map/*` and `/oba/api/where/*`).

## Status codes

The mapping lives in
[`HttpResponseStatus.java`](../src/main/java/org/mtr/core/servlet/HttpResponseStatus.java):

| Code | Reason                | When                                                                       |
|------|-----------------------|----------------------------------------------------------------------------|
| 200  | OK                    | Request succeeded.                                                         |
| 301  | Moved Permanently     | Returned by static-resource handlers when redirecting (`Location` header). |
| 400  | Bad Request           | Invalid `dimension` query parameter.                                       |
| 404  | Not Found             | Unknown endpoint, or a handler returned `null` payload.                    |
| 500  | Internal Server Error | Unexpected exception while handling a request.                             |

## Embedding extensions

Hosts embedding the jar can register additional servlets at custom paths via the
`additionalWebserverSetup` `Consumer<Webserver>` accepted by the `Main` constructor — the
hook fires after the three built-in servlets are registered but before
`Webserver.start()` is called.
