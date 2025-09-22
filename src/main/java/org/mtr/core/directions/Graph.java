package org.mtr.core.directions;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.RefreshableObject;

import javax.annotation.Nullable;

public final class Graph extends RefreshableObject<Long2ObjectOpenHashMap<Long2ObjectOpenHashMap<IndependentConnection>>> {

	private final Long2ObjectOpenHashMap<Long2ObjectOpenHashMap<IndependentConnection>> independentConnections = new Long2ObjectOpenHashMap<>();
	private final Simulator simulator;

	public Graph(Simulator simulator) {
		super(new Long2ObjectOpenHashMap<>(), 30000);
		this.simulator = simulator;
	}

	@Nullable
	@Override
	public Long2ObjectOpenHashMap<Long2ObjectOpenHashMap<IndependentConnection>> refresh(int currentRefreshStep) {
		if (currentRefreshStep == 0) {
			// Grid cell size: choose half of maxWalkingDistance for efficient neighborhood search
			final double gridSize = DirectionsFinder.MAX_WALKING_DISTANCE / 2D;

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

							final long distance = platformMidPosition.manhattanDistance(walkingPlatform.getMidPosition());
							if (distance <= DirectionsFinder.MAX_WALKING_DISTANCE) {
								independentConnections.computeIfAbsent(platformId, key -> new Long2ObjectOpenHashMap<>()).put(walkingPlatform.getId(), new IndependentConnection(
										null,
										platformId, walkingPlatform.getId(),
										Math.round(distance / DirectionsFinder.WALKING_SPEED), distance
								));
							}
						}
					}
				}
			});

			return null;
		} else {
			simulator.routes.forEach(route -> {
				if (route.getTransportMode().continuousMovement && !route.getHidden()) {
					DirectionsFinder.processRoute(
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

			return independentConnections;
		}
	}

	private static long createGridKey(double x, double z, double gridSize) {
		final long gridX = (long) Math.floor(x / gridSize);
		final long gridZ = (long) Math.floor(z / gridSize);
		return (gridX << 32) | (gridZ & 0XFFFFFFFFL);
	}
}
