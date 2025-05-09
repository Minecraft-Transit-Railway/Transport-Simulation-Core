package org.mtr.core.simulation;

import it.unimi.dsi.fastutil.ints.IntIntImmutablePair;
import it.unimi.dsi.fastutil.objects.*;
import org.mtr.core.Main;
import org.mtr.core.data.*;
import org.mtr.core.serializer.SerializedDataBase;
import org.mtr.core.serializer.SerializedDataBaseWithId;
import org.mtr.core.servlet.MessageQueue;
import org.mtr.core.servlet.OperationProcessor;
import org.mtr.core.servlet.QueueObject;
import org.mtr.core.tool.Utilities;
import org.mtr.legacy.data.LegacyRailLoader;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;

public class Simulator extends Data implements Utilities {

	private long lastMillis;
	private boolean autoSave = false;
	private long gameMillis;
	private long gameMillisPerDay = 20 * 60 * MILLIS_PER_SECOND; // default value
	private boolean isTimeMoving;
	private long lastSetGameMillisMidnight;

	public final ObjectArraySet<Client> clients = new ObjectArraySet<>();
	public final String dimension;
	public final String[] dimensions;

	private final FileLoader<Station> fileLoaderStations;
	private final FileLoader<Platform> fileLoaderPlatforms;
	private final FileLoader<Siding> fileLoaderSidings;
	private final FileLoader<Route> fileLoaderRoutes;
	private final FileLoader<Depot> fileLoaderDepots;
	private final FileLoader<Lift> fileLoaderLifts;
	private final FileLoader<Rail> fileLoaderRails;
	private final FileLoader<Settings> fileLoaderSettings;
	private final Consumer<Settings> writeSettings;
	private final MessageQueue<Runnable> queuedRuns = new MessageQueue<>();
	private final ObjectImmutableList<ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>>> vehiclePositions;
	private final Object2LongOpenHashMap<UUID> ridingVehicleIds = new Object2LongOpenHashMap<>();
	private final MessageQueue<QueueObject> messageQueueC2S = new MessageQueue<>();
	private final MessageQueue<QueueObject> messageQueueS2C = new MessageQueue<>();

	private static final int SIMULATION_DIFFERENCE_LOGGING_THRESHOLD = 120000;

	public Simulator(String dimension, String[] dimensions, Path rootPath, boolean threadedFileLoading) {
		this.dimension = dimension;
		this.dimensions = dimensions;

		// Load data
		final Path savePath = rootPath.resolve(dimension);
		final ObjectLongImmutablePair<FileLoaderHolder> fileLoaderHolderAndDuration = Utilities.measureDuration(() -> {
			LegacyRailLoader.load(savePath, rails, threadedFileLoading);
			return new FileLoaderHolder(
					new FileLoader<>(stations, messagePackHelper -> new Station(messagePackHelper, this), savePath, "stations", threadedFileLoading),
					new FileLoader<>(platforms, messagePackHelper -> new Platform(messagePackHelper, this), savePath, "platforms", threadedFileLoading),
					new FileLoader<>(sidings, messagePackHelper -> new Siding(messagePackHelper, this), savePath, "sidings", threadedFileLoading),
					new FileLoader<>(routes, messagePackHelper -> new Route(messagePackHelper, this), savePath, "routes", threadedFileLoading),
					new FileLoader<>(depots, messagePackHelper -> new Depot(messagePackHelper, this), savePath, "depots", threadedFileLoading),
					new FileLoader<>(lifts, messagePackHelper -> new Lift(messagePackHelper, this), savePath, "lifts", threadedFileLoading),
					new FileLoader<>(rails, Rail::new, savePath, "rails", threadedFileLoading)
			);
		});
		fileLoaderStations = fileLoaderHolderAndDuration.left().fileLoaderStations;
		fileLoaderPlatforms = fileLoaderHolderAndDuration.left().fileLoaderPlatforms;
		fileLoaderSidings = fileLoaderHolderAndDuration.left().fileLoaderSidings;
		fileLoaderRoutes = fileLoaderHolderAndDuration.left().fileLoaderRoutes;
		fileLoaderDepots = fileLoaderHolderAndDuration.left().fileLoaderDepots;
		fileLoaderLifts = fileLoaderHolderAndDuration.left().fileLoaderLifts;
		fileLoaderRails = fileLoaderHolderAndDuration.left().fileLoaderRails;
		Main.LOGGER.info("Data loading complete for {} in {} second(s)", dimension, (float) fileLoaderHolderAndDuration.rightLong() / MILLIS_PER_SECOND);

		// Initialize cache
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

		// Load settings
		final ObjectArraySet<Settings> settings = new ObjectArraySet<>();
		fileLoaderSettings = new FileLoader<>(settings, Settings::new, savePath, "settings", threadedFileLoading);
		writeSettings = newSettings -> {
			settings.clear();
			settings.add(newSettings);
		};

		// Set the last simulated millis
		setCurrentMillis(Utilities.getElement(new ObjectArrayList<>(settings), 0, new Settings(0)).getLastSimulationMillis());
	}

	public void tick() {
		final long totalDifference = System.currentTimeMillis() - getCurrentMillis();
		if (totalDifference >= SIMULATION_DIFFERENCE_LOGGING_THRESHOLD) {
			if (totalDifference > MILLIS_PER_HOUR) {
				// If the simulation is over an hour behind, jump to one hour ago and simulate the last hour
				setCurrentMillis(System.currentTimeMillis() - MILLIS_PER_HOUR);
				sidings.forEach(Siding::clearVehicles);
			}
			final ObjectLongImmutablePair<Integer> ticksAndDuration = Utilities.measureDuration(this::tickUntilCaughtUp);
			Main.LOGGER.info(
					"Simulation difference of {}h{}m for {} caught up with {} ticks in {} second(s)",
					totalDifference / MILLIS_PER_SECOND / 3600, (totalDifference / MILLIS_PER_SECOND / 60) % 60,
					dimension,
					ticksAndDuration.left(),
					(float) ticksAndDuration.rightLong() / MILLIS_PER_SECOND
			);
		} else {
			tickUntilCaughtUp();
		}
	}

	public void save() {
		autoSave = true;
	}

	public void stop() {
		save(false);
	}

	/**
	 * @param millis Milliseconds to check
	 * @return 1 if upcoming, 0 if current, -1 if passed
	 */
	public int matchMillis(long millis) {
		if (Utilities.circularDifference(getCurrentMillis(), millis, MILLIS_PER_DAY) < 0) {
			return 1;
		} else {
			return Utilities.circularDifference(millis, lastMillis, MILLIS_PER_DAY) > 0 ? 0 : -1;
		}
	}

	public void instantDeployDepots(ObjectArrayList<Depot> depotsToInstantDeploy) {
		final long oldLastMillis = lastMillis;
		final long oldCurrentMillis = getCurrentMillis();
		for (int i = 0; i < MILLIS_PER_DAY; i += MILLIS_PER_SECOND) {
			lastMillis = getCurrentMillis();
			setCurrentMillis(lastMillis + MILLIS_PER_SECOND);
			depotsToInstantDeploy.forEach(depot -> depot.savedRails.forEach(siding -> siding.simulateTrain(MILLIS_PER_SECOND, null)));
		}
		lastMillis = oldLastMillis;
		setCurrentMillis(oldCurrentMillis);
	}

	public void instantDeployDepotsByName(Simulator simulator, String filter) {
		instantDeployDepots(NameColorDataBase.getDataByName(simulator.depots, filter));
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
		lastSetGameMillisMidnight = getCurrentMillis() - gameMillis;
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

	public <T extends SerializedDataBase> void sendMessageS2C(String key, SerializedDataBase data, @Nullable Consumer<T> consumer, @Nullable Class<T> responseDataClass) {
		messageQueueS2C.put(new QueueObject(key, data, consumer == null ? null : responseData -> run(() -> consumer.accept(responseData)), responseDataClass));
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

	/**
	 * Simulates the system in one-second intervals until the simulation is all caught up
	 *
	 * @return the number of ticks it took
	 */
	private int tickUntilCaughtUp() {
		int ticks = 0;
		while (true) {
			ticks++;
			final long totalDifference = System.currentTimeMillis() - getCurrentMillis();
			if (totalDifference > MILLIS_PER_SECOND) {
				tick(MILLIS_PER_SECOND);
			} else {
				tick(totalDifference);
				return ticks;
			}
		}
	}

	/**
	 * The main simulation tick loop
	 *
	 * @param millisElapsed the number of milliseconds since the last tick
	 */
	private void tick(long millisElapsed) {
		lastMillis = getCurrentMillis();
		setCurrentMillis(lastMillis + millisElapsed);

		try {
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

			sidings.forEach(siding -> siding.simulateTrain(millisElapsed, vehiclePositions.get(siding.getTransportModeOrdinal())));
			clients.forEach(client -> client.sendUpdates(this));

			if (autoSave) {
				save(true);
				autoSave = false;
			}

			lifts.forEach(lift -> lift.tick(millisElapsed));

			// Process queued runs
			queuedRuns.process(Runnable::run);

			// Process messages
			messageQueueC2S.process(queueObject -> queueObject.runCallback(OperationProcessor.process(queueObject.key, queueObject.data, this)));
		} catch (Throwable e) {
			Main.LOGGER.fatal("", e);
		}
	}

	private void save(boolean useReducedHash) {
		// Save all data
		final ObjectLongImmutablePair<Boolean> changedAndDuration = Utilities.measureDuration(() -> {
			final boolean changed1 = save(fileLoaderStations, useReducedHash);
			final boolean changed2 = save(fileLoaderPlatforms, useReducedHash);
			final boolean changed3 = save(fileLoaderSidings, useReducedHash);
			final boolean changed4 = save(fileLoaderRoutes, useReducedHash);
			final boolean changed5 = save(fileLoaderDepots, useReducedHash);
			final boolean changed6 = save(fileLoaderLifts, useReducedHash);
			final boolean changed7 = save(fileLoaderRails, useReducedHash);
			return changed1 || changed2 || changed3 || changed4 || changed5 || changed6 || changed7;
		});
		if (changedAndDuration.left() || !useReducedHash) {
			Main.LOGGER.info("Save complete for {} in {} second(s)", dimension, changedAndDuration.rightLong() / 1000F);
		}

		// Save settings
		writeSettings.accept(new Settings(getCurrentMillis()));
		if (useReducedHash) {
			fileLoaderSettings.save(false);
		} else {
			save(fileLoaderSettings, false);
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

	private static class FileLoaderHolder {

		private final FileLoader<Station> fileLoaderStations;
		private final FileLoader<Platform> fileLoaderPlatforms;
		private final FileLoader<Siding> fileLoaderSidings;
		private final FileLoader<Route> fileLoaderRoutes;
		private final FileLoader<Depot> fileLoaderDepots;
		private final FileLoader<Lift> fileLoaderLifts;
		private final FileLoader<Rail> fileLoaderRails;

		private FileLoaderHolder(
				FileLoader<Station> fileLoaderStations,
				FileLoader<Platform> fileLoaderPlatforms,
				FileLoader<Siding> fileLoaderSidings,
				FileLoader<Route> fileLoaderRoutes,
				FileLoader<Depot> fileLoaderDepots,
				FileLoader<Lift> fileLoaderLifts,
				FileLoader<Rail> fileLoaderRails
		) {
			this.fileLoaderStations = fileLoaderStations;
			this.fileLoaderPlatforms = fileLoaderPlatforms;
			this.fileLoaderSidings = fileLoaderSidings;
			this.fileLoaderRoutes = fileLoaderRoutes;
			this.fileLoaderDepots = fileLoaderDepots;
			this.fileLoaderLifts = fileLoaderLifts;
			this.fileLoaderRails = fileLoaderRails;
		}
	}
}
