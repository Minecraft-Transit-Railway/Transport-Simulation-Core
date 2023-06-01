package org.mtr.core.data;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.core.generated.RailNodeSchema;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.tools.DataFixer;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;

public final class RailNode extends RailNodeSchema implements SerializedDataBaseWithId {

	RailNode(Position position) {
		super(position);
	}

	public RailNode(ReaderBase readerBase) {
		super(DataFixer.convertRailNode(readerBase));
		updateData(readerBase);
	}

	@Override
	public String getHexId() {
		return String.format("%s-%s-%s", Utilities.numberToPaddedHexString(position.getX()), Utilities.numberToPaddedHexString(position.getY()), Utilities.numberToPaddedHexString(position.getZ()));
	}

	@Override
	public boolean isValid() {
		connections.removeIf(railNodeConnection -> railNodeConnection.isInvalid(position));
		return !connections.isEmpty();
	}

	public Position getPosition() {
		return position;
	}

	public Object2ObjectOpenHashMap<Position, Rail> getConnectionsAsMap() {
		final Object2ObjectOpenHashMap<Position, Rail> connectionsMap = new Object2ObjectOpenHashMap<>();
		connections.forEach(railNodeConnection -> railNodeConnection.writeToMap(connectionsMap));
		return connectionsMap;
	}
}
