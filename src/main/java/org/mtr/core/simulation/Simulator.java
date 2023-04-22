package org.mtr.core.simulation;

import it.unimi.dsi.fastutil.ints.IntIntImmutablePair;
import it.unimi.dsi.fastutil.objects.*;
import org.mtr.core.Main;
import org.mtr.core.data.*;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;

import java.nio.file.Path;
import java.util.Locale;

public class Simulator implements Utilities {

	private long lastMillis;
	private long currentMillis;
	private boolean autoSave = false;
	private String generateKey = null;

	public final int millisPerGameDay;
	public final float startingGameDayPercentage;

	public final ObjectAVLTreeSet<Station> stations = new ObjectAVLTreeSet<>();
	public final ObjectAVLTreeSet<Platform> platforms = new ObjectAVLTreeSet<>();
	public final ObjectAVLTreeSet<Siding> sidings = new ObjectAVLTreeSet<>();
	public final ObjectAVLTreeSet<Route> routes = new ObjectAVLTreeSet<>();
	public final ObjectAVLTreeSet<Depot> depots = new ObjectAVLTreeSet<>();
	public final ObjectAVLTreeSet<Lift> lifts = new ObjectAVLTreeSet<>();
	public final ObjectOpenHashBigSet<RailNode> railNodes = new ObjectOpenHashBigSet<>();
	public final SignalBlocks signalBlocks = new SignalBlocks();
	public final DataCache dataCache = new DataCache(this);

	private final String dimension;
	private final FileLoader<Station> fileLoaderStations;
	private final FileLoader<Platform> fileLoaderPlatforms;
	private final FileLoader<Siding> fileLoaderSidings;
	private final FileLoader<Route> fileLoaderRoutes;
	private final FileLoader<Depot> fileLoaderDepots;
	private final FileLoader<Lift> fileLoaderLifts;
	private final FileLoader<RailNode> fileLoaderRailNodes;
	private final ObjectArrayList<Runnable> queuedRuns = new ObjectArrayList<>();
	private final ObjectImmutableList<ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>>> vehiclePositions;

	public Simulator(String dimension, Path rootPath, int millisPerGameDay, float startingGameDayPercentage) {
		this.dimension = dimension;
		this.millisPerGameDay = millisPerGameDay;
		this.startingGameDayPercentage = startingGameDayPercentage;

		final long startMillis = System.currentTimeMillis();
		final Path savePath = rootPath.resolve(dimension);
		fileLoaderStations = new FileLoader<>(stations, Station::new, savePath, "stations", false);
		fileLoaderPlatforms = new FileLoader<>(platforms, Platform::new, savePath, "platforms", true);
		fileLoaderSidings = new FileLoader<>(sidings, messagePackHelper -> new Siding(messagePackHelper, this), savePath, "sidings", true);
		fileLoaderRoutes = new FileLoader<>(routes, Route::new, savePath, "routes", false);
		fileLoaderDepots = new FileLoader<>(depots, messagePackHelper -> new Depot(messagePackHelper, this), savePath, "depots", false);
		fileLoaderLifts = new FileLoader<>(lifts, Lift::new, savePath, "lifts", true);
		fileLoaderRailNodes = new FileLoader<>(railNodes, RailNode::new, savePath, "rails", true);

		dataCache.sync();
		depots.forEach(Depot::init);
		currentMillis = System.currentTimeMillis();

		final ObjectArrayList<ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>>> tempVehiclePositions = new ObjectArrayList<>();
		for (int i = 0; i < TransportMode.values().length; i++) {
			final ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> vehiclePositionsForTransportMode = new ObjectArrayList<>();
			vehiclePositionsForTransportMode.add(new Object2ObjectAVLTreeMap<>());
			vehiclePositionsForTransportMode.add(new Object2ObjectAVLTreeMap<>());
			tempVehiclePositions.add(vehiclePositionsForTransportMode);
		}
		vehiclePositions = new ObjectImmutableList<>(tempVehiclePositions);
		sidings.forEach(siding -> siding.initVehiclePositions(vehiclePositions.get(siding.transportMode.ordinal()).get(1)));

		Main.LOGGER.info(String.format("Data loading complete for %s in %s second(s)", dimension, (currentMillis - startMillis) / 1000F));
	}

	public void tick() {
		try {
			lastMillis = currentMillis;
			currentMillis = System.currentTimeMillis();

			vehiclePositions.forEach(vehiclePositionsForTransportMode -> {
				vehiclePositionsForTransportMode.remove(0);
				vehiclePositionsForTransportMode.add(new Object2ObjectAVLTreeMap<>());
			});

			depots.forEach(Depot::tick);
			sidings.forEach(Siding::tick);
			sidings.forEach(siding -> siding.simulateTrain(currentMillis - lastMillis, vehiclePositions.get(siding.transportMode.ordinal())));

			if (autoSave) {
				save(true);
				autoSave = false;
			}

			if (generateKey != null) {
				depots.stream().filter(depot -> depot.name.toLowerCase(Locale.ENGLISH).contains(generateKey)).forEach(Depot::generateMainRoute);
				generateKey = null;
			}

			if (!queuedRuns.isEmpty()) {
				final Runnable runnable = queuedRuns.remove(0);
				if (runnable != null) {
					runnable.run();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	}

	public void save() {
		autoSave = true;
	}

	public void stop() {
		save(false);
	}

	public void generatePath(String generateKey) {
		this.generateKey = generateKey.toLowerCase(Locale.ENGLISH).trim();
	}

	/**
	 * @param millis Milliseconds to check
	 * @return 1 if upcoming, 0 if current, -1 if passed
	 */
	public int matchMillis(long millis) {
		if (Utilities.circularDifference(currentMillis, millis, MILLIS_PER_DAY) < 0) {
			return 1;
		} else {
			return Utilities.circularDifference(millis, lastMillis, MILLIS_PER_DAY) > 0 ? 0 : -1;
		}
	}

	public void run(Runnable runnable) {
		queuedRuns.add(runnable);
	}

	private void save(boolean useReducedHash) {
		final long startMillis = System.currentTimeMillis();
		save(fileLoaderStations, useReducedHash);
		save(fileLoaderPlatforms, useReducedHash);
		save(fileLoaderSidings, useReducedHash);
		save(fileLoaderRoutes, useReducedHash);
		save(fileLoaderDepots, useReducedHash);
		save(fileLoaderLifts, useReducedHash);
		save(fileLoaderRailNodes, useReducedHash);
		Main.LOGGER.info(String.format("Save complete for %s in %s second(s)", dimension, (System.currentTimeMillis() - startMillis) / 1000F));
	}

	private <T extends SerializedDataBase> void save(FileLoader<T> fileLoader, boolean useReducedHash) {
		final IntIntImmutablePair saveCounts = fileLoader.save(useReducedHash);
		if (saveCounts.leftInt() > 0) {
			Main.LOGGER.info(String.format("- Changed %s: %s", fileLoader.key, saveCounts.leftInt()));
		}
		if (saveCounts.rightInt() > 0) {
			Main.LOGGER.info(String.format("- Deleted %s: %s", fileLoader.key, saveCounts.rightInt()));
		}
	}
}
