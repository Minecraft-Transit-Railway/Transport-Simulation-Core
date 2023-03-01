package org.mtr.core.path;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.core.data.*;
import org.mtr.core.tools.Angle;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;

import java.util.List;
import java.util.function.Consumer;

public class SidingPathFinder<T extends AreaBase<T, U>, U extends SavedRailBase<U, T>, V extends AreaBase<V, W>, W extends SavedRailBase<W, V>> extends PathFinder<SidingPathFinder.PositionAndAngle, PathData> {

	public final U startSavedRail;
	public final W endSavedRail;
	public final int stopIndex;
	private final Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> rails;

	public SidingPathFinder(Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> rails, U startSavedRail, W endSavedRail, int stopIndex) {
		super(new PositionAndAngle(startSavedRail.getAnyPos(), null), new PositionAndAngle(endSavedRail.getAnyPos(), null));
		this.rails = rails;
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
			final boolean skipFirst = startSavedRail.containsPos(Utilities.getElement(connectionDetailsList, 1).node.position);
			final boolean skipLast = endSavedRail.containsPos(Utilities.getElement(connectionDetailsList, -2).node.position);
			final ObjectArrayList<PathData> path = new ObjectArrayList<>();

			for (int i = skipFirst ? 1 : 0; i < connectionDetailsList.size() - (skipLast ? 2 : 1); i++) {
				final Position position1 = connectionDetailsList.get(i).node.position;
				final Position position2 = connectionDetailsList.get(i + 1).node.position;
				final Rail rail = DataCache.tryGet(rails, position1, position2);
				if (rail == null) {
					return new ObjectArrayList<>();
				}
				path.add(new PathData(rail, 0, 0, position1, position2, stopIndex));
			}

			return path;
		}
	}

	@Override
	protected ObjectAVLTreeSet<ConnectionDetails<PositionAndAngle>> getConnections(ConnectionDetails<PositionAndAngle> data) {
		final ObjectAVLTreeSet<ConnectionDetails<PositionAndAngle>> connections = new ObjectAVLTreeSet<>();
		final Object2ObjectOpenHashMap<Position, Rail> railConnections = rails.get(data.node.position);

		if (railConnections != null) {
			railConnections.forEach((position, rail) -> {
				if (data.node.angle == null || data.node.angle == rail.facingStart) {
					connections.add(getConnectionDetails(position, rail, true));
					if (rail.railType == RailType.TURN_BACK) {
						connections.add(getConnectionDetails(position, rail, false));
					}
				}
			});
		}

		return connections;
	}

	@Override
	protected double getWeightFromEndNode(PositionAndAngle node) {
		return node.position.distManhattan(endNode.position);
	}

	public static <T extends AreaBase<T, U>, U extends SavedRailBase<U, T>, V extends AreaBase<V, W>, W extends SavedRailBase<W, V>> void findPathTick(Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> rails, ObjectArrayList<PathData> path, List<SidingPathFinder<T, U, V, W>> sidingPathFinders, Consumer<Integer> callback) {
		if (!sidingPathFinders.isEmpty()) {
			final SidingPathFinder<T, U, V, W> sidingPathFinder = sidingPathFinders.get(0);
			final ObjectArrayList<PathData> tempPath = sidingPathFinder.tick();

			if (tempPath != null) {
				if (tempPath.size() < 2) {
					sidingPathFinders.clear();
					path.clear();
				} else {
					if (!path.isEmpty() && !Utilities.getElement(path, -1).endPosition.equals(Utilities.getElement(tempPath, 0).startPosition)) {
						addPathData(rails, path, true, sidingPathFinder.startSavedRail, tempPath, false, sidingPathFinder.stopIndex);
					}
					path.addAll(tempPath);
					addPathData(rails, path, false, sidingPathFinder.endSavedRail, tempPath, true, sidingPathFinder.stopIndex + 1);
					callback.accept(sidingPathFinders.remove(0).stopIndex);
				}
			}
		}
	}

	public static <T extends AreaBase<T, U>, U extends SavedRailBase<U, T>> void addPathData(Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> rails, ObjectArrayList<PathData> path, boolean isFirst, SavedRailBase<U, T> savedRail, ObjectArrayList<PathData> pathForPosition, boolean useDwellTime, int stopIndex) {
		final PathData pathData = Utilities.getElement(pathForPosition, isFirst ? 0 : -1);
		if (pathData != null && savedRail != null) {
			final Position position1 = isFirst ? pathData.startPosition : pathData.endPosition;
			final Position position2 = savedRail.getOtherPosition(position1);
			final Position position3 = isFirst ? position2 : position1;
			final Position position4 = isFirst ? position1 : position2;
			path.add(new PathData(DataCache.tryGet(rails, position3, position4), savedRail.id, useDwellTime ? savedRail.getIntegerValue() : 0, position3, position4, stopIndex));
		}
	}

	private static ConnectionDetails<PositionAndAngle> getConnectionDetails(Position position, Rail rail, boolean isOpposite) {
		return new ConnectionDetails<>(new PositionAndAngle(position, isOpposite ? rail.facingEnd.getOpposite() : rail.facingEnd), rail.getLength() / rail.railType.speedLimitMetersPerSecond, 0, 0);
	}

	protected static class PositionAndAngle {

		private final Position position;
		private final Angle angle;

		private PositionAndAngle(Position position, Angle angle) {
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
	}
}
