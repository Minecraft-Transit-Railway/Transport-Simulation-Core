package org.mtr.core.oba;

import org.mtr.core.generated.oba.ReferencesBaseSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.com.google.gson.JsonObject;

public abstract class ReferencesBase extends ReferencesBaseSchema {

	protected ReferencesBase(References references) {
		super(references);
	}

	protected ReferencesBase(ReaderBase readerBase) {
		super(readerBase);
	}

	public final void addRoute(int routeColor) {
		if (isIncludeReferences()) {
			references.addRoute(routeColor);
		}
	}

	public final void addStop(long platformId) {
		if (isIncludeReferences()) {
			references.addStop(platformId);
		}
	}

	public final void addTrip(Trip trip) {
		if (isIncludeReferences()) {
			references.addTrip(trip);
		}
	}

	public abstract JsonObject toJson(Simulator simulator);

	protected abstract boolean isIncludeReferences();
}
