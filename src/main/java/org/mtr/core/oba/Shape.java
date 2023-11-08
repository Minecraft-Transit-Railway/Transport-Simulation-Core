package org.mtr.core.oba;

import org.mtr.core.generated.oba.ShapeSchema;
import org.mtr.core.serializer.ReaderBase;

public final class Shape extends ShapeSchema {

	public Shape(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}
}
