package org.mtr.core.simulation;

import org.mtr.core.Main;
import org.mtr.core.data.*;
import org.mtr.core.integration.Response;
import org.mtr.core.path.DirectionsPathFinder;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.serializer.SerializedDataBase;
import org.mtr.core.serializer.SerializedDataBaseWithId;
import org.mtr.core.servlet.MessageQueue;
import org.mtr.core.servlet.OperationServlet;
import org.mtr.core.servlet.QueueObject;
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

	private final FileLoader<Station> fileLoaderStations;
	private final FileLoader<Platform> fileLoaderPlatforms;
	private final FileLoader<Siding> fileLoaderSidings;
	private final FileLoader<Route> fileLoaderRoutes;
	private final FileLoader<Depot> fileLoaderDepots;
	private final FileLoader<Lift> fileLoaderLifts;
	private final FileLoader<Rail> fileLoaderRails;
	private final MessageQueue<Runnable> queuedRuns = new MessageQueue<>();
	private final ObjectImmutableList<ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>>> vehiclePositions;
	private final Object2LongOpenHashMap<UUID> ridingVehicleIds = new Object2LongOpenHashMap<>();
	private final ObjectOpenHashSet<DirectionsPathFinder> directionsPathFinders = new ObjectOpenHashSet<>();
	private final MessageQueue<QueueObject> messageQueueC2S = new MessageQueue<>();
	private final MessageQueue<QueueObject> messageQueueS2C = new MessageQueue<>();

	public Simulator(String dimension, String[] dimensions, Path rootPath) {
		this.dimension = dimension;
		this.dimensions = dimensions;
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

			rails.forEach(rail -> rail.tick(this));
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
			final long currentMillis = System.currentTimeMillis();
			while (!directionsPathFinders.isEmpty() && System.currentTimeMillis() - currentMillis < 400) {
				directionsPathFinders.removeIf(DirectionsPathFinder::tick);
			}

			// Process queued runs
			queuedRuns.process(Runnable::run);

			// Process messages
			messageQueueC2S.process(queueObject -> queueObject.runCallback(new Response(200, currentMillis, "", OperationServlet.process(queueObject.key, new JsonReader(Utilities.getJsonObjectFromData(queueObject.data)), currentMillis, this)).getJson()));
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
		queuedRuns.put(runnable);
	}

	public void sendMessageC2S(QueueObject queueObject) {
		messageQueueC2S.put(queueObject);
	}

	public void sendMessageS2C(String key, SerializedDataBase data, @Nullable Consumer<JsonObject> consumer) {
		messageQueueS2C.put(new QueueObject(key, data, consumer == null ? null : jsonObject -> run(() -> consumer.accept(jsonObject))));
	}

	public void processMessagesS2C(Consumer<QueueObject> callback) {
		messageQueueS2C.process(callback);
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
		final boolean changed1 = save(fileLoaderStations, useReducedHash);
		final boolean changed2 = save(fileLoaderPlatforms, useReducedHash);
		final boolean changed3 = save(fileLoaderSidings, useReducedHash);
		final boolean changed4 = save(fileLoaderRoutes, useReducedHash);
		final boolean changed5 = save(fileLoaderDepots, useReducedHash);
		final boolean changed6 = save(fileLoaderLifts, useReducedHash);
		final boolean changed7 = save(fileLoaderRails, useReducedHash);
		if (changed1 || changed2 || changed3 || changed4 || changed5 || changed6 || changed7 || !useReducedHash) {
			Main.LOGGER.info("Save complete for {} in {} second(s)", dimension, (System.currentTimeMillis() - startMillis) / 1000F);
		}
	}

	private <T extends SerializedDataBaseWithId> boolean save(FileLoader<T> fileLoader, boolean useReducedHash) {
		final IntIntImmutablePair saveCounts = fileLoader.save(useReducedHash);
		final int changedCount = saveCounts.leftInt();
		if (changedCount > 0) {
			Main.LOGGER.info("- Changed {}: {}", fileLoader.key, changedCount);
		}
		final int deletedCount = saveCounts.rightInt();
		if (deletedCount > 0) {
			Main.LOGGER.info("- Deleted {}: {}", fileLoader.key, deletedCount);
		}
		return changedCount > 0 || deletedCount > 0;
	}
}
