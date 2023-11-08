package org.mtr.core.integration;

import org.mtr.core.generated.integration.ResponseSchema;
import org.mtr.core.serializer.ReaderBase;

public final class Response extends ResponseSchema {

	public Response(int code, long currentTime, String text) {
		super(code, currentTime, text, 1);
	}

	public Response(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}
}
