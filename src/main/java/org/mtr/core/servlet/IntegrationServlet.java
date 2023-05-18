package org.mtr.core.servlet;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.core.data.*;
import org.mtr.core.simulation.Simulator;
import org.mtr.webserver.Webserver;

public class IntegrationServlet extends ServletBase {

	public IntegrationServlet(Webserver webserver, String path, ObjectImmutableList<Simulator> simulators) {
		super(webserver, path, simulators);
	}

	@Override
	public JsonObject getContent(String endpoint, String data, Object2ObjectAVLTreeMap<String, String> parameters, JsonObject bodyObject, long currentMillis, Simulator simulator) {
		final IntegrationResponse integrationResponse = new IntegrationResponse(data, parameters, bodyObject, currentMillis, simulator);
		switch (endpoint) {
			case "update-station":
				return integrationResponse.update(simulator.stations, simulator.dataCache.stationIdMap, jsonReader -> new Station(jsonReader, simulator));
			case "update-platform":
				return integrationResponse.update(simulator.platforms, simulator.dataCache.platformIdMap, jsonReader -> new Platform(jsonReader, simulator));
			case "update-siding":
				return integrationResponse.update(simulator.sidings, simulator.dataCache.sidingIdMap, jsonReader -> new Siding(jsonReader, simulator));
			case "update-route":
				return integrationResponse.update(simulator.routes, simulator.dataCache.routeIdMap, jsonReader -> new Route(jsonReader, simulator));
			case "update-depot":
				return integrationResponse.update(simulator.depots, simulator.dataCache.depotIdMap, jsonReader -> new Depot(jsonReader, simulator));
			default:
				return null;
		}
	}
}
