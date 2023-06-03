package org.mtr.core.servlet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import org.mtr.core.data.*;
import org.mtr.core.serializers.JsonReader;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.Utilities;

import java.util.function.Consumer;
import java.util.function.Function;

public class IntegrationResponse extends ResponseBase {

	public IntegrationResponse(String data, Object2ObjectAVLTreeMap<String, String> parameters, JsonObject bodyObject, long currentMillis, Simulator simulator) {
		super(data, parameters, bodyObject, currentMillis, simulator);
	}

	public JsonObject update() {
		final JsonObject jsonObject = parseBody(IntegrationResponse::update);
		simulator.dataCache.sync();
		return jsonObject;
	}

	public JsonObject get() {
		return parseBody(IntegrationResponse::get);
	}

	public JsonObject delete() {
		parseBody(IntegrationResponse::delete);
		simulator.dataCache.sync();
		return new JsonObject();
	}

	private JsonObject parseBody(BodyCallback bodyCallback) {
		final JsonObject jsonObject = new JsonObject();

		bodyObject.keySet().forEach(key -> {
			try {
				final JsonArray bodyArray = bodyObject.getAsJsonArray(key);
				final JsonArray resultArray = new JsonArray();
				switch (key) {
					case "stations":
						iterateBodyArray(bodyArray, jsonElement -> bodyCallback.accept(jsonElement, resultArray, simulator.stations, simulator.dataCache.stationIdMap, jsonReader -> new Station(jsonReader, simulator)));
						break;
					case "platforms":
						iterateBodyArray(bodyArray, jsonElement -> bodyCallback.accept(jsonElement, resultArray, simulator.platforms, simulator.dataCache.platformIdMap, jsonReader -> new Platform(jsonReader, simulator)));
						break;
					case "sidings":
						iterateBodyArray(bodyArray, jsonElement -> bodyCallback.accept(jsonElement, resultArray, simulator.sidings, simulator.dataCache.sidingIdMap, jsonReader -> new Siding(jsonReader, simulator)));
						break;
					case "routes":
						iterateBodyArray(bodyArray, jsonElement -> bodyCallback.accept(jsonElement, resultArray, simulator.routes, simulator.dataCache.routeIdMap, jsonReader -> new Route(jsonReader, simulator)));
						break;
					case "depots":
						iterateBodyArray(bodyArray, jsonElement -> bodyCallback.accept(jsonElement, resultArray, simulator.depots, simulator.dataCache.depotIdMap, jsonReader -> new Depot(jsonReader, simulator)));
						break;
				}
				jsonObject.add(key, resultArray);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});

		return jsonObject;
	}

	private static void iterateBodyArray(JsonArray bodyArray, Consumer<JsonElement> consumer) {
		bodyArray.forEach(jsonElement -> {
			try {
				consumer.accept(jsonElement);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

	private static <T extends NameColorDataBase> void update(JsonElement jsonElement, JsonArray resultArray, ObjectAVLTreeSet<T> dataSet, Long2ObjectOpenHashMap<T> dataIdMap, Function<JsonReader, T> createData) {
		final JsonObject jsonObject = jsonElement.getAsJsonObject();
		final JsonReader jsonReader = new JsonReader(jsonObject);
		final JsonElement idElement = jsonObject.get("id");
		final boolean isNew;

		if (idElement == null) {
			isNew = true;
		} else {
			final T data = dataIdMap.get(idElement.getAsLong());
			if (data == null) {
				isNew = true;
			} else {
				data.updateData(jsonReader);
				resultArray.add(Utilities.getJsonObjectFromData(data));
				isNew = false;
			}
		}

		if (isNew) {
			final T newData = createData.apply(jsonReader);
			dataSet.add(newData);
			resultArray.add(Utilities.getJsonObjectFromData(newData));
		}
	}

	private static <T extends NameColorDataBase> void get(JsonElement jsonElement, JsonArray resultArray, ObjectAVLTreeSet<T> dataSet, Long2ObjectOpenHashMap<T> dataIdMap, Function<JsonReader, T> createData) {
		final T data = dataIdMap.get(jsonElement.getAsLong());
		if (data != null) {
			resultArray.add(Utilities.getJsonObjectFromData(data));
		}
	}

	private static <T extends NameColorDataBase> void delete(JsonElement jsonElement, JsonArray resultArray, ObjectAVLTreeSet<T> dataSet, Long2ObjectOpenHashMap<T> dataIdMap, Function<JsonReader, T> createData) {
		final long id = jsonElement.getAsLong();
		dataSet.removeIf(data -> data.getId() == id);
	}

	@FunctionalInterface
	private interface BodyCallback {
		<T extends NameColorDataBase> void accept(JsonElement jsonElement, JsonArray resultArray, ObjectAVLTreeSet<T> dataSet, Long2ObjectOpenHashMap<T> dataIdMap, Function<JsonReader, T> createData);
	}
}
