package org.mtr.core.oba;

import org.mtr.core.generated.oba.StopWithArrivalsAndDeparturesSchema;

public final class StopWithArrivalsAndDepartures extends StopWithArrivalsAndDeparturesSchema {

	public StopWithArrivalsAndDepartures(String stopId) {
		super(stopId);
	}

	public void add(ArrivalAndDeparture arrivalAndDeparture) {
		arrivalsAndDepartures.add(arrivalAndDeparture);
	}

	public void add(String platformId) {
		nearbyStopIds.add(platformId);
	}
}
