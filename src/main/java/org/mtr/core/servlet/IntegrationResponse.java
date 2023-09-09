package org.mtr.core.servlet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import org.mtr.core.data.*;
import org.mtr.core.serializers.JsonReader;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class IntegrationResponse extends ResponseBase {

	public IntegrationResponse(String data, Object2ObjectAVLTreeMap<String, String> parameters, JsonObject bodyObject, long currentMillis, Simulator simulator) {
		super(data, parameters, bodyObject, currentMillis, simulator);
	}

	public JsonObject update() {
		final JsonObject jsonObject = parseBody(IntegrationResponse::update, (resultArray, railNode) -> {
			final Position position1 = railNode.getPosition();
			final RailNode existingRailNode = getExistingRailNode(position1);
			final RailNode newRailNode;

			if (existingRailNode == null) {
				newRailNode = new RailNode(position1);
				simulator.railNodes.add(newRailNode);
			} else {
				newRailNode = existingRailNode;
			}

			railNode.getConnectionsAsMap().forEach((position2, rail) -> {
				final Rail newRail = Rail.copy(position1, position2, rail);
				if (newRail.isValid()) {
					newRailNode.addConnection(position2, newRail);
					resultArray.add(Utilities.getJsonObjectFromData(newRailNode));
				}
			});
		});

		simulator.sync();
		return jsonObject;
	}

	public JsonObject get() {
		return parseBody(IntegrationResponse::get, (resultArray, railNode) -> resultArray.add(Utilities.getJsonObjectFromData(getExistingRailNode(railNode.getPosition()))));
	}

	public JsonObject delete() {
		final JsonObject response = parseBody(IntegrationResponse::delete, (resultArray, railNode) -> {
			final Position position1 = railNode.getPosition();
			final RailNode existingRailNode = getExistingRailNode(position1);

			if (existingRailNode != null) {
				final Object2ObjectOpenHashMap<Position, Rail> connections = railNode.getConnectionsAsMap();

				// If the delete message just has a rail node with no rail connections specified, just delete all connections to the node
				if (connections.isEmpty()) {
					existingRailNode.getConnectionsAsMap().forEach((position2, rail) -> removeRailNodeConnection(resultArray, getExistingRailNode(position2), position1));
					resultArray.add(Utilities.getJsonObjectFromData(existingRailNode));
					simulator.railNodes.remove(existingRailNode);
				} else {
					connections.forEach((position2, rail) -> removeRailNodeConnection(resultArray, existingRailNode, position2));
				}
			}
		});

		simulator.sync();
		return response;
	}

	public JsonObject generate() {
		return parseBody(IntegrationResponse::generate, (resultArray, railNode) -> {
		});
	}

	public JsonObject clear() {
		return parseBody(IntegrationResponse::clear, (resultArray, railNode) -> {
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

	private JsonObject parseBody(BodyCallback bodyCallback, BiConsumer<JsonArray, RailNode> railCallback) {
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
						iterateBodyArray(bodyArray, bodyObject -> bodyCallback.accept(bodyObject, resultArray, simulator.platforms, simulator.platformIdMap, jsonReader -> new Platform(jsonReader, simulator)));
						break;
					case "sidings":
						iterateBodyArray(bodyArray, bodyObject -> bodyCallback.accept(bodyObject, resultArray, simulator.sidings, simulator.sidingIdMap, jsonReader -> new Siding(jsonReader, simulator)));
						break;
					case "routes":
						iterateBodyArray(bodyArray, bodyObject -> bodyCallback.accept(bodyObject, resultArray, simulator.routes, simulator.routeIdMap, jsonReader -> new Route(jsonReader, simulator)));
						break;
					case "depots":
						iterateBodyArray(bodyArray, bodyObject -> bodyCallback.accept(bodyObject, resultArray, simulator.depots, simulator.depotIdMap, jsonReader -> new Depot(jsonReader, simulator)));
						break;
					case "rails":
						iterateBodyArray(bodyArray, bodyObject -> railCallback.accept(resultArray, new RailNode(new JsonReader(bodyObject))));
						break;
				}
				jsonObject.add(key, resultArray);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});

		return jsonObject;
	}

	private RailNode getExistingRailNode(Position position) {
		return simulator.railNodes.stream().filter(checkRailNode -> checkRailNode.getPosition().equals(position)).findFirst().orElse(null);
	}

	private void removeRailNodeConnection(JsonArray resultArray, RailNode railNode, Position position) {
		if (railNode != null) {
			if (railNode.removeConnection(position)) {
				resultArray.add(Utilities.getJsonObjectFromData(railNode));
			}
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
			final T newData = createData.apply(jsonReader);
			dataSet.add(newData);
			resultArray.add(Utilities.getJsonObjectFromData(newData));
		} else {
			data.updateData(jsonReader);
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
}
