package org.mtr.core.directions;

import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.map.DirectionsRequest;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;

public final class DirectionsFinder {

	private final Graph graph;
	private final Arrivals arrivals;
	private final ConnectionScanAlgorithmProcessor connectionScanAlgorithmProcessor;

	public static final long MAX_WALKING_DISTANCE = 500;
	public static final float WALKING_SPEED = 4F / Utilities.MILLIS_PER_SECOND; // 4 m/s

	public DirectionsFinder(Simulator simulator) {
		graph = new Graph(simulator);
		arrivals = new Arrivals(simulator);
		connectionScanAlgorithmProcessor = new ConnectionScanAlgorithmProcessor(graph, arrivals, simulator);
	}

	public void tick() {
		if (!connectionScanAlgorithmProcessor.directionsRequests.isEmpty()) {
			if (graph.tick()) {
				return;
			}

			if (arrivals.tick()) {
				return;
			}
		}

		connectionScanAlgorithmProcessor.tick();
	}

	public void addRequest(DirectionsRequest directionsRequest) {
		connectionScanAlgorithmProcessor.directionsRequests.add(directionsRequest);
	}

	/**
	 * Iterates through the route backwards from {@code startIndex}.
	 *
	 * @param route      the route to iterate
	 * @param startIndex the index to begin backwards iteration
	 * @param callback   a callback with the departure offsets (not arrival)
	 */
	public static void processRoute(Route route, int startIndex, RouteOffsetAndPlatformsCallback callback) {
		if (!route.getHidden() && route.durations.size() == route.getRoutePlatforms().size() - 1) {
			long totalOffset = route.getRoutePlatforms().get(startIndex).platform.getDwellTime();
			for (int i = startIndex - 1; i >= 0; i--) {
				final Platform platform1 = route.getRoutePlatforms().get(i).platform;
				final Platform platform2 = route.getRoutePlatforms().get(i + 1).platform;
				final long duration = route.durations.getLong(i);
				totalOffset += duration;
				callback.accept(totalOffset, duration, platform1, platform2);
				totalOffset += platform1.getDwellTime();
			}
		}
	}

	@FunctionalInterface
	public interface RouteOffsetAndPlatformsCallback {
		void accept(long offsetTimeFromLastDeparture, long duration, Platform platform1, Platform platform2);
	}
}
