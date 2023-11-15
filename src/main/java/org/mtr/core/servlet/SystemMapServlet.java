package org.mtr.core.servlet;

import org.mtr.core.map.StationAndRoutes;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.webserver.Webserver;

public final class SystemMapServlet extends ServletBase {

	public SystemMapServlet(Webserver webserver, String path, ObjectImmutableList<Simulator> simulators) {
		super(webserver, path, simulators);
	}

	@Override
	public JsonObject getContent(String endpoint, String data, Object2ObjectAVLTreeMap<String, String> parameters, JsonReader jsonReader, long currentMillis, Simulator simulator) {
		final StationAndRoutes stationAndRoutes = new StationAndRoutes();
		simulator.stations.forEach(stationAndRoutes::addStation);
		simulator.routes.forEach(stationAndRoutes::addRoute);
		return Utilities.getJsonObjectFromData(stationAndRoutes);
	}
}
