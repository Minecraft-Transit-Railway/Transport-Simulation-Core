package org.mtr.core.oba;

import org.mtr.core.generated.oba.StopWithArrivalsAndDeparturesSchema;

import java.util.Collections;

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

	public void sort() {
		Collections.sort(arrivalsAndDepartures);
	}
}
