package org.mtr.core.directions;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.core.data.PassengerDirection;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.data.Station;
import org.mtr.core.map.DirectionsConnection;
import org.mtr.core.map.DirectionsRequest;
import org.mtr.core.map.DirectionsResponse;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.RefreshableObject;

import javax.annotation.Nullable;

public final class ConnectionScanAlgorithmProcessor extends RefreshableObject<Object> {

	private Request[] requests = new Request[0];

	public final ObjectArrayList<DirectionsRequest> directionsRequests = new ObjectArrayList<>();
	private final Graph graph;
	private final Arrivals arrivals;
	private final Simulator simulator;

	private static final long START_PLATFORM_ID = -1;
	private static final long END_PLATFORM_ID = -2;

	public ConnectionScanAlgorithmProcessor(Graph graph, Arrivals arrivals, Simulator simulator) {
		super(new ObjectArrayList<>(), 0);
		this.graph = graph;
		this.arrivals = arrivals;
		this.simulator = simulator;
	}

	@Nullable
	@Override
	public Object refresh(int currentRefreshStep) {
		final int index1 = currentRefreshStep - 1;
		final int index2 = index1 - arrivals.getData().size();

		if (currentRefreshStep == 0) {
			final int directionsRequestCount = directionsRequests.size();

			if (directionsRequestCount == 0) {
				return 0;
			} else {
				// Map requests to array
				requests = new Request[directionsRequestCount];
				final long millis = System.currentTimeMillis();
				for (int i = 0; i < directionsRequestCount; i++) {
					final DirectionsRequest directionsRequest = directionsRequests.get(i);
					requests[i] = new Request(
							directionsRequest.getStartPosition(simulator),
							directionsRequest.getEndPosition(simulator),
							Math.max(millis, directionsRequest.getStartTime()),
							new Long2ObjectOpenHashMap<>(),
							new Long2LongOpenHashMap(),
							directionsRequest.callback1,
							directionsRequest.callback2
					);
				}
				directionsRequests.clear();

				// Generate caches
				simulator.platforms.forEach(endPlatform -> {
					for (final Request request : requests) {
						// Walking from the start position to platforms
						final Position endPosition = endPlatform.getMidPosition();
						final long distanceToStart = request.startPosition().manhattanDistance(endPosition);
						if (distanceToStart <= DirectionsFinder.MAX_WALKING_DISTANCE) {
							final long endTime = request.startTime() + Math.round(distanceToStart / DirectionsFinder.WALKING_SPEED);
							request.earliestConnections().put(endPlatform.getId(), new Connection(
									null,
									START_PLATFORM_ID, endPlatform.getId(),
									request.startTime(), endTime,
									distanceToStart
							));
							addIndependentConnectionsBFS(endPlatform.getId(), request.earliestConnections());
						}

						// Cache distances of platforms to the end position
						final long distanceToEnd = request.endPosition().manhattanDistance(endPosition);
						if (distanceToEnd <= DirectionsFinder.MAX_WALKING_DISTANCE) {
							request.walkingDistancesToEnd().put(endPlatform.getId(), distanceToEnd);
						}
					}
				});

				return null;
			}
		} else if (index1 < arrivals.getData().size()) {
			// Process connections in order
			arrivals.getData().get(index1).forEach(connection -> {
				for (final Request request : requests) {
					if (addConnection(connection, request.earliestConnections())) {
						addIndependentConnectionsBFS(connection.endPlatformId(), request.earliestConnections());
					}
				}
			});

			return null;
		} else if (index2 < requests.length) {
			final Request request = requests[index2];
			final DirectionsResponse directionsResponse = request.callback1() == null ? null : new DirectionsResponse(
					graph.getTotalRefreshTime(),
					arrivals.getTotalRefreshTime(),
					getTotalRefreshTime(),
					graph.getLongestRefreshTime(),
					arrivals.getLongestRefreshTime(),
					getLongestRefreshTime()
			);
			final ObjectArrayList<PassengerDirection> passengerDirections = request.callback2() == null ? null : new ObjectArrayList<>();
			Connection current = getEndConnection(request.earliestConnections(), request.walkingDistancesToEnd());

			while (current != null) {
				final Platform startPlatform = current.startPlatformId() == START_PLATFORM_ID ? null : simulator.platformIdMap.get(current.startPlatformId());
				final Station startStation = startPlatform == null ? null : startPlatform.area;
				final Platform endPlatform = current.endPlatformId() == END_PLATFORM_ID ? null : simulator.platformIdMap.get(current.endPlatformId());
				final Station endStation = endPlatform == null ? null : endPlatform.area;

				if (directionsResponse != null) {
					directionsResponse.getDirectionsConnections().add(0, new DirectionsConnection(
							current.route() == null ? "" : current.route().getHexId(),
							startStation == null ? "" : startStation.getHexId(), endStation == null ? "" : endStation.getHexId(),
							startPlatform == null ? "" : startPlatform.getName(), endPlatform == null ? "" : endPlatform.getName(),
							current.startTime(), current.endTime(),
							current.walkingDistance()
					));
				}

				if (passengerDirections != null) {
					passengerDirections.add(0, new PassengerDirection(
							current.route() == null ? 0 : current.route().getId(),
							startPlatform == null ? 0 : startPlatform.getId(),
							endPlatform == null ? 0 : endPlatform.getId(),
							current.startTime(), current.endTime()
					));
				}

				current = request.earliestConnections().get(current.startPlatformId());
			}

			if (request.callback1() != null) {
				request.callback1().accept(directionsResponse);
			}

			if (request.callback2() != null) {
				request.callback2().accept(passengerDirections);
			}

			if (index2 == requests.length - 1) {
				return 0;
			} else {
				return null;
			}
		} else {
			// This shouldn't be reached, but it's here just in case
			return 0;
		}
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
			final Long2ObjectOpenHashMap<IndependentConnection> independentConnectionsForPlatformId = graph.getData().get(startPlatformId);

			if (independentConnectionsForPlatformId != null) {
				final Connection startConnection = earliestConnections.get(startPlatformId);
				independentConnectionsForPlatformId.forEach((endPlatformId, independentConnection) -> {
					if (addConnection(new Connection(
							independentConnection.route(),
							startPlatformId, endPlatformId,
							startConnection.endTime(), startConnection.endTime() + independentConnection.duration(),
							independentConnection.walkingDistance()
					), earliestConnections)) {
						queue.add(endPlatformId);
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
			final long startTime = entry.getValue().startTime();
			final long distance = walkingDistancesToEnd.getOrDefault(platformId, DirectionsFinder.MAX_WALKING_DISTANCE + 1);
			if (distance <= DirectionsFinder.MAX_WALKING_DISTANCE) {
				final long endTime = startTime + Math.round(distance / DirectionsFinder.WALKING_SPEED);
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
		final Connection startConnection = earliestConnections.get(connection.startPlatformId());
		final Connection endConnection = earliestConnections.get(connection.endPlatformId());

		if (startConnection != null && startConnection.endTime() <= connection.startTime() && (endConnection == null || connection.endTime() < endConnection.endTime()) && (startConnection.route() != null || connection.route() != null)) {
			earliestConnections.put(connection.endPlatformId(), connection);
			return true;
		} else {
			return false;
		}
	}
}
