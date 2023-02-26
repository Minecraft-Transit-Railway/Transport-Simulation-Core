package org.mtr.core.tools;

import org.mtr.core.data.MessagePackHelper;

import java.util.Set;
import java.util.function.Consumer;

public class DataFixer {

	private static final String KEY_POS_1 = "pos_1";
	private static final String KEY_POS_2 = "pos_2";
	private static final String KEY_NODE_POS = "node_pos";

	private static final int PACKED_X_LENGTH = 26;
	private static final int PACKED_Z_LENGTH = PACKED_X_LENGTH;
	private static final int PACKED_Y_LENGTH = 64 - PACKED_X_LENGTH - PACKED_Z_LENGTH;
	private static final int Z_OFFSET = PACKED_Y_LENGTH;
	private static final int X_OFFSET = PACKED_Y_LENGTH + PACKED_Z_LENGTH;

	public static void unpackSavedRailBase(MessagePackHelper messagePackHelper, Set<Position> positions) {
		messagePackHelper.unpackLong(KEY_POS_1, value -> positions.add(convertCoordinates(value)));
		messagePackHelper.unpackLong(KEY_POS_2, value -> positions.add(convertCoordinates(value)));
	}

	public static void unpackRailEntry(MessagePackHelper messagePackHelper, Consumer<Position> consumer) {
		messagePackHelper.unpackLong(KEY_NODE_POS, value -> consumer.accept(convertCoordinates(value)));
	}

	public static Position convertCoordinates(long packedPosition) {
		return new Position(
				(int) (packedPosition << 64 - X_OFFSET - PACKED_X_LENGTH >> 64 - PACKED_X_LENGTH),
				(int) (packedPosition << 64 - PACKED_Y_LENGTH >> 64 - PACKED_Y_LENGTH),
				(int) (packedPosition << 64 - Z_OFFSET - PACKED_Z_LENGTH >> 64 - PACKED_Z_LENGTH)
		);
	}
}
