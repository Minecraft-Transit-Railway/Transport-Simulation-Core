package org.mtr.core.path;

import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.data.Route;
import org.mtr.core.operation.DirectionsResponse;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.Objects;
import java.util.function.Consumer;

public final class DirectionsPathFinder extends PathFinder<DirectionsPathFinder.PositionAndPlatform> {

	private final Simulator simulator;
	private final long startMillis;
	private final Consumer<JsonObject> sendResponse;
	private static final int WALKING_MULTIPLIER = 1000; // milliseconds per meter

	public DirectionsPathFinder(Simulator simulator, Position startPosition, Position endPosition, Consumer<JsonObject> sendResponse) {
		super(new PositionAndPlatform(startPosition, 0, 0), new PositionAndPlatform(endPosition, 0, 0));
		this.simulator = simulator;
		startMillis = System.currentTimeMillis();
		this.sendResponse = sendResponse;
	}

	@Override
	protected ObjectOpenHashSet<ConnectionDetails<PositionAndPlatform>> getConnections(long elapsedTime, PositionAndPlatform data) {
		final ObjectOpenHashSet<ConnectionDetails<PositionAndPlatform>> connections = new ObjectOpenHashSet<>();
		final Platform platform = data.platformId == 0 ? null : simulator.platformIdMap.get(data.platformId);
		final LongAVLTreeSet visitedPlatformIds = new LongAVLTreeSet();

		if (platform != null) {
			platform.routes.forEach(route -> route.depots.forEach(depot -> depot.savedRails.forEach(siding -> {
				if (data.sidingId != siding.getId()) {
					siding.getArrivals(startMillis + elapsedTime, data.platformId, (newPlatformId, routeIds, departureTime, duration) -> {
						final Position position = simulator.platformIdToPosition.get(newPlatformId);
						if (position != null) {
							visitedPlatformIds.add(newPlatformId);
							final long waitingTime = departureTime - startMillis - elapsedTime;
							final long walkingTime = data.position.manhattanDistance(position) * WALKING_MULTIPLIER;
							if (walkingTime < waitingTime + duration) {
								connections.add(new ConnectionDetails<>(new PositionAndPlatform(position, newPlatformId, 0), walkingTime, 0, 0));
							} else {
								connections.add(new ConnectionDetails<>(new PositionAndPlatform(position, newPlatformId, siding.getId()), duration, waitingTime, routeIds.getLong(0)));
							}
						}
					});
				}
			})));
		}

		if (data.sidingId != 0) {
			simulator.platformIdToPosition.forEach((newPlatformId, platformPosition) -> {
				if (data.platformId != newPlatformId && !visitedPlatformIds.contains(newPlatformId.longValue())) {
					connections.add(new ConnectionDetails<>(new PositionAndPlatform(platformPosition, newPlatformId, 0), data.position.manhattanDistance(platformPosition) * WALKING_MULTIPLIER, 0, 0));
				}
			});
		}

		connections.add(new ConnectionDetails<>(endNode, data.position.manhattanDistance(endNode.position) * WALKING_MULTIPLIER, 0, 0));
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
		} else if (connectionDetailsList.size() < 2) {
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
		// A unique identifier for how we got to the current node (0 for walking)
		// Used to prevent using the same mode twice (e.g. getting off and on the same train, walking two times in a row, etc.)
		private final long sidingId;

		private PositionAndPlatform(Position position, long platformId, long sidingId) {
			this.position = position;
			this.platformId = platformId;
			this.sidingId = sidingId;
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
			return Objects.hash(position, platformId);
		}
	}
}
