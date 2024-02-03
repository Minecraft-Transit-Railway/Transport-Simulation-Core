package org.mtr.legacy.data;

import org.mtr.core.data.Position;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.tool.Utilities;
import org.mtr.legacy.generated.data.RailNodeSchema;

import java.util.function.Consumer;

public final class LegacyRailNode extends RailNodeSchema {

	public LegacyRailNode(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	@Override
	public String getHexId() {
		return Utilities.numberToPaddedHexString(node_pos);
	}

	@Override
	public boolean isValid() {
		return true;
	}

	public Position getStartPosition() {
		return DataFixer.fromLong(node_pos);
	}

	public long getStartPositionLong() {
		return node_pos;
	}

	public void iterateConnections(Consumer<RailNodeConnection> consumer) {
		rail_connections.forEach(consumer);
	}
}
