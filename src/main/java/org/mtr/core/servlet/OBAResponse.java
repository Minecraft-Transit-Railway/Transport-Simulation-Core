package org.mtr.core.servlet;

import org.mtr.core.data.Platform;
import org.mtr.core.data.Siding;
import org.mtr.core.oba.*;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.LatLon;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;

public final class OBAResponse extends ResponseBase<Object> {

	private final boolean includeReferences;

	private static final Agency AGENCY = new Agency();

	public OBAResponse(String data, Object2ObjectAVLTreeMap<String, String> parameters, long currentMillis, Simulator simulator) {
		super(data, parameters, new Object(), currentMillis, simulator);
		includeReferences = !("false".equals(parameters.get("includeReferences")));
	}

	public JsonObject getAgenciesWithCoverage() {
		final ListElement<AgencyWithCoverage> listElement = ListElement.create(includeReferences, AGENCY);
		listElement.add(new AgencyWithCoverage());
		return listElement.toJson(simulator);
	}

	public JsonObject getAgency() {
		if (data.equals("1")) {
			final SingleElement<Agency> singleElement = SingleElement.create(includeReferences, AGENCY);
			singleElement.set(AGENCY);
			return singleElement.toJson(simulator);
		} else {
			return null;
		}
	}

	public JsonObject getArrivalsAndDeparturesForStop() {
		try {
			final long platformId = Long.parseUnsignedLong(data, 16);
			final Platform platform = simulator.platformIdMap.get(platformId);
			final SingleElement<StopWithArrivalsAndDepartures> singleElement = SingleElement.create(includeReferences, AGENCY);
			final StopWithArrivalsAndDepartures stopWithArrivalsAndDepartures = new StopWithArrivalsAndDepartures(platform.getHexId());
			singleElement.set(stopWithArrivalsAndDepartures);
			singleElement.addStop(platformId);

			if (platform.area != null) {
				platform.area.savedRails.forEach(nearbyPlatform -> {
					if (nearbyPlatform.getId() != platformId) {
						singleElement.addStop(nearbyPlatform.getId());
						stopWithArrivalsAndDepartures.add(nearbyPlatform.getHexId());
					}
				});
			}

			platform.routes.forEach(route -> route.depots.forEach(depot -> depot.savedRails.forEach(siding -> siding.getOBAArrivalsAndDeparturesElementsWithTripsUsed(
					singleElement,
					stopWithArrivalsAndDepartures,
					currentMillis,
					platform,
					Math.max(0, (int) getParameter("minutesBefore", 5)) * 60000,
					Math.max(0, (int) getParameter("minutesAfter", 35)) * 60000
			))));

			return singleElement.toJson(simulator);
		} catch (Exception ignored) {
		}

		return null;
	}

	public JsonObject getStopsForLocation() {
		final LatLon latLon = getLatLonParameter();

		if (latLon == null) {
			return ListElement.create(includeReferences, AGENCY).toJson(simulator);
		} else {
			final double latSpan;
			final double lonSpan;

			if (containsParameter("latSpan") && containsParameter("lonSpan")) {
				latSpan = Math.abs(getParameter("latSpan", 0)) / 2;
				lonSpan = Math.abs(getParameter("lonSpan", 0)) / 2;
			} else {
				final double radius = getParameter("radius", 100);
				latSpan = LatLon.metersToLat(radius) / 2;
				lonSpan = LatLon.metersToLon(radius) / 2;
			}

			final ListElement<Stop> listElement = ListElement.create(includeReferences, AGENCY);
			for (final Platform platform : simulator.platforms) {
				final LatLon platformLatLon = new LatLon(platform.getMidPosition());
				if (Utilities.isBetween(platformLatLon.lat - latLon.lat, -latSpan, latSpan) && Utilities.isBetween(platformLatLon.lon - latLon.lon, -lonSpan, lonSpan) && !platform.routeColors.isEmpty()) {
					final IntAVLTreeSet colorsUsed = new IntAVLTreeSet();
					if (listElement.add(platform.getOBAStopElement(colorsUsed))) {
						colorsUsed.forEach(listElement::addRoute);
					} else {
						break;
					}
				}
			}

			return listElement.toJson(simulator);
		}
	}

	public JsonObject getTripDetails() {
		final String[] tripIdSplit = data.split("_");
		if (tripIdSplit.length == 4) {
			try {
				final Siding siding = simulator.sidingIdMap.get(Long.parseUnsignedLong(tripIdSplit[0], 16));
				if (siding != null) {
					final SingleElement<TripDetails> singleElement = SingleElement.create(includeReferences, AGENCY);
					siding.getOBATripDetailsWithDataUsed(singleElement, currentMillis, Integer.parseInt(tripIdSplit[1]), Integer.parseInt(tripIdSplit[2]), Long.parseLong(tripIdSplit[3]));
					return singleElement.toJson(simulator);
				}
			} catch (Exception ignored) {
			}
		}
		return null;
	}

	private LatLon getLatLonParameter() {
		try {
			return new LatLon(Double.parseDouble(parameters.get("lat")), Double.parseDouble(parameters.get("lon")));
		} catch (Exception ignored) {
		}
		return null;
	}

	private double getParameter(String name, double defaultValue) {
		try {
			return Double.parseDouble(parameters.get(name));
		} catch (Exception ignored) {
		}
		return defaultValue;
	}

	private boolean containsParameter(String name) {
		return parameters.get(name) != null;
	}
}
