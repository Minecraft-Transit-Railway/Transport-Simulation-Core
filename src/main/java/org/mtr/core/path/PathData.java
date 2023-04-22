package org.mtr.core.path;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import org.msgpack.core.MessagePacker;
import org.mtr.core.data.ConditionalList;
import org.mtr.core.data.DataCache;
import org.mtr.core.data.Rail;
import org.mtr.core.data.SerializedDataBase;
import org.mtr.core.reader.MessagePackHelper;
import org.mtr.core.reader.ReaderBase;
import org.mtr.core.tools.Position;

import java.io.IOException;

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

	public <T extends ReaderBase<U, T>, U> PathData(T readerBase, DataCache dataCache) {
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
			rail = new Rail(new MessagePackHelper(new Object2ObjectArrayMap<>()));
			validRail = false;
		} else {
			rail = tempRail;
			validRail = true;
		}
	}

	@Override
	public <T extends ReaderBase<U, T>, U> void updateData(T readerBase) {
	}

	@Override
	public void toMessagePack(MessagePacker messagePacker) throws IOException {
		messagePacker.packString(KEY_SAVED_RAIL_BASE_ID).packLong(savedRailBaseId);
		messagePacker.packString(KEY_DWELL_TIME).packInt(dwellTimeMillis);
		messagePacker.packString(KEY_STOP_INDEX).packInt(stopIndex);
		messagePacker.packString(KEY_START_DISTANCE).packDouble(startDistance);
		messagePacker.packString(KEY_END_DISTANCE).packDouble(endDistance);
		messagePacker.packString(KEY_START_POSITION_X).packLong(startPosition.x);
		messagePacker.packString(KEY_START_POSITION_Y).packLong(startPosition.y);
		messagePacker.packString(KEY_START_POSITION_Z).packLong(startPosition.z);
		messagePacker.packString(KEY_END_POSITION_X).packLong(endPosition.x);
		messagePacker.packString(KEY_END_POSITION_Y).packLong(endPosition.y);
		messagePacker.packString(KEY_END_POSITION_Z).packLong(endPosition.z);
	}

	@Override
	public int messagePackLength() {
		return 11;
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
