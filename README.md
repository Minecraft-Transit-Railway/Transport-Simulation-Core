# Transport Simulation Core

Transport Simulation Core is a standalone Java 21 backend that simulates transport networks (stations, routes, depots, vehicles, lifts, paths) across one or more dimensions. It can run as a normal service with an embedded webserver, or be embedded in another Java process and driven via an in-process message queue.

At runtime it serves:

- a bundled dashboard at `/`
- system map APIs at `/mtr/api/map/*`
- OneBusAway-compatible read APIs at `/oba/api/where/*`

## Documentation

- Build and development: [`docs/BUILD.md`](docs/BUILD.md)
- Running and CLI arguments: [`docs/RUNNING.md`](docs/RUNNING.md)
- HTTP endpoints: [`docs/API.md`](docs/API.md)
- Architecture and internals: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- Schema generation flow: [`docs/SCHEMA.md`](docs/SCHEMA.md)
- Code conventions: [`docs/CODE_STYLES.md`](docs/CODE_STYLES.md)

## How the simulator works (high level)

1. `Main` starts one `Simulator` per configured dimension.
2. Each simulator loads saved data from disk into an in-memory model (stations, routes, depots, rails, vehicles, etc.).
3. The simulation advances in ticks (default every 10 ms in threaded mode), updating vehicle/state progression.
4. External work is queued onto the simulator thread (`simulator.run(...)`) to keep state mutation single-threaded and consistent.
5. HTTP requests are parsed by servlet handlers and dispatched to the target simulator; responses are returned as JSON envelopes.
6. On shutdown or manual save, each simulator persists current state back to disk.

For the full process layout and component responsibilities, see [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Quick start

1. Build the jar (see [`docs/BUILD.md`](docs/BUILD.md)).
2. Run it with a root data path, webserver port, threading flags, and one or more dimensions (see [`docs/RUNNING.md`](docs/RUNNING.md)).
3. Open the dashboard at `http://localhost:<port>/`.
