package org.mtr.core.simulation;

import org.mtr.core.Main;
import org.mtr.core.data.*;
import org.mtr.core.integration.Response;
import org.mtr.core.path.DirectionsPathFinder;
import org.mtr.core.serializer.SerializedDataBase;
import org.mtr.core.serializer.SerializedDataBaseWithId;
import org.mtr.core.servlet.HttpResponseStatus;
import org.mtr.core.tool.RequestHelper;
import org.mtr.core.tool.Utilities;
import org.mtr.legacy.data.LegacyRailLoader;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.ints.IntIntImmutablePair;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.*;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;

public class Simulator extends Data implements Utilities {

	private long lastMillis;
	private long currentMillis;
	private boolean autoSave = false;
	private long gameMillis;
	private long gameMillisPerDay = 20 * 60 * MILLIS_PER_SECOND; // default value
	private boolean isTimeMoving;
	private long lastSetGameMillisMidnight;

	public final Object2ObjectOpenHashMap<String, Client> clients = new Object2ObjectOpenHashMap<>();
	public final String dimension;
	public final String[] dimensions;

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
	private final ObjectOpenHashSet<DirectionsPathFinder> directionsPathFinders = new ObjectOpenHashSet<>();

	public static final RequestHelper REQUEST_HELPER = new RequestHelper(false);

	public Simulator(String dimension, String[] dimensions, Path rootPath, int clientWebserverPort) {
		this.dimension = dimension;
		this.dimensions = dimensions;
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
		Main.LOGGER.info("Data loading complete for {} in {} second(s)", dimension, (float) (currentMillis - startMillis) / MILLIS_PER_SECOND);

		sync();
		depots.forEach(Depot::init);
		rails.forEach(Rail::checkMigrationStatus);

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

			// Try setting a siding's default path data
			// If a siding doesn't have a rail associated with it, it should be removed from the data set
			if (sidings.removeIf(Siding::tick)) {
				sync();
			}

			sidings.forEach(siding -> siding.simulateTrain(currentMillis - lastMillis, vehiclePositions.get(siding.getTransportModeOrdinal())));
			clients.forEach((clientId, client) -> client.sendUpdates(this));

			if (autoSave) {
				save(true);
				autoSave = false;
			}

			lifts.forEach(lift -> lift.tick(currentMillis - lastMillis));

			// Process directions requests
			final long startMillis = System.currentTimeMillis();
			while (!directionsPathFinders.isEmpty() && System.currentTimeMillis() - startMillis < 400) {
				directionsPathFinders.removeIf(DirectionsPathFinder::tick);
			}

			while (!queuedRuns.isEmpty()) {
				final Runnable runnable = queuedRuns.remove(0);
				if (runnable != null) {
					runnable.run();
				}
			}
		} catch (Throwable e) {
			Main.LOGGER.fatal("", e);
		}
	}

	public void save() {
		autoSave = true;
	}

	public void stop() {
		save(false);
	}

	public void addDirectionsPathFinder(Position position1, Position position2, long maxWalkingDistance, Consumer<JsonObject> sendResponse) {
		directionsPathFinders.add(new DirectionsPathFinder(this, position1, position2, maxWalkingDistance, sendResponse));
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
	 * @param isTimeMoving     whether the daylight cycle is on
	 */
	public void setGameTime(long gameMillis, long gameMillisPerDay, boolean isTimeMoving) {
		this.gameMillis = gameMillisPerDay > 0 ? gameMillis % gameMillisPerDay : gameMillis;
		this.gameMillisPerDay = gameMillisPerDay;
		this.isTimeMoving = isTimeMoving;
		lastSetGameMillisMidnight = currentMillis - gameMillis;
		depots.forEach(Depot::generatePlatformDirectionsAndWriteDeparturesToSidings);
	}

	public long getGameMillisPerDay() {
		return gameMillisPerDay;
	}

	public boolean isTimeMoving() {
		return isTimeMoving;
	}

	/**
	 * @return milliseconds after epoch of the first midnight in-game
	 */
	public long getMillisOfGameMidnight() {
		return gameMillisPerDay > 0 && isTimeMoving ? Math.max(0, lastSetGameMillisMidnight - lastSetGameMillisMidnight / gameMillisPerDay * gameMillisPerDay) : 0;
	}

	/**
	 * @return the game hour (0-23)
	 */
	public int getHour() {
		return gameMillisPerDay > 0 ? (int) (gameMillis * HOURS_PER_DAY / gameMillisPerDay) : 0;
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
		Main.LOGGER.info("Save complete for {} in {} second(s)", dimension, (System.currentTimeMillis() - startMillis) / 1000F);
	}

	private <T extends SerializedDataBaseWithId> void save(FileLoader<T> fileLoader, boolean useReducedHash) {
		final IntIntImmutablePair saveCounts = fileLoader.save(useReducedHash);
		if (saveCounts.leftInt() > 0) {
			Main.LOGGER.info("- Changed {}: {}", fileLoader.key, saveCounts.leftInt());
		}
		if (saveCounts.rightInt() > 0) {
			Main.LOGGER.info("- Deleted {}: {}", fileLoader.key, saveCounts.rightInt());
		}
	}

	public void sendHttpRequest(String endpoint, SerializedDataBase data, @Nullable Consumer<JsonObject> consumer) {
		REQUEST_HELPER.sendPostRequest(String.format("http://localhost:%s/%s", clientWebserverPort, endpoint), new Response(HttpResponseStatus.OK.code, System.currentTimeMillis(), "Success", Utilities.getJsonObjectFromData(data)).getJson(), consumer);
	}
}
