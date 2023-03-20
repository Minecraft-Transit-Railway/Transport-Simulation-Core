package org.mtr.core.path;

import org.msgpack.core.MessagePacker;
import org.mtr.core.data.Rail;
import org.mtr.core.data.SerializedDataBase;
import org.mtr.core.reader.ReaderBase;
import org.mtr.core.tools.Position;

import java.io.IOException;

public class PathData extends SerializedDataBase {

	public final Rail rail;
	public final long savedRailBaseId;
	public final int dwellTimeMillis;
	public final int stopIndex;

	public final Position startPosition;
	public final Position endPosition;

	private static final String KEY_RAIL = "rail";
	private static final String KEY_SAVED_RAIL_BASE_ID = "saved_rail_base_id";
	private static final String KEY_DWELL_TIME = "dwell_time";
	private static final String KEY_STOP_INDEX = "stop_index";
	private static final String KEY_START_POSITION_X = "start_pos_x";
	private static final String KEY_START_POSITION_Y = "start_pos_y";
	private static final String KEY_START_POSITION_Z = "start_pos_z";
	private static final String KEY_END_POSITION_X = "end_pos_x";
	private static final String KEY_END_POSITION_Y = "end_pos_y";
	private static final String KEY_END_POSITION_Z = "end_pos_z";

	public PathData(Rail rail, long savedRailBaseId, int dwellTimeMillis, Position startPosition, Position endPosition, int stopIndex) {
		this.rail = rail;
		this.savedRailBaseId = savedRailBaseId;
		this.dwellTimeMillis = dwellTimeMillis;
		this.startPosition = startPosition;
		this.endPosition = endPosition;
		this.stopIndex = stopIndex;
	}

	public <T extends ReaderBase<U, T>, U> PathData(T readerBase) {
		rail = new Rail(readerBase.getChild(KEY_RAIL));
		savedRailBaseId = readerBase.getLong(KEY_SAVED_RAIL_BASE_ID, 0);
		dwellTimeMillis = readerBase.getInt(KEY_DWELL_TIME, 0);
		stopIndex = readerBase.getInt(KEY_STOP_INDEX, 0);
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
	}

	@Override
	public <T extends ReaderBase<U, T>, U> void updateData(T readerBase) {
	}

	@Override
	public void toMessagePack(MessagePacker messagePacker) throws IOException {
		messagePacker.packString(KEY_RAIL);
		messagePacker.packMapHeader(rail.messagePackLength());
		rail.toMessagePack(messagePacker);

		messagePacker.packString(KEY_SAVED_RAIL_BASE_ID).packLong(savedRailBaseId);
		messagePacker.packString(KEY_DWELL_TIME).packInt(dwellTimeMillis);
		messagePacker.packString(KEY_STOP_INDEX).packInt(stopIndex);
		messagePacker.packString(KEY_START_POSITION_X).packLong(startPosition.x);
		messagePacker.packString(KEY_START_POSITION_Y).packLong(startPosition.y);
		messagePacker.packString(KEY_START_POSITION_Z).packLong(startPosition.z);
		messagePacker.packString(KEY_END_POSITION_X).packLong(endPosition.x);
		messagePacker.packString(KEY_END_POSITION_Y).packLong(endPosition.y);
		messagePacker.packString(KEY_END_POSITION_Z).packLong(endPosition.z);
	}

	@Override
	public int messagePackLength() {
		return 6;
	}

	public boolean isSameRail(PathData pathData) {
		return startPosition.equals(pathData.startPosition) && endPosition.equals(pathData.endPosition);
	}

	public boolean isOppositeRail(PathData pathData) {
		return startPosition.equals(pathData.endPosition) && endPosition.equals(pathData.startPosition);
	}
}
