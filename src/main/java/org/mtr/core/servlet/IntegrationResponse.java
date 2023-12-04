package org.mtr.core.servlet;

import org.mtr.core.Main;
import org.mtr.core.data.*;
import org.mtr.core.integration.Integration;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.serializer.SerializedDataBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.*;

import javax.annotation.Nullable;

public final class IntegrationResponse extends ResponseBase<Integration> {

	public IntegrationResponse(String data, Object2ObjectAVLTreeMap<String, String> parameters, Integration body, long currentMillis, Simulator simulator) {
		super(data, parameters, body, currentMillis, simulator);
	}

	public Integration update() {
		return parseBody(IntegrationResponse::update, PositionCallback.EMPTY, (signalModification, railsToUpdate, positionsToUpdate) -> signalModification.applyModificationToRail(simulator, railsToUpdate, positionsToUpdate), true);
	}

	public Integration get() {
		return parseBody(IntegrationResponse::get, PositionCallback.EMPTY, SignalModificationCallback.EMPTY, false);
	}

	public Integration delete() {
		return parseBody(IntegrationResponse::delete, (position, railsToUpdate, positionsToUpdate) -> simulator.positionsToRail.getOrDefault(position, new Object2ObjectOpenHashMap<>()).forEach((connectedPosition, rail) -> {
			simulator.rails.remove(rail);
			railsToUpdate.add(rail);
			positionsToUpdate.add(connectedPosition);
		}), SignalModificationCallback.EMPTY, true);
	}

	public Integration generate() {
		return parseBody(IntegrationResponse::generate, PositionCallback.EMPTY, SignalModificationCallback.EMPTY, false);
	}

	public Integration clear() {
		return parseBody(IntegrationResponse::clear, PositionCallback.EMPTY, SignalModificationCallback.EMPTY, false);
	}

	public Integration list() {
		// Outbound list operations (not update packets) should contain never contain simplified routes
		final Integration integration = new Integration(simulator);
		integration.add(simulator.stations, simulator.platforms, simulator.sidings, simulator.routes, simulator.depots, null);
		return integration;
	}

	private Integration parseBody(BodyCallback bodyCallback, PositionCallback positionCallback, SignalModificationCallback signalModificationCallback, boolean shouldSync) {
		final ObjectAVLTreeSet<Station> stationsToUpdate = new ObjectAVLTreeSet<>();
		final ObjectAVLTreeSet<Platform> platformsToUpdate = new ObjectAVLTreeSet<>();
		final ObjectAVLTreeSet<Siding> sidingsToUpdate = new ObjectAVLTreeSet<>();
		final ObjectAVLTreeSet<Route> routesToUpdate = new ObjectAVLTreeSet<>();
		final ObjectAVLTreeSet<Depot> depotsToUpdate = new ObjectAVLTreeSet<>();
		final ObjectOpenHashSet<Rail> railsToUpdate = new ObjectOpenHashSet<>();
		final ObjectOpenHashSet<Position> positionsToUpdate = new ObjectOpenHashSet<>();

		try {
			body.iterateStations(station -> bodyCallback.accept(station, true, simulator.stationIdMap.get(station.getId()), simulator.stations, stationsToUpdate));
			body.iteratePlatforms(platform -> bodyCallback.accept(platform, false, simulator.platformIdMap.get(platform.getId()), simulator.platforms, platformsToUpdate));
			body.iterateSidings(siding -> bodyCallback.accept(siding, false, simulator.sidingIdMap.get(siding.getId()), simulator.sidings, sidingsToUpdate));
			body.iterateRoutes(route -> bodyCallback.accept(route, true, simulator.routeIdMap.get(route.getId()), simulator.routes, routesToUpdate));
			body.iterateDepots(depot -> bodyCallback.accept(depot, true, simulator.depotIdMap.get(depot.getId()), simulator.depots, depotsToUpdate));
			body.iterateRails(rail -> bodyCallback.accept(rail, true, rail.getRailFromData(simulator, positionsToUpdate), simulator.rails, railsToUpdate));
			body.iteratePositions(position -> {
				positionsToUpdate.add(position);
				positionCallback.accept(position, railsToUpdate, positionsToUpdate);
			});
			body.iterateSignals(signalModification -> signalModificationCallback.accept(signalModification, railsToUpdate, positionsToUpdate));
		} catch (Exception e) {
			Main.logException(e);
		}

		if (shouldSync) {
			railsToUpdate.forEach(rail -> rail.checkOrCreatePlatform(simulator, platformsToUpdate, sidingsToUpdate));
			simulator.sync();
		}
		positionsToUpdate.removeIf(position -> !simulator.positionsToRail.getOrDefault(position, new Object2ObjectOpenHashMap<>()).isEmpty());

		// Inbound update packets should never contain simplified routes
		final Integration integration = new Integration(simulator);
		integration.add(stationsToUpdate, platformsToUpdate, sidingsToUpdate, routesToUpdate, depotsToUpdate, null);
		integration.add(railsToUpdate, positionsToUpdate);
		return integration;
	}

	private static <T extends SerializedDataBase> void update(T bodyData, boolean addNewData, @Nullable T existingData, ObjectSet<T> dataSet, ObjectSet<T> dataToUpdate) {
		final boolean isRail = bodyData instanceof Rail;
		final boolean isValid = !isRail || ((Rail) bodyData).isValid();

		if (existingData == null) {
			if (addNewData && isValid) {
				dataSet.add(bodyData);
				dataToUpdate.add(bodyData);
			}
		} else if (isValid) {
			// For AVL tree sets, data must be removed and re-added when modified
			dataSet.remove(existingData);
			if (isRail) {
				dataSet.add(bodyData);
				dataToUpdate.add(bodyData);
			} else {
				existingData.updateData(new JsonReader(Utilities.getJsonObjectFromData(bodyData)));
				dataSet.add(existingData);
				dataToUpdate.add(existingData);
			}
		}
	}

	private static <T extends SerializedDataBase> void get(T bodyData, boolean addNewData, @Nullable T existingData, ObjectSet<T> dataSet, ObjectSet<T> dataToUpdate) {
		if (existingData != null) {
			dataToUpdate.add(existingData);
		}
	}

	private static <T extends SerializedDataBase> void delete(T bodyData, boolean addNewData, @Nullable T existingData, ObjectSet<T> dataSet, ObjectSet<T> dataToUpdate) {
		if (existingData != null && dataSet.remove(existingData)) {
			dataToUpdate.add(existingData);
		}
	}

	private static <T extends SerializedDataBase> void generate(T bodyData, boolean addNewData, @Nullable T existingData, ObjectSet<T> dataSet, ObjectSet<T> dataToUpdate) {
		if (existingData instanceof Depot) {
			dataToUpdate.add(existingData);
			((Depot) existingData).generateMainRoute();
		}
	}

	private static <T extends SerializedDataBase> void clear(T bodyData, boolean addNewData, @Nullable T existingData, ObjectSet<T> dataSet, ObjectSet<T> dataToUpdate) {
		if (existingData instanceof Siding) {
			dataToUpdate.add(existingData);
			((Siding) existingData).clearVehicles();
		}
		if (existingData instanceof Depot) {
			dataToUpdate.add(existingData);
			((Depot) existingData).savedRails.forEach(Siding::clearVehicles);
		}
	}

	@FunctionalInterface
	private interface BodyCallback {
		<T extends SerializedDataBase> void accept(T bodyData, boolean addNewData, @Nullable T existingData, ObjectSet<T> dataSet, ObjectSet<T> dataToUpdate);
	}

	@FunctionalInterface
	private interface PositionCallback {
		PositionCallback EMPTY = (position, railsToUpdate, positionsToUpdate) -> {
		};

		void accept(Position position, ObjectOpenHashSet<Rail> railsToUpdate, ObjectOpenHashSet<Position> positionsToUpdate);
	}

	@FunctionalInterface
	private interface SignalModificationCallback {
		SignalModificationCallback EMPTY = (signalModification, railsToUpdate, positionsToUpdate) -> {
		};

		void accept(SignalModification signalModification, ObjectOpenHashSet<Rail> railsToUpdate, ObjectOpenHashSet<Position> positionsToUpdate);
	}
}
