package org.mtr.core.operation;

import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.core.data.Rail;
import org.mtr.core.generated.operation.RailsResponseSchema;
import org.mtr.core.serializer.ReaderBase;

public final class RailsResponse extends RailsResponseSchema {

	RailsResponse() {
		super();
	}

	public RailsResponse(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public ObjectImmutableList<Rail> getRails() {
		return new ObjectImmutableList<>(rails);
	}

	void add(Rail rail) {
		rails.add(rail);
	}
}
