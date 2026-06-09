package org.mtr.core.data;

import it.unimi.dsi.fastutil.longs.Long2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import org.mtr.core.generated.data.ClientSchema;
import org.mtr.core.operation.DynamicDataResponse;
import org.mtr.core.operation.PlayerPresentResponse;
import org.mtr.core.operation.VehicleUpdate;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.serializer.SerializedDataBase;
import org.mtr.core.servlet.OperationProcessor;
import org.mtr.core.simulation.Simulator;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class Client extends ClientSchema {

	public final UUID uuid;

	private final LongAVLTreeSet existingVehicleIds = new LongAVLTreeSet();
	private final LongAVLTreeSet keepVehicleIds = new LongAVLTreeSet();
	private final Long2ObjectAVLTreeMap<VehicleUpdate> vehicleUpdates = new Long2ObjectAVLTreeMap<>();

	private final LongAVLTreeSet existingLiftIds = new LongAVLTreeSet();
	private final LongAVLTreeSet keepLiftIds = new LongAVLTreeSet();
	private final Long2ObjectAVLTreeMap<Lift> liftUpdates = new Long2ObjectAVLTreeMap<>();

	private final ObjectAVLTreeSet<String> existingRailIds = new ObjectAVLTreeSet<>();
	private final ObjectAVLTreeSet<String> keepRailIds = new ObjectAVLTreeSet<>();
	private final Object2ObjectAVLTreeMap<String, Rail> signalBlockUpdates = new Object2ObjectAVLTreeMap<>();

	private final LongAVLTreeSet existingPassengerIds = new LongAVLTreeSet();
	private final LongAVLTreeSet keepPassengerIds = new LongAVLTreeSet();
	private final Long2ObjectAVLTreeMap<Passenger> passengerUpdates = new Long2ObjectAVLTreeMap<>();

	/**
	 * Create a new client with the given unique identifier.
	 *
	 * @param uuid client UUID used for tracking across the mod and dashboard
	 */
	public Client(UUID uuid) {
		super(uuid.toString());
		this.uuid = uuid;
	}

	/**
	 * Deserialisation constructor used by the wire / on-disk layer.
	 */
	public Client(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
		uuid = UUID.fromString(clientId);
	}

	@Override
	protected Position getDefaultPosition() {
		return new Position(0, 0, 0);
	}

	public Position getPosition() {
		return position;
	}

	public double getUpdateRadius() {
		return updateRadius;
	}

	/**
	 * Update the client's tracked position and area-of-interest radius. Entities within this
	 * radius will be pushed to the client on the next {@link #sendUpdates} cycle.
	 */
	public void setPositionAndUpdateRadius(Position position, long updateRadius) {
		this.position = position;
		this.updateRadius = updateRadius;
	}

	/**
	 * Send pending vehicle / lift / passenger / signal-block updates to the client via the
	 * server-to-client message queue.
	 */
	public void sendUpdates(Simulator simulator) {
		final DynamicDataResponse dynamicDataResponse = new DynamicDataResponse(uuid, simulator);
		final boolean hasUpdate1 = process(vehicleUpdates, existingVehicleIds, keepVehicleIds, dynamicDataResponse::addVehicleToUpdate, dynamicDataResponse::addVehicleToKeep);
		final boolean hasUpdate2 = process(liftUpdates, existingLiftIds, keepLiftIds, dynamicDataResponse::addLiftToUpdate, dynamicDataResponse::addLiftToKeep);
		final boolean hasUpdate3 = process(signalBlockUpdates, existingRailIds, keepRailIds, dynamicDataResponse::addSignalBlockUpdate, railId -> {
		});
		final boolean hasUpdate4 = process(passengerUpdates, existingPassengerIds, keepPassengerIds, dynamicDataResponse::addPassengerToUpdate, dynamicDataResponse::addPassengerToKeep);

		if (hasUpdate1 || hasUpdate2 || hasUpdate3 || hasUpdate4) {
			simulator.sendMessageS2C(OperationProcessor.VEHICLES_LIFTS, dynamicDataResponse, playerPresentResponse -> playerPresentResponse.verify(simulator, uuid), PlayerPresentResponse.class);
		}
	}

	/**
	 * Track a vehicle for the next {@link #sendUpdates} cycle. If the vehicle is new or
	 * dirty it is queued for a full update; otherwise it is kept alive so the client knows not
	 * to remove it.
	 *
	 * @param vehicle         the vehicle to track
	 * @param needsUpdate     whether the vehicle's state has changed since the last sync
	 * @param pathUpdateIndex index into the vehicle's path data for partial updates
	 */
	public void update(Vehicle vehicle, boolean needsUpdate, int pathUpdateIndex) {
		final long vehicleId = vehicle.getId();
		if (needsUpdate || !existingVehicleIds.contains(vehicleId)) {
			vehicleUpdates.put(vehicleId, new VehicleUpdate(vehicle, vehicle.vehicleExtraData.copy(pathUpdateIndex)));
			keepVehicleIds.remove(vehicleId);
		} else if (!vehicleUpdates.containsKey(vehicleId)) {
			keepVehicleIds.add(vehicleId);
		}
	}

	/**
	 * Track a lift for the next {@link #sendUpdates} cycle.
	 *
	 * @param lift        the lift to track
	 * @param needsUpdate whether the lift's state has changed since the last sync
	 */
	public void update(Lift lift, boolean needsUpdate) {
		final long liftId = lift.getId();
		if (needsUpdate || !existingLiftIds.contains(liftId)) {
			liftUpdates.put(liftId, lift);
			keepLiftIds.remove(liftId);
		} else if (!liftUpdates.containsKey(liftId)) {
			keepLiftIds.add(liftId);
		}
	}

	/**
	 * Track a rail for signal-block updates on the next {@link #sendUpdates} cycle.
	 *
	 * @param rail        the rail to track
	 * @param needsUpdate whether the rail's signal-block state has changed since the last sync
	 */
	public void update(Rail rail, boolean needsUpdate) {
		final String railId = rail.getHexId();
		if (needsUpdate || !existingRailIds.contains(railId)) {
			signalBlockUpdates.put(railId, rail);
			keepRailIds.remove(railId);
		} else if (!signalBlockUpdates.containsKey(railId)) {
			keepRailIds.add(railId);
		}
	}

	/**
	 * Track a passenger for the next {@link #sendUpdates} cycle. If the passenger is new or
	 * dirty it is queued for a full update; otherwise it is kept alive so the client knows not
	 * to remove it.
	 *
	 * @param passenger   the passenger to track
	 * @param needsUpdate whether the passenger's state has changed since the last sync
	 */
	public void update(Passenger passenger, boolean needsUpdate) {
		final long passengerId = passenger.getId();
		if (needsUpdate || !existingPassengerIds.contains(passengerId)) {
			passengerUpdates.put(passengerId, passenger);
			keepPassengerIds.remove(passengerId);
		} else if (!passengerUpdates.containsKey(passengerId)) {
			keepPassengerIds.add(passengerId);
		}
	}

	private static <T, U extends SerializedDataBase> boolean process(Map<T, U> dataUpdates, Set<T> existingIds, Set<T> keepIds, Consumer<U> addDataToUpdate, Consumer<T> addDataToKeep) {
		dataUpdates.forEach((id, data) -> {
			addDataToUpdate.accept(data);
			existingIds.remove(id);
		});

		keepIds.forEach(id -> {
			addDataToKeep.accept(id);
			existingIds.remove(id);
		});

		// Has data to remove or has data to update
		final boolean hasUpdate = !existingIds.isEmpty() || !dataUpdates.isEmpty();

		existingIds.clear();
		existingIds.addAll(dataUpdates.keySet());
		existingIds.addAll(keepIds);

		dataUpdates.clear();
		keepIds.clear();
		return hasUpdate;
	}
}
