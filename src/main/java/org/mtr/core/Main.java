package org.mtr.core;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.core.servlet.Webserver;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.Utilities;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class Main {

	public static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
	public static final long START_MILLIS = System.currentTimeMillis();

	public static void main(String[] args) {
		try {
			int i = 0;
			final int millisPerGameDay = Integer.parseInt(args[i++]);
			final float startingGameDayPercentage = Float.parseFloat(args[i++]);
			final Path rootPath = Paths.get(args[i++]);
			final int webserverPort = Integer.parseInt(args[i++]);
			final ObjectArrayList<Simulator> simulators = new ObjectArrayList<>();

			LOGGER.info("Loading files...");
			while (i < args.length) {
				simulators.add(new Simulator(args[i++], rootPath, millisPerGameDay, startingGameDayPercentage));
			}

			start(new ObjectImmutableList<>(simulators), webserverPort);
		} catch (Exception e) {
			printHelp();
			e.printStackTrace();
		}
	}

	private static void start(ObjectImmutableList<Simulator> simulators, int webserverPort) {
		final Webserver webserver = new Webserver(Utilities.clamp(webserverPort, 1025, 65535), simulators);
		final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(simulators.size());
		simulators.forEach(simulator -> scheduledExecutorService.scheduleAtFixedRate(simulator::tick, 0, 2, TimeUnit.MILLISECONDS));

		while (true) {
			try {
				final String input = new BufferedReader(new InputStreamReader(System.in)).readLine().trim().toLowerCase(Locale.ENGLISH).replaceAll("[^a-z]", "");
				switch (input) {
					case "exit":
					case "stop":
					case "quit":
						LOGGER.info("Stopping...");
						stop(webserver, scheduledExecutorService);
						LOGGER.info("Starting full save...");
						simulators.forEach(Simulator::stop);
						return;
					case "save":
					case "save-all":
						LOGGER.info("Starting quick save...");
						simulators.forEach(Simulator::save);
						break;
					default:
						LOGGER.info(String.format("Unknown command \"%s\"", input));
						break;
				}
			} catch (Exception e) {
				e.printStackTrace();
				stop(webserver, scheduledExecutorService);
				return;
			}
		}
	}

	private static void stop(Webserver webserver, ScheduledExecutorService scheduledExecutorService) {
		webserver.stop();
		scheduledExecutorService.shutdown();
		Utilities.awaitTermination(scheduledExecutorService);
	}

	private static void printHelp() {
		LOGGER.info("Usage:");
		LOGGER.info("java -jar Transport-Simulation-Core.jar <millisPerGameDay> <currentGameDayPercentage> <rootPath> <webserverPort> <dimensions...>");
	}
}
