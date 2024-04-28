package org.mtr.core.path;

import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.data.Route;
import org.mtr.core.operation.DirectionsResponse;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2LongAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

import javax.annotation.Nullable;
import java.util.function.Consumer;

public final class DirectionsPathFinder extends PathFinder<DirectionsPathFinder.PositionAndPlatform> {

	private final Simulator simulator;
	private final long startMillis;
	private final long maxWalkingDistance;
	private final Consumer<JsonObject> sendResponse;
	private static final int WALKING_MULTIPLIER = 1000; // milliseconds per meter

	public DirectionsPathFinder(Simulator simulator, Position startPosition, Position endPosition, long maxWalkingDistance, Consumer<JsonObject> sendResponse) {
		super(new PositionAndPlatform(startPosition, 0), new PositionAndPlatform(endPosition, 0));
		this.simulator = simulator;
		startMillis = System.currentTimeMillis();
		this.maxWalkingDistance = maxWalkingDistance;
		this.sendResponse = sendResponse;
	}

	@Override
	protected ObjectArrayList<ConnectionDetails<PositionAndPlatform>> getConnections(long elapsedTime, PositionAndPlatform data, @Nullable Long previousRouteId) {
		final ObjectArrayList<ConnectionDetails<PositionAndPlatform>> connections = new ObjectArrayList<>();
		final Platform platform = data.platformId == 0 ? null : simulator.platformIdMap.get(data.platformId);

		if (platform != null) {
			final Long2LongAVLTreeMap visitedPlatformTimes = new Long2LongAVLTreeMap();
			platform.routes.forEach(route -> route.depots.forEach(depot -> depot.savedRails.forEach(siding -> siding.getArrivals(startMillis, data.platformId, (newPlatformId, routeId, departureTime, duration) -> {
				if (departureTime > startMillis + elapsedTime && departureTime < visitedPlatformTimes.getOrDefault(newPlatformId, Long.MAX_VALUE)) {
					final Position position = simulator.platformIdToPosition.get(newPlatformId);
					if (position != null) {
						connections.add(new ConnectionDetails<>(new PositionAndPlatform(position, newPlatformId), duration, departureTime - startMillis - elapsedTime, routeId));
					}
					visitedPlatformTimes.put(newPlatformId, departureTime);
				}
			}))));
		}

		if (previousRouteId == null || previousRouteId != 0) {
			simulator.platformIdToPosition.forEach((newPlatformId, platformPosition) -> {
				final long distance = data.position.manhattanDistance(platformPosition);
				if (distance <= maxWalkingDistance) {
					connections.add(new ConnectionDetails<>(new PositionAndPlatform(platformPosition, newPlatformId), distance * WALKING_MULTIPLIER, 0, 0));
				}
			});

			final long distance = data.position.manhattanDistance(endNode.position);
			if (distance <= maxWalkingDistance) {
				connections.add(new ConnectionDetails<>(endNode, distance * WALKING_MULTIPLIER, 0, 0));
			}
		}

		return connections;
	}

	@Override
	protected long getWeightFromEndNode(PositionAndPlatform node) {
		return node.position.manhattanDistance(endNode.position);
	}

	public boolean tick() {
		final ObjectArrayList<ConnectionDetails<PositionAndPlatform>> connectionDetailsList = findPath();

		if (connectionDetailsList == null) {
			return false;
		} else if (connectionDetailsList.isEmpty()) {
			sendResponse.accept(Utilities.getJsonObjectFromData(new DirectionsResponse(startMillis)));
			return true;
		} else {
			if (!connectionDetailsList.get(0).node.equals(startNode)) {
				connectionDetailsList.add(0, new ConnectionDetails<>(startNode, 0, 0, 0));
			}

			final DirectionsResponse directionsResponse = new DirectionsResponse(startMillis);
			for (int i = 1; i < connectionDetailsList.size(); i++) {
				final ConnectionDetails<PositionAndPlatform> connectionDetails1 = connectionDetailsList.get(i - 1);
				final ConnectionDetails<PositionAndPlatform> connectionDetails2 = connectionDetailsList.get(i);
				final Platform startPlatform = simulator.platformIdMap.get(connectionDetails1.node.platformId);
				final Platform endPlatform = simulator.platformIdMap.get(connectionDetails2.node.platformId);
				final Route route = simulator.routeIdMap.get(connectionDetails2.routeId);
				directionsResponse.addSegment(
						startPlatform,
						endPlatform,
						route,
						connectionDetails2.duration,
						connectionDetails2.waitingTime
				);
			}

			sendResponse.accept(Utilities.getJsonObjectFromData(directionsResponse));
			return true;
		}
	}

	protected static final class PositionAndPlatform {

		private final Position position;
		private final long platformId;

		private PositionAndPlatform(Position position, long platformId) {
			this.position = position;
			this.platformId = platformId;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof PositionAndPlatform) {
				return platformId == ((PositionAndPlatform) obj).platformId && position.equals(((PositionAndPlatform) obj).position);
			} else {
				return super.equals(obj);
			}
		}

		@Override
		public int hashCode() {
			return (int) (position.hashCode() ^ platformId);
		}
	}
}
