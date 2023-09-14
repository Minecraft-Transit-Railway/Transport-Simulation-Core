package org.mtr.core.servlet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.*;
import org.mtr.core.data.*;
import org.mtr.core.serializers.JsonReader;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;

import java.util.function.Consumer;
import java.util.function.Function;

public class IntegrationResponse extends ResponseBase {

	public IntegrationResponse(String data, Object2ObjectAVLTreeMap<String, String> parameters, JsonObject bodyObject, long currentMillis, Simulator simulator) {
		super(data, parameters, bodyObject, currentMillis, simulator);
	}

	public JsonObject update() {
		final JsonObject jsonObject = parseBody(IntegrationResponse::update, this::updateOrDeleteRailNode);
		simulator.sync();
		return jsonObject;
	}

	public JsonObject get() {
		return parseBody(IntegrationResponse::get, (positionsToUpdate, platformsToAdd, sidingsToAdd, railNode) -> {
			// Any requested rail node positions are automatically added to the list of positions to update
		});
	}

	public JsonObject delete() {
		final JsonObject response = parseBody(IntegrationResponse::delete, this::updateOrDeleteRailNode);
		simulator.sync();
		return response;
	}

	public JsonObject generate() {
		return parseBody(IntegrationResponse::generate, (positionsToUpdate, platformsToAdd, sidingsToAdd, railNode) -> {
		});
	}

	public JsonObject clear() {
		return parseBody(IntegrationResponse::clear, (positionsToUpdate, platformsToAdd, sidingsToAdd, railNode) -> {
		});
	}

	public JsonObject list() {
		final JsonObject jsonObject = new JsonObject();
		jsonObject.add("stations", getDataAsArray(simulator.stations));
		jsonObject.add("platforms", getDataAsArray(simulator.platforms));
		jsonObject.add("sidings", getDataAsArray(simulator.sidings));
		jsonObject.add("routes", getDataAsArray(simulator.routes));
		jsonObject.add("depots", getDataAsArray(simulator.depots));
		jsonObject.add("rails", new JsonArray());
		return jsonObject;
	}

	private JsonObject parseBody(BodyCallback bodyCallback, RailCallback railCallback) {
		final JsonObject jsonObject = new JsonObject();

		bodyObject.keySet().forEach(key -> {
			try {
				final JsonArray bodyArray = bodyObject.getAsJsonArray(key);
				final JsonArray resultArray = new JsonArray();
				switch (key) {
					case "stations":
						iterateBodyArray(bodyArray, bodyObject -> bodyCallback.accept(bodyObject, resultArray, simulator.stations, simulator.stationIdMap, jsonReader -> new Station(jsonReader, simulator)));
						break;
					case "platforms":
						iterateBodyArray(bodyArray, bodyObject -> bodyCallback.accept(bodyObject, resultArray, simulator.platforms, simulator.platformIdMap, null));
						break;
					case "sidings":
						iterateBodyArray(bodyArray, bodyObject -> bodyCallback.accept(bodyObject, resultArray, simulator.sidings, simulator.sidingIdMap, null));
						break;
					case "routes":
						iterateBodyArray(bodyArray, bodyObject -> bodyCallback.accept(bodyObject, resultArray, simulator.routes, simulator.routeIdMap, jsonReader -> new Route(jsonReader, simulator)));
						break;
					case "depots":
						iterateBodyArray(bodyArray, bodyObject -> bodyCallback.accept(bodyObject, resultArray, simulator.depots, simulator.depotIdMap, jsonReader -> new Depot(jsonReader, simulator)));
						break;
					case "rails":
						final ObjectOpenHashSet<Position> positionsToUpdate = new ObjectOpenHashSet<>();
						final ObjectAVLTreeSet<Platform> platformsToAdd = new ObjectAVLTreeSet<>();
						final ObjectAVLTreeSet<Siding> sidingsToAdd = new ObjectAVLTreeSet<>();
						iterateBodyArray(bodyArray, bodyObject -> {
							final RailNode railNode = new RailNode(new JsonReader(bodyObject));
							positionsToUpdate.add(railNode.getPosition());
							railCallback.accept(positionsToUpdate, platformsToAdd, sidingsToAdd, railNode);
						});
						positionsToUpdate.forEach(position -> resultArray.add(Utilities.getJsonObjectFromData(getOrCreateRailNode(position))));
						platformsToAdd.forEach(platform -> jsonObject.getAsJsonArray("platforms").add(Utilities.getJsonObjectFromData(platform)));
						sidingsToAdd.forEach(siding -> jsonObject.getAsJsonArray("sidings").add(Utilities.getJsonObjectFromData(siding)));
						break;
				}
				jsonObject.add(key, resultArray);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});

		return jsonObject;
	}

	private void updateOrDeleteRailNode(ObjectOpenHashSet<Position> positionsToUpdate, ObjectAVLTreeSet<Platform> platformsToAdd, ObjectAVLTreeSet<Siding> sidingsToAdd, RailNode railNode) {
		final Position position1 = railNode.getPosition();
		final Object2ObjectOpenHashMap<Position, Rail> connections = railNode.getConnectionsAsMap();
		final RailNode newRailNode = getOrCreateRailNode(position1);

		// If the message just has a rail node with no rail connections specified, just delete all connections to the node
		if (connections.isEmpty()) {
			simulator.nodesConnectedToPosition.getOrDefault(position1, new ObjectOpenHashBigSet<>()).forEach(connectedRailNode -> removeRailNodeConnection(positionsToUpdate, connectedRailNode, position1));
			newRailNode.getConnectionsAsMap().forEach((position2, rail) -> positionsToUpdate.add(position2));
			simulator.railNodes.remove(newRailNode);
		} else {
			connections.forEach((position2, rail) -> {
				// Attempt to recreate the rail; if invalid, treat is as a delete operation
				final Rail newRail = Rail.copy(position1, position2, rail, simulator, platformsToAdd, sidingsToAdd);
				if (newRail == null) {
					removeRailNodeConnection(positionsToUpdate, newRailNode, position2);
				} else {
					newRailNode.addConnection(position2, newRail);
					positionsToUpdate.add(position2);
					simulator.railNodes.add(newRailNode);
				}
			});
		}
	}

	private RailNode getOrCreateRailNode(Position position) {
		return simulator.railNodes.stream().filter(checkRailNode -> checkRailNode.getPosition().equals(position)).findFirst().orElse(new RailNode(position));
	}

	private void removeRailNodeConnection(ObjectOpenHashSet<Position> positionsToUpdate, RailNode railNode, Position position) {
		if (railNode != null) {
			railNode.removeConnection(position);
			positionsToUpdate.add(railNode.getPosition());
			if (railNode.getConnectionsAsMap().isEmpty()) {
				simulator.railNodes.remove(railNode);
			}
		}
	}

	private static void iterateBodyArray(JsonArray bodyArray, Consumer<JsonObject> consumer) {
		bodyArray.forEach(jsonElement -> {
			try {
				consumer.accept(jsonElement.getAsJsonObject());
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

	private static <T extends NameColorDataBase> void update(JsonObject jsonObject, JsonArray resultArray, ObjectAVLTreeSet<T> dataSet, Long2ObjectOpenHashMap<T> dataIdMap, Function<JsonReader, T> createData) {
		final JsonReader jsonReader = new JsonReader(jsonObject);
		final T data = dataIdMap.get(getId(jsonObject));
		if (data == null) {
			if (createData != null) {
				final T newData = createData.apply(jsonReader);
				dataSet.add(newData);
				resultArray.add(Utilities.getJsonObjectFromData(newData));
			}
		} else {
			// For AVL tree sets, data must be removed and re-added when modified
			dataSet.remove(data);
			data.updateData(jsonReader);
			dataSet.add(data);
			resultArray.add(Utilities.getJsonObjectFromData(data));
		}
	}

	private static <T extends NameColorDataBase> void get(JsonObject jsonObject, JsonArray resultArray, ObjectAVLTreeSet<T> dataSet, Long2ObjectOpenHashMap<T> dataIdMap, Function<JsonReader, T> createData) {
		final T data = dataIdMap.get(getId(jsonObject));
		if (data != null) {
			resultArray.add(Utilities.getJsonObjectFromData(data));
		}
	}

	private static <T extends NameColorDataBase> void delete(JsonObject jsonObject, JsonArray resultArray, ObjectAVLTreeSet<T> dataSet, Long2ObjectOpenHashMap<T> dataIdMap, Function<JsonReader, T> createData) {
		final long id = getId(jsonObject);
		final ObjectAVLTreeSet<T> objectsToRemove = new ObjectAVLTreeSet<>();
		dataSet.forEach(data -> {
			if (data.getId() == id) {
				resultArray.add(Utilities.getJsonObjectFromData(data));
				objectsToRemove.add(data);
			}
		});
		objectsToRemove.forEach(dataSet::remove);
	}

	private static <T extends NameColorDataBase> void generate(JsonObject jsonObject, JsonArray resultArray, ObjectAVLTreeSet<T> dataSet, Long2ObjectOpenHashMap<T> dataIdMap, Function<JsonReader, T> createData) {
		final T data = dataIdMap.get(getId(jsonObject));
		if (data instanceof Depot) {
			resultArray.add(Utilities.getJsonObjectFromData(data));
			((Depot) data).generateMainRoute();
		}
	}

	private static <T extends NameColorDataBase> void clear(JsonObject jsonObject, JsonArray resultArray, ObjectAVLTreeSet<T> dataSet, Long2ObjectOpenHashMap<T> dataIdMap, Function<JsonReader, T> createData) {
		final T data = dataIdMap.get(getId(jsonObject));
		if (data instanceof Siding) {
			resultArray.add(Utilities.getJsonObjectFromData(data));
			((Siding) data).clearVehicles();
		}
	}

	private static <T extends NameColorDataBase> JsonArray getDataAsArray(ObjectAVLTreeSet<T> dataSet) {
		final JsonArray jsonArray = new JsonArray();
		dataSet.forEach(data -> jsonArray.add(Utilities.getJsonObjectFromData(data)));
		return jsonArray;
	}

	private static long getId(JsonObject jsonObject) {
		try {
			return jsonObject.get("id").getAsLong();
		} catch (Exception ignored) {
			return 0;
		}
	}

	@FunctionalInterface
	private interface BodyCallback {
		<T extends NameColorDataBase> void accept(JsonObject jsonObject, JsonArray resultArray, ObjectAVLTreeSet<T> dataSet, Long2ObjectOpenHashMap<T> dataIdMap, Function<JsonReader, T> createData);
	}

	@FunctionalInterface
	private interface RailCallback {
		void accept(ObjectOpenHashSet<Position> positionsToUpdate, ObjectAVLTreeSet<Platform> platformsToAdd, ObjectAVLTreeSet<Siding> sidingsToAdd, RailNode railNode);
	}
}
