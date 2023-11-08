package org.mtr.core.oba;

import org.mtr.core.generated.oba.CurrentTimeSchema;
import org.mtr.core.serializer.ReaderBase;

public final class CurrentTime extends CurrentTimeSchema {

	public CurrentTime(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}
}
