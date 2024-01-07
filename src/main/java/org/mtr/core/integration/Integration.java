package org.mtr.core.integration;

import org.mtr.core.data.*;
import org.mtr.core.generated.integration.IntegrationSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectSet;

import javax.annotation.Nullable;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

public final class Integration extends IntegrationSchema {

	private static Data DATA;

	public Integration(Data data) {
		super();
		DATA = data;
	}

	public Integration(ReaderBase readerBase, Data data) {
		super(readerBase);
		DATA = data;
		updateData(readerBase);
	}

	public void iterateStations(Consumer<Station> consumer) {
		stations.forEach(consumer);
	}

	public void iteratePlatforms(Consumer<Platform> consumer) {
		platforms.forEach(consumer);
	}

	public void iterateSidings(Consumer<Siding> consumer) {
		sidings.forEach(consumer);
	}

	public void iterateRoutes(Consumer<Route> consumer) {
		routes.forEach(consumer);
	}

	public void iterateDepots(Consumer<Depot> consumer) {
		depots.forEach(consumer);
	}

	public void iterateLifts(Consumer<Lift> consumer) {
		lifts.forEach(consumer);
	}

	public void iterateRails(Consumer<Rail> consumer) {
		rails.forEach(consumer);
	}

	public void iterateRailNodePositions(Consumer<Position> consumer) {
		railNodePositions.forEach(consumer);
	}

	public void iterateSignals(Consumer<SignalModification> consumer) {
		signals.forEach(consumer);
	}

	public void iterateSimplifiedRoutes(Consumer<SimplifiedRoute> consumer) {
		simplifiedRoutes.forEach(consumer);
	}

	public void iterateVehiclesToUpdate(Consumer<VehicleUpdate> consumer) {
		vehiclesToUpdate.forEach(consumer);
	}

	public void iterateVehiclesToKeep(LongConsumer consumer) {
		vehiclesToKeep.forEach(consumer);
	}

	public void iterateVehiclesToRemove(LongConsumer consumer) {
		vehiclesToRemove.forEach(consumer);
	}

	public void iterateLiftsToUpdate(Consumer<Lift> consumer) {
		liftsToUpdate.forEach(consumer);
	}

	public void iterateLiftsToKeep(LongConsumer consumer) {
		liftsToKeep.forEach(consumer);
	}

	public void iterateLiftsToRemove(LongConsumer consumer) {
		liftsToRemove.forEach(consumer);
	}

	public void add(@Nullable ObjectSet<Station> stations, @Nullable ObjectSet<Platform> platforms, @Nullable ObjectSet<Siding> sidings, @Nullable ObjectSet<Route> routes, @Nullable ObjectSet<Depot> depots, @Nullable ObjectSet<Lift> lifts, @Nullable ObjectSet<SimplifiedRoute> simplifiedRoutes) {
		if (stations != null) {
			this.stations.addAll(stations);
		}
		if (platforms != null) {
			this.platforms.addAll(platforms);
		}
		if (sidings != null) {
			this.sidings.addAll(sidings);
		}
		if (routes != null) {
			this.routes.addAll(routes);
		}
		if (depots != null) {
			this.depots.addAll(depots);
		}
		if (lifts != null) {
			this.lifts.addAll(lifts);
		}
		if (simplifiedRoutes != null) {
			this.simplifiedRoutes.addAll(simplifiedRoutes);
		}
	}

	public void add(@Nullable ObjectSet<Rail> rails, @Nullable ObjectSet<Position> railNodePositions) {
		if (rails != null) {
			this.rails.addAll(rails);
		}
		if (railNodePositions != null) {
			this.railNodePositions.addAll(railNodePositions);
		}
	}

	public void add(@Nullable ObjectSet<SignalModification> signals) {
		if (signals != null) {
			this.signals.addAll(signals);
		}
	}

	public void addVehicleToUpdate(VehicleUpdate vehicleUpdate) {
		vehiclesToUpdate.add(vehicleUpdate);
	}

	public void addVehicleToKeep(long vehicleId) {
		vehiclesToKeep.add(vehicleId);
	}

	public void addVehicleToRemove(long vehicleId) {
		vehiclesToRemove.add(vehicleId);
	}

	public void addLiftToUpdate(Lift lift) {
		liftsToUpdate.add(lift);
	}

	public void addLiftToKeep(long liftId) {
		liftsToKeep.add(liftId);
	}

	public void addLiftToRemove(long liftId) {
		liftsToRemove.add(liftId);
	}

	public boolean noVehicleOrLiftUpdates() {
		return vehiclesToUpdate.isEmpty() && vehiclesToRemove.isEmpty() && liftsToUpdate.isEmpty() && liftsToRemove.isEmpty();
	}

	public boolean hasData() {
		return !stations.isEmpty() || !platforms.isEmpty() || !sidings.isEmpty() || !routes.isEmpty() || !depots.isEmpty() || !lifts.isEmpty() || !rails.isEmpty() || !railNodePositions.isEmpty() || !signals.isEmpty();
	}

	public boolean hasVehicleOrLift() {
		return !vehiclesToUpdate.isEmpty() || !vehiclesToKeep.isEmpty() || !vehiclesToRemove.isEmpty() || !liftsToUpdate.isEmpty() || !liftsToKeep.isEmpty() || !liftsToRemove.isEmpty();
	}

	@Deprecated
	public static Data getData() {
		return DATA;
	}
}
