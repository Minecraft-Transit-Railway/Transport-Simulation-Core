package org.mtr.core.servlet;

import org.mtr.core.data.Depot;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Siding;
import org.mtr.core.data.Station;
import org.mtr.core.operation.*;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.serializer.SerializedDataBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;

import javax.annotation.Nullable;

public final class OperationProcessor {

	@Nullable
	public static SerializedDataBase process(Operation operation, SerializedDataBase data, long currentMillis, Simulator simulator) {
		// TODO is there a better way to create these objects than to cast to a JSON and back?
		final JsonReader jsonReader = new JsonReader(Utilities.getJsonObjectFromData(data));

		switch (operation) {
			case GET_DATA:
				return new DataRequest(jsonReader).getData(simulator);
			case UPDATE_DATA:
				return new UpdateDataRequest(jsonReader, simulator).update();
			case DELETE_DATA:
				return new DeleteDataRequest(jsonReader).delete(simulator);
			case LIST_DATA:
				return new ListDataResponse(jsonReader, simulator).list();
			case ARRIVALS:
				return new ArrivalsRequest(jsonReader).getArrivals(simulator, currentMillis);
			case SET_TIME:
				new SetTime(jsonReader).setGameTime(simulator);
				return null;
			case UPDATE_RIDING_ENTITIES:
				return new UpdateVehicleRidingEntities(jsonReader).update(simulator);
			case PRESS_LIFT:
				new PressLift(jsonReader).pressLift(simulator);
				return null;
			case NEARBY_STATIONS:
				return new NearbyAreasRequest<Station, Platform>(jsonReader).query(simulator, simulator.stations);
			case NEARBY_DEPOTS:
				return new NearbyAreasRequest<Depot, Siding>(jsonReader).query(simulator, simulator.depots);
			case RAILS:
				return new RailsRequest(jsonReader).query(simulator);
			case GENERATE_BY_DEPOT_IDS:
				new GenerateOrClearByDepotIds(jsonReader).generate(simulator);
				return null;
			case GENERATE_BY_DEPOT_NAME:
				new GenerateOrClearByDepotName(jsonReader).generate(simulator);
				return null;
			case GENERATE_BY_LIFT:
				new GenerateByLift(jsonReader, simulator).generate();
				return null;
			case CLEAR_BY_DEPOT_IDS:
				new GenerateOrClearByDepotIds(jsonReader).clear(simulator);
				return null;
			case CLEAR_BY_DEPOT_NAME:
				new GenerateOrClearByDepotName(jsonReader).clear(simulator);
				return null;
			default:
				return null;
		}
	}
}
