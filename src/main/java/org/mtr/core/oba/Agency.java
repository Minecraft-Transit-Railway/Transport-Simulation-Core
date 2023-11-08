package org.mtr.core.oba;

import org.mtr.core.generated.oba.AgencySchema;
import org.mtr.core.serializer.ReaderBase;

import java.util.TimeZone;

public final class Agency extends AgencySchema {

	public Agency() {
		super("1", "My Agency", "https://github.com/jonafanho/Transport-Simulation-Core", TimeZone.getDefault().getID());
		lang = "en";
	}

	public Agency(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}
}
