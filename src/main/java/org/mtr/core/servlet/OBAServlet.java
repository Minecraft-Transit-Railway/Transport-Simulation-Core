package org.mtr.core.servlet;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.jspecify.annotations.Nullable;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.simulation.Simulator;

import java.util.function.Consumer;

public final class OBAServlet extends ServletBase {

	public OBAServlet(ObjectImmutableList<Simulator> simulators) {
		super(simulators);
	}

	@Override
	public void getContent(String endpoint, String data, Object2ObjectAVLTreeMap<String, String> parameters, JsonReader jsonReader, Simulator simulator, Consumer<@Nullable JsonObject> sendResponse) {
		final OBAResponse obaResponse = new OBAResponse(data, parameters, simulator.getCurrentMillis(), simulator);
		switch (endpoint) {
			// --- Implemented endpoints ---
			case "agencies-with-coverage" -> sendResponse.accept(obaResponse.getAgenciesWithCoverage());
			case "agency" -> sendResponse.accept(obaResponse.getAgency());
			case "arrivals-and-departures-for-stop" -> sendResponse.accept(obaResponse.getArrivalsAndDeparturesForStop());
			case "stops-for-location" -> sendResponse.accept(obaResponse.getStopsForLocation());
			case "trip-details" -> sendResponse.accept(obaResponse.getTripDetails());
			// --- OneBusAway compatibility stubs: documented but not yet implemented. ---
			// These endpoints exist so that OBA client libraries probing the full surface get
			// a well-formed (empty) response instead of a 404. Do not rely on a non-empty
			// payload — see docs/API.md for the implementation status.
			case "arrival-and-departure-for-stop",
			     "arrivals-and-departures-for-location",
			     "block",
			     "cancel-alarm",
			     "current-time",
			     "register-alarm-for-arrival-and-departure-at-stop",
			     "report-problem-with-stop",
			     "report-problem-with-trip",
			     "route-ids-for-agency",
			     "route",
			     "routes-for-agency",
			     "routes-for-location",
			     "schedule-for-route",
			     "schedule-for-stop",
			     "shape",
			     "stop-ids-for-agency",
			     "stop",
			     "stops-for-route",
			     "trip-for-vehicle",
			     "trip",
			     "trips-for-location",
			     "trips-for-route",
			     "vehicles-for-agency" -> sendResponse.accept(new JsonObject());
			default -> sendResponse.accept(null);
		}
	}
}
