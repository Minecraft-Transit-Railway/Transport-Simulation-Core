package org.mtr.core.oba;

import org.mtr.core.generated.oba.StopGroupSchema;
import org.mtr.core.serializer.ReaderBase;

public final class StopGroup extends StopGroupSchema {

	public StopGroup(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}
}
