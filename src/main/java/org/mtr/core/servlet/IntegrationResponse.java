package org.mtr.core.servlet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.*;
import org.mtr.core.Main;
import org.mtr.core.data.*;
import org.mtr.core.serializers.JsonReader;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;

import javax.annotation.Nullable;
import java.util.function.Consumer;

public class IntegrationResponse extends ResponseBase {

	public IntegrationResponse(String data, Object2ObjectAVLTreeMap<String, String> parameters, JsonObject bodyObject, long currentMillis, Simulator simulator) {
		super(data, parameters, bodyObject, currentMillis, simulator);
	}

	public JsonObject update() {
		return parseBody(IntegrationResponse::update, PositionCallback.EMPTY, true);
	}

	public JsonObject get() {
		return parseBody(IntegrationResponse::get, PositionCallback.EMPTY, false);
	}

	public JsonObject delete() {
		return parseBody(IntegrationResponse::delete, (position, railsToUpdate, positionsToUpdate) -> simulator.positionsToRail.getOrDefault(position, new Object2ObjectOpenHashMap<>()).forEach((connectedPosition, rail) -> {
			simulator.rails.remove(rail);
			railsToUpdate.add(rail);
			positionsToUpdate.add(connectedPosition);
		}), true);
	}

	public JsonObject generate() {
		return parseBody(IntegrationResponse::generate, PositionCallback.EMPTY, false);
	}

	public JsonObject clear() {
		return parseBody(IntegrationResponse::clear, PositionCallback.EMPTY, false);
	}

	public JsonObject list() {
		final JsonObject jsonObject = new JsonObject();
		jsonObject.add("stations", getDataAsArray(simulator.stations));
		jsonObject.add("platforms", getDataAsArray(simulator.platforms));
		jsonObject.add("sidings", getDataAsArray(simulator.sidings));
		jsonObject.add("routes", getDataAsArray(simulator.routes));
		jsonObject.add("depots", getDataAsArray(simulator.depots));
		jsonObject.add("rails", new JsonArray());
		jsonObject.add("positions", new JsonArray());
		return jsonObject;
	}

	private JsonObject parseBody(BodyCallback bodyCallback, PositionCallback positionCallback, boolean shouldSync) {
		final ObjectAVLTreeSet<Station> stationsToUpdate = new ObjectAVLTreeSet<>();
		final ObjectAVLTreeSet<Platform> platformsToUpdate = new ObjectAVLTreeSet<>();
		final ObjectAVLTreeSet<Siding> sidingsToUpdate = new ObjectAVLTreeSet<>();
		final ObjectAVLTreeSet<Route> routesToUpdate = new ObjectAVLTreeSet<>();
		final ObjectAVLTreeSet<Depot> depotsToUpdate = new ObjectAVLTreeSet<>();
		final ObjectOpenHashSet<Rail> railsToUpdate = new ObjectOpenHashSet<>();
		final ObjectOpenHashSet<Position> positionsToUpdate = new ObjectOpenHashSet<>();

		try {
			iterateBodyArray(bodyObject.getAsJsonArray("stations"), bodyObject -> {
				final JsonReader jsonReader = new JsonReader(bodyObject);
				final Station newData = new Station(jsonReader, simulator);
				bodyCallback.accept(jsonReader, newData, simulator.stationIdMap.get(newData.getId()), simulator.stations, stationsToUpdate);
			});
			iterateBodyArray(bodyObject.getAsJsonArray("platforms"), bodyObject -> {
				final JsonReader jsonReader = new JsonReader(bodyObject);
				final Platform newData = new Platform(jsonReader, simulator);
				bodyCallback.accept(jsonReader, null, simulator.platformIdMap.get(newData.getId()), simulator.platforms, platformsToUpdate);
			});
			iterateBodyArray(bodyObject.getAsJsonArray("sidings"), bodyObject -> {
				final JsonReader jsonReader = new JsonReader(bodyObject);
				final Siding newData = new Siding(jsonReader, simulator);
				bodyCallback.accept(jsonReader, null, simulator.sidingIdMap.get(newData.getId()), simulator.sidings, sidingsToUpdate);
			});
			iterateBodyArray(bodyObject.getAsJsonArray("routes"), bodyObject -> {
				final JsonReader jsonReader = new JsonReader(bodyObject);
				final Route newData = new Route(jsonReader, simulator);
				bodyCallback.accept(jsonReader, newData, simulator.routeIdMap.get(newData.getId()), simulator.routes, routesToUpdate);
			});
			iterateBodyArray(bodyObject.getAsJsonArray("depots"), bodyObject -> {
				final JsonReader jsonReader = new JsonReader(bodyObject);
				final Depot newData = new Depot(jsonReader, simulator);
				bodyCallback.accept(jsonReader, newData, simulator.depotIdMap.get(newData.getId()), simulator.depots, depotsToUpdate);
			});
			iterateBodyArray(bodyObject.getAsJsonArray("rails"), bodyObject -> {
				final JsonReader jsonReader = new JsonReader(bodyObject);
				final Rail newData = new Rail(jsonReader);
				bodyCallback.accept(jsonReader, newData, newData.getRailFromData(simulator, positionsToUpdate), simulator.rails, railsToUpdate);
			});
			iterateBodyArray(bodyObject.getAsJsonArray("positions"), bodyObject -> {
				final Position position = new Position(new JsonReader(bodyObject));
				positionsToUpdate.add(position);
				positionCallback.accept(position, railsToUpdate, positionsToUpdate);
			});
		} catch (Exception e) {
			Main.logException(e);
		}

		if (shouldSync) {
			railsToUpdate.forEach(rail -> rail.checkOrCreatePlatform(simulator, platformsToUpdate, sidingsToUpdate));
			simulator.sync();
		}
		positionsToUpdate.removeIf(position -> !simulator.positionsToRail.getOrDefault(position, new Object2ObjectOpenHashMap<>()).isEmpty());

		final JsonObject jsonObject = new JsonObject();
		jsonObject.add("stations", getDataAsArray(stationsToUpdate));
		jsonObject.add("platforms", getDataAsArray(platformsToUpdate));
		jsonObject.add("sidings", getDataAsArray(sidingsToUpdate));
		jsonObject.add("routes", getDataAsArray(routesToUpdate));
		jsonObject.add("depots", getDataAsArray(depotsToUpdate));
		jsonObject.add("rails", getDataAsArray(railsToUpdate));
		jsonObject.add("positions", getDataAsArray(positionsToUpdate)); // "positions" should always return a list of nodes that have no connections to it
		return jsonObject;
	}

	private static void iterateBodyArray(JsonArray bodyArray, Consumer<JsonObject> consumer) {
		bodyArray.forEach(jsonElement -> {
			try {
				consumer.accept(jsonElement.getAsJsonObject());
			} catch (Exception e) {
				Main.logException(e);
			}
		});
	}

	private static <T extends SerializedDataBase> void update(JsonReader jsonReader, @Nullable T newData, @Nullable T existingData, ObjectSet<T> dataSet, ObjectSet<T> dataToUpdate) {
		final boolean isRail = newData instanceof Rail;
		final boolean isValid = !isRail || ((Rail) newData).isValid();

		if (existingData == null) {
			if (newData != null && isValid) {
				dataSet.add(newData);
				dataToUpdate.add(newData);
			}
		} else if (isValid) {
			// For AVL tree sets, data must be removed and re-added when modified
			dataSet.remove(existingData);
			if (isRail) {
				dataSet.add(newData);
				dataToUpdate.add(newData);
			} else {
				existingData.updateData(jsonReader);
				dataSet.add(existingData);
				dataToUpdate.add(existingData);
			}
		}
	}

	private static <T extends SerializedDataBase> void get(JsonReader jsonReader, @Nullable T newData, @Nullable T existingData, ObjectSet<T> dataSet, ObjectSet<T> dataToUpdate) {
		if (existingData != null) {
			dataToUpdate.add(existingData);
		}
	}

	private static <T extends SerializedDataBase> void delete(JsonReader jsonReader, @Nullable T newData, @Nullable T existingData, ObjectSet<T> dataSet, ObjectSet<T> dataToUpdate) {
		if (existingData != null && dataSet.remove(existingData)) {
			dataToUpdate.add(existingData);
		}
	}

	private static <T extends SerializedDataBase> void generate(JsonReader jsonReader, @Nullable T newData, @Nullable T existingData, ObjectSet<T> dataSet, ObjectSet<T> dataToUpdate) {
		if (existingData instanceof Depot) {
			dataToUpdate.add(existingData);
			((Depot) existingData).generateMainRoute();
		}
	}

	private static <T extends SerializedDataBase> void clear(JsonReader jsonReader, @Nullable T newData, @Nullable T existingData, ObjectSet<T> dataSet, ObjectSet<T> dataToUpdate) {
		if (existingData instanceof Siding) {
			dataToUpdate.add(existingData);
			((Siding) existingData).clearVehicles();
		}
	}

	private static <T extends SerializedDataBase> JsonArray getDataAsArray(ObjectSet<T> dataSet) {
		final JsonArray jsonArray = new JsonArray();
		dataSet.forEach(data -> jsonArray.add(Utilities.getJsonObjectFromData(data)));
		return jsonArray;
	}

	@FunctionalInterface
	private interface BodyCallback {
		<T extends SerializedDataBase> void accept(JsonReader jsonReader, @Nullable T newData, @Nullable T existingData, ObjectSet<T> dataSet, ObjectSet<T> dataToUpdate);
	}

	@FunctionalInterface
	private interface PositionCallback {
		PositionCallback EMPTY = (position, railsToUpdate, positionsToUpdate) -> {
		};

		void accept(Position position, ObjectOpenHashSet<Rail> railsToUpdate, ObjectOpenHashSet<Position> positionsToUpdate);
	}
}
