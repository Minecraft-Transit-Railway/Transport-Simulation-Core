package org.mtr.core.servlet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.Position;

import java.util.Locale;

public class SystemMapResponse extends ResponseBase {

	public SystemMapResponse(String data, Object2ObjectAVLTreeMap<String, String> parameters, long currentMillis, Simulator simulator) {
		super(data, parameters, currentMillis, simulator);
	}

	public JsonObject getData() {
		final JsonArray stationsArray = new JsonArray();
		simulator.stations.forEach(station -> {
			final JsonArray connectionsArray = new JsonArray();
			simulator.dataCache.stationIdToConnectingStations.get(station).forEach(connectingStation -> connectionsArray.add(connectingStation.getHexId()));

			final JsonObject jsonObject = new JsonObject();
			jsonObject.addProperty("id", station.getHexId());
			jsonObject.addProperty("name", station.name);
			jsonObject.addProperty("color", station.getColorHex());
			jsonObject.addProperty("zone", station.zone);
			jsonObject.add("connections", connectionsArray);

			stationsArray.add(jsonObject);
		});

		final JsonArray routesArray = new JsonArray();
		simulator.routes.forEach(route -> {
			final JsonArray routeStationsArray = new JsonArray();
			route.platformIds.forEach(routePlatform -> {
				final Platform platform = simulator.dataCache.platformIdMap.get(routePlatform.platformId);
				if (platform != null && platform.area != null) {
					final JsonObject jsonObject = new JsonObject();
					jsonObject.addProperty("id", platform.area.getHexId());
					final Position position = platform.getMidPosition();
					jsonObject.addProperty("x", position.x);
					jsonObject.addProperty("y", position.y);
					jsonObject.addProperty("z", position.z);
					routeStationsArray.add(jsonObject);
				}
			});

			final JsonObject jsonObject = new JsonObject();
			jsonObject.addProperty("name", route.name);
			jsonObject.addProperty("color", route.getColorHex());
			jsonObject.addProperty("number", route.routeNumber);
			jsonObject.addProperty("type", String.format("%s_%s", route.transportMode.toString(), route.routeType.toString()).toLowerCase(Locale.ENGLISH));
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
