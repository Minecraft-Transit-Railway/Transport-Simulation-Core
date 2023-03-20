package org.mtr.core.servlet;

import com.google.gson.JsonObject;
import org.mtr.core.simulation.Simulator;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class DataServlet extends ServletBase {

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) {
		sendResponse(request, response);
	}

	@Override
	public JsonObject getContent(HttpServletRequest request, Simulator simulator) {
		final String[] requestUri = request.getRequestURI().split("/");
		final String endpoint = requestUri[3].split("\\.")[0];
		switch (endpoint) {
			case "agencies-with-coverage":
				return buildResponseObject(new JsonObject());
			case "agency":
				return buildResponseObject(new JsonObject());
			case "arrival-and-departure-for-stop":
				return buildResponseObject(new JsonObject());
			case "arrivals-and-departures-for-stop":
				return buildResponseObject(new JsonObject());
			case "arrivals-and-departures-for-location":
				return buildResponseObject(new JsonObject());
			case "block":
				return buildResponseObject(new JsonObject());
			case "cancel-alarm":
				return buildResponseObject(new JsonObject());
			case "current-time":
				return buildResponseObject(new JsonObject());
			case "register-alarm-for-arrival-and-departure-at-stop":
				return buildResponseObject(new JsonObject());
			case "report-problem-with-stop":
				return buildResponseObject(new JsonObject());
			case "report-problem-with-trip":
				return buildResponseObject(new JsonObject());
			case "route-ids-for-agency":
				return buildResponseObject(new JsonObject());
			case "route":
				return buildResponseObject(new JsonObject());
			case "routes-for-agency":
				return buildResponseObject(new JsonObject());
			case "routes-for-location":
				return buildResponseObject(new JsonObject());
			case "schedule-for-route":
				return buildResponseObject(new JsonObject());
			case "schedule-for-stop":
				return buildResponseObject(new JsonObject());
			case "shape":
				return buildResponseObject(new JsonObject());
			case "stop-ids-for-agency":
				return buildResponseObject(new JsonObject());
			case "stop":
				return buildResponseObject(new JsonObject());
			case "stops-for-location":
				return buildResponseObject(new JsonObject());
			case "stops-for-route":
				return buildResponseObject(new JsonObject());
			case "trip-details":
				return buildResponseObject(new JsonObject());
			case "trip-for-vehicle":
				return buildResponseObject(new JsonObject());
			case "trip":
				return buildResponseObject(new JsonObject());
			case "trips-for-location":
				return buildResponseObject(new JsonObject());
			case "trips-for-route":
				return buildResponseObject(new JsonObject());
			case "vehicles-for-agency":
				return buildResponseObject(new JsonObject());
			default:
				return buildResponseObject(ResponseCode.NOT_FOUND, endpoint);
		}
	}
}
