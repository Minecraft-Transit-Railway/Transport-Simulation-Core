package org.mtr.core.data;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.serializers.WriterBase;
import org.mtr.core.tools.DataFixer;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;

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

	public RailNode(ReaderBase readerBase) {
		position = getPosition(readerBase);
		connections = new Object2ObjectOpenHashMap<>();
		updateData(readerBase);
	}

	@Override
	public void updateData(ReaderBase readerBase) {
		readerBase.iterateReaderArray(KEY_RAIL_CONNECTIONS, value -> connections.put(getPosition(value), new Rail(value)));
	}

	@Override
	public void toMessagePack(WriterBase writerBase) {
		writerBase.writeLong(KEY_NODE_POSITION_X, position.x);
		writerBase.writeLong(KEY_NODE_POSITION_Y, position.y);
		writerBase.writeLong(KEY_NODE_POSITION_Z, position.z);

		final WriterBase.Array writerBaseArray = writerBase.writeArray(KEY_RAIL_CONNECTIONS);
		connections.forEach((position, rail) -> {
			final WriterBase writerBaseChild = writerBaseArray.writeChild();
			writerBaseChild.writeLong(KEY_NODE_POSITION_X, position.x);
			writerBaseChild.writeLong(KEY_NODE_POSITION_Y, position.y);
			writerBaseChild.writeLong(KEY_NODE_POSITION_Z, position.z);
			rail.toMessagePack(writerBaseChild);
		});
	}

	@Override
	public String getHexId() {
		return String.format("%s-%s-%s", Utilities.numberToPaddedHexString(position.x), Utilities.numberToPaddedHexString(position.y), Utilities.numberToPaddedHexString(position.z));
	}

	private static Position getPosition(ReaderBase readerBase) {
		final long x = readerBase.getLong(KEY_NODE_POSITION_X, 0);
		final long y = readerBase.getLong(KEY_NODE_POSITION_Y, 0);
		final long z = readerBase.getLong(KEY_NODE_POSITION_Z, 0);
		final Position[] newPosition = {new Position(x, y, z)};
		DataFixer.unpackRailEntry(readerBase, value -> newPosition[0] = value);
		return newPosition[0];
	}
}
