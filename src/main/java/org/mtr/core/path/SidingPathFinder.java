package org.mtr.core.path;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.mtr.core.data.AreaBase;
import org.mtr.core.data.DataCache;
import org.mtr.core.data.Rail;
import org.mtr.core.data.SavedRailBase;
import org.mtr.core.tools.Angle;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;

import java.util.List;
import java.util.Objects;

public class SidingPathFinder<T extends AreaBase<T, U>, U extends SavedRailBase<U, T>, V extends AreaBase<V, W>, W extends SavedRailBase<W, V>> extends PathFinder<SidingPathFinder.PositionAndAngle, PathData> {

	public final U startSavedRail;
	public final W endSavedRail;
	public final int stopIndex;
	private final Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> positionToRailConnections;

	public SidingPathFinder(DataCache dataCache, U startSavedRail, W endSavedRail, int stopIndex) {
		super(new PositionAndAngle(startSavedRail.positions.left(), null), new PositionAndAngle(endSavedRail.positions.left(), null));
		positionToRailConnections = dataCache.positionToRailConnections;
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
			// always trim the SavedRail itself
			final boolean skipFirst = startSavedRail.containsPos(Utilities.getElement(connectionDetailsList, 1).node.position);
			final boolean skipLast = endSavedRail.containsPos(Utilities.getElement(connectionDetailsList, -2).node.position);
			final ObjectArrayList<PathData> path = new ObjectArrayList<>();

			for (int i = skipFirst ? 1 : 0; i < connectionDetailsList.size() - (skipLast ? 2 : 1); i++) {
				final Position position1 = connectionDetailsList.get(i).node.position;
				final Position position2 = connectionDetailsList.get(i + 1).node.position;
				final Rail rail = DataCache.tryGet(positionToRailConnections, position1, position2);
				if (rail == null) {
					return new ObjectArrayList<>();
				}
				path.add(new PathData(rail, stopIndex, position1, position2));
			}

			return path;
		}
	}

	@Override
	protected ObjectOpenHashSet<ConnectionDetails<PositionAndAngle>> getConnections(PositionAndAngle node) {
		final ObjectOpenHashSet<ConnectionDetails<PositionAndAngle>> connections = new ObjectOpenHashSet<>();
		final Object2ObjectOpenHashMap<Position, Rail> railConnections = positionToRailConnections.get(node.position);

		if (railConnections != null) {
			railConnections.forEach((position, rail) -> {
				if (node.angle == null || node.angle == rail.facingStart) {
					connections.add(getConnectionDetails(position, rail, true));
					if (rail.canTurnBack) {
						connections.add(getConnectionDetails(position, rail, false));
					}
				}
			});
		}

		return connections;
	}

	@Override
	protected long getWeightFromEndNode(PositionAndAngle node) {
		return node.position.distManhattan(endNode.position);
	}

	public static <T extends AreaBase<T, U>, U extends SavedRailBase<U, T>, V extends AreaBase<V, W>, W extends SavedRailBase<W, V>> void findPathTick(DataCache dataCache, ObjectArrayList<PathData> path, List<SidingPathFinder<T, U, V, W>> sidingPathFinders, Runnable callbackSuccess, Runnable callbackFail) {
		if (!sidingPathFinders.isEmpty()) {
			final SidingPathFinder<T, U, V, W> sidingPathFinder = sidingPathFinders.get(0);
			final ObjectArrayList<PathData> tempPath = sidingPathFinder.tick();

			if (tempPath != null) {
				if (tempPath.size() < 2) {
					sidingPathFinders.clear();
					path.clear();
					callbackFail.run();
				} else {
					// add extra PathData if the vehicle is reversing out of the previous SavedRail
					if (needsReverse(path, tempPath)) {
						addPathData(dataCache, tempPath, true, sidingPathFinder.startSavedRail, tempPath, false, sidingPathFinder.stopIndex);
					}
					path.addAll(tempPath);
					// always arrive at the SavedRail fully
					addPathData(dataCache, path, false, sidingPathFinder.endSavedRail, tempPath, true, sidingPathFinder.stopIndex + 1);
					sidingPathFinders.remove(0);
					if (sidingPathFinders.isEmpty()) {
						callbackSuccess.run();
					}
				}
			}
		}
	}

	public static boolean needsReverse(ObjectArrayList<PathData> path, ObjectArrayList<PathData> tempPath) {
		return !path.isEmpty() && !Utilities.getElement(path, -1).endPosition.equals(Utilities.getElement(tempPath, 0).startPosition);
	}

	public static <T extends AreaBase<T, U>, U extends SavedRailBase<U, T>> void addPathData(DataCache dataCache, ObjectArrayList<PathData> path, boolean isFirst, SavedRailBase<U, T> savedRail, ObjectArrayList<PathData> pathForPosition, boolean useDwellTime, int stopIndex) {
		final PathData pathData = Utilities.getElement(pathForPosition, isFirst ? 0 : -1);
		if (pathData != null && savedRail != null) {
			final Position position1 = isFirst ? pathData.startPosition : pathData.endPosition;
			final Position position2 = savedRail.getOtherPosition(position1);
			final Position position3 = isFirst ? position2 : position1;
			final Position position4 = isFirst ? position1 : position2;
			final Rail rail = DataCache.tryGet(dataCache.positionToRailConnections, position3, position4);
			if (useDwellTime) {
				path.add(isFirst ? 0 : path.size(), new PathData(rail, savedRail.id, savedRail.getTimeValueMillis(), stopIndex, position3, position4));
			} else {
				path.add(isFirst ? 0 : path.size(), new PathData(rail, stopIndex, position3, position4));
			}
		}
	}

	private static ConnectionDetails<PositionAndAngle> getConnectionDetails(Position position, Rail rail, boolean isOpposite) {
		return new ConnectionDetails<>(new PositionAndAngle(position, isOpposite ? rail.facingEnd.getOpposite() : rail.facingEnd), Math.round(rail.getLength() / rail.speedLimitMetersPerMillisecond), 0, 0);
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

		@Override
		public int hashCode() {
			return Objects.hash(position, angle);
		}
	}
}
