package org.mtr.core.servlet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import org.mtr.core.data.NameColorDataBase;
import org.mtr.core.data.SerializedDataBase;
import org.mtr.core.serializers.JsonReader;
import org.mtr.core.serializers.JsonWriter;
import org.mtr.core.simulation.Simulator;

import java.util.function.Function;

public class IntegrationResponse extends ResponseBase {

	public IntegrationResponse(String data, Object2ObjectAVLTreeMap<String, String> parameters, JsonObject bodyObject, long currentMillis, Simulator simulator) {
		super(data, parameters, bodyObject, currentMillis, simulator);
	}

	public <T extends NameColorDataBase> JsonObject update(ObjectAVLTreeSet<T> dataSet, Long2ObjectOpenHashMap<T> dataIdMap, Function<JsonReader, T> createData) {
		final JsonReader jsonReader = new JsonReader(bodyObject);
		final JsonElement idElement = bodyObject.get("id");

		if (idElement != null) {
			try {
				final T data = dataIdMap.get(idElement.getAsLong());
				if (data != null) {
					data.updateData(jsonReader);
					simulator.dataCache.sync();
					return getDataAsJson(data);
				}
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}

		final T newData = createData.apply(jsonReader);
		dataSet.add(newData);
		simulator.dataCache.sync();
		return getDataAsJson(newData);
	}

	private static <T extends SerializedDataBase> JsonObject getDataAsJson(T data) {
		final JsonObject jsonObject = new JsonObject();
		data.serializeData(new JsonWriter(jsonObject));
		return jsonObject;
	}
}
