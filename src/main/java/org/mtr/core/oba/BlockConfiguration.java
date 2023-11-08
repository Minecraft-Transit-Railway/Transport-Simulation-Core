package org.mtr.core.oba;

import org.mtr.core.generated.oba.BlockConfigurationSchema;
import org.mtr.core.serializer.ReaderBase;

public final class BlockConfiguration extends BlockConfigurationSchema {

	public BlockConfiguration(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}
}
