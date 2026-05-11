package org.mtr.core;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import lombok.extern.log4j.Log4j2;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jspecify.annotations.Nullable;
import org.mtr.core.data.Depot;
import org.mtr.core.generated.WebserverResources;
import org.mtr.core.servlet.*;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Process entry point and embedded-mode bootstrap for Transport Simulation Core.
 *
 * <p>{@link #main(String[])} starts the simulator as a standalone server with an embedded
 * Jetty webserver. The same {@link Main#Main(Path, int, boolean, boolean, Consumer, String...)}
 * constructor is used by the Minecraft Transit Railway mod to embed the simulator in-process —
 * in that mode the mod typically passes {@code webserverPort = 0} (no HTTP), drives ticks itself
 * via {@link #manualTick()}, and bridges its own packet pipeline through
 * {@link #sendMessageC2S(Integer, QueueObject)} / {@link #processMessagesS2C(int, Consumer)}.</p>
 */
@Log4j2
public class Main {

	private final ObjectImmutableList<Simulator> simulators;
	@Nullable
	private final Webserver webserver;
	@Nullable
	private final ScheduledExecutorService scheduledExecutorService;

	/**
	 * Optional, externally-installed resolver from a Minecraft player {@link UUID} to a display
	 * name. Set by the embedding mod so {@link org.mtr.core.map.Client} payloads can carry the
	 * player's name without the simulator needing to know about Minecraft's player registry.
	 */
	@Nullable
	public static Function<UUID, String> CLIENT_NAME_RESOLVER;

	/**
	 * Wall-clock interval between simulator ticks in threaded mode, in milliseconds.
	 */
	public static final int MILLISECONDS_PER_TICK = 10;

	/**
	 * Standalone server entry point.
	 *
	 * <p>Arguments are parsed by picocli so operators get validation, helpful parse errors, and
	 * generated {@code --help} / {@code --version} output.</p>
	 */
	public static void main(String[] args) {
		final MainArguments mainArguments = new MainArguments();
		final CommandLine commandLine = new CommandLine(mainArguments);

		try {
			final CommandLine.ParseResult parseResult = commandLine.parseArgs(args);
			if (CommandLine.printHelpIfRequested(parseResult)) {
				return;
			}

			final Main main = new Main(Objects.requireNonNull(mainArguments.rootPath), mainArguments.webserverPort, mainArguments.threadedSimulation, mainArguments.threadedFileLoading, null, Objects.requireNonNull(mainArguments.dimensions));
			main.readConsoleInput();
		} catch (ParameterException e) {
			commandLine.usage(System.out);
			log.error("Failed to parse arguments: {}", e.getMessage(), e);
		} catch (Exception e) {
			commandLine.usage(System.out);
			log.error("Failed to start simulation", e);
		}
	}

	/**
	 * Construct and start the simulator. Used by both {@link #main(String[])} and by the
	 * embedding mod for in-process use.
	 *
	 * @param rootPath                 directory under which each dimension's data lives
	 * @param webserverPort            Jetty listen port; {@code 0} or negative disables the webserver
	 * @param threadedSimulation       if {@code true}, each {@link Simulator} ticks on its own scheduled thread
	 * @param threadedFileLoading      if {@code true}, file loading parallelises across dimensions
	 * @param additionalWebserverSetup optional hook letting the embedder register extra servlets before {@link Webserver#start()} is called
	 * @param dimensions               one or more dimension identifiers to load
	 */
	public Main(Path rootPath, int webserverPort, boolean threadedSimulation, boolean threadedFileLoading, @Nullable Consumer<Webserver> additionalWebserverSetup, String... dimensions) {
		final ObjectArrayList<Simulator> tempSimulators = new ObjectArrayList<>();

		log.info("Loading files...");
		for (final String dimension : dimensions) {
			tempSimulators.add(new Simulator(dimension, dimensions, rootPath, threadedFileLoading));
		}

		simulators = new ObjectImmutableList<>(tempSimulators);

		if (webserverPort > 0) {
			webserver = new Webserver(webserverPort);
			webserver.addServlet(new ServletHolder(new MainWebServlet(WebserverResources::get, "/")), "/");
			webserver.addServlet(new ServletHolder(new SystemMapServlet(simulators)), "/mtr/api/map/*");
			webserver.addServlet(new ServletHolder(new OBAServlet(simulators)), "/oba/api/where/*");
			if (additionalWebserverSetup != null) {
				additionalWebserverSetup.accept(webserver);
			}
			webserver.start();
		} else {
			webserver = null;
		}

		if (threadedSimulation) {
			scheduledExecutorService = Executors.newScheduledThreadPool(simulators.size());
			simulators.forEach(simulator -> scheduledExecutorService.scheduleAtFixedRate(simulator::tick, 0, MILLISECONDS_PER_TICK, TimeUnit.MILLISECONDS));
		} else {
			scheduledExecutorService = null;
		}

		log.info("Server started with dimensions {}", Arrays.toString(dimensions));
	}

	/**
	 * Tick every simulator exactly once on the calling thread. Used by the embedding mod when
	 * the host process drives simulation pacing itself (Minecraft server tick).
	 */
	public void manualTick() {
		simulators.forEach(Simulator::tick);
	}

	/**
	 * Forward a client-to-server message into one or all simulators.
	 *
	 * @param worldIndex  0-based dimension index, or {@code null} to broadcast to every simulator
	 * @param queueObject opaque message payload, dispatched onto the target simulator's thread
	 */
	public void sendMessageC2S(@Nullable Integer worldIndex, QueueObject queueObject) {
		if (worldIndex == null) {
			simulators.forEach(simulator -> simulator.sendMessageC2S(queueObject));
		} else if (worldIndex >= 0 && worldIndex < simulators.size()) {
			simulators.get(worldIndex).sendMessageC2S(queueObject);
		}
	}

	/**
	 * Drain pending server-to-client messages from one simulator and feed them to {@code callback}.
	 *
	 * @param worldIndex 0-based dimension index; out-of-range values are silently ignored
	 * @param callback   invoked once per drained message
	 */
	public void processMessagesS2C(int worldIndex, Consumer<QueueObject> callback) {
		if (worldIndex >= 0 && worldIndex < simulators.size()) {
			simulators.get(worldIndex).processMessagesS2C(callback);
		}
	}

	/**
	 * Persist the current state of every simulator to disk.
	 */
	public void save() {
		simulators.forEach(Simulator::save);
	}

	/**
	 * Stop the webserver, the tick scheduler, and every simulator (each with a final save).
	 * Safe to call from the embedding process during shutdown.
	 */
	public void stop() {
		log.info("Stopping...");

		if (webserver != null) {
			webserver.stop();
		}

		if (scheduledExecutorService != null) {
			scheduledExecutorService.shutdown();
			Utilities.awaitTermination(scheduledExecutorService);
		}

		log.info("Starting full save...");
		simulators.forEach(Simulator::stop);
		log.info("Stopped");
	}

	private void readConsoleInput() {
		// Wrap System.in once so we are not allocating a fresh BufferedReader per loop iteration.
		final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
		while (true) {
			try {
				final String line = reader.readLine();
				if (line == null) {
					// Stdin closed (e.g. nohup'd background process) — keep ticking, no commands available.
					stop();
					return;
				}
				final String[] input = line.trim().toLowerCase(Locale.ENGLISH).replaceAll("[^a-z ]", "").split(" ");
				switch (input[0]) {
					case "exit", "stop", "quit" -> {
						stop();
						return;
					}
					case "save", "save-all" -> save();
					case "generate", "regenerate" -> {
						final StringBuilder generateKey = new StringBuilder();
						for (int i = 1; i < input.length; i++) {
							generateKey.append(input[i]).append(" ");
						}
						simulators.forEach(simulator -> Depot.generateDepotsByName(simulator, generateKey.toString()));
					}
					default -> log.info("Unknown command \"{}\"", input[0]);
				}
			} catch (Exception e) {
				log.error("Failed to read console input", e);
				stop();
				return;
			}
		}
	}

	@Command(name = "Transport Simulation Core", mixinStandardHelpOptions = true, version = Version.VERSION, description = "Starts one simulator per dimension and optionally exposes the web dashboard and APIs.")
	private static final class MainArguments {

		@Option(names = {"-r", "--root-path"}, required = true, paramLabel = "<path>", description = "Directory containing per-dimension save folders")
		@Nullable
		private Path rootPath;

		@Option(names = {"-p", "--webserver-port"}, defaultValue = "8888", paramLabel = "<port>", description = "Jetty listen port; use 0 to disable the webserver (default: ${DEFAULT-VALUE})")
		private int webserverPort = 8888;

		@Option(names = "--threaded-simulation", negatable = true, defaultValue = "true", description = "Tick each dimension on a scheduled thread (default: ${DEFAULT-VALUE})")
		private boolean threadedSimulation = true;

		@Option(names = "--threaded-file-loading", negatable = true, defaultValue = "true", description = "Load dimension data in parallel at startup (default: ${DEFAULT-VALUE})")
		private boolean threadedFileLoading = true;

		@Parameters(arity = "1..*", paramLabel = "<dimension>", description = "One or more dimension identifiers to load")
		@Nullable
		private String[] dimensions;
	}

	/**
	 * Trivial concrete subclass of {@link WebServlet} bound to the dashboard's static resources.
	 * Exists only so the {@link ServletHolder} above has a concrete type to instantiate.
	 */
	private static class MainWebServlet extends WebServlet {

		MainWebServlet(Function<String, String> contentProvider, String expectedPath) {
			super(contentProvider, expectedPath);
		}
	}
}
