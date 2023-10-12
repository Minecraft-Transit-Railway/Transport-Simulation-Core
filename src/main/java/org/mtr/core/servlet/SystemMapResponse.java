package org.mtr.core.servlet;

import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.Position;
import org.mtr.libraries.com.google.gson.JsonArray;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;

public class SystemMapResponse extends ResponseBase {

	public SystemMapResponse(String data, Object2ObjectAVLTreeMap<String, String> parameters, JsonObject bodyObject, long currentMillis, Simulator simulator) {
		super(data, parameters, bodyObject, currentMillis, simulator);
	}

	public JsonObject getData() {
		final JsonArray stationsArray = new JsonArray();
		simulator.stations.forEach(station -> {
			final JsonArray connectionsArray = new JsonArray();
			station.connectedStations.forEach(connectingStation -> connectionsArray.add(connectingStation.getHexId()));

			final JsonObject jsonObject = new JsonObject();
			jsonObject.addProperty("id", station.getHexId());
			jsonObject.addProperty("name", station.getName());
			jsonObject.addProperty("color", station.getColorHex());
			jsonObject.addProperty("zone1", station.getZone1());
			jsonObject.addProperty("zone2", station.getZone2());
			jsonObject.addProperty("zone3", station.getZone3());
			jsonObject.add("connections", connectionsArray);

			stationsArray.add(jsonObject);
		});

		final JsonArray routesArray = new JsonArray();
		simulator.routes.forEach(route -> {
			final JsonArray routeStationsArray = new JsonArray();
			route.getRoutePlatforms().forEach(routePlatform -> {
				final Platform platform = routePlatform.platform;
				if (platform != null && platform.area != null) {
					final JsonObject jsonObject = new JsonObject();
					jsonObject.addProperty("id", platform.area.getHexId());
					final Position position = platform.getMidPosition();
					jsonObject.addProperty("x", position.getX());
					jsonObject.addProperty("y", position.getY());
					jsonObject.addProperty("z", position.getZ());
					routeStationsArray.add(jsonObject);
				}
			});

			final JsonObject jsonObject = new JsonObject();
			jsonObject.addProperty("name", route.getName());
			jsonObject.addProperty("color", route.getColorHex());
			jsonObject.addProperty("number", route.getRouteNumber());
			jsonObject.addProperty("type", route.getRouteTypeKey());
			jsonObject.addProperty("circular", route.getCircularState() == Route.CircularState.NONE ? "" : route.getCircularState().toString());
			jsonObject.add("stations", routeStationsArray);

			routesArray.add(jsonObject);
		});

		final JsonObject jsonObject = new JsonObject();
		jsonObject.add("stations", stationsArray);
		jsonObject.add("routes", routesArray);
		return jsonObject;
	}
}
