package org.mtr.core.path;

import org.mtr.core.data.ConditionalList;
import org.mtr.core.data.DataCache;
import org.mtr.core.data.Rail;
import org.mtr.core.data.SerializedDataBase;
import org.mtr.core.serializers.MessagePackReader;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.serializers.WriterBase;
import org.mtr.core.tools.Position;

public class PathData extends SerializedDataBase implements ConditionalList {

	public final Rail rail;
	public final boolean validRail;
	public final long savedRailBaseId;
	public final int dwellTimeMillis;
	public final int stopIndex;
	public final double startDistance;
	public final double endDistance;
	public final Position startPosition;
	public final Position endPosition;
	public final boolean reversePositions;

	private static final String KEY_SAVED_RAIL_BASE_ID = "saved_rail_base_id";
	private static final String KEY_DWELL_TIME = "dwell_time";
	private static final String KEY_STOP_INDEX = "stop_index";
	private static final String KEY_START_DISTANCE = "start_distance";
	private static final String KEY_END_DISTANCE = "end_distance";
	private static final String KEY_START_POSITION_X = "start_pos_x";
	private static final String KEY_START_POSITION_Y = "start_pos_y";
	private static final String KEY_START_POSITION_Z = "start_pos_z";
	private static final String KEY_END_POSITION_X = "end_pos_x";
	private static final String KEY_END_POSITION_Y = "end_pos_y";
	private static final String KEY_END_POSITION_Z = "end_pos_z";

	public PathData(Rail rail, long savedRailBaseId, int dwellTimeMillis, int stopIndex, Position startPosition, Position endPosition) {
		this(rail, savedRailBaseId, dwellTimeMillis, stopIndex, 0, 0, startPosition, endPosition);
	}

	public PathData(PathData oldPathData, double startDistance, double endDistance) {
		this(oldPathData.rail, oldPathData.savedRailBaseId, oldPathData.dwellTimeMillis, oldPathData.stopIndex, startDistance, endDistance, oldPathData.startPosition, oldPathData.endPosition);
	}

	public PathData(Rail rail, long savedRailBaseId, int dwellTimeMillis, int stopIndex, double startDistance, double endDistance, Position startPosition, Position endPosition) {
		this.rail = rail;
		this.savedRailBaseId = savedRailBaseId;
		this.dwellTimeMillis = dwellTimeMillis;
		this.stopIndex = stopIndex;
		this.startDistance = startDistance;
		this.endDistance = endDistance;
		this.startPosition = startPosition;
		this.endPosition = endPosition;
		validRail = true;
		reversePositions = startPosition.compareTo(endPosition) > 0;
	}

	public PathData(ReaderBase readerBase, DataCache dataCache) {
		savedRailBaseId = readerBase.getLong(KEY_SAVED_RAIL_BASE_ID, 0);
		dwellTimeMillis = readerBase.getInt(KEY_DWELL_TIME, 0);
		stopIndex = readerBase.getInt(KEY_STOP_INDEX, 0);
		startDistance = readerBase.getDouble(KEY_START_DISTANCE, 0);
		endDistance = readerBase.getDouble(KEY_END_DISTANCE, 0);
		startPosition = new Position(
				readerBase.getLong(KEY_START_POSITION_X, 0),
				readerBase.getLong(KEY_START_POSITION_Y, 0),
				readerBase.getLong(KEY_START_POSITION_Z, 0)
		);
		endPosition = new Position(
				readerBase.getLong(KEY_END_POSITION_X, 0),
				readerBase.getLong(KEY_END_POSITION_Y, 0),
				readerBase.getLong(KEY_END_POSITION_Z, 0)
		);
		reversePositions = startPosition.compareTo(endPosition) > 0;

		final Rail tempRail = DataCache.tryGet(dataCache.positionToRailConnections, startPosition, endPosition);
		if (tempRail == null) {
			rail = new Rail(new MessagePackReader());
			validRail = false;
		} else {
			rail = tempRail;
			validRail = true;
		}
	}

	@Override
	public void updateData(ReaderBase readerBase) {
	}

	@Override
	public void toMessagePack(WriterBase writerBase) {
		writerBase.writeLong(KEY_SAVED_RAIL_BASE_ID, savedRailBaseId);
		writerBase.writeInt(KEY_DWELL_TIME, dwellTimeMillis);
		writerBase.writeInt(KEY_STOP_INDEX, stopIndex);
		writerBase.writeDouble(KEY_START_DISTANCE, startDistance);
		writerBase.writeDouble(KEY_END_DISTANCE, endDistance);
		writerBase.writeLong(KEY_START_POSITION_X, startPosition.x);
		writerBase.writeLong(KEY_START_POSITION_Y, startPosition.y);
		writerBase.writeLong(KEY_START_POSITION_Z, startPosition.z);
		writerBase.writeLong(KEY_END_POSITION_X, endPosition.x);
		writerBase.writeLong(KEY_END_POSITION_Y, endPosition.y);
		writerBase.writeLong(KEY_END_POSITION_Z, endPosition.z);
	}

	@Override
	public String getHexId() {
		return "";
	}

	@Override
	public boolean matchesCondition(double value) {
		return value >= startDistance;
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
