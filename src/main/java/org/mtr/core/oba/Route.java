package org.mtr.core.oba;

import org.mtr.core.generated.oba.RouteSchema;
import org.mtr.core.serializer.ReaderBase;

public final class Route extends RouteSchema {

	public Route(String id, String shortName, String longName, String description, long type, String color) {
		super(id, "1", shortName, longName, description, type, "", color, "");
	}

	public Route(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}
}
