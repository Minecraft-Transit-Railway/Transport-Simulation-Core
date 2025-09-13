package org.mtr.core.servlet;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.longs.Long2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.core.data.NameColorDataBase;
import org.mtr.core.map.*;
import org.mtr.core.operation.ArrivalsRequest;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;

import java.util.function.Consumer;

public final class SystemMapServlet extends ServletBase {

	private final Object2ObjectAVLTreeMap<String, CachedResponse> stationsAndRoutesResponses = new Object2ObjectAVLTreeMap<>();
	private final Object2ObjectAVLTreeMap<String, CachedResponse> departuresResponses = new Object2ObjectAVLTreeMap<>();
	private final Object2ObjectAVLTreeMap<String, CachedResponse> clientsResponses = new Object2ObjectAVLTreeMap<>();

	public SystemMapServlet(ObjectImmutableList<Simulator> simulators) {
		super(simulators);
	}

	@Override
	public void getContent(String endpoint, String data, Object2ObjectAVLTreeMap<String, String> parameters, JsonReader jsonReader, Simulator simulator, Consumer<JsonObject> sendResponse) {
		sendResponse.accept(switch (endpoint) {
			case "stations-and-routes" -> stationsAndRoutesResponses.computeIfAbsent(simulator.dimension, key -> new CachedResponse(SystemMapServlet::getStationsAndRoutes, 30000)).get(simulator);
			case "departures" -> departuresResponses.computeIfAbsent(simulator.dimension, key -> new CachedResponse(SystemMapServlet::getDepartures, 3000)).get(simulator);
			case "arrivals" -> Utilities.getJsonObjectFromData(new ArrivalsRequest(jsonReader).getArrivals(simulator));
			case "clients" -> clientsResponses.computeIfAbsent(simulator.dimension, key -> new CachedResponse(SystemMapServlet::getClients, 3000)).get(simulator);
			case "directions" -> Utilities.getJsonObjectFromData(new DirectionsGroupRequest(jsonReader).getDirections(simulator));
			default -> new JsonObject();
		});
	}

	private static JsonObject getStationsAndRoutes(Simulator simulator) {
		final StationAndRoutes stationAndRoutes = new StationAndRoutes(simulator.dimensions);
		simulator.stations.forEach(stationAndRoutes::addStation);
		simulator.routes.forEach(stationAndRoutes::addRoute);
		return Utilities.getJsonObjectFromData(stationAndRoutes);
	}

	private static JsonObject getDepartures(Simulator simulator) {
		final long currentMillis = System.currentTimeMillis();
		final Object2ObjectAVLTreeMap<String, Long2ObjectAVLTreeMap<LongArrayList>> departures = new Object2ObjectAVLTreeMap<>();
		simulator.sidings.forEach(siding -> siding.getDeparturesForMap(currentMillis, departures));
		return Utilities.getJsonObjectFromData(new Departures(currentMillis, departures));
	}

	private static JsonObject getClients(Simulator simulator) {
		final long currentMillis = System.currentTimeMillis();
		final Object2ObjectAVLTreeMap<String, Client> clients = new Object2ObjectAVLTreeMap<>();

		simulator.clients.forEach(client -> {
			final String clientId = client.uuid.toString();
			clients.put(clientId, new Client(
					clientId,
					client.getPosition().getX(), client.getPosition().getZ(),
					simulator.stations.stream().filter(station -> station.inArea(client.getPosition())).map(NameColorDataBase::getHexId).findFirst().orElse("")
			));
		});

		simulator.sidings.forEach(siding -> siding.iterateVehiclesAndRidingEntities((vehicleExtraData, vehicleRidingEntity) -> {
			final String clientId = vehicleRidingEntity.uuid.toString();
			final Client client = clients.get(clientId);
			if (client != null) {
				clients.put(clientId, new Client(
						client,
						Utilities.numberToPaddedHexString(vehicleExtraData.getThisRouteId()),
						Utilities.numberToPaddedHexString(vehicleExtraData.getThisStationId()),
						Utilities.numberToPaddedHexString(vehicleExtraData.getNextStationId())
				));
			}
		}));

		return Utilities.getJsonObjectFromData(new Clients(currentMillis, new ObjectArrayList<>(clients.values())));
	}
}
