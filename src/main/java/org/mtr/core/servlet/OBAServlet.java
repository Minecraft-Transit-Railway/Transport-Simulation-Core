package org.mtr.core.servlet;

import org.mtr.core.serializer.JsonReader;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;

import java.util.function.Consumer;

public final class OBAServlet extends ServletBase {

	public OBAServlet(ObjectImmutableList<Simulator> simulators) {
		super(simulators);
	}

	@Override
	public void getContent(String endpoint, String data, Object2ObjectAVLTreeMap<String, String> parameters, JsonReader jsonReader, Simulator simulator, Consumer<JsonObject> sendResponse) {
		final OBAResponse obaResponse = new OBAResponse(data, parameters, simulator.getCurrentMillis(), simulator);
		switch (endpoint) {
			case "agencies-with-coverage":
				sendResponse.accept(obaResponse.getAgenciesWithCoverage());
				break;
			case "agency":
				sendResponse.accept(obaResponse.getAgency());
				break;
			case "arrival-and-departure-for-stop":
				sendResponse.accept(new JsonObject());
				break;
			case "arrivals-and-departures-for-stop":
				sendResponse.accept(obaResponse.getArrivalsAndDeparturesForStop());
				break;
			case "arrivals-and-departures-for-location":
				sendResponse.accept(new JsonObject());
				break;
			case "block":
				sendResponse.accept(new JsonObject());
				break;
			case "cancel-alarm":
				sendResponse.accept(new JsonObject());
				break;
			case "current-time":
				sendResponse.accept(new JsonObject());
				break;
			case "register-alarm-for-arrival-and-departure-at-stop":
				sendResponse.accept(new JsonObject());
				break;
			case "report-problem-with-stop":
				sendResponse.accept(new JsonObject());
				break;
			case "report-problem-with-trip":
				sendResponse.accept(new JsonObject());
				break;
			case "route-ids-for-agency":
				sendResponse.accept(new JsonObject());
				break;
			case "route":
				sendResponse.accept(new JsonObject());
				break;
			case "routes-for-agency":
				sendResponse.accept(new JsonObject());
				break;
			case "routes-for-location":
				sendResponse.accept(new JsonObject());
				break;
			case "schedule-for-route":
				sendResponse.accept(new JsonObject());
				break;
			case "schedule-for-stop":
				sendResponse.accept(new JsonObject());
				break;
			case "shape":
				sendResponse.accept(new JsonObject());
				break;
			case "stop-ids-for-agency":
				sendResponse.accept(new JsonObject());
				break;
			case "stop":
				sendResponse.accept(new JsonObject());
				break;
			case "stops-for-location":
				sendResponse.accept(obaResponse.getStopsForLocation());
				break;
			case "stops-for-route":
				sendResponse.accept(new JsonObject());
				break;
			case "trip-details":
				sendResponse.accept(obaResponse.getTripDetails());
				break;
			case "trip-for-vehicle":
				sendResponse.accept(new JsonObject());
				break;
			case "trip":
				sendResponse.accept(new JsonObject());
				break;
			case "trips-for-location":
				sendResponse.accept(new JsonObject());
				break;
			case "trips-for-route":
				sendResponse.accept(new JsonObject());
				break;
			case "vehicles-for-agency":
				sendResponse.accept(new JsonObject());
				break;
			default:
				sendResponse.accept(null);
				break;
		}
	}
}
