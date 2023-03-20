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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class Main {

	public static final Logger LOGGER = Logger.getLogger("Transport-Simulation-Core");
	public static final long START_MILLIS = System.currentTimeMillis();

	public static void main(String[] args) {
		final ObjectArrayList<Simulator> simulators = new ObjectArrayList<>();
		int webserverPort = 0;

		try {
			int i = 0;
			final int millisPerGameDay = Integer.parseInt(args[i++]);
			final float startingGameDayPercentage = Float.parseFloat(args[i++]);
			final Path rootPath = Paths.get(args[i++]);
			webserverPort = Integer.parseInt(args[i++]);

			while (i < args.length) {
				simulators.add(new Simulator(args[i++], rootPath, millisPerGameDay, startingGameDayPercentage));
			}
		} catch (Exception e) {
			printHelp();
			e.printStackTrace();
		}

		final Webserver webserver = new Webserver(Utilities.clamp(webserverPort, 1025, 65535), new ObjectImmutableList<>(simulators));
		final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(simulators.size());
		simulators.forEach(simulator -> scheduledExecutorService.scheduleAtFixedRate(simulator::tick, 0, 2, TimeUnit.MILLISECONDS));

		while (true) {
			String input = null;
			try {
				input = new BufferedReader(new InputStreamReader(System.in)).readLine().trim();
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (input == null || input.equalsIgnoreCase("stop")) {
				webserver.stop();
				simulators.forEach(Simulator::stop);
				scheduledExecutorService.shutdown();
				break;
			} else {
				LOGGER.info(String.format("Unknown command \"%s\"", input));
			}
		}
		// Endpoints:
		// get all data by position and radius
		// get all station names and colours
		// get data information by id
		// post data updates by id
		// - update and create if not exist
		// - update and ignore if not exist
		// - delete
	}

	private static void printHelp() {
		LOGGER.info("Usage:");
		LOGGER.info("java -jar Transport-Simulation-Core.jar <millisPerGameDay> <currentGameDayPercentage> <rootPath> <webserverPort> <dimensions...>");
	}
}
