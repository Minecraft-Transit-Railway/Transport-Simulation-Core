package org.mtr.core.servlet;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import lombok.extern.log4j.Log4j2;
import org.jspecify.annotations.Nullable;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Siding;
import org.mtr.core.oba.*;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.LatLon;
import org.mtr.core.tool.Utilities;

/**
 * Builds the JSON response bodies for the OneBusAway-compatible read endpoints exposed under
 * {@code /oba/api/where/*} (see {@link OBAServlet}).
 *
 * <p>Each {@code getXxx} method maps directly to one OBA endpoint and returns either a fully
 * populated {@link JsonObject} or {@code null} to signal "no such resource" (which the servlet
 * layer translates into a {@code 404}). The OBA payload shape (the {@link SingleElement} /
 * {@link ListElement} envelopes) is fixed by the OBA specification.</p>
 */
@Log4j2
public final class OBAResponse extends ResponseBase<Object> {

	private final boolean includeReferences;

	private static final Agency AGENCY = new Agency();

	/**
	 * @param data            path-suffix the request was made against (interpreted per endpoint)
	 * @param parameters      query-string parameters keyed by name
	 * @param currentMillis   simulator-wall-clock millis at the time the request was admitted
	 * @param simulator       simulator the request was routed to
	 */
	public OBAResponse(String data, Object2ObjectAVLTreeMap<String, String> parameters, long currentMillis, Simulator simulator) {
		super(data, parameters, new Object(), currentMillis, simulator);
		includeReferences = !"false".equals(parameters.get("includeReferences"));
	}

	/** @return JSON envelope for {@code /oba/api/where/agencies-with-coverage} */
	public JsonObject getAgenciesWithCoverage() {
		final ListElement<AgencyWithCoverage> listElement = ListElement.create(includeReferences, AGENCY);
		listElement.add(new AgencyWithCoverage());
		return listElement.toJson(simulator);
	}

	/** @return JSON envelope for {@code /oba/api/where/agency/{id}}, or {@code null} if {@code id != "1"} */
	@Nullable
	public JsonObject getAgency() {
		if (data.equals("1")) {
			final SingleElement<Agency> singleElement = SingleElement.create(includeReferences, AGENCY);
			singleElement.set(AGENCY);
			return singleElement.toJson(simulator);
		} else {
			return null;
		}
	}

	/**
	 * @return JSON envelope for {@code /oba/api/where/arrivals-and-departures-for-stop/{platformIdHex}},
	 * or {@code null} if the platform id does not parse / does not resolve.
	 */
	@Nullable
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

			final LongAVLTreeSet visitedSidingIds = new LongAVLTreeSet();
			platform.routes.forEach(route -> route.depots.forEach(depot -> depot.savedRails.forEach(siding -> {
				if (!visitedSidingIds.contains(siding.getId())) {
					visitedSidingIds.add(siding.getId());
					siding.getOBAArrivalsAndDeparturesElementsWithTripsUsed(
						singleElement,
						stopWithArrivalsAndDepartures,
						currentMillis,
						platform,
						Math.max(0, (int) getParameter("minutesBefore", 5)) * 60000,
						Math.max(0, (int) getParameter("minutesAfter", 35)) * 60000
					);
				}
			})));

			return singleElement.toJson(simulator);
		} catch (Exception e) {
			// Bad hex id, missing platform, or NPE inside platform.routes — all map to a 404.
			// Logged at debug so a flood of malformed client requests stays silent (§3.14).
			log.debug("getArrivalsAndDeparturesForStop({}) returning null", data, e);
		}

		return null;
	}

	/** @return JSON envelope for {@code /oba/api/where/stops-for-location} */
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
				if (Utilities.isBetween(platformLatLon.lat() - latLon.lat(), -latSpan, latSpan) && Utilities.isBetween(platformLatLon.lon() - latLon.lon(), -lonSpan, lonSpan) && !platform.routeColors.isEmpty()) {
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

	/**
	 * @return JSON envelope for {@code /oba/api/where/trip-details/{tripId}}, or {@code null} if
	 * the composite trip id does not parse / does not resolve.
	 */
	@Nullable
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
			} catch (Exception e) {
				// Malformed components in the composite trip id — fall through to a 404. (§3.14)
				log.debug("getTripDetails({}) returning null", data, e);
			}
		}
		return null;
	}

	@Nullable
	private LatLon getLatLonParameter() {
		try {
			return new LatLon(Double.parseDouble(parameters.get("lat")), Double.parseDouble(parameters.get("lon")));
		} catch (Exception e) {
			// Missing or non-numeric lat/lon query parameters — caller treats null as "no anchor".
			log.debug("getLatLonParameter() returning null (lat={}, lon={})", parameters.get("lat"), parameters.get("lon"), e);
		}
		return null;
	}

	private double getParameter(String name, double defaultValue) {
		try {
			return Double.parseDouble(parameters.get(name));
		} catch (Exception e) {
			// Missing or non-numeric parameter — fall back to the documented default. (§3.14)
			log.debug("getParameter({}) falling back to default {}", name, defaultValue, e);
		}
		return defaultValue;
	}

	private boolean containsParameter(String name) {
		return parameters.get(name) != null;
	}
}
