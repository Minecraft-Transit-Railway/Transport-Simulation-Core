package org.mtr.core.path;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.data.Route;
import org.mtr.core.data.Station;
import org.mtr.core.map.DirectionsConnection;
import org.mtr.core.map.DirectionsRequest;
import org.mtr.core.map.DirectionsResponse;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

public final class DirectionsFinder {

	private long graphExpiry = 0;
	private long arrivalsExpiry = 0;

	private int maxWalkingDistance;
	private final Long2ObjectOpenHashMap<Long2ObjectOpenHashMap<IndependentConnection>> independentConnections = new Long2ObjectOpenHashMap<>();
	private final ObjectArrayList<Connection> routeConnections = new ObjectArrayList<>();

	private final Simulator simulator;
	private static final float WALKING_SPEED = 4F / Utilities.MILLIS_PER_SECOND; // 4 m/s
	private static final long START_PLATFORM_ID = -1;
	private static final long END_PLATFORM_ID = -2;
	private static final long GRAPH_TIMEOUT = 30000;
	private static final long ARRIVALS_TIMEOUT = 5000;

	public DirectionsFinder(Simulator simulator) {
		this.simulator = simulator;
	}

	public void refreshGraph(int maxWalkingDistance) {
		final long millis = System.currentTimeMillis();
		if (millis > graphExpiry) {
			graphExpiry = millis + GRAPH_TIMEOUT;
			independentConnections.clear();
			this.maxWalkingDistance = maxWalkingDistance;

			// Grid cell size: choose half of maxWalkingDistance for efficient neighborhood search
			final double gridSize = maxWalkingDistance / 2D;

			// Build a spatial grid
			final Long2ObjectOpenHashMap<ObjectArrayList<Platform>> grid = new Long2ObjectOpenHashMap<>();
			simulator.platforms.forEach(platform -> {
				final Position platformMidPosition = platform.getMidPosition();
				final long gridKey = createGridKey(platformMidPosition.getX(), platformMidPosition.getZ(), gridSize);
				grid.computeIfAbsent(gridKey, key -> new ObjectArrayList<>()).add(platform);
			});

			// For each platform, connect to nearby platforms within maxWalkingDistance
			simulator.platforms.forEach(platform -> {
				final Position platformMidPosition = platform.getMidPosition();
				final long platformId = platform.getId();

				final int gridX = (int) Math.floor(platformMidPosition.getX() / gridSize);
				final int gridZ = (int) Math.floor(platformMidPosition.getZ() / gridSize);

				// Search surrounding 3×3 cells
				for (int x = -1; x <= 1; x++) {
					for (int z = -1; z <= 1; z++) {
						final long gridKey = createGridKey((gridX + x) * gridSize, (gridZ + z) * gridSize, gridSize);
						final ObjectArrayList<Platform> cell = grid.get(gridKey);
						if (cell == null) {
							continue;
						}

						for (final Platform walkingPlatform : cell) {
							if (walkingPlatform.getId() == platformId) {
								continue;
							}

							final long distance = getDistance(platformMidPosition, walkingPlatform.getMidPosition());
							if (distance <= maxWalkingDistance) {
								independentConnections.computeIfAbsent(platformId, key -> new Long2ObjectOpenHashMap<>()).put(walkingPlatform.getId(), new IndependentConnection(
										null,
										platformId, walkingPlatform.getId(),
										Math.round(distance / WALKING_SPEED), distance
								));
							}
						}
					}
				}
			});

			simulator.routes.forEach(route -> {
				if (route.getTransportMode().continuousMovement && !route.getHidden()) {
					processRoute(
							route,
							route.getRoutePlatforms().size() - 1,
							(offsetTimeFromLastDeparture, duration, platform1, platform2) -> independentConnections.computeIfAbsent(platform1.getId(), key -> new Long2ObjectOpenHashMap<>()).put(platform2.getId(), new IndependentConnection(
									route,
									platform1.getId(), platform2.getId(),
									duration, 0
							))
					);
				}
			});
		}
	}

	public void refreshArrivals() {
		final long millis = System.currentTimeMillis();
		if (millis > arrivalsExpiry) {
			arrivalsExpiry = millis + ARRIVALS_TIMEOUT;
			routeConnections.clear();
			final Long2ObjectOpenHashMap<ObjectObjectImmutablePair<Route, LongArrayList>> tempDepartures = new Long2ObjectOpenHashMap<>();
			simulator.sidings.forEach(siding -> siding.getDeparturesForDirections(millis, tempDepartures));

			tempDepartures.values().forEach(departuresForRoute -> processRoute(departuresForRoute.left(), departuresForRoute.left().getRoutePlatforms().size() - 1, (offsetTimeFromLastDeparture, duration, platform1, platform2) -> {
				for (final long departureForRoute : departuresForRoute.right()) {
					final long vehicleArrival1 = departureForRoute - offsetTimeFromLastDeparture;
					final long vehicleArrival2 = vehicleArrival1 + duration;

					if (vehicleArrival1 >= millis) {
						routeConnections.add(new Connection(
								departuresForRoute.left(),
								platform1.getId(), platform2.getId(),
								vehicleArrival1, vehicleArrival2,
								0
						));
					}
				}
			}));

			// Sort by the start time of each connection
			routeConnections.sort(Comparator.comparingLong(Connection::startTime));
		}
	}

	public ObjectArrayList<DirectionsResponse> earliestArrivalCSA(ObjectArrayList<DirectionsRequest> directionsRequests) {
		final int directionsRequestCount = directionsRequests.size();
		final Request[] requests = new Request[directionsRequestCount];
		final long millis = System.currentTimeMillis();
		for (int i = 0; i < directionsRequestCount; i++) {
			final DirectionsRequest directionsRequest = directionsRequests.get(i);
			requests[i] = new Request(directionsRequest.getStartPosition(simulator), directionsRequest.getEndPosition(simulator), Math.max(millis, directionsRequest.getStartTime()), new Long2ObjectOpenHashMap<>(), new Long2LongOpenHashMap());
		}

		simulator.platforms.forEach(endPlatform -> {
			for (int i = 0; i < directionsRequestCount; i++) {
				final Request request = requests[i];

				// Walking from the start position to platforms
				final Position endPosition = endPlatform.getMidPosition();
				final long distanceToStart = getDistance(request.startPosition, endPosition);
				if (distanceToStart <= maxWalkingDistance) {
					final long endTime = request.startTime + Math.round(distanceToStart / WALKING_SPEED);
					request.earliestConnections.put(endPlatform.getId(), new Connection(
							null,
							START_PLATFORM_ID, endPlatform.getId(),
							request.startTime, endTime,
							distanceToStart
					));
					addIndependentConnectionsBFS(endPlatform.getId(), request.earliestConnections);
				}

				// Cache distances of platforms to the end position
				final long distanceToEnd = getDistance(request.endPosition, endPosition);
				if (distanceToEnd <= maxWalkingDistance) {
					request.walkingDistancesToEnd.put(endPlatform.getId(), distanceToEnd);
				}
			}
		});

		// Process connections in order
		routeConnections.forEach(independentConnection -> {
			for (final Request request : requests) {
				if (addConnection(independentConnection, request.earliestConnections)) {
					addIndependentConnectionsBFS(independentConnection.endPlatformId, request.earliestConnections);
				}
			}
		});

		return Arrays.stream(requests).map(request -> {
			final DirectionsResponse directionsResponse = new DirectionsResponse();
			Connection current = getEndConnection(request.earliestConnections, request.walkingDistancesToEnd);

			while (current != null) {
				final Platform startPlatform = current.startPlatformId == START_PLATFORM_ID ? null : simulator.platformIdMap.get(current.startPlatformId);
				final Station startStation = startPlatform == null ? null : startPlatform.area;
				final Platform endPlatform = current.endPlatformId == END_PLATFORM_ID ? null : simulator.platformIdMap.get(current.endPlatformId);
				final Station endStation = endPlatform == null ? null : endPlatform.area;
				directionsResponse.getDirectionsConnections().add(0, new DirectionsConnection(
						current.route == null ? "" : current.route.getHexId(),
						startStation == null ? "" : startStation.getHexId(), endStation == null ? "" : endStation.getHexId(),
						startPlatform == null ? "" : startPlatform.getName(), endPlatform == null ? "" : endPlatform.getName(),
						current.startTime, current.endTime,
						current.walkingDistance
				));
				current = request.earliestConnections.get(current.startPlatformId);
			}

			return directionsResponse;
		}).collect(Collectors.toCollection(ObjectArrayList::new));
	}

	/**
	 * If a connection has been added, perform iterative walking relaxation (BFS-style) starting from the last platform.
	 */
	private void addIndependentConnectionsBFS(long lastPlatformId, Long2ObjectOpenHashMap<Connection> earliestConnections) {
		final LongArrayList queue = new LongArrayList();
		queue.add(lastPlatformId);
		int index = 0;

		while (index < queue.size()) {
			final long startPlatformId = queue.getLong(index++);
			final Long2ObjectOpenHashMap<IndependentConnection> independentConnectionsForPlatformId = independentConnections.get(startPlatformId);

			if (independentConnectionsForPlatformId != null) {
				final Connection startConnection = earliestConnections.get(startPlatformId);
				independentConnectionsForPlatformId.forEach((endPlatformId, independentConnection) -> {
					if (addConnection(new Connection(
							independentConnection.route,
							startPlatformId, endPlatformId,
							startConnection.endTime, startConnection.endTime + independentConnection.duration,
							independentConnection.walkingDistance
					), earliestConnections)) {
						queue.add(endPlatformId.longValue());
					}
				});
			}
		}
	}

	@Nullable
	private Connection getEndConnection(Long2ObjectOpenHashMap<Connection> earliestConnections, Long2LongOpenHashMap walkingDistancesToEnd) {
		long bestPlatformId = 0;
		long bestStartTime = 0;
		long bestEndTime = Long.MAX_VALUE;
		long bestWalkingDistance = 0;

		for (final Long2ObjectMap.Entry<Connection> entry : earliestConnections.long2ObjectEntrySet()) {
			final long platformId = entry.getLongKey();
			final long startTime = entry.getValue().startTime;
			final long distance = walkingDistancesToEnd.getOrDefault(platformId, maxWalkingDistance + 1);
			if (distance <= maxWalkingDistance) {
				final long endTime = startTime + Math.round(distance / WALKING_SPEED);
				if (endTime < bestEndTime) {
					bestPlatformId = platformId;
					bestStartTime = startTime;
					bestEndTime = endTime;
					bestWalkingDistance = distance;
				}
			}
		}

		if (bestEndTime == Long.MAX_VALUE) {
			return null;
		} else {
			return new Connection(
					null,
					bestPlatformId, END_PLATFORM_ID,
					bestStartTime, bestEndTime,
					bestWalkingDistance
			);
		}
	}

	private static boolean addConnection(Connection connection, Long2ObjectOpenHashMap<Connection> earliestConnections) {
		final Connection startConnection = earliestConnections.get(connection.startPlatformId);
		final Connection endConnection = earliestConnections.get(connection.endPlatformId);

		if (startConnection != null && startConnection.endTime <= connection.startTime && (endConnection == null || connection.endTime < endConnection.endTime) && (startConnection.route != null || connection.route != null)) {
			earliestConnections.put(connection.endPlatformId, connection);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Iterates through the route backwards from {@code startIndex}.
	 *
	 * @param route      the route to iterate
	 * @param startIndex the index to begin backwards iteration
	 * @param callback   a callback with the departure offsets (not arrival)
	 */
	private static void processRoute(Route route, int startIndex, RouteOffsetAndPlatformsCallback callback) {
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

	private static long createGridKey(double x, double z, double gridSize) {
		final long gridX = (long) Math.floor(x / gridSize);
		final long gridZ = (long) Math.floor(z / gridSize);
		return (gridX << 32) | (gridZ & 0XFFFFFFFFL);
	}

	private static long getDistance(Position pos1, Position pos2) {
		final double dx = pos1.getX() - pos2.getX();
		final double dy = pos1.getY() - pos2.getY();
		final double dz = pos1.getZ() - pos2.getZ();
		return Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
	}

	private record Connection(@Nullable Route route, long startPlatformId, long endPlatformId, long startTime, long endTime, long walkingDistance) {
	}

	private record Request(Position startPosition, Position endPosition, long startTime, Long2ObjectOpenHashMap<Connection> earliestConnections, Long2LongOpenHashMap walkingDistancesToEnd) {
	}

	private record IndependentConnection(@Nullable Route route, long startPlatformId, long endPlatformId, long duration, long walkingDistance) {
	}

	@FunctionalInterface
	private interface RouteOffsetAndPlatformsCallback {
		void accept(long offsetTimeFromLastDeparture, long duration, Platform platform1, Platform platform2);
	}
}
