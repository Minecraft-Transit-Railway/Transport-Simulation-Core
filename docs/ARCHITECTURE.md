# Architecture

> A bird's-eye view of how Transport Simulation Core is put together. For coding conventions
> see [CODE_STYLES.md](CODE_STYLES.md); for the HTTP surface see [API.md](API.md).

## What this project is

Transport Simulation Core is a standalone Java 21 service that owns the simulation state
(stations, routes, depots, vehicles, lifts, riding entities, paths) for one or more
"dimensions" — the term comes from Minecraft, but the core has no Minecraft dependency. It
ships as a fat jar with embedded Jetty and exposes:

- a built-in Angular dashboard at `/`,
- a public read-only system map / OBA-compatible REST API for clients (`/mtr/api/map/*`,
  `/oba/api/where/*`),
- an in-process message queue API (`Main.sendMessageC2S` / `processMessagesS2C`) used by the
  Minecraft Transit Railway mod when this jar is embedded.

It is designed to be embeddable: the Minecraft Transit Railway mod links the jar directly
and feeds it world events through the message queue, while the dashboard and REST API stay
exactly the same as in standalone mode.

## Process layout

```
                          ┌──────────────────────────────────────┐
                          │                Main                  │
                          │  (entry point, owns lifecycle)       │
                          └────────────┬─────────────────────────┘
                                       │
        ┌──────────────────────────────┼─────────────────────────────────┐
        │                              │                                 │
        ▼                              ▼                                 ▼
┌────────────────┐           ┌──────────────────┐               ┌─────────────────┐
│ ScheduledExec. │           │     Webserver    │               │   Simulators    │
│  (one thread   │  ticks    │  (embedded Jetty │   serves      │  (one per game  │
│  per dim, every│ ───────▶  │   on configured  │  ─ JSON ─▶    │   dimension)    │
│  10 ms)        │           │   port)          │               │                 │
└────────────────┘           └──────────────────┘               └─────────────────┘
                                       │                                 ▲
                                       │ registers                       │
                                       ▼                                 │
                          ┌──────────────────────────┐                   │
                          │       Servlets           │                   │
                          │ MainWebServlet ("/")     │                   │
                          │ SystemMapServlet         │ ─ run on sim ─────┘
                          │ OBAServlet               │
                          └──────────────────────────┘
```

`Main` ([`Main.java`](../src/main/java/org/mtr/core/Main.java)) constructs one `Simulator`
per requested dimension, optionally spins up Jetty, and (if threaded simulation is enabled)
starts a `ScheduledExecutorService` that ticks each `Simulator` every
`Main.MILLISECONDS_PER_TICK` (10 ms). When the threaded flag is off — typical in embedded
mode — the host calls `Main.manualTick()` from its own loop.

## Simulator

[`org.mtr.core.simulation.Simulator`](../src/main/java/org/mtr/core/simulation/Simulator.java)
is the unit of state. One instance per dimension owns:

- the **data model** (`org.mtr.core.data`): `Station`, `Platform`, `Route`, `Depot`,
  `Siding`, `Vehicle`, `Rail`, `Lift`, `Client`, `RidingVehicle`, …
- file persistence (loads on construction, saves on `save()` / `stop()`),
- a queued runnable list (`run(Runnable)`) — every external mutation is enqueued and runs
  on the simulator thread, keeping the data model single-threaded,
- two message queues bridging client ↔ server inside an embedded host:
  `messageQueueC2S` (host → core) and `messageQueueS2C` (core → host), both carrying
  `QueueObject`s,
- a path-finding subsystem (`org.mtr.core.path`, `directionsFinder`).

The simulator's `tick()` advances vehicle positions, processes queued runnables, drains the
inbound message queue through `OperationProcessor`, and writes outbound messages to the
S2C queue.

### Passenger demand model (homes / landmarks)

#### Homes

Homes own a persisted `ObjectArrayList<Passenger>` and converge it towards the configured
`population` each tick (at most 10 adds/removes per tick, FIFO order — oldest removed first
when shrinking). Convergence is bounded so that loading a large zone with many homes doesn't
spike the per-tick cost.

#### Passenger state machine

Each `Passenger` runs a deterministic while-loop every tick. A **cooldown gate** at the top of
the method checks `simulator.getCurrentMillis() < cooldownTime`: if the passenger is waiting
(for a landmark visit, boarding retry, or journey cooldown), the tick returns immediately.

```
                    ┌──────────────────────┐
                    │   Cooldown gate      │  if now < cooldownTime → return
                    │   (cooldownTime)     │
                    └──────────┬───────────┘
                               │ cooldown expired
                               ▼
                    ┌──────────────────────┐
                    │   Idle / no          │  end landmark visit (if any)
                    │   directions         │  pick destination (home or landmark, 1:3)
                    └──────────┬───────────┘
                               │ findDirections async via DirectionsFinder
                               │ (budget-capped; cooldownTime = Long.MAX_VALUE during request)
                               ▼
                    ┌──────────────────────┐
                    │   Walking leg        │  no route → wait for endTime, then consume
                    │   (currentRoute==null)│
                    └──────────┬───────────┘
                               ▼
                    ┌──────────────────────┐
                    │   Awaiting vehicle   │  vehicleId == 0
                    │                      │  check jammed / REALTIME_REPLAN_THRESHOLD
                    │                      │  → replan if expired
                    │                      │  waitForVehicle(simulator)
                    │                      │    ─ scan all sidings
                    │                      │    ─ found → set currentVehicle
                    │                      │    ─ not found → reset & wait(BOARDING_COOLDOWN)
                    │                      │  boardVehicle(getBoardingCar(), 10)
                    │                      │    ─ hash-based car; 10% adjacent spillover
                    │                      │    ─ all full → replan
                    └──────────┬───────────┘
                               ▼
   ┌────────────────────────────────────────────┐
   │   On vehicle  (vehicleId != 0)              │
   │   updateCurrentSidingAndVehicleCache()      │
   │   ┌────────────────────────────────────┐    │
   │   │ 5a vanished  → reset & replan      │    │
   │   │ 5b arrived   → doors open/depot →  │    │
   │   │                alightVehicle()      │    │
   │   │                consume leg          │    │
   │   │                (stay aboard if      │    │
   │   │                 next leg same route)│    │
   │   │ 6 missed-stop  → alight & replan   │    │
   │   └────────────────────────────────────┘    │
   └──────────────────┬─────────────────────────┘
                      │ consumed / completed
                      ▼
               ┌────────────────────┐
               │   Leg complete     │  directions not empty → replan or continue
               │   / Journey done   │  directions empty → completeJourney
               └────────────────────┘
                      │
                      ▼
               ┌────────────────────┐
               │   Complete         │  at landmark: cooldownTime = landmarkVisitEndTime
               │                    │  at home: wait(GENERIC_COOLDOWN, randomize)
               └────────────────────┘
```

**Cooldown system**: A single `cooldownTime` field replaces the older `findDirectionsTime`.
It is set to:
- `Long.MAX_VALUE` while a CSA request is in flight (prevents duplicate submissions),
- `landmarkVisitEndTime` while visiting a landmark,
- `now + GENERIC_COOLDOWN + random(GENERIC_COOLDOWN)` after a journey completes (home),
- `now + BOARDING_COOLDOWN + random(BOARDING_COOLDOWN)` when no vehicle found,
- `now + RETRY_COOLDOWN + random(RETRY_COOLDOWN)` on budget exhaustion or failed reservation.
The callback resets `cooldownTime = 0` on success so the next tick proceeds.

**Boarding**: `waitForVehicle()` scans all sidings for a vehicle on the current route/platform.
On find, fields `sidingId`, `currentSiding`, `vehicleId`, `currentVehicle` are set.
`boardVehicle()` uses a hash of the passenger's ID to choose a preferred car, with 10%
spillover to an adjacent car when the preferred car is full (avoids hotspots).
`alightVehicle()` removes the passenger from every car's passenger set.

**Vanished-vehicle replan (5a)**: When the boarded vehicle cannot be found in any siding
(via `updateCurrentSidingAndVehicleCache()`), the passenger immediately replans instead of
waiting. The replan origin is the destination platform's position (or home center if unknown).

**Missed-stop guard (6)**: If the leg's estimated arrival time passes without the vehicle
reaching the target platform, the passenger force-alights and replans. This is handled within
the same `if` branch as arrival (5b) — both end with `alightVehicle()`, leg consumption, and
either completion or replan. The distinction is cosmetic for the passenger state machine.

**CSA budget**: The simulator caps the number of concurrent outbound CSA requests per tick.
When the budget is exhausted the passenger retries after `RETRY_COOLDOWN + random(RETRY_COOLDOWN)`.

**Population convergence cleanup**: When a passenger is removed from its home during
population convergence, `clearVehicleReferences()` is called to remove it from any vehicle
car set and to end any active landmark visit (`endVisit`). This prevents stale references
in the shared occupancy data structures.

#### Landmark density model

Landmarks use 288 time-of-day slots (5 minutes each) with per-hour density limits. A visit
occupies every slot between arrival and departure. The `reserveVisit` method walks forward
from the arrival slot, consuming slots until either the density limit is reached or the
randomized max-stay cap is hit. This gives a visit duration proportional to available
capacity at that time of day.

- `useRealTime = true` → wall-clock time indexing (slots aligned to real 5-minute boundaries).
- `useRealTime = false` → in-game time indexing (slots distributed across the game day length).

The density arrays are cumulative within a session and reset on save/load
(not persisted — reconstructed from the schema fields). The first tick of a newly loaded
landmark is lenient (no prior occupancy), but thereafter the double-buffered
(`currentVisitingPassengers` / `currentVisitingPassengersOld`) snapshot system provides
correct back-pressure.

#### Runtime caches

- `sidingIdMap` → siding lookup (rebuilt by `Data.sync()`).
- `Siding.vehicleIdMap` → per-siding vehicle lookup (the canonical vehicle store, replacing
  the old set).
- `Passenger.currentSiding` / `currentVehicle` → per-passenger transient cache, resolved from
  persisted `sidingId` / `vehicleId` fields by
  {@link org.mtr.core.data.Passenger#updateCurrentSidingAndVehicleCache}.
- `VehicleCar.passengers` → per-car passenger occupancy set (runtime only, rebuilt on load via
  {@link org.mtr.core.data.Passenger#writeVehicleCache}). Replaces the removed
  `Simulator.vehicleIdToPassengers` — occupancy now lives on the car, not the vehicle.
- `Passenger.vehicleCarNumber` → persisted car index; written on board, cleared on alight.
  Survives restarts so passengers are restored to the correct car on load.
- `jammedRouteIds` → routes where a vehicle has stalled past threshold (excluded from both
  `/mtr/api/map/directions` and passenger replanning).

## Servlets and HTTP surface

Three servlets are registered in `Main` when `webserverPort > 0`:

| Path               | Servlet                                                                           | Purpose                                             |
|--------------------|-----------------------------------------------------------------------------------|-----------------------------------------------------|
| `/`                | `MainWebServlet` → `WebserverResources::get`                                      | Serves the bundled Angular dashboard.               |
| `/mtr/api/map/*`   | [`SystemMapServlet`](../src/main/java/org/mtr/core/servlet/SystemMapServlet.java) | Public read-only map data (stations, routes, etc.). |
| `/oba/api/where/*` | [`OBAServlet`](../src/main/java/org/mtr/core/servlet/OBAServlet.java)             | OneBusAway-compatible read API.                     |

`Webserver` ([`Webserver.java`](../src/main/java/org/mtr/core/servlet/Webserver.java)) is a
thin wrapper around a Jetty `Server` with a `QueuedThreadPool` sized via
`Webserver.MAX_THREADS` / `MIN_THREADS` / `IDLE_TIMEOUT_MILLIS`.

[`ServletBase`](../src/main/java/org/mtr/core/servlet/ServletBase.java) handles the request
plumbing common to all map / OBA endpoints:

1. parses the JSON body (if any) into a `JsonReader`,
2. resolves the target dimension from the `dimension` query parameter (or runs across all
   dimensions when `dimensions=all`),
3. enqueues the work onto the target `Simulator` via `simulator.run(...)`, so the handler
   sees a coherent snapshot,
4. wraps the response in the standard `Response` envelope (status code, reason phrase,
   payload) and writes it asynchronously via Jetty's `WriteListener`.

`SystemMapServlet` keeps per-dimension `CachedResponse` instances for the heavier endpoints
— `STATIONS_AND_ROUTES_CACHE_MILLIS` (30 s) for the relatively static stations/routes graph
and `LIVE_DATA_CACHE_MILLIS` (3 s) for departures and live client positions.

For the full list of endpoints, parameters and responses, see [API.md](API.md).

## Operations and the message queue

When this jar is embedded in another process (the Minecraft Transit Railway mod), the host
talks to it through `Main.sendMessageC2S(worldIndex, queueObject)` and
`Main.processMessagesS2C(worldIndex, callback)` instead of HTTP. Both sides exchange
[`QueueObject`s](../src/main/java/org/mtr/core/servlet/QueueObject.java) keyed by an
operation name. The receiving side dispatches the key through
[`OperationProcessor`](../src/main/java/org/mtr/core/servlet/OperationProcessor.java), which
constructs the appropriate `*Request` from `org.mtr.core.operation` and returns a
`SerializedDataBase` reply (or `null` for fire-and-forget operations).

Operation keys (all `lower_snake_case`):

| Direction       | Keys                                                                                                                                                                                                                                                                                                                                                                 |
|-----------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Client → server | `get_data`, `update_data`, `delete_data`, `list_data`, `arrivals`, `set_time`, `update_riding_entities`, `block_rails`, `press_lift`, `nearby_stations`, `nearby_depots`, `rails`, `generate_by_depot_ids`, `generate_by_depot_name`, `generate_by_lift`, `clear_by_depot_ids`, `clear_by_depot_name`, `instant_deploy_by_depot_ids`, `instant_deploy_by_depot_name` |
| Server → client | `vehicles_lifts`, `generation_status_update`                                                                                                                                                                                                                                                                                                                         |

The queue is **not** exposed over HTTP by default — only the read-only map / OBA endpoints
are. Hosts that want to expose operations externally do so by registering additional Jetty
servlets via the `additionalWebserverSetup` `Consumer` accepted by the `Main` constructor.

## Package layout

| Package                    | Role                                                                                         |
|----------------------------|----------------------------------------------------------------------------------------------|
| `org.mtr.core`             | Entry point (`Main`), top-level constants, generated `Version`.                              |
| `org.mtr.core.data`        | Persisted simulation state — `Station`, `Platform`, `Route`, `Depot`, `Siding`, `Vehicle`, … |
| `org.mtr.core.simulation`  | The `Simulator` itself; ticks and queues.                                                    |
| `org.mtr.core.servlet`     | Jetty servlets, web-server wrapper, message queue, `OperationProcessor`.                     |
| `org.mtr.core.operation`   | DTO-style request/response classes for the message queue and `arrivals` REST endpoint.       |
| `org.mtr.core.map`         | DTOs for `/mtr/api/map/*` responses (`StationAndRoutes`, `Departures`, `Clients`, …).        |
| `org.mtr.core.oba`         | DTOs for `/oba/api/where/*` responses, plus the `OBAResponse` builder.                       |
| `org.mtr.core.integration` | Generic `Request` / `Response` envelope shared with embedded hosts.                          |
| `org.mtr.core.serializer`  | The streaming reader/writer abstraction (`ReaderBase`, `WriterBase`, `JsonReader`, …).       |
| `org.mtr.core.path`        | Path-finding for the `directions` endpoint.                                                  |
| `org.mtr.core.tool`        | Shared utilities (`Utilities`, geometry helpers, …).                                         |
| `org.mtr.core.legacy`      | Backwards-compatible loaders for old save formats.                                           |
| `org.mtr.core.generated`   | **Generated** — never edit by hand. See [SCHEMA.md](SCHEMA.md).                              |

Every package has a `package-info.java` annotated `@NullMarked` (JSpecify).

## Frontend

The `website/` folder is a standalone Angular 21 app (PrimeNG, Transloco, Three.js,
iconify-icon). It is built into the jar at packaging time as `WebserverResources` (a
generated class produced by `WebserverSetup` in `buildSrc/`) and served by `MainWebServlet`.
The `/mtr/api/map/*` endpoints are the only data source it talks to — there's no separate
backend-for-frontend.

The app is structured as standalone components under `website/src/app/component/<name>/`,
services under `website/src/app/service/`, generated entities under
`website/src/app/entity/generated/` (mirroring the Java side, see [SCHEMA.md](SCHEMA.md))
and pipes under `website/src/app/pipe/`.

## Embedding flow

```text
Minecraft Transit Railway mod
        │
        │  new Main(rootPath, port, threaded=false, …, additionalWebserverSetup, dims)
        ▼
   Transport Simulation Core (this jar)
        │
        ├─ loads each dimension's saved state from rootPath/<dim>/
        ├─ starts Jetty on `port` (or skips if port = 0)
        ├─ runs the `additionalWebserverSetup` Consumer so the host can add its own servlets
        │
   ◀── per server tick ──
        │  main.manualTick()
        │  main.sendMessageC2S(worldIndex, queueObject)
        │  main.processMessagesS2C(worldIndex, callback)
   ── on shutdown ──
        │  main.save()
        │  main.stop()
```
