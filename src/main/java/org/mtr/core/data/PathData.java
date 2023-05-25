package org.mtr.core.data;

import org.mtr.core.generated.PathDataSchema;
import org.mtr.core.serializers.MessagePackReader;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.tools.Position;

public class PathData extends PathDataSchema implements ConditionalList {

	private Rail rail = new Rail(new MessagePackReader());
	public final boolean reversePositions;

	public PathData(Rail rail, long savedRailBaseId, long dwellTime, long stopIndex, Position startPosition, Position endPosition) {
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
	public String getHexId() {
		return "";
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

	public boolean init(DataCache dataCache) {
		final Rail tempRail = DataCache.tryGet(dataCache.positionToRailConnections, startPosition, endPosition);
		if (tempRail == null) {
			rail = new Rail(new MessagePackReader());
			return true;
		} else {
			rail = tempRail;
			return false;
		}
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
}
