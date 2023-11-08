package org.mtr.core.integration;

import org.mtr.core.data.*;
import org.mtr.core.generated.integration.IntegrationSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.function.Consumer;

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

	public void add(ObjectAVLTreeSet<Station> stations, ObjectAVLTreeSet<Platform> platforms, ObjectAVLTreeSet<Siding> sidings, ObjectAVLTreeSet<Route> routes, ObjectAVLTreeSet<Depot> depots) {
		this.stations.addAll(stations);
		this.platforms.addAll(platforms);
		this.sidings.addAll(sidings);
		this.routes.addAll(routes);
		this.depots.addAll(depots);
	}

	public void add(ObjectOpenHashSet<Rail> rails, ObjectOpenHashSet<Position> positions) {
		this.rails.addAll(rails);
		this.positions.addAll(positions);
	}
}
