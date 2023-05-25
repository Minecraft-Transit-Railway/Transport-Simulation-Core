package org.mtr.core.data;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.core.generated.RailNodeConnectionSchema;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.tools.Position;

public final class RailNodeConnection extends RailNodeConnectionSchema {

	public RailNodeConnection(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	@Override
	public String getHexId() {
		return "";
	}

	public void writeToMap(Object2ObjectOpenHashMap<Position, Rail> connectionsMap) {
		connectionsMap.put(position, rail);
	}
}
