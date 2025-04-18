package org.mtr.core.data;

import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
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
		canAccelerate = oldPathData.canAccelerate;
	}

	public PathData(@Nullable Rail rail, long savedRailBaseId, long dwellTime, long stopIndex, double startDistance, double endDistance, Position startPosition, Angle startAngle, Position endPosition, Angle endAngle) {
		super(savedRailBaseId, dwellTime, stopIndex, startDistance, endDistance, startPosition, startAngle, endPosition, endAngle);
		this.rail = rail;
		reversePositions = startPosition.compareTo(endPosition) > 0;
		if (rail != null) {
			shape = rail.railMath.getShape();
			verticalRadius = rail.railMath.getVerticalRadius();
			speedLimit = getSpeedLimitKilometersPerHour();
			canAccelerate = canAccelerate();
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
		return getRail().getSpeedLimitMetersPerMillisecond(reversePositions);
	}

	public long getSpeedLimitKilometersPerHour() {
		return getRail().getSpeedLimitKilometersPerHour(reversePositions);
	}

	public boolean canAccelerate() {
		return getRail().canAccelerate();
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
			final double ratio = Utilities.clamp(rawValue / getRailLength(), 0, 1);
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

	public void writePathCache(Data data) {
		rail = Data.tryGet(data.positionsToRail, startPosition, endPosition);
		if (rail == null) {
			rail = defaultRail();
		} else {
			shape = rail.railMath.getShape();
			verticalRadius = rail.railMath.getVerticalRadius();
			speedLimit = getSpeedLimitKilometersPerHour();
			canAccelerate = canAccelerate();
		}
	}

	private Rail defaultRail() {
		final ObjectObjectImmutablePair<Angle, Angle> angles = Rail.getAngles(startPosition, startAngle.angleDegrees, endPosition, endAngle.angleDegrees);
		return Rail.newRail(startPosition, angles.left(), endPosition, angles.right(), shape, verticalRadius, new ObjectArrayList<>(), speedLimit == 0 ? SidingPathFinder.AIRPLANE_SPEED : speedLimit, 0, false, false, canAccelerate, false, false, TransportMode.TRAIN);
	}

	public static void writePathCache(ObjectArrayList<PathData> path, Data data) {
		path.forEach(pathData -> pathData.writePathCache(data));
	}
}
