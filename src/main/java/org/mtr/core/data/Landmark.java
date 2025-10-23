package org.mtr.core.data;

import org.mtr.core.generated.data.HomeSchema;
import org.mtr.core.generated.data.LandmarkSchema;
import org.mtr.core.serializer.ReaderBase;

public final class Landmark extends LandmarkSchema {

	public Landmark(Data data) {
		super(TransportMode.values()[0], data);
	}

	public Landmark(ReaderBase readerBase, Data data) {
		super(readerBase, data);
		updateData(readerBase);
	}
}
