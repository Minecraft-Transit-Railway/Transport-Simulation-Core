package org.mtr.core.servlet;

import org.mtr.core.map.Departures;
import org.mtr.core.map.StationAndRoutes;
import org.mtr.core.operation.ArrivalsRequest;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;

import java.util.function.Consumer;

public final class SystemMapServlet extends ServletBase {

	private final Object2ObjectAVLTreeMap<String, CachedResponse> stationsAndRoutesResponses = new Object2ObjectAVLTreeMap<>();
	private final CachedResponse departuresResponse = new CachedResponse(simulator -> {
		final long currentMillis = System.currentTimeMillis();
		final Object2ObjectAVLTreeMap<String, Long2ObjectAVLTreeMap<LongArrayList>> departures = new Object2ObjectAVLTreeMap<>();
		simulator.sidings.forEach(siding -> {
			if (!siding.getTransportMode().continuousMovement) {
				siding.getDepartures(currentMillis, departures);
			}
		});
		return Utilities.getJsonObjectFromData(new Departures(currentMillis, departures));
	}, 3000);

	public SystemMapServlet(ObjectImmutableList<Simulator> simulators) {
		super(simulators);
	}

	@Override
	public void getContent(String endpoint, String data, Object2ObjectAVLTreeMap<String, String> parameters, JsonReader jsonReader, Simulator simulator, Consumer<JsonObject> sendResponse) {
		final JsonObject response;
		switch (endpoint) {
			case "stations-and-routes":
				response = stationsAndRoutesResponses.computeIfAbsent(simulator.dimension, key -> new CachedResponse(SystemMapServlet::getStationsAndRoutes, 30000)).get(simulator);
				break;
			case "departures":
				response = departuresResponse.get(simulator);
				break;
			case "arrivals":
				response = Utilities.getJsonObjectFromData(new ArrivalsRequest(jsonReader).getArrivals(simulator));
				break;
			default:
				response = new JsonObject();
				break;
		}
		sendResponse.accept(response);
	}

	private static JsonObject getStationsAndRoutes(Simulator simulator) {
		final StationAndRoutes stationAndRoutes = new StationAndRoutes(simulator.dimensions);
		simulator.stations.forEach(stationAndRoutes::addStation);
		simulator.routes.forEach(stationAndRoutes::addRoute);
		return Utilities.getJsonObjectFromData(stationAndRoutes);
	}
}
