package org.mtr.core.path;

import org.mtr.core.data.*;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Utilities;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.BiConsumer;

public final class SidingPathFinder<T extends AreaBase<T, U>, U extends SavedRailBase<U, T>, V extends AreaBase<V, W>, W extends SavedRailBase<W, V>> extends PathFinder<SidingPathFinder.PositionAndAngle> {

	public final U startSavedRail;
	public final W endSavedRail;
	public final int stopIndex;
	private final TransportMode transportMode;
	private final Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> positionsToRail;
	private final Object2ObjectOpenHashMap<Position, Rail> runwaysInbound;
	private final ObjectOpenHashSet<Position> runwaysOutbound;

	public static final int AIRPLANE_SPEED = 900;
	private static final int MAX_AIRPLANE_TURN_ARC = 128;

	public SidingPathFinder(Data data, U startSavedRail, W endSavedRail, int stopIndex) {
		super(new PositionAndAngle(startSavedRail.getRandomPosition(), null), new PositionAndAngle(endSavedRail.getRandomPosition(), null));
		transportMode = startSavedRail.getTransportMode();
		positionsToRail = data.positionsToRail;
		runwaysInbound = data.runwaysInbound;
		runwaysOutbound = data.runwaysOutbound;
		this.startSavedRail = startSavedRail;
		this.endSavedRail = endSavedRail;
		this.stopIndex = stopIndex;
	}

	@Override
	protected ObjectOpenHashSet<ConnectionDetails<PositionAndAngle>> getConnections(long elapsedTime, PositionAndAngle node) {
		final ObjectOpenHashSet<ConnectionDetails<PositionAndAngle>> connections = new ObjectOpenHashSet<>();
		final Object2ObjectOpenHashMap<Position, Rail> railConnections = positionsToRail.get(node.position);

		if (railConnections != null) {
			railConnections.forEach((position, rail) -> {
				final double speedLimit = rail.getSpeedLimitMetersPerMillisecond(node.position);
				if (speedLimit > 0 && (node.angle == null || node.angle == rail.getStartAngle(node.position) || rail.canTurnBack())) {
					connections.add(new ConnectionDetails<>(new PositionAndAngle(position, rail.getStartAngle(position).getOpposite()), Math.round(rail.railMath.getLength() / speedLimit), 0, 0));
				}
			});
		}

		if (transportMode == TransportMode.AIRPLANE && runwaysOutbound.contains(node.position)) {
			runwaysInbound.forEach((position, rail) -> connections.add(new ConnectionDetails<>(new PositionAndAngle(position, rail.getStartAngle(position)), 1, 0, 0)));
		}

		return connections;
	}

	@Override
	protected long getWeightFromEndNode(PositionAndAngle node) {
		return node.position.manhattanDistance(endNode.position);
	}

	@Nullable
	private ObjectArrayList<PathData> tick(long cruisingAltitude) {
		final ObjectArrayList<ConnectionDetails<PositionAndAngle>> connectionDetailsList = findPath();

		if (connectionDetailsList == null) {
			return null;
		} else if (connectionDetailsList.size() < 2) {
			return new ObjectArrayList<>();
		} else {
			padConnectionDetailsList(connectionDetailsList, startSavedRail, false);
			padConnectionDetailsList(connectionDetailsList, endSavedRail, true);

			final ObjectArrayList<PathData> path = new ObjectArrayList<>();
			for (int i = 1; i < connectionDetailsList.size(); i++) {
				final Position position1 = connectionDetailsList.get(i - 1).node.position;
				final Position position2 = connectionDetailsList.get(i).node.position;
				final Rail rail = Data.tryGet(positionsToRail, position1, position2);

				if (rail == null) {
					final Angle angle1 = connectionDetailsList.get(i - 1).node.angle;
					final Angle angle2 = connectionDetailsList.get(i).node.angle;

					if (transportMode == TransportMode.AIRPLANE && angle1 != null && angle2 != null) {
						final long heightDifference1 = cruisingAltitude - position1.getY();
						final long heightDifference2 = cruisingAltitude - position2.getY();
						final Position cruisingPosition1 = position1.offset(Math.round(angle1.cos * Math.abs(heightDifference1) * 4), heightDifference1, Math.round(angle1.sin * Math.abs(heightDifference1) * 4));
						final Position cruisingPosition4 = position2.offset(Math.round(-angle2.cos * Math.abs(heightDifference2) * 4), heightDifference2, Math.round(-angle2.sin * Math.abs(heightDifference2) * 4));
						final long turnArc = Math.min(MAX_AIRPLANE_TURN_ARC, cruisingPosition1.manhattanDistance(cruisingPosition4) / 8);

						path.add(getAirplanePathData(position1, angle1, cruisingPosition1, angle1.getOpposite(), stopIndex));

						final Angle expectedAngle = Angle.fromAngle((float) Math.toDegrees(Math.atan2(cruisingPosition4.getZ() - cruisingPosition1.getZ(), cruisingPosition4.getX() - cruisingPosition1.getX())));
						final Position cruisingPosition2 = addAirplanePath(angle1, cruisingPosition1, expectedAngle, turnArc, path, stopIndex, false);
						final ObjectArrayList<PathData> tempRailData = new ObjectArrayList<>();
						final Position cruisingPosition3 = addAirplanePath(angle2.getOpposite(), cruisingPosition4, expectedAngle.getOpposite(), turnArc, tempRailData, stopIndex, true);

						path.add(getAirplanePathData(cruisingPosition2, expectedAngle, cruisingPosition3, expectedAngle.getOpposite(), stopIndex));
						path.addAll(tempRailData);

						path.add(getAirplanePathData(cruisingPosition4, angle2, position2, angle2.getOpposite(), stopIndex));
					} else {
						return new ObjectArrayList<>();
					}
				} else {
					if (i == connectionDetailsList.size() - 1) {
						path.add(new PathData(rail, endSavedRail.getId(), endSavedRail instanceof Platform ? ((Platform) endSavedRail).getDwellTime() : 1, stopIndex + 1, position1, position2));
					} else if (rail.canTurnBack() && connectionDetailsList.get(i + 1).node.position.equals(position1)) {
						path.add(new PathData(rail, 0, 1, stopIndex, position1, position2));
					} else {
						path.add(new PathData(rail, 0, 0, stopIndex, position1, position2));
					}
				}
			}

			return path;
		}
	}

	public static <T extends AreaBase<T, U>, U extends SavedRailBase<U, T>, V extends AreaBase<V, W>, W extends SavedRailBase<W, V>> void findPathTick(ObjectArrayList<PathData> path, ObjectArrayList<SidingPathFinder<T, U, V, W>> sidingPathFinders, long cruisingAltitude, Runnable callbackSuccess, BiConsumer<U, W> callbackFail) {
		if (!sidingPathFinders.isEmpty()) {
			final long startMillis = System.currentTimeMillis();
			while (System.currentTimeMillis() - startMillis < 5) {
				final SidingPathFinder<T, U, V, W> sidingPathFinder = sidingPathFinders.get(0);
				final ObjectArrayList<PathData> tempPath = sidingPathFinder.tick(cruisingAltitude);

				if (tempPath != null) {
					if (tempPath.size() < 2) {
						sidingPathFinders.clear();
						path.clear();
						callbackFail.accept(sidingPathFinder.startSavedRail, sidingPathFinder.endSavedRail);
						return;
					} else {
						if (overlappingPaths(path, tempPath)) {
							tempPath.remove(0);
						}
						path.addAll(tempPath);
						sidingPathFinders.remove(0);
						if (sidingPathFinders.isEmpty()) {
							callbackSuccess.run();
							return;
						}
					}
				}
			}
		}
	}

	public static void generatePathDataDistances(ObjectArrayList<PathData> path, double initialDistance) {
		final ObjectArrayList<PathData> tempPath = new ObjectArrayList<>(path);
		double endDistance = initialDistance;
		path.clear();

		for (final PathData oldPathData : tempPath) {
			final double startDistance = endDistance;
			endDistance += oldPathData.getRailLength();
			path.add(new PathData(oldPathData, startDistance, endDistance));
		}
	}

	public static boolean overlappingPaths(ObjectArrayList<PathData> path, ObjectArrayList<PathData> newPath) {
		if (path.isEmpty() || newPath.isEmpty()) {
			return false;
		} else {
			return Utilities.getElement(path, -1).isSameRail(newPath.get(0));
		}
	}

	private <X extends AreaBase<X, Y>, Y extends SavedRailBase<Y, X>> void padConnectionDetailsList(ObjectArrayList<ConnectionDetails<PositionAndAngle>> connectionDetailsList, SavedRailBase<Y, X> savedRail, boolean isEnd) {
		final Position lastPosition = Utilities.getElement(connectionDetailsList, isEnd ? -1 : 0).node.position;
		if (!savedRail.containsPos(lastPosition)) {
			positionsToRail.get(lastPosition).keySet().stream().filter(savedRail::containsPos).findFirst().ifPresent(newPosition -> {
				connectionDetailsList.add(isEnd ? connectionDetailsList.size() : 0, new ConnectionDetails<>(new PositionAndAngle(newPosition, null), 0, 0, 0));
				connectionDetailsList.add(isEnd ? connectionDetailsList.size() : 0, new ConnectionDetails<>(new PositionAndAngle(savedRail.getOtherPosition(newPosition), null), 0, 0, 0));
			});
		} else if (!savedRail.containsPos(Utilities.getElement(connectionDetailsList, isEnd ? -2 : 1).node.position)) {
			connectionDetailsList.add(isEnd ? connectionDetailsList.size() : 0, new ConnectionDetails<>(new PositionAndAngle(savedRail.getOtherPosition(lastPosition), null), 0, 0, 0));
		}
	}

	private static Position addAirplanePath(Angle startAngle, Position startPos, Angle expectedAngle, long turnArc, ObjectArrayList<PathData> tempRailPath, int stopIndex, boolean reverse) {
		final Angle angleDifference = expectedAngle.sub(startAngle);
		final boolean turnRight = angleDifference.angleRadians > 0;
		Angle tempAngle = startAngle;
		Position tempPos = startPos;

		for (int i = 0; i < Angle.values().length; i++) {
			if (tempAngle == expectedAngle) {
				break;
			}

			final Angle oldTempAngle = tempAngle;
			final Position oldTempPos = tempPos;
			final Angle rotateAngle = turnRight ? Angle.SEE : Angle.NEE;
			tempAngle = tempAngle.add(rotateAngle);
			final Vector posOffset = new Vector(turnArc, 0, 0).rotateY(-oldTempAngle.angleRadians - rotateAngle.angleRadians / 2);
			tempPos = oldTempPos.offset(Math.round(posOffset.x), Math.round(posOffset.y), Math.round(posOffset.z));

			if (reverse) {
				tempRailPath.add(0, getAirplanePathData(tempPos, tempAngle.getOpposite(), oldTempPos, oldTempAngle, stopIndex));
			} else {
				tempRailPath.add(getAirplanePathData(oldTempPos, oldTempAngle, tempPos, tempAngle.getOpposite(), stopIndex));
			}
		}

		return tempPos;
	}

	private static PathData getAirplanePathData(Position position1, Angle angle1, Position position2, Angle angle2, int stopIndex) {
		return new PathData(Rail.newRail(
				position1, angle1, position2, angle2,
				Rail.Shape.QUADRATIC, 0, "", AIRPLANE_SPEED, 0,
				false, false, true, false, false, TransportMode.AIRPLANE
		), 0, 0, stopIndex, position1, position2);
	}

	protected static class PositionAndAngle {

		private final Position position;
		@Nullable
		private final Angle angle;

		private PositionAndAngle(Position position, @Nullable Angle angle) {
			this.position = position;
			this.angle = angle;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof PositionAndAngle) {
				return position.equals(((PositionAndAngle) obj).position) && (angle == null || ((PositionAndAngle) obj).angle == null || angle == ((PositionAndAngle) obj).angle);
			} else {
				return super.equals(obj);
			}
		}

		@Override
		public int hashCode() {
			return Objects.hash(position, angle);
		}
	}
}
