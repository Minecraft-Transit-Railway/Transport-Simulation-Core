# Running

> How to run the packaged jar. For build steps see [BUILD.md](BUILD.md); for the HTTP
> surface see [API.md](API.md).

## Standalone jar

The fat jar produced by `./gradlew build` (`build/libs/Transport-Simulation-Core-*.jar`) is
runnable directly. The CLI is parsed by picocli and supports named options:

```text
java -jar Transport-Simulation-Core.jar --root-path <path> [--webserver-port <port>] [--[no-]threaded-simulation] [--[no-]threaded-file-loading] <dimensions...>
```

| Argument / option              | Description                                                                                                                                                               |
|--------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `--root-path`, `-r`            | Filesystem directory containing per-dimension save folders. Each `<dimension>` argument is resolved relative to this path.                                                |
| `--webserver-port`, `-p`       | TCP port for the embedded Jetty server. Default is `8888`. **Pass `0`** to disable the webserver entirely (handy when embedding from another process).                    |
| `--[no-]threaded-simulation`   | Enabled by default. Disable with `--no-threaded-simulation` when the caller drives `Main.manualTick()`.                                                                   |
| `--[no-]threaded-file-loading` | Enabled by default. Disable with `--no-threaded-file-loading` to load sequentially (less memory pressure on small machines).                                              |
| `dimensions...`                | One or more dimension names. Each becomes a `Simulator` and a `<rootPath>/<name>/` subdirectory. The order defines the integer index used by the `dimension` query param. |

### Example

```bash
java -jar Transport-Simulation-Core-1.0.0.jar \
    --root-path /var/lib/transport-simulation \
    --webserver-port 8888 \
    --threaded-simulation \
    --threaded-file-loading \
    overworld nether end
```

This starts the server on port 8888, ticks each of the three dimensions every 10 ms on its
own thread, and exposes:

- the dashboard at <http://localhost:8888/>,
- map data at <http://localhost:8888/mtr/api/map/stations-and-routes?dimension=0> (and
  `dimension=1`, `dimension=2`),
- OBA at <http://localhost:8888/oba/api/where/...>.

To print generated help / version text:

```text
java -jar Transport-Simulation-Core.jar --help
java -jar Transport-Simulation-Core.jar --version
```

If required arguments are missing or unparseable, picocli prints usage automatically and the process logs the parse error.

## Console commands

While the standalone server is running, it reads commands from stdin
([`Main.readConsoleInput`](../src/main/java/org/mtr/core/Main.java)). Input is lower-cased
and split on whitespace; only `[a-z ]` characters are kept.

| Command                                | Effect                                                                                                                                       |
|----------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| `stop`, `exit`, `quit`                 | Stop the webserver, shut down the tick scheduler, run a full save, then exit cleanly.                                                        |
| `save`, `save-all`                     | Persist every dimension's state to disk. The server keeps running.                                                                           |
| `generate <name>`, `regenerate <name>` | Run `Depot.generateDepotsByName(simulator, name)` against every dimension — regenerates routes for the depot(s) whose name matches `<name>`. |
| anything else                          | Logged as `Unknown command "..."`.                                                                                                           |

Any uncaught exception in the input loop logs the stack trace, runs `stop()` and exits.

## Logging

Logging is via Log4j2; the configuration ships in
[`src/main/resources/log4j2.properties`](../src/main/resources/log4j2.properties). The root
logger writes to stdout — wrap the process in `systemd`, `nohup` or your supervisor of
choice to capture logs.

The shared logger is `Main.LOGGER` (category `TransportSimulationCore`).

## Embedded usage

When this jar is embedded in another Java process (the canonical case is the Minecraft
Transit Railway mod), construct `Main` directly instead of using `main(String[])`:

```java
final Main main = new Main(
    rootPath,
    webserverPort,                   // 0 to disable
    /* threadedSimulation = */ false,
    /* threadedFileLoading = */ true,
    webserver -> webserver.addServlet(myCustomServletHolder, "/my/path/*"),
    "overworld", "nether", "end");

// per host tick:
main.manualTick();

// per host outbound message:
main.sendMessageC2S(worldIndex, queueObject);

// per host poll:
main.processMessagesS2C(worldIndex, queueObject -> { ... });

// on host shutdown:
main.stop();
```

The `additionalWebserverSetup` `Consumer<Webserver>` is invoked after the built-in servlets
are registered and before Jetty starts, so embedding hosts can layer their own routes on top
of the public `/mtr/api/map/*` and `/oba/api/where/*` endpoints. Set
`Main.CLIENT_NAME_RESOLVER` if you want the `clients` map endpoint to populate display
names.
