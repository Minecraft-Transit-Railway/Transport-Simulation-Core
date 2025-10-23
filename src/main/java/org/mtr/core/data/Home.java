package org.mtr.core.data;

import org.mtr.core.generated.data.HomeSchema;
import org.mtr.core.serializer.ReaderBase;

public final class Home extends HomeSchema {

	public Home(Data data) {
		super(TransportMode.values()[0], data);
	}

	public Home(ReaderBase readerBase, Data data) {
		super(readerBase, data);
		updateData(readerBase);
	}
}
