package org.mtr.core.oba;

import org.mtr.core.generated.oba.BlockTripSchema;
import org.mtr.core.serializer.ReaderBase;

public final class BlockTrip extends BlockTripSchema {

	public BlockTrip(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}
}
