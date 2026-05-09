package org.mtr.core.servlet;

import org.jspecify.annotations.Nullable;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Siding;
import org.mtr.core.data.Station;
import org.mtr.core.operation.*;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.serializer.SerializedDataBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;

/**
 * Dispatch table for the simulator's operation wire protocol.
 *
 * <p>Every {@code public static final String} on this class names a {@code lower_snake_case}
 * operation key (per CODE_STYLES §1.3). {@link #process(String, SerializedDataBase, Simulator)}
 * routes an incoming {@code (key, payload)} pair to the matching request DTO and returns the
 * response payload (or {@code null} for fire-and-forget operations).</p>
 */
public final class OperationProcessor {

	// Client to server
	/** Bulk-read entities by id. */
	public static final String GET_DATA = "get_data";
	/** Create or update entities. */
	public static final String UPDATE_DATA = "update_data";
	/** Delete entities by id. */
	public static final String DELETE_DATA = "delete_data";
	/** List entities matching a filter. */
	public static final String LIST_DATA = "list_data";
	/** Query upcoming arrivals at one or more platforms. */
	public static final String ARRIVALS = "arrivals";
	/** Push the in-game time and day length into the simulator. */
	public static final String SET_TIME = "set_time";
	/** Update which entities are riding which vehicles. */
	public static final String UPDATE_RIDING_ENTITIES = "update_riding_entities";
	/** Mark a stretch of rail as blocked / unblocked. */
	public static final String BLOCK_RAILS = "block_rails";
	/** Press a hall-call button on a lift. */
	public static final String PRESS_LIFT = "press_lift";
	/** Find stations near a position. */
	public static final String NEARBY_STATIONS = "nearby_stations";
	/** Find depots near a position. */
	public static final String NEARBY_DEPOTS = "nearby_depots";
	/** Query rails near a position. */
	public static final String RAILS = "rails";
	/** Trigger depot path generation by id list. */
	public static final String GENERATE_BY_DEPOT_IDS = "generate_by_depot_ids";
	/** Trigger depot path generation by name filter. */
	public static final String GENERATE_BY_DEPOT_NAME = "generate_by_depot_name";
	/** Trigger lift path generation. */
	public static final String GENERATE_BY_LIFT = "generate_by_lift";
	/** Clear depot generation results by id list. */
	public static final String CLEAR_BY_DEPOT_IDS = "clear_by_depot_ids";
	/** Clear depot generation results by name filter. */
	public static final String CLEAR_BY_DEPOT_NAME = "clear_by_depot_name";
	/** Instantly fast-forward depots through one in-game day, by id list. */
	public static final String INSTANT_DEPLOY_BY_DEPOT_IDS = "instant_deploy_by_depot_ids";
	/** Instantly fast-forward depots through one in-game day, by name filter. */
	public static final String INSTANT_DEPLOY_BY_DEPOT_NAME = "instant_deploy_by_depot_name";

	// Server to client
	/** Server-pushed bulk update of vehicle and lift states. */
	public static final String VEHICLES_LIFTS = "vehicles_lifts";
	/** Server-pushed depot path-generation status update. */
	public static final String GENERATION_STATUS_UPDATE = "generation_status_update";

	/**
	 * Dispatch an incoming {@code (key, data)} pair to its handler.
	 *
	 * @param key       one of the operation keys defined on this class
	 * @param data      the request payload, already wrapped in a {@link SerializedDataBase}
	 * @param simulator simulator the operation should be applied to
	 * @return the response payload to send back to the client, or {@code null} for fire-and-forget operations
	 * (and for an unknown {@code key}).
	 */
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
			case BLOCK_RAILS:
				new BlockRails(jsonReader).blockRails(simulator);
				return null;
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
				new DepotOperationByIds(jsonReader).generate(simulator);
				return null;
			case GENERATE_BY_DEPOT_NAME:
				new DepotOperationByName(jsonReader).generate(simulator);
				return null;
			case GENERATE_BY_LIFT:
				new GenerateByLift(jsonReader, simulator).generate();
				return null;
			case CLEAR_BY_DEPOT_IDS:
				new DepotOperationByIds(jsonReader).clear(simulator);
				return null;
			case CLEAR_BY_DEPOT_NAME:
				new DepotOperationByName(jsonReader).clear(simulator);
				return null;
			case INSTANT_DEPLOY_BY_DEPOT_IDS:
				new DepotOperationByIds(jsonReader).instantDeploy(simulator);
				return null;
			case INSTANT_DEPLOY_BY_DEPOT_NAME:
				new DepotOperationByName(jsonReader).instantDeploy(simulator);
				return null;
			default:
				return null;
		}
	}
}
