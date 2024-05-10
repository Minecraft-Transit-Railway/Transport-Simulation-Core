package org.mtr.legacy.data;

import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.SignalModification;
import org.mtr.core.data.TransportMode;
import org.mtr.core.simulation.FileLoader;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArraySet;

import java.nio.file.Path;
import java.util.UUID;

public final class LegacyRailLoader {

	public static void load(Path savePath, ObjectArraySet<Rail> rails) {
		final ObjectArraySet<LegacyRailNode> legacyRailNodes = new ObjectArraySet<>();
		final ObjectArraySet<LegacySignalBlock> legacySignalBlocks = new ObjectArraySet<>();
		new FileLoader<>(legacyRailNodes, LegacyRailNode::new, savePath, "rails");
		new FileLoader<>(legacySignalBlocks, LegacySignalBlock::new, savePath, "signal-blocks");

		final Object2ObjectOpenHashMap<UUID, DataFixer.RailType> railCache = new Object2ObjectOpenHashMap<>();

		legacyRailNodes.forEach(legacyRailNode -> {
			final Position startPosition = legacyRailNode.getStartPosition();
			final long startPositionLong = legacyRailNode.getStartPositionLong();
			legacyRailNode.iterateConnections(railNodeConnection -> {
				final DataFixer.RailType railType = railNodeConnection.getRailType();
				final Position endPosition = railNodeConnection.getEndPosition();
				final long endPositionLong = railNodeConnection.getEndPositionLong();
				final Angle startAngle = railNodeConnection.getStartAngle();
				final Angle endAngle = railNodeConnection.getEndAngle();
				final TransportMode transportMode = railNodeConnection.getTransportMode();
				final String modelKey = railNodeConnection.getModelKey();
				final ObjectArrayList<String> styles;
				if (modelKey.isEmpty()) {
					styles = transportMode == TransportMode.BOAT ? new ObjectArrayList<>() : ObjectArrayList.of("default");
				} else if (modelKey.equals("null")) {
					styles = new ObjectArrayList<>();
				} else {
					styles = ObjectArrayList.of(String.format("%s_%s", modelKey, railNodeConnection.getIsSecondaryDirection() ? 1 : 2));
				}
				final double verticalRadius = railNodeConnection.getVerticalRadius();
				final UUID uuid = getUuid(startPositionLong, endPositionLong);
				final DataFixer.RailType oldRailType = railCache.get(uuid);

				if (oldRailType != null) {
					final Rail rail;
					switch (railType) {
						case PLATFORM:
							rail = Rail.newPlatformRail(startPosition, startAngle, endPosition, endAngle, verticalRadius == 0 ? Rail.Shape.QUADRATIC : Rail.Shape.TWO_RADII, Math.max(verticalRadius, 0), styles, transportMode);
							break;
						case SIDING:
							rail = Rail.newSidingRail(startPosition, startAngle, endPosition, endAngle, verticalRadius == 0 ? Rail.Shape.QUADRATIC : Rail.Shape.TWO_RADII, Math.max(verticalRadius, 0), styles, transportMode);
							break;
						case TURN_BACK:
							rail = Rail.newTurnBackRail(startPosition, startAngle, endPosition, endAngle, verticalRadius == 0 ? Rail.Shape.QUADRATIC : Rail.Shape.TWO_RADII, Math.max(verticalRadius, 0), styles, transportMode);
							break;
						default:
							final Rail.Shape shape = railType == DataFixer.RailType.CABLE_CAR || oldRailType == DataFixer.RailType.CABLE_CAR ? Rail.Shape.CABLE : verticalRadius == 0 ? Rail.Shape.QUADRATIC : Rail.Shape.TWO_RADII;
							rail = Rail.newRail(
									startPosition, startAngle,
									endPosition, endAngle,
									shape, Math.max(verticalRadius, 0), styles, railType.speedLimitKilometersPerHour, oldRailType.speedLimitKilometersPerHour,
									false, false, true, railType == DataFixer.RailType.RUNWAY, true, transportMode
							);
							break;
					}

					final SignalModification signalModification = new SignalModification(startPosition, endPosition, false);
					legacySignalBlocks.forEach(legacySignalBlock -> {
						if (legacySignalBlock.isRail(startPosition, endPosition)) {
							signalModification.putColorToAdd(legacySignalBlock.getColor());
						}
					});
					rail.applyModification(signalModification);
					rails.add(rail);
				} else {
					railCache.put(uuid, railType);
				}
			});
		});
	}

	private static UUID getUuid(long value1, long value2) {
		return value1 > value2 ? new UUID(value1, value2) : new UUID(value2, value1);
	}
}
