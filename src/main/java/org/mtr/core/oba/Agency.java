package org.mtr.core.oba;

import org.mtr.core.generated.oba.AgencySchema;
import org.mtr.core.serializer.ReaderBase;

import java.util.TimeZone;

/**
 * OneBusAway {@code Agency} entity. The simulator hosts a single agency representing the
 * whole transit network, so the constructor seeds it with hard-coded identifying values.
 */
public final class Agency extends AgencySchema {

	/**
	 * Construct the singleton default agency for this server's network.
	 */
	public Agency() {
		super("1", "My Agency", "https://github.com/jonafanho/Transport-Simulation-Core", TimeZone.getDefault().getID());
		lang = "en";
	}

	/**
	 * Reconstruct an agency from its serialised form.
	 *
	 * @param readerBase the source of the serialised representation
	 */
	public Agency(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}
}
