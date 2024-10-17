package org.mtr.core.servlet;

import org.mtr.core.data.Depot;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Siding;
import org.mtr.core.data.Station;
import org.mtr.core.operation.*;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

public final class OperationServlet extends ServletBase {

	public OperationServlet(ObjectImmutableList<Simulator> simulators) {
		super(simulators);
	}

	@Override
	protected void getContent(String endpoint, String data, Object2ObjectAVLTreeMap<String, String> parameters, JsonReader jsonReader, long currentMillis, Simulator simulator, Consumer<JsonObject> sendResponse) {
		sendResponse.accept(process(endpoint, jsonReader, currentMillis, simulator));
	}

	@Nonnull
	public static JsonObject process(String key, JsonReader jsonReader, long currentMillis, Simulator simulator) {
		switch (key) {
			case "get-data":
				return new DataRequest(jsonReader).getData(simulator);
			case "update-data":
				return new UpdateDataRequest(jsonReader, simulator).update();
			case "delete-data":
				return new DeleteDataRequest(jsonReader).delete(simulator);
			case "list-data":
				return new ListDataResponse(jsonReader, simulator).list();
			case "arrivals":
				return new ArrivalsRequest(jsonReader).getArrivals(simulator, currentMillis);
			case "set-time":
				return new SetTime(jsonReader).setGameTime(simulator);
			case "update-riding-entities":
				return new UpdateVehicleRidingEntities(jsonReader).update(simulator);
			case "press-lift":
				return new PressLift(jsonReader).pressLift(simulator);
			case "nearby-stations":
				return new NearbyAreasRequest<Station, Platform>(jsonReader).query(simulator, simulator.stations);
			case "nearby-depots":
				return new NearbyAreasRequest<Depot, Siding>(jsonReader).query(simulator, simulator.depots);
			case "rails":
				return new RailsRequest(jsonReader).query(simulator);
			case "generate-by-depot-ids":
				return new GenerateOrClearByDepotIds(jsonReader).generate(simulator);
			case "generate-by-depot-name":
				return new GenerateOrClearByDepotName(jsonReader).generate(simulator);
			case "generate-by-lift":
				return new GenerateByLift(jsonReader, simulator).generate();
			case "clear-by-depot-ids":
				return new GenerateOrClearByDepotIds(jsonReader).clear(simulator);
			case "clear-by-depot-name":
				return new GenerateOrClearByDepotName(jsonReader).clear(simulator);
			default:
				return new JsonObject();
		}
	}
}
