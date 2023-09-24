package org.mtr.core.data;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.core.generated.RailNodeConnectionSchema;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.tools.DataFixer;
import org.mtr.core.tools.Position;

public final class RailNodeConnection extends RailNodeConnectionSchema {

	RailNodeConnection(Position position, Rail rail) {
		super(position, rail);
	}

	public RailNodeConnection(ReaderBase readerBase) {
		super(DataFixer.convertRailNodeConnection(readerBase));
		updateData(readerBase);
	}

	void writeToMap(Object2ObjectOpenHashMap<Position, Rail> connectionsMap) {
		connectionsMap.put(position, rail);
	}

	boolean isValid(Position checkPosition) {
		return rail.isValid() && !matchesPosition(checkPosition);
	}

	boolean matchesPosition(Position checkPosition) {
		return checkPosition.equals(position);
	}
}