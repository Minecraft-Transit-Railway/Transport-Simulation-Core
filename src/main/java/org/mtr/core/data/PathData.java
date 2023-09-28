package org.mtr.core.data;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.core.generated.PathDataSchema;
import org.mtr.core.serializers.MessagePackReader;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.tools.Angle;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Vector;

public class PathData extends PathDataSchema implements ConditionalList {

	private Rail rail = new Rail(new MessagePackReader());
	public final boolean reversePositions;

	public PathData(Rail rail, long savedRailBaseId, long dwellTime, int stopIndex, Position startPosition, Position endPosition) {
		this(rail, savedRailBaseId, dwellTime, stopIndex, 0, 0, startPosition, endPosition);
	}

	public PathData(PathData oldPathData, double startDistance, double endDistance) {
		this(oldPathData.rail, oldPathData.savedRailBaseId, oldPathData.dwellTime, oldPathData.stopIndex, startDistance, endDistance, oldPathData.startPosition, oldPathData.endPosition);
	}

	public PathData(Rail rail, long savedRailBaseId, long dwellTime, long stopIndex, double startDistance, double endDistance, Position startPosition, Position endPosition) {
		super(savedRailBaseId, dwellTime, stopIndex, startDistance, endDistance, startPosition, endPosition);
		this.rail = rail;
		reversePositions = startPosition.compareTo(endPosition) > 0;
	}

	public PathData(ReaderBase readerBase) {
		super(readerBase);
		reversePositions = startPosition.compareTo(endPosition) > 0;
	}

	@Override
	public boolean matchesCondition(double value) {
		return value >= startDistance;
	}

	public final Rail getRail() {
		return rail;
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
		return rail.getStartAngle(reversePositions);
	}

	public double getSpeedLimitMetersPerMillisecond() {
		return rail.getSpeedLimitMetersPerMillisecond(reversePositions);
	}

	public long getSpeedLimitKilometersPerHour() {
		return rail.getSpeedLimitKilometersPerHour(reversePositions);
	}

	public boolean canAccelerate() {
		return rail.canAccelerate();
	}

	public double getRailLength() {
		return rail.railMath.getLength();
	}

	public Vector getPosition(double rawValue) {
		return rail.railMath.getPosition(rawValue, reversePositions);
	}

	public boolean isSignalBlocked(long vehicleId) {
		return rail.isBlocked(vehicleId);
	}

	public boolean writePathCache(Data data) {
		final Rail tempRail = Data.tryGet(data.positionsToRail, startPosition, endPosition);
		if (tempRail == null) {
			rail = new Rail(new MessagePackReader());
			return true;
		} else {
			rail = tempRail;
			return false;
		}
	}

	public static void writePathCache(ObjectArrayList<PathData> path, Data data, boolean removePathIfInvalid) {
		final ObjectArrayList<PathData> pathDataToRemove = new ObjectArrayList<>();
		path.forEach(pathData -> {
			if (pathData.writePathCache(data) && removePathIfInvalid) {
				pathDataToRemove.add(pathData);
			}
		});
		pathDataToRemove.forEach(path::remove);
	}
}
