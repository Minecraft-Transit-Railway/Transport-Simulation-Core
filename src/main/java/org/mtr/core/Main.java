package org.mtr.core;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.core.generated.WebserverResources;
import org.mtr.core.servlet.IntegrationServlet;
import org.mtr.core.servlet.OBAServlet;
import org.mtr.core.servlet.SocketHandler;
import org.mtr.core.servlet.SystemMapServlet;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.Utilities;
import org.mtr.webserver.Webserver;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@ParametersAreNonnullByDefault
public class Main {

	private final ObjectImmutableList<Simulator> simulators;
	private final Webserver webserver;
	private final ScheduledExecutorService scheduledExecutorService;

	public static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
	public static final long START_MILLIS = System.currentTimeMillis();
	public static final int MILLISECONDS_PER_TICK = 10;

	public static void main(String[] args) {
		try {
			int i = 0;
			final int millisPerGameDay = Integer.parseInt(args[i++]);
			final float startingGameDayPercentage = Float.parseFloat(args[i++]);
			final Path rootPath = Paths.get(args[i++]);
			final int webserverPort = Integer.parseInt(args[i++]);
			final String[] dimensions = new String[args.length - i];
			System.arraycopy(args, i, dimensions, 0, dimensions.length);
			final Main main = new Main(millisPerGameDay, startingGameDayPercentage, rootPath, webserverPort, dimensions);
			main.readConsoleInput();
		} catch (Exception e) {
			printHelp();
			logException(e);
		}
	}

	public Main(int millisPerGameDay, float startingGameDayPercentage, Path rootPath, int webserverPort, String... dimensions) {
		final ObjectArrayList<Simulator> tempSimulators = new ObjectArrayList<>();

		LOGGER.info("Loading files...");
		for (final String dimension : dimensions) {
			tempSimulators.add(new Simulator(dimension, rootPath, millisPerGameDay, startingGameDayPercentage));
		}

		simulators = new ObjectImmutableList<>(tempSimulators);
		webserver = new Webserver(Main.class, WebserverResources::get, Utilities.clamp(webserverPort, 1025, 65535), StandardCharsets.UTF_8, jsonObject -> 0);
		new IntegrationServlet(webserver, "/mtr/api/data/*", simulators);
		new SystemMapServlet(webserver, "/mtr/api/map/*", simulators);
		new OBAServlet(webserver, "/oba/api/where/*", simulators);
		SocketHandler.register(webserver, simulators);
		webserver.start();
		scheduledExecutorService = Executors.newScheduledThreadPool(simulators.size());
		simulators.forEach(simulator -> scheduledExecutorService.scheduleAtFixedRate(simulator::tick, 0, MILLISECONDS_PER_TICK, TimeUnit.MILLISECONDS));
		LOGGER.info("Server started with dimensions " + Arrays.toString(dimensions));
	}

	public void save() {
		LOGGER.info("Starting quick save...");
		simulators.forEach(Simulator::save);
	}

	public void stop() {
		LOGGER.info("Stopping...");
		webserver.stop();
		scheduledExecutorService.shutdown();
		Utilities.awaitTermination(scheduledExecutorService);
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
						simulators.forEach(simulator -> simulator.generatePath(generateKey.toString()));
						break;
					default:
						LOGGER.info(String.format("Unknown command \"%s\"", input[0]));
						break;
				}
			} catch (Exception e) {
				logException(e);
				stop();
				return;
			}
		}
	}

	public static void logException(Exception e) {
		LOGGER.log(Level.INFO, e.getMessage(), e);
	}

	private static void printHelp() {
		LOGGER.info("Usage:");
		LOGGER.info("java -jar Transport-Simulation-Core.jar <millisPerGameDay> <currentGameDayPercentage> <rootPath> <webserverPort> <dimensions...>");
	}
}
