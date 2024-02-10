package org.mtr.core.servlet;

import org.mtr.core.serializer.JsonReader;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;

import javax.annotation.Nullable;

public final class OBAServlet extends ServletBase {

	public OBAServlet(ObjectImmutableList<Simulator> simulators) {
		super(simulators);
	}

	@Nullable
	@Override
	public JsonObject getContent(String endpoint, String data, Object2ObjectAVLTreeMap<String, String> parameters, JsonReader jsonReader, long currentMillis, Simulator simulator) {
		final OBAResponse obaResponse = new OBAResponse(data, parameters, currentMillis, simulator);
		switch (endpoint) {
			case "agencies-with-coverage":
				return obaResponse.getAgenciesWithCoverage();
			case "agency":
				return obaResponse.getAgency();
			case "arrival-and-departure-for-stop":
				return new JsonObject();
			case "arrivals-and-departures-for-stop":
				return obaResponse.getArrivalsAndDeparturesForStop();
			case "arrivals-and-departures-for-location":
				return new JsonObject();
			case "block":
				return new JsonObject();
			case "cancel-alarm":
				return new JsonObject();
			case "current-time":
				return new JsonObject();
			case "register-alarm-for-arrival-and-departure-at-stop":
				return new JsonObject();
			case "report-problem-with-stop":
				return new JsonObject();
			case "report-problem-with-trip":
				return new JsonObject();
			case "route-ids-for-agency":
				return new JsonObject();
			case "route":
				return new JsonObject();
			case "routes-for-agency":
				return new JsonObject();
			case "routes-for-location":
				return new JsonObject();
			case "schedule-for-route":
				return new JsonObject();
			case "schedule-for-stop":
				return new JsonObject();
			case "shape":
				return new JsonObject();
			case "stop-ids-for-agency":
				return new JsonObject();
			case "stop":
				return new JsonObject();
			case "stops-for-location":
				return obaResponse.getStopsForLocation();
			case "stops-for-route":
				return new JsonObject();
			case "trip-details":
				return obaResponse.getTripDetails();
			case "trip-for-vehicle":
				return new JsonObject();
			case "trip":
				return new JsonObject();
			case "trips-for-location":
				return new JsonObject();
			case "trips-for-route":
				return new JsonObject();
			case "vehicles-for-agency":
				return new JsonObject();
			default:
				return null;
		}
	}
}
