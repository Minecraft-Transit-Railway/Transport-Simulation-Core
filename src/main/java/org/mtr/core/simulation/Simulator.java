package org.mtr.core.simulation;

import org.mtr.core.Main;
import org.mtr.core.data.*;
import org.mtr.core.serializer.SerializedDataBaseWithId;
import org.mtr.core.tool.Utilities;
import org.mtr.legacy.data.LegacyRailLoader;
import org.mtr.libraries.it.unimi.dsi.fastutil.ints.IntIntImmutablePair;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.*;

import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

public class Simulator extends Data implements Utilities {

	private long lastMillis;
	private long currentMillis;
	private boolean autoSave = false;
	private String generateKey = null;
	private long gameMillis;
	private long gameMillisPerDay;
	private long lastSetGameMillis;

	public final Object2ObjectOpenHashMap<String, Client> clients = new Object2ObjectOpenHashMap<>();

	private final String dimension;
	private final int clientWebserverPort;
	private final FileLoader<Station> fileLoaderStations;
	private final FileLoader<Platform> fileLoaderPlatforms;
	private final FileLoader<Siding> fileLoaderSidings;
	private final FileLoader<Route> fileLoaderRoutes;
	private final FileLoader<Depot> fileLoaderDepots;
	private final FileLoader<Lift> fileLoaderLifts;
	private final FileLoader<Rail> fileLoaderRails;
	private final ObjectArrayList<Runnable> queuedRuns = new ObjectArrayList<>();
	private final ObjectImmutableList<ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>>> vehiclePositions;
	private final Object2LongOpenHashMap<UUID> ridingVehicleIds = new Object2LongOpenHashMap<>();

	public Simulator(String dimension, Path rootPath, int clientWebserverPort) {
		this.dimension = dimension;
		this.clientWebserverPort = clientWebserverPort;
		final long startMillis = System.currentTimeMillis();

		final Path savePath = rootPath.resolve(dimension);
		LegacyRailLoader.load(savePath, rails);
		fileLoaderStations = new FileLoader<>(stations, messagePackHelper -> new Station(messagePackHelper, this), savePath, "stations");
		fileLoaderPlatforms = new FileLoader<>(platforms, messagePackHelper -> new Platform(messagePackHelper, this), savePath, "platforms");
		fileLoaderSidings = new FileLoader<>(sidings, messagePackHelper -> new Siding(messagePackHelper, this), savePath, "sidings");
		fileLoaderRoutes = new FileLoader<>(routes, messagePackHelper -> new Route(messagePackHelper, this), savePath, "routes");
		fileLoaderDepots = new FileLoader<>(depots, messagePackHelper -> new Depot(messagePackHelper, this), savePath, "depots");
		fileLoaderLifts = new FileLoader<>(lifts, messagePackHelper -> new Lift(messagePackHelper, this), savePath, "lifts");
		fileLoaderRails = new FileLoader<>(rails, Rail::new, savePath, "rails");

		currentMillis = System.currentTimeMillis();
		Main.LOGGER.info(String.format("Data loading complete for %s in %s second(s)", dimension, (float) (currentMillis - startMillis) / MILLIS_PER_SECOND));

		sync();
		depots.forEach(Depot::init);

		final ObjectArrayList<ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>>> tempVehiclePositions = new ObjectArrayList<>();
		for (int i = 0; i < TransportMode.values().length; i++) {
			final ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> vehiclePositionsForTransportMode = new ObjectArrayList<>();
			vehiclePositionsForTransportMode.add(new Object2ObjectAVLTreeMap<>());
			vehiclePositionsForTransportMode.add(new Object2ObjectAVLTreeMap<>());
			tempVehiclePositions.add(vehiclePositionsForTransportMode);
		}
		vehiclePositions = new ObjectImmutableList<>(tempVehiclePositions);
		sidings.forEach(siding -> siding.initVehiclePositions(vehiclePositions.get(siding.getTransportModeOrdinal()).get(1)));
	}

	public void tick() {
		try {
			lastMillis = currentMillis;
			currentMillis = System.currentTimeMillis();

			vehiclePositions.forEach(vehiclePositionsForTransportMode -> {
				vehiclePositionsForTransportMode.remove(0);
				vehiclePositionsForTransportMode.add(new Object2ObjectAVLTreeMap<>());
			});

			rails.forEach(Rail::tick);
			depots.forEach(Depot::tick);
			sidings.forEach(Siding::tick);
			sidings.forEach(siding -> siding.simulateTrain(currentMillis - lastMillis, vehiclePositions.get(siding.getTransportModeOrdinal())));
			clients.forEach((clientId, client) -> client.sendUpdates(this, clientWebserverPort));

			if (autoSave) {
				save(true);
				autoSave = false;
			}

			if (generateKey != null) {
				depots.stream().filter(depot -> depot.getName().toLowerCase(Locale.ENGLISH).contains(generateKey)).forEach(Depot::generateMainRoute);
				generateKey = null;
			}

			lifts.forEach(lift -> lift.tick(currentMillis - lastMillis));

			if (!queuedRuns.isEmpty()) {
				final Runnable runnable = queuedRuns.remove(0);
				if (runnable != null) {
					runnable.run();
				}
			}
		} catch (Exception e) {
			Main.logException(e);
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

	/**
	 * @param gameMillis       the number of real-time milliseconds since midnight of the in-game time
	 * @param gameMillisPerDay the total number of real-time milliseconds of one in-game day
	 */
	public void setGameTime(long gameMillis, long gameMillisPerDay) {
		this.gameMillis = gameMillis % gameMillisPerDay;
		this.gameMillisPerDay = gameMillisPerDay;
		lastSetGameMillis = currentMillis;
		depots.forEach(Depot::generatePlatformDirectionsAndWriteDeparturesToSidings);
	}

	public long getGameMillisPerDay() {
		return gameMillisPerDay;
	}

	/**
	 * @return milliseconds after epoch of the first midnight in-game
	 */
	public long getMillisOfGameMidnight() {
		return Math.max(0, lastSetGameMillis - lastSetGameMillis / gameMillisPerDay * gameMillisPerDay - gameMillis);
	}

	public void run(Runnable runnable) {
		queuedRuns.add(runnable);
	}

	public boolean isRiding(UUID uuid, long vehicleId) {
		return ridingVehicleIds.getLong(uuid) == vehicleId;
	}

	public void ride(UUID uuid, long vehicleId) {
		ridingVehicleIds.put(uuid, vehicleId);
	}

	public void stopRiding(UUID uuid) {
		ridingVehicleIds.removeLong(uuid);
	}

	private void save(boolean useReducedHash) {
		final long startMillis = System.currentTimeMillis();
		save(fileLoaderStations, useReducedHash);
		save(fileLoaderPlatforms, useReducedHash);
		save(fileLoaderSidings, useReducedHash);
		save(fileLoaderRoutes, useReducedHash);
		save(fileLoaderDepots, useReducedHash);
		save(fileLoaderLifts, useReducedHash);
		save(fileLoaderRails, useReducedHash);
		Main.LOGGER.info(String.format("Save complete for %s in %s second(s)", dimension, (System.currentTimeMillis() - startMillis) / 1000F));
	}

	private <T extends SerializedDataBaseWithId> void save(FileLoader<T> fileLoader, boolean useReducedHash) {
		final IntIntImmutablePair saveCounts = fileLoader.save(useReducedHash);
		if (saveCounts.leftInt() > 0) {
			Main.LOGGER.info(String.format("- Changed %s: %s", fileLoader.key, saveCounts.leftInt()));
		}
		if (saveCounts.rightInt() > 0) {
			Main.LOGGER.info(String.format("- Deleted %s: %s", fileLoader.key, saveCounts.rightInt()));
		}
	}
}
