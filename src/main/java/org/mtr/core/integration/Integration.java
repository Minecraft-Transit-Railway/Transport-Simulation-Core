package org.mtr.core.integration;

import org.mtr.core.data.*;
import org.mtr.core.generated.integration.IntegrationSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectSet;

import javax.annotation.Nullable;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

public final class Integration extends IntegrationSchema {

	public Integration() {
		super();
	}

	public Integration(ReaderBase readerBase) {
		super(readerBase);
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

	public void iterateRails(Consumer<Rail> consumer) {
		rails.forEach(consumer);
	}

	public void iteratePositions(Consumer<Position> consumer) {
		positions.forEach(consumer);
	}

	public void iterateSignals(Consumer<SignalModification> consumer) {
		signals.forEach(consumer);
	}

	public void iterateSimplifiedRoutes(Consumer<SimplifiedRoute> consumer) {
		simplifiedRoutes.forEach(consumer);
	}

	public void iterateVehiclesToUpdate(Consumer<VehicleUpdate> consumer) {
		update.forEach(consumer);
	}

	public void iterateVehiclesToKeep(LongConsumer consumer) {
		keep.forEach(consumer);
	}

	public void iterateVehiclesToRemove(LongConsumer consumer) {
		remove.forEach(consumer);
	}

	public void add(@Nullable ObjectSet<Station> stations, @Nullable ObjectSet<Platform> platforms, @Nullable ObjectSet<Siding> sidings, @Nullable ObjectSet<Route> routes, @Nullable ObjectSet<Depot> depots, @Nullable ObjectSet<SimplifiedRoute> simplifiedRoutes) {
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
		if (simplifiedRoutes != null) {
			this.simplifiedRoutes.addAll(simplifiedRoutes);
		}
	}

	public void add(@Nullable ObjectSet<Rail> rails, @Nullable ObjectSet<Position> positions) {
		if (rails != null) {
			this.rails.addAll(rails);
		}
		if (positions != null) {
			this.positions.addAll(positions);
		}
	}

	public void add(@Nullable ObjectSet<SignalModification> signals) {
		if (signals != null) {
			this.signals.addAll(signals);
		}
	}

	public void addVehicleToUpdate(VehicleUpdate vehicleUpdate) {
		update.add(vehicleUpdate);
	}

	public void addVehicleToKeep(long vehicleId) {
		keep.add(vehicleId);
	}

	public void addVehicleToRemove(long vehicleId) {
		remove.add(vehicleId);
	}

	public boolean noVehicleUpdates() {
		return update.isEmpty() && remove.isEmpty();
	}

	public boolean hasData() {
		return !stations.isEmpty() || !platforms.isEmpty() || !sidings.isEmpty() || !routes.isEmpty() || !depots.isEmpty() || !rails.isEmpty() || !positions.isEmpty() || !signals.isEmpty();
	}

	public boolean hasVehicle() {
		return !update.isEmpty() || !keep.isEmpty() || !remove.isEmpty();
	}
}