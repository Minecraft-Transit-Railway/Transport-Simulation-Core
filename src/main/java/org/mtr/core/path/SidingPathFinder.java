package org.mtr.core.path;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.mtr.core.data.*;
import org.mtr.core.tools.Angle;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

public class SidingPathFinder<T extends AreaBase<T, U>, U extends SavedRailBase<U, T>, V extends AreaBase<V, W>, W extends SavedRailBase<W, V>> extends PathFinder<SidingPathFinder.PositionAndAngle, PathData> {

	public final U startSavedRail;
	public final W endSavedRail;
	public final int stopIndex;
	private final Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> positionsToRail;

	public SidingPathFinder(Data data, U startSavedRail, W endSavedRail, int stopIndex) {
		super(new PositionAndAngle(startSavedRail.getRandomPosition(), null), new PositionAndAngle(endSavedRail.getRandomPosition(), null));
		positionsToRail = data.positionsToRail;
		this.startSavedRail = startSavedRail;
		this.endSavedRail = endSavedRail;
		this.stopIndex = stopIndex;
	}

	@Override
	public ObjectArrayList<PathData> tick() {
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
					return new ObjectArrayList<>();
				}
				if (i == connectionDetailsList.size() - 1) {
					path.add(new PathData(rail, endSavedRail.getId(), endSavedRail instanceof Platform ? ((Platform) endSavedRail).getDwellTime() : 1, stopIndex + 1, position1, position2));
				} else if (rail.canTurnBack() && connectionDetailsList.get(i + 1).node.position.equals(position1)) {
					path.add(new PathData(rail, 0, 1, stopIndex, position1, position2));
				} else {
					path.add(new PathData(rail, 0, 0, stopIndex, position1, position2));
				}
			}

			return path;
		}
	}

	@Override
	protected ObjectOpenHashSet<ConnectionDetails<PositionAndAngle>> getConnections(PositionAndAngle node) {
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

		return connections;
	}

	@Override
	protected long getWeightFromEndNode(PositionAndAngle node) {
		return node.position.distManhattan(endNode.position);
	}

	public static <T extends AreaBase<T, U>, U extends SavedRailBase<U, T>, V extends AreaBase<V, W>, W extends SavedRailBase<W, V>> void findPathTick(ObjectArrayList<PathData> path, List<SidingPathFinder<T, U, V, W>> sidingPathFinders, Runnable callbackSuccess, Runnable callbackFail) {
		if (!sidingPathFinders.isEmpty()) {
			final long startMillis = System.currentTimeMillis();
			while (System.currentTimeMillis() - startMillis < 5) {
				final SidingPathFinder<T, U, V, W> sidingPathFinder = sidingPathFinders.get(0);
				final ObjectArrayList<PathData> tempPath = sidingPathFinder.tick();

				if (tempPath != null) {
					if (tempPath.size() < 2) {
						sidingPathFinders.clear();
						path.clear();
						callbackFail.run();
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

	private static <T extends AreaBase<T, U>, U extends SavedRailBase<U, T>> void padConnectionDetailsList(ObjectArrayList<ConnectionDetails<PositionAndAngle>> connectionDetailsList, SavedRailBase<U, T> savedRail, boolean isEnd) {
		if (!savedRail.containsPos(Utilities.getElement(connectionDetailsList, isEnd ? -2 : 1).node.position)) {
			connectionDetailsList.add(isEnd ? connectionDetailsList.size() : 0, new ConnectionDetails<>(new PositionAndAngle(savedRail.getOtherPosition(Utilities.getElement(connectionDetailsList, isEnd ? -1 : 0).node.position), null), 0, 0, 0));
		}
	}

	protected static class PositionAndAngle {

		private final Position position;
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
