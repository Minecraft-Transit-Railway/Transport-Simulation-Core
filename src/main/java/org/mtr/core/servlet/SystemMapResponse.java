package org.mtr.core.servlet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.Position;

public class SystemMapResponse extends ResponseBase {

	public SystemMapResponse(String data, Object2ObjectAVLTreeMap<String, String> parameters, long currentMillis, Simulator simulator) {
		super(data, parameters, currentMillis, simulator);
	}

	public JsonObject getData() {
		final JsonArray stationsArray = new JsonArray();
		simulator.stations.forEach(station -> {
			final Position position = station.getCenter();
			if (position != null) {
				final JsonArray connectionsArray = new JsonArray();
				simulator.dataCache.stationIdToConnectingStations.get(station).forEach(connectingStation -> connectionsArray.add(connectingStation.id));

				final JsonObject jsonObject = new JsonObject();
				jsonObject.addProperty("id", station.id);
				jsonObject.addProperty("name", station.name);
				jsonObject.addProperty("color", station.color);
				jsonObject.addProperty("zone", station.zone);
				jsonObject.addProperty("x", position.x);
				jsonObject.addProperty("z", position.z);
				jsonObject.add("connections", connectionsArray);

				stationsArray.add(jsonObject);
			}
		});

		final JsonArray routesArray = new JsonArray();
		simulator.routes.forEach(route -> {
			final JsonArray routeStationsArray = new JsonArray();
			route.platformIds.forEach(routePlatform -> {
				final Platform platform = simulator.dataCache.platformIdMap.get(routePlatform.platformId);
				if (platform != null && platform.area != null) {
					routeStationsArray.add(platform.area.id);
				}
			});

			final JsonObject jsonObject = new JsonObject();
			jsonObject.addProperty("name", route.name);
			jsonObject.addProperty("color", route.color);
			jsonObject.addProperty("number", route.routeNumber);
			jsonObject.addProperty("type", route.transportMode.toString());
			jsonObject.addProperty("circular", route.circularState == Route.CircularState.NONE ? "" : route.circularState.toString());
			jsonObject.add("stations", routeStationsArray);

			routesArray.add(jsonObject);
		});

		final JsonObject jsonObject = new JsonObject();
		jsonObject.add("stations", stationsArray);
		jsonObject.add("routes", routesArray);
		return jsonObject;
	}
}
