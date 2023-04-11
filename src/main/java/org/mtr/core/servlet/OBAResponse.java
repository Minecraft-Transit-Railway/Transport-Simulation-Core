package org.mtr.core.servlet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.LatLon;
import org.mtr.core.tools.Utilities;

import javax.servlet.http.HttpServletRequest;
import java.util.TimeZone;

public class OBAResponse {

	private final String data;
	private final HttpServletRequest request;
	private final Simulator simulator;
	private final boolean includeReferences;

	private static final JsonObject AGENCY = new JsonObject();

	static {
		AGENCY.addProperty("disclaimer", "");
		AGENCY.addProperty("email", "");
		AGENCY.addProperty("fareUrl", "");
		AGENCY.addProperty("id", "1");
		AGENCY.addProperty("lang", "en");
		AGENCY.addProperty("name", "My Agency");
		AGENCY.addProperty("phone", "");
		AGENCY.addProperty("privateService", false);
		AGENCY.addProperty("timezone", TimeZone.getDefault().getID());
		AGENCY.addProperty("url", "https://github.com/jonafanho/Transport-Simulation-Core");
	}

	public OBAResponse(String data, HttpServletRequest request, Simulator simulator) {
		this.data = data;
		this.request = request;
		this.simulator = simulator;
		includeReferences = !("false".equals(request.getParameter("includeReferences")));
	}

	public JsonObject getAgenciesWithCoverage() {
		final JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("agencyId", "1");
		jsonObject.addProperty("lat", 0);
		jsonObject.addProperty("latSpan", LatLon.MAX_LAT * 2);
		jsonObject.addProperty("lon", 0);
		jsonObject.addProperty("lonSpan", LatLon.MAX_LON * 2);

		final JsonArray jsonArray = new JsonArray();
		jsonArray.add(jsonObject);
		return getListElement(jsonArray, false, null, null, null);
	}

	public JsonObject getAgency() {
		if (data.equals("1")) {
			return getSingleElement(AGENCY, null, null, null);
		} else {
			return null;
		}
	}

	public JsonObject getArrivalsAndDeparturesForStop() {
		try {
			final long platformId = Long.parseUnsignedLong(data, 16);
			final Platform platform = simulator.dataCache.platformIdMap.get(platformId);

			final LongArraySet platformIdsUsed = new LongArraySet();
			platformIdsUsed.add(platformId);

			final JsonArray nearbyPlatformsArray = new JsonArray();
			if (platform.area != null) {
				platform.area.savedRails.forEach(nearbyPlatform -> {
					if (nearbyPlatform.id != platformId) {
						nearbyPlatformsArray.add(nearbyPlatform.getHexId());
						platformIdsUsed.add(nearbyPlatform.id);
					}
				});
			}

			final JsonArray arrivalsAndDeparturesArray = new JsonArray();
			final JsonArray tripsUsedArray = new JsonArray();
			simulator.dataCache.platformIdToSidings.getOrDefault(platformId, new ObjectArraySet<>()).forEach(siding -> siding.schedule.getOBAArrivalsAndDeparturesElementsWithTripsUsed(
					platform,
					Math.max(0, (int) getParameter("minutesBefore", 5)) * 60000,
					Math.max(0, (int) getParameter("minutesAfter", 35)) * 60000,
					arrivalsAndDeparturesArray,
					tripsUsedArray
			));

			final JsonObject jsonObject = new JsonObject();
			jsonObject.add("arrivalsAndDepartures", arrivalsAndDeparturesArray);
			jsonObject.add("nearbyStopIds", nearbyPlatformsArray);
			jsonObject.add("situationIds", new JsonArray());
			jsonObject.addProperty("stopId", data);
			return getSingleElement(jsonObject, new IntArraySet(), platformIdsUsed, tripsUsedArray);
		} catch (Exception ignored) {
		}
		return null;
	}

	public JsonObject getStopsForLocation() {
		final LatLon latLon = getLatLonParameter();

		if (latLon == null) {
			return getListElement(new JsonArray(), false, null, null, null);
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

			final JsonArray jsonArray = new JsonArray();
			final IntArraySet colorsUsed = new IntArraySet();
			int count = 0;
			for (final Platform platform : simulator.platforms) {
				final LatLon platformLatLon = new LatLon(platform.getMidPosition());
				if (Utilities.isBetween(platformLatLon.lat - latLon.lat, -latSpan, latSpan) && Utilities.isBetween(platformLatLon.lon - latLon.lon, -lonSpan, lonSpan) && !simulator.dataCache.platformIdToRouteColors.getOrDefault(platform.id, new IntArraySet()).isEmpty()) {
					jsonArray.add(platform.getOBAStopElement(simulator.dataCache, colorsUsed));
					count++;
					if (count == 100) {
						break;
					}
				}
			}

			return getListElement(jsonArray, count == 100, colorsUsed, null, null);
		}
	}

	private LatLon getLatLonParameter() {
		try {
			return new LatLon(Double.parseDouble(request.getParameter("lat")), Double.parseDouble(request.getParameter("lon")));
		} catch (Exception ignored) {
		}
		return null;
	}

	private double getParameter(String name, double defaultValue) {
		try {
			return Double.parseDouble(request.getParameter(name));
		} catch (Exception ignored) {
		}
		return defaultValue;
	}

	private boolean containsParameter(String name) {
		return request.getParameter(name) != null;
	}

	private JsonObject getReferences(IntArraySet colorsUsed, LongArraySet platformIdsUsed, JsonArray tripsUsed) {
		final JsonArray agenciesArray = new JsonArray();
		if (includeReferences) {
			agenciesArray.add(AGENCY);
		}

		final JsonArray stopsArray = new JsonArray();
		if (includeReferences && platformIdsUsed != null) {
			platformIdsUsed.forEach(platformId -> {
				final Platform platform = simulator.dataCache.platformIdMap.get(platformId);
				if (platform != null) {
					stopsArray.add(platform.getOBAStopElement(simulator.dataCache, colorsUsed));
				}
			});
		}

		final JsonArray routesArray = new JsonArray();
		if (includeReferences && colorsUsed != null) {
			colorsUsed.forEach(color -> {
				final Route route = simulator.dataCache.routeColorMap.get(color);
				if (route != null) {
					routesArray.add(route.getOBARouteElement());
				}
			});
		}

		final JsonObject jsonObject = new JsonObject();
		jsonObject.add("agencies", agenciesArray);
		jsonObject.add("routes", routesArray);
		jsonObject.add("situations", new JsonArray());
		jsonObject.add("stopTimes", new JsonArray());
		jsonObject.add("stops", stopsArray);
		jsonObject.add("trips", tripsUsed == null ? new JsonArray() : tripsUsed);
		return jsonObject;
	}

	private JsonObject getSingleElement(JsonObject entryObject, IntArraySet colorsUsed, LongArraySet platformIdsUsed, JsonArray tripsUsed) {
		final JsonObject jsonObject = new JsonObject();
		jsonObject.add("entry", entryObject);
		jsonObject.add("references", getReferences(colorsUsed, platformIdsUsed, tripsUsed));
		return jsonObject;
	}

	private JsonObject getListElement(JsonArray listArray, boolean limitExceeded, IntArraySet colorsUsed, LongArraySet platformIdsUsed, JsonArray tripsUsed) {
		final JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("limitExceeded", limitExceeded);
		jsonObject.add("list", listArray);
		jsonObject.addProperty("outOfRange", false);
		jsonObject.add("references", getReferences(colorsUsed, platformIdsUsed, tripsUsed));
		return jsonObject;
	}
}
