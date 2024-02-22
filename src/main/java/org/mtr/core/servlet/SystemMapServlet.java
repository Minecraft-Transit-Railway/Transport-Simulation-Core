package org.mtr.core.servlet;

import org.mtr.core.map.StationAndRoutes;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;

import java.util.function.Consumer;

public final class SystemMapServlet extends ServletBase {

	public SystemMapServlet(ObjectImmutableList<Simulator> simulators) {
		super(simulators);
	}

	@Override
	public void getContent(String endpoint, String data, Object2ObjectAVLTreeMap<String, String> parameters, JsonReader jsonReader, long currentMillis, Simulator simulator, Consumer<JsonObject> sendResponse) {
		final StationAndRoutes stationAndRoutes = new StationAndRoutes();
		simulator.stations.forEach(stationAndRoutes::addStation);
		simulator.routes.forEach(stationAndRoutes::addRoute);
		sendResponse.accept(Utilities.getJsonObjectFromData(stationAndRoutes));
	}
}
