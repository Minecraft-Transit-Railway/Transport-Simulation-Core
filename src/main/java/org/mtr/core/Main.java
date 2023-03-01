package org.mtr.core;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.core.simulation.Simulator;

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
		if (args.length <= 1) {
			LOGGER.warning("Not enough arguments!");
			printHelp();
			return;
		}

		final ObjectArrayList<Simulator> simulators = new ObjectArrayList<>();

		try {
			int i = 0;
			final int millisPerGameDay = Integer.parseInt(args[i++]);
			final float startingGameDayPercentage = Float.parseFloat(args[i++]);
			final Path rootPath = Paths.get(args[i++]);

			for (int j = i; j < args.length; j++) {
				final String dimension = args[j];
				simulators.add(new Simulator(dimension, rootPath, millisPerGameDay, startingGameDayPercentage));
			}
		} catch (Exception ignored) {
			printHelp();
		}

		final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(simulators.size());
		simulators.forEach(simulator -> scheduledExecutorService.scheduleAtFixedRate(simulator::tick, 0, 2, TimeUnit.MILLISECONDS));

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
		LOGGER.info("java -jar Transport-Simulation-Core.jar <millisPerGameDay> <currentGameDayPercentage> <rootPath> <dimensions...>");
	}
}
