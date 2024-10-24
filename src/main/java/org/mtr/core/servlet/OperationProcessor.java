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

	// Client to server
	public static final String GET_DATA = "get_data";
	public static final String UPDATE_DATA = "update_data";
	public static final String DELETE_DATA = "delete_data";
	public static final String LIST_DATA = "list_data";
	public static final String ARRIVALS = "arrivals";
	public static final String SET_TIME = "set_time";
	public static final String UPDATE_RIDING_ENTITIES = "update_riding_entities";
	public static final String PRESS_LIFT = "press_lift";
	public static final String NEARBY_STATIONS = "nearby_stations";
	public static final String NEARBY_DEPOTS = "nearby_depots";
	public static final String RAILS = "rails";
	public static final String GENERATE_BY_DEPOT_IDS = "generate_by_depot_ids";
	public static final String GENERATE_BY_DEPOT_NAME = "generate_by_depot_name";
	public static final String GENERATE_BY_LIFT = "generate_by_lift";
	public static final String CLEAR_BY_DEPOT_IDS = "clear_by_depot_ids";
	public static final String CLEAR_BY_DEPOT_NAME = "clear_by_depot_name";

	// Server to client
	public static final String VEHICLES_LIFTS = "vehicles_lifts";
	public static final String GENERATION_STATUS_UPDATE = "generation_status_update";

	@Nullable
	public static SerializedDataBase process(String key, SerializedDataBase data, Simulator simulator) {
		// TODO is there a better way to create these objects than to cast to a JSON and back?
		final JsonReader jsonReader = new JsonReader(Utilities.getJsonObjectFromData(data));

		switch (key) {
			case GET_DATA:
				return new DataRequest(jsonReader).getData(simulator);
			case UPDATE_DATA:
				return new UpdateDataRequest(jsonReader, simulator).update();
			case DELETE_DATA:
				return new DeleteDataRequest(jsonReader).delete(simulator);
			case LIST_DATA:
				return new ListDataResponse(jsonReader, simulator).list();
			case ARRIVALS:
				return new ArrivalsRequest(jsonReader).getArrivals(simulator);
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
