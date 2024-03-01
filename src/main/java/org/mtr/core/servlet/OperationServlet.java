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

import java.util.function.Consumer;

public final class OperationServlet extends ServletBase {

	public OperationServlet(ObjectImmutableList<Simulator> simulators) {
		super(simulators);
	}

	@Override
	protected void getContent(String endpoint, String data, Object2ObjectAVLTreeMap<String, String> parameters, JsonReader jsonReader, long currentMillis, Simulator simulator, Consumer<JsonObject> sendResponse) {
		switch (endpoint) {
			case "get-data":
				sendResponse.accept(new DataRequest(jsonReader).getData(simulator));
				break;
			case "update-data":
				sendResponse.accept(new UpdateDataRequest(jsonReader, simulator).update());
				break;
			case "delete-data":
				sendResponse.accept(new DeleteDataRequest(jsonReader).delete(simulator));
				break;
			case "list-data":
				sendResponse.accept(new ListDataResponse(jsonReader, simulator).list());
				break;
			case "arrivals":
				sendResponse.accept(new ArrivalsRequest(jsonReader).getArrivals(simulator, currentMillis));
				break;
			case "set-time":
				sendResponse.accept(new SetTime(jsonReader).setGameTime(simulator));
				break;
			case "update-riding-entities":
				sendResponse.accept(new UpdateVehicleRidingEntities(jsonReader).update(simulator));
				break;
			case "press-lift":
				sendResponse.accept(new PressLift(jsonReader).pressLift(simulator));
				break;
			case "nearby-stations":
				sendResponse.accept(new NearbyAreasRequest<Station, Platform>(jsonReader).query(simulator, simulator.stations));
				break;
			case "nearby-depots":
				sendResponse.accept(new NearbyAreasRequest<Depot, Siding>(jsonReader).query(simulator, simulator.depots));
				break;
			case "rails":
				sendResponse.accept(new RailsRequest(jsonReader).query(simulator));
				break;
			case "generate-by-depot-ids":
				new GenerateOrClearByDepotIds(jsonReader).generate(simulator, sendResponse);
				break;
			case "generate-by-depot-name":
				new GenerateOrClearByDepotName(jsonReader).generate(simulator, sendResponse);
				break;
			case "generate-by-lift":
				sendResponse.accept(new GenerateByLift(jsonReader, simulator).generate());
				break;
			case "clear-by-depot-ids":
				new GenerateOrClearByDepotIds(jsonReader).clear(simulator);
				break;
			case "clear-by-depot-name":
				new GenerateOrClearByDepotName(jsonReader).clear(simulator);
				break;
			case "directions":
				new DirectionsRequest(jsonReader).find(simulator, sendResponse);
				break;
			default:
				sendResponse.accept(null);
				break;
		}
	}
}
