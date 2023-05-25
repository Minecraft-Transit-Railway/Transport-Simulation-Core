package org.mtr.core.servlet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import org.mtr.core.data.*;
import org.mtr.core.serializers.JsonReader;
import org.mtr.core.serializers.JsonWriter;
import org.mtr.core.simulation.Simulator;

import java.util.function.Function;

public class IntegrationResponse extends ResponseBase {

	public IntegrationResponse(String data, Object2ObjectAVLTreeMap<String, String> parameters, JsonObject bodyObject, long currentMillis, Simulator simulator) {
		super(data, parameters, bodyObject, currentMillis, simulator);
	}

	public JsonObject update() {
		final JsonObject jsonObject = new JsonObject();

		bodyObject.keySet().forEach(key -> {
			try {
				final JsonArray bodyArray = bodyObject.getAsJsonArray(key);
				final JsonArray resultArray = new JsonArray();
				switch (key) {
					case "stations":
						update(bodyArray, resultArray, simulator.stations, simulator.dataCache.stationIdMap, jsonReader -> new Station(jsonReader, simulator));
						break;
					case "platforms":
						update(bodyArray, resultArray, simulator.platforms, simulator.dataCache.platformIdMap, jsonReader -> new Platform(jsonReader, simulator));
						break;
					case "sidings":
						update(bodyArray, resultArray, simulator.sidings, simulator.dataCache.sidingIdMap, jsonReader -> new Siding(jsonReader, simulator));
						break;
					case "routes":
						update(bodyArray, resultArray, simulator.routes, simulator.dataCache.routeIdMap, jsonReader -> new Route(jsonReader, simulator));
						break;
					case "depots":
						update(bodyArray, resultArray, simulator.depots, simulator.dataCache.depotIdMap, jsonReader -> new Depot(jsonReader, simulator));
						break;
				}
				jsonObject.add(key, resultArray);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});

		simulator.dataCache.sync();
		return jsonObject;
	}

	private static <T extends NameColorDataBase> void update(JsonArray bodyArray, JsonArray resultArray, ObjectAVLTreeSet<T> dataSet, Long2ObjectOpenHashMap<T> dataIdMap, Function<JsonReader, T> createData) {
		bodyArray.forEach(jsonElement -> {
			try {
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
						resultArray.add(getDataAsJson(data));
						isNew = false;
					}
				}

				if (isNew) {
					final T newData = createData.apply(jsonReader);
					dataSet.add(newData);
					resultArray.add(getDataAsJson(newData));
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

	private static <T extends SerializedDataBase> JsonObject getDataAsJson(T data) {
		final JsonObject jsonObject = new JsonObject();
		data.serializeData(new JsonWriter(jsonObject));
		return jsonObject;
	}
}
