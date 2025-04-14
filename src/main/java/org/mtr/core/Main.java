package org.mtr.core;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mtr.core.data.Depot;
import org.mtr.core.generated.WebserverResources;
import org.mtr.core.servlet.*;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.org.eclipse.jetty.servlet.ServletHolder;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

@ParametersAreNonnullByDefault
public class Main {

	private final ObjectImmutableList<Simulator> simulators;
	@Nullable
	private final Webserver webserver;
	@Nullable
	private final ScheduledExecutorService scheduledExecutorService;

	public static final Logger LOGGER = LogManager.getLogger("TransportSimulationCore");
	public static final int MILLISECONDS_PER_TICK = 10;

	public static void main(String[] args) {
		try {
			int i = 0;
			final Path rootPath = Paths.get(args[i++]);
			final int webserverPort = Integer.parseInt(args[i++]);
			final boolean threadedSimulation = Boolean.parseBoolean(args[i++]);
			final boolean threadedFileLoading = Boolean.parseBoolean(args[i++]);
			final String[] dimensions = new String[args.length - i];
			System.arraycopy(args, i, dimensions, 0, dimensions.length);
			final Main main = new Main(rootPath, webserverPort, threadedSimulation, threadedFileLoading, null, dimensions);
			main.readConsoleInput();
		} catch (Exception e) {
			printHelp();
			LOGGER.error("", e);
		}
	}

	public Main(Path rootPath, int webserverPort, boolean threadedSimulation, boolean threadedFileLoading, @Nullable Consumer<Webserver> additionalWebserverSetup, String... dimensions) {
		final ObjectArrayList<Simulator> tempSimulators = new ObjectArrayList<>();

		LOGGER.info("Loading files...");
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

		LOGGER.info("Server started with dimensions {}", Arrays.toString(dimensions));
	}

	public void manualTick() {
		simulators.forEach(Simulator::tick);
	}

	public void sendMessageC2S(@Nullable Integer worldIndex, QueueObject queueObject) {
		if (worldIndex == null) {
			simulators.forEach(simulator -> simulator.sendMessageC2S(queueObject));
		} else if (worldIndex >= 0 && worldIndex < simulators.size()) {
			simulators.get(worldIndex).sendMessageC2S(queueObject);
		}
	}

	public void processMessagesS2C(int worldIndex, Consumer<QueueObject> callback) {
		if (worldIndex >= 0 && worldIndex < simulators.size()) {
			simulators.get(worldIndex).processMessagesS2C(callback);
		}
	}

	public void save() {
		simulators.forEach(Simulator::save);
	}

	public void stop() {
		LOGGER.info("Stopping...");

		if (webserver != null) {
			webserver.stop();
		}

		if (scheduledExecutorService != null) {
			scheduledExecutorService.shutdown();
			Utilities.awaitTermination(scheduledExecutorService);
		}

		LOGGER.info("Starting full save...");
		simulators.forEach(Simulator::stop);
		LOGGER.info("Stopped");
	}

	private void readConsoleInput() {
		while (true) {
			try {
				final String[] input = new BufferedReader(new InputStreamReader(System.in)).readLine().trim().toLowerCase(Locale.ENGLISH).replaceAll("[^a-z ]", "").split(" ");
				switch (input[0]) {
					case "exit":
					case "stop":
					case "quit":
						stop();
						return;
					case "save":
					case "save-all":
						save();
						break;
					case "generate":
					case "regenerate":
						final StringBuilder generateKey = new StringBuilder();
						for (int i = 1; i < input.length; i++) {
							generateKey.append(input[i]).append(" ");
						}
						simulators.forEach(simulator -> Depot.generateDepotsByName(simulator, generateKey.toString()));
						break;
					default:
						LOGGER.info("Unknown command \"{}\"", input[0]);
						break;
				}
			} catch (Exception e) {
				LOGGER.error("", e);
				stop();
				return;
			}
		}
	}

	private static void printHelp() {
		LOGGER.info("Usage:");
		LOGGER.info("java -jar Transport-Simulation-Core.jar <rootPath> <webserverPort> <useThreadedSimulation> <useThreadedFileLoading> <dimensions...>");
	}

	private static class MainWebServlet extends WebServlet {

		public MainWebServlet(Function<String, String> contentProvider, String expectedPath) {
			super(contentProvider, expectedPath);
		}
	}
}
