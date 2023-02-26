package org.mtr.core;

import org.mtr.core.simulation.Simulator;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

public class Main {

	public static Logger LOGGER = Logger.getLogger("Transport-Simulation-Core");

	public static void main(String[] args) {
		if (args.length <= 1) {
			LOGGER.warning("Not enough arguments!");
			printHelp();
			return;
		}

		try {
			int i = 0;
			final int millisPerGameHour = Integer.parseInt(args[i++]);
			final float currentGameDayPercentage = Float.parseFloat(args[i++]);
			final Path rootPath = Paths.get(args[i++]);

			for (int j = i; j < args.length; j++) {
				final String dimension = args[j];
				final Simulator simulator = new Simulator(dimension, rootPath);
			}
		} catch (Exception ignored) {
			printHelp();
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
		LOGGER.info("java -jar Transport-Simulation-Core.jar <millisPerGameHour> <currentGameDayPercentage> <rootPath> <dimensions...>");
	}
}
