package org.mtr.core.servlet;

import com.google.gson.JsonObject;
import org.mtr.core.simulation.Simulator;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class OBAServlet extends ServletBase {

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) {
		sendResponse(request, response);
	}

	@Override
	public JsonObject getContent(String endpoint, String data, HttpServletRequest request, long currentMillis, Simulator simulator) {
		final OBAResponse obaResponse = new OBAResponse(data, request, currentMillis, simulator);
		switch (endpoint) {
			case "agencies-with-coverage":
				return buildResponseObject(currentMillis, obaResponse.getAgenciesWithCoverage());
			case "agency":
				return buildResponseObject(currentMillis, obaResponse.getAgency(), data);
			case "arrival-and-departure-for-stop":
				return buildResponseObject(currentMillis, new JsonObject());
			case "arrivals-and-departures-for-stop":
				return buildResponseObject(currentMillis, obaResponse.getArrivalsAndDeparturesForStop(), data);
			case "arrivals-and-departures-for-location":
				return buildResponseObject(currentMillis, new JsonObject());
			case "block":
				return buildResponseObject(currentMillis, new JsonObject());
			case "cancel-alarm":
				return buildResponseObject(currentMillis, new JsonObject());
			case "current-time":
				return buildResponseObject(currentMillis, new JsonObject());
			case "register-alarm-for-arrival-and-departure-at-stop":
				return buildResponseObject(currentMillis, new JsonObject());
			case "report-problem-with-stop":
				return buildResponseObject(currentMillis, new JsonObject());
			case "report-problem-with-trip":
				return buildResponseObject(currentMillis, new JsonObject());
			case "route-ids-for-agency":
				return buildResponseObject(currentMillis, new JsonObject());
			case "route":
				return buildResponseObject(currentMillis, new JsonObject());
			case "routes-for-agency":
				return buildResponseObject(currentMillis, new JsonObject());
			case "routes-for-location":
				return buildResponseObject(currentMillis, new JsonObject());
			case "schedule-for-route":
				return buildResponseObject(currentMillis, new JsonObject());
			case "schedule-for-stop":
				return buildResponseObject(currentMillis, new JsonObject());
			case "shape":
				return buildResponseObject(currentMillis, new JsonObject());
			case "stop-ids-for-agency":
				return buildResponseObject(currentMillis, new JsonObject());
			case "stop":
				return buildResponseObject(currentMillis, new JsonObject());
			case "stops-for-location":
				return buildResponseObject(currentMillis, obaResponse.getStopsForLocation());
			case "stops-for-route":
				return buildResponseObject(currentMillis, new JsonObject());
			case "trip-details":
				return buildResponseObject(currentMillis, obaResponse.getTripDetails(), data);
			case "trip-for-vehicle":
				return buildResponseObject(currentMillis, new JsonObject());
			case "trip":
				return buildResponseObject(currentMillis, new JsonObject());
			case "trips-for-location":
				return buildResponseObject(currentMillis, new JsonObject());
			case "trips-for-route":
				return buildResponseObject(currentMillis, new JsonObject());
			case "vehicles-for-agency":
				return buildResponseObject(currentMillis, new JsonObject());
			default:
				return buildResponseObject(currentMillis, ResponseCode.NOT_FOUND, endpoint);
		}
	}
}
