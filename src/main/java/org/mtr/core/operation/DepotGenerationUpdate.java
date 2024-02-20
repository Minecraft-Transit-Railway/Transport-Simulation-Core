package org.mtr.core.operation;

import org.mtr.core.data.Data;
import org.mtr.core.data.Depot;
import org.mtr.core.generated.operation.DepotGenerationUpdateSchema;
import org.mtr.core.serializer.ReaderBase;

public final class DepotGenerationUpdate extends DepotGenerationUpdateSchema {

	public DepotGenerationUpdate(long depotId, long lastGeneratedMillis, long lastGeneratedFailedStartId, long lastGeneratedFailedEndId) {
		super(depotId, lastGeneratedMillis, lastGeneratedFailedStartId, lastGeneratedFailedEndId);
	}

	public DepotGenerationUpdate(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public void write(Data data) {
		final Depot depot = data.depotIdMap.get(depotId);
		if (depot != null) {
			depot.updateGenerationStatus(lastGeneratedMillis, lastGeneratedFailedStartId, lastGeneratedFailedEndId);
		}
	}
}
