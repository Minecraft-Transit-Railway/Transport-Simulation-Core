package org.mtr.core.data;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.msgpack.core.MessagePacker;
import org.mtr.core.reader.ReaderBase;
import org.mtr.core.tools.DataFixer;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;

import java.io.IOException;
import java.util.Map;

public class RailNode extends SerializedDataBase {

	public final Position position;
	public final Object2ObjectOpenHashMap<Position, Rail> connections;

	private static final String KEY_NODE_POSITION_X = "node_position_x";
	private static final String KEY_NODE_POSITION_Y = "node_position_y";
	private static final String KEY_NODE_POSITION_Z = "node_position_z";
	private static final String KEY_RAIL_CONNECTIONS = "rail_connections";

	public RailNode(Position position, Object2ObjectOpenHashMap<Position, Rail> connections) {
		this.position = position;
		this.connections = connections;
	}

	public <T extends ReaderBase<U, T>, U> RailNode(T readerBase) {
		position = getPosition(readerBase);
		connections = new Object2ObjectOpenHashMap<>();
		updateData(readerBase);
	}

	@Override
	public <T extends ReaderBase<U, T>, U> void updateData(T readerBase) {
		readerBase.iterateReaderArray(KEY_RAIL_CONNECTIONS, value -> connections.put(getPosition(value), new Rail(value)));
	}

	@Override
	public void toMessagePack(MessagePacker messagePacker) throws IOException {
		messagePacker.packString(KEY_NODE_POSITION_X).packLong(position.x);
		messagePacker.packString(KEY_NODE_POSITION_Y).packLong(position.y);
		messagePacker.packString(KEY_NODE_POSITION_Z).packLong(position.z);

		messagePacker.packString(KEY_RAIL_CONNECTIONS).packArrayHeader(connections.size());
		for (final Map.Entry<Position, Rail> entry : connections.entrySet()) {
			final Position endNodePosition = entry.getKey();
			messagePacker.packMapHeader(entry.getValue().messagePackLength() + 3);
			messagePacker.packString(KEY_NODE_POSITION_X).packLong(endNodePosition.x);
			messagePacker.packString(KEY_NODE_POSITION_Y).packLong(endNodePosition.y);
			messagePacker.packString(KEY_NODE_POSITION_Z).packLong(endNodePosition.z);
			entry.getValue().toMessagePack(messagePacker);
		}
	}

	@Override
	public int messagePackLength() {
		return 4;
	}

	@Override
	public String getHexId() {
		return String.format("%s-%s-%s", Utilities.longToPaddedHexString(position.x), Utilities.longToPaddedHexString(position.y), Utilities.longToPaddedHexString(position.z));
	}

	private static <T extends ReaderBase<U, T>, U> Position getPosition(T readerBase) {
		final long x = readerBase.getLong(KEY_NODE_POSITION_X, 0);
		final long y = readerBase.getLong(KEY_NODE_POSITION_Y, 0);
		final long z = readerBase.getLong(KEY_NODE_POSITION_Z, 0);
		final Position[] newPosition = {new Position(x, y, z)};
		DataFixer.unpackRailEntry(readerBase, value -> newPosition[0] = value);
		return newPosition[0];
	}
}
