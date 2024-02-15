package org.mtr.core;

import org.eclipse.jetty.servlet.ServletHolder;
import org.mtr.core.servlet.*;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;

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
import java.util.logging.Level;
import java.util.logging.Logger;

@ParametersAreNonnullByDefault
public class Main {

	private final ObjectImmutableList<Simulator> simulators;
	private final Webserver webserver;
	private final ScheduledExecutorService scheduledExecutorService;

	public static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
	public static final int MILLISECONDS_PER_TICK = 10;

	public static void main(String[] args) {
		try {
			int i = 0;
			final Path rootPath = Paths.get(args[i++]);
			final int webserverPort = Integer.parseInt(args[i++]);
			final int clientWebserverPort = Integer.parseInt(args[i++]);
			final String[] dimensions = new String[args.length - i];
			System.arraycopy(args, i, dimensions, 0, dimensions.length);
			final Main main = new Main(rootPath, webserverPort, clientWebserverPort, dimensions);
			main.readConsoleInput();
		} catch (Exception e) {
			printHelp();
			logException(e);
		}
	}

	public Main(Path rootPath, int webserverPort, int clientWebserverPort, String... dimensions) {
		final ObjectArrayList<Simulator> tempSimulators = new ObjectArrayList<>();

		LOGGER.info("Loading files...");
		for (final String dimension : dimensions) {
			tempSimulators.add(new Simulator(dimension, rootPath, clientWebserverPort));
		}

		simulators = new ObjectImmutableList<>(tempSimulators);
		webserver = new Webserver(webserverPort);
		webserver.addServlet(new ServletHolder(new WebServlet()), "/");
		webserver.addServlet(new ServletHolder(new OperationServlet(simulators)), "/mtr/api/operation/*");
		webserver.addServlet(new ServletHolder(new SystemMapServlet(simulators)), "/mtr/api/map/stations-and-routes");
		webserver.addServlet(new ServletHolder(new OBAServlet(simulators)), "/oba/api/where/*");
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
		LOGGER.info("java -jar Transport-Simulation-Core.jar <rootPath> <webserverPort> <dimensions...>");
	}
}
