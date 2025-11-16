package org.mtr.core.data;

import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.core.generated.data.PathDataSchema;
import org.mtr.core.path.SidingPathFinder;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.ConditionalList;
import org.mtr.core.tool.Utilities;
import org.mtr.core.tool.Vector;

import javax.annotation.Nullable;

public class PathData extends PathDataSchema implements ConditionalList {

	@Nullable
	private Rail rail;
	public final boolean reversePositions;

	public PathData(Rail rail, long savedRailBaseId, long dwellTime, int stopIndex, Position startPosition, Position endPosition) {
		this(rail, savedRailBaseId, dwellTime, stopIndex, 0, 0, startPosition, rail.getStartAngle(startPosition), endPosition, rail.getStartAngle(endPosition));
	}

	public PathData(PathData oldPathData, double startDistance, double endDistance) {
		this(oldPathData.rail, oldPathData.savedRailBaseId, oldPathData.dwellTime, oldPathData.stopIndex, startDistance, endDistance, oldPathData.startPosition, oldPathData.startAngle, oldPathData.endPosition, oldPathData.endAngle);
		shape = oldPathData.shape;
		verticalRadius = oldPathData.verticalRadius;
		speedLimit = oldPathData.speedLimit;
	}

	public PathData(@Nullable Rail rail, long savedRailBaseId, long dwellTime, long stopIndex, double startDistance, double endDistance, Position startPosition, Angle startAngle, Position endPosition, Angle endAngle) {
		super(savedRailBaseId, dwellTime, stopIndex, startDistance, endDistance, startPosition, startAngle, endPosition, endAngle);
		this.rail = rail;
		reversePositions = startPosition.compareTo(endPosition) > 0;
		if (rail != null) {
			shape = rail.railMath.getShape();
			verticalRadius = rail.railMath.getVerticalRadius();
		}
	}

	public PathData(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
		reversePositions = startPosition.compareTo(endPosition) > 0;
	}

	@Override
	public boolean matchesCondition(double value) {
		return value >= startDistance;
	}

	public final Rail getRail() {
		return rail == null ? defaultRail() : rail;
	}

	public final long getSavedRailBaseId() {
		return savedRailBaseId;
	}

	public final double getStartDistance() {
		return startDistance;
	}

	public final double getEndDistance() {
		return endDistance;
	}

	public final long getDwellTime() {
		return dwellTime;
	}

	public final int getStopIndex() {
		return (int) stopIndex;
	}

	public boolean isSameRail(PathData pathData) {
		return startPosition.equals(pathData.startPosition) && endPosition.equals(pathData.endPosition);
	}

	public boolean isOppositeRail(PathData pathData) {
		return startPosition.equals(pathData.endPosition) && endPosition.equals(pathData.startPosition);
	}

	public Position getOrderedPosition1() {
		return reversePositions ? endPosition : startPosition;
	}

	public Position getOrderedPosition2() {
		return reversePositions ? startPosition : endPosition;
	}

	public Angle getFacingStart() {
		return getRail().getStartAngle(reversePositions);
	}

	public double getSpeedLimitMetersPerMillisecond() {
		return Utilities.kilometersPerHourToMetersPerMillisecond(getSpeedLimitKilometersPerHour());
	}

	public long getSpeedLimitKilometersPerHour() {
		return Math.max(1, speedLimit);
	}

	public double getRailLength() {
		return rail == null ? endDistance - startDistance : rail.railMath.getLength();
	}

	public boolean isDescending() {
		return endPosition.getY() < startPosition.getY();
	}

	public Vector getPosition(double rawValue) {
		if (rail != null && rail.railMath.isValid()) {
			return rail.railMath.getPosition(rawValue, reversePositions);
		} else {
			// TODO better positioning when vehicle is moving too quickly
			final double ratio = Utilities.clampSafe(rawValue / getRailLength(), 0, 1);
			return new Vector(
					startPosition.getX() + ratio * (endPosition.getX() - startPosition.getX()) + 0.5,
					startPosition.getY() + ratio * (endPosition.getY() - startPosition.getY()),
					startPosition.getZ() + ratio * (endPosition.getZ() - startPosition.getZ()) + 0.5
			);
		}
	}

	public String getHexId(boolean reverse) {
		return reverse ? TwoPositionsBase.getHexIdRaw(endPosition, startPosition) : TwoPositionsBase.getHexIdRaw(startPosition, endPosition);
	}

	public boolean isSignalBlocked(long vehicleId, Rail.BlockReservation blockReservation) {
		return getRail().isBlocked(vehicleId, blockReservation);
	}

	public IntAVLTreeSet getSignalColors() {
		return getRail().getSignalColors();
	}

	private void writePathCache(Data data) {
		rail = Data.tryGet(data.positionsToRail, startPosition, endPosition);
		if (rail == null) {
			rail = defaultRail();
		} else {
			shape = rail.railMath.getShape();
			verticalRadius = rail.railMath.getVerticalRadius();
		}
	}

	private Rail defaultRail() {
		final ObjectObjectImmutablePair<Angle, Angle> angles = Rail.getAngles(startPosition, startAngle.angleDegrees, endPosition, endAngle.angleDegrees);
		return Rail.newRail(
				startPosition, angles.left(),
				endPosition, angles.right(),
				shape, verticalRadius, 0,
				0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
				new ObjectArrayList<>(), speedLimit == 0 ? SidingPathFinder.AIRPLANE_SPEED : speedLimit, 0,
				false, false, true, false, false, TransportMode.TRAIN
		);
	}

	public static void writePathCache(ObjectList<PathData> path, Data data, TransportMode transportMode) {
		for (int i = 0; i < path.size(); i++) {
			final PathData pathData = path.get(i);
			pathData.writePathCache(data);
			pathData.speedLimit = getRailSpeed(path, i, transportMode.defaultSpeedKilometersPerHour);
		}
	}

	/**
	 * Gets the rail speed on a path section. If {@link Rail#canAccelerate()} for the rail is {@code false}, (such as platform or turnback), search before and after for a rail with a speed.
	 *
	 * @param path                          the current path
	 * @param currentIndex                  the index of the current rail
	 * @param defaultSpeedKilometersPerHour the default value if searching fails
	 * @return the speed in km/h
	 */
	private static long getRailSpeed(ObjectList<PathData> path, int currentIndex, long defaultSpeedKilometersPerHour) {
		for (int offset = 0; offset <= Math.max(currentIndex, path.size() - currentIndex - 1); offset++) {
			for (int sign = -1; sign <= 1; sign += 2) {
				final PathData pathData = Utilities.getElement(path, currentIndex + sign * offset);

				if (pathData == null) {
					break;
				}

				if (pathData.getRail().canAccelerate()) {
					return pathData.getRail().getSpeedLimitKilometersPerHour(pathData.reversePositions);
				}

				if (offset == 0) {
					break;
				}
			}
		}

		return defaultSpeedKilometersPerHour;
	}
}
