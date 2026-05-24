package org.mtr.core.simulation;

import it.unimi.dsi.fastutil.ints.IntIntImmutablePair;
import it.unimi.dsi.fastutil.objects.*;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.jspecify.annotations.Nullable;
import org.mtr.core.data.*;
import org.mtr.core.directions.DirectionsFinder;
import org.mtr.core.serializer.SerializedDataBase;
import org.mtr.core.serializer.SerializedDataBaseWithId;
import org.mtr.core.servlet.MessageQueue;
import org.mtr.core.servlet.OperationProcessor;
import org.mtr.core.servlet.QueueObject;
import org.mtr.core.tool.Utilities;
import org.mtr.legacy.data.LegacyRailLoader;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Per-dimension simulation engine — one {@link Simulator} per Minecraft world / dimension.
 *
 * <p>The simulator owns the in-memory graph of stations, platforms, sidings, routes, depots,
 * lifts, rails, homes, landmarks and clients for its dimension, and ticks them forward in
 * one-second slices via {@link #tickUntilCaughtUp()}. State mutation is single-threaded: any
 * cross-thread work (HTTP servlets, embedding mod callbacks) must be marshalled through
 * {@link #run(Runnable)} so it executes on the simulator's own thread.</p>
 *
 * <p>Persistence is delegated to {@link FileLoader} — one per top-level entity type. Saves are
 * incremental (only changed buckets are rewritten) unless {@code useReducedHash} is {@code false}
 * during the final shutdown save.</p>
 */
@Log4j2
public class Simulator extends Data implements Utilities {

	private long lastMillis;
	private boolean autoSave = false;
	private long gameMillis;
	/**
	 * Real-time milliseconds per in-game day or {@code 0} if unknown / paused; default = 20 in-game minutes ≈ Minecraft's vanilla rate.
	 */
	@Getter
	private long gameMillisPerDay = DEFAULT_GAME_MILLIS_PER_DAY;
	/**
	 * Whether the daylight cycle (and therefore the in-game clock) is currently advancing.
	 */
	@Getter
	private boolean isTimeMoving;
	private long lastSetGameMillisMidnight;

	/**
	 * Connected dashboard / mod clients for this dimension.
	 */
	public final ObjectArraySet<Client> clients = new ObjectArraySet<>();
	/**
	 * Stable dimension identifier (e.g. {@code "minecraft/overworld"}).
	 */
	public final String dimension;
	/**
	 * Identifiers of every dimension hosted in the same process — used for cross-dimension routing.
	 */
	public final String[] dimensions;
	/**
	 * Background path-finder for passenger directions queries.
	 */
	public final DirectionsFinder directionsFinder = new DirectionsFinder(this);

	private final FileLoader<Station> fileLoaderStations;
	private final FileLoader<Platform> fileLoaderPlatforms;
	private final FileLoader<Siding> fileLoaderSidings;
	private final FileLoader<Route> fileLoaderRoutes;
	private final FileLoader<Depot> fileLoaderDepots;
	private final FileLoader<Lift> fileLoaderLifts;
	private final FileLoader<Rail> fileLoaderRails;
	private final FileLoader<Home> fileLoaderHomes;
	private final FileLoader<Landmark> fileLoaderLandmarks;
	private final FileLoader<Settings> fileLoaderSettings;
	private final Consumer<Settings> writeSettings;
	private final MessageQueue<Runnable> queuedRuns = new MessageQueue<>();
	private final ObjectImmutableList<ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>>> vehiclePositions;
	private final Object2LongOpenHashMap<UUID> ridingVehicleIds = new Object2LongOpenHashMap<>();
	private final MessageQueue<QueueObject> messageQueueC2S = new MessageQueue<>();
	private final MessageQueue<QueueObject> messageQueueS2C = new MessageQueue<>();

	/**
	 * If the simulation falls more than this many milliseconds behind wall clock, log a notice and
	 * fast-forward in one-second slices until caught up. Picked at two minutes as a balance between
	 * "noisy log on a sluggish host" and "silent multi-hour drift".
	 */
	private static final int SIMULATION_DIFFERENCE_LOGGING_THRESHOLD = 120000;
	/**
	 * Default in-game day length in real-time milliseconds (20 in-game minutes).
	 */
	private static final long DEFAULT_GAME_MILLIS_PER_DAY = 20L * 60 * MILLIS_PER_SECOND;

	/**
	 * Load a dimension from disk and bring its in-memory graph up to a tickable state.
	 *
	 * @param dimension           identifier of the dimension being loaded
	 * @param dimensions          identifiers of every dimension hosted in the same process
	 * @param rootPath            root data directory; per-dimension state lives under {@code rootPath/<dimension>}
	 * @param threadedFileLoading if {@code true}, fan file reads out across a thread pool
	 */
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
				new FileLoader<>(rails, Rail::new, savePath, "rails", threadedFileLoading),
				new FileLoader<>(homes, messagePackHelper -> new Home(messagePackHelper, this), savePath, "homes", threadedFileLoading),
				new FileLoader<>(landmarks, messagePackHelper -> new Landmark(messagePackHelper, this), savePath, "landmarks", threadedFileLoading)
			);
		});
		fileLoaderStations = fileLoaderHolderAndDuration.left().fileLoaderStations;
		fileLoaderPlatforms = fileLoaderHolderAndDuration.left().fileLoaderPlatforms;
		fileLoaderSidings = fileLoaderHolderAndDuration.left().fileLoaderSidings;
		fileLoaderRoutes = fileLoaderHolderAndDuration.left().fileLoaderRoutes;
		fileLoaderDepots = fileLoaderHolderAndDuration.left().fileLoaderDepots;
		fileLoaderLifts = fileLoaderHolderAndDuration.left().fileLoaderLifts;
		fileLoaderRails = fileLoaderHolderAndDuration.left().fileLoaderRails;
		fileLoaderHomes = fileLoaderHolderAndDuration.left().fileLoaderHomes;
		fileLoaderLandmarks = fileLoaderHolderAndDuration.left().fileLoaderLandmarks;
		log.info("Data loading complete for {} in {} second(s)", dimension, (float) fileLoaderHolderAndDuration.rightLong() / MILLIS_PER_SECOND);

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

	/**
	 * Catch the simulation up to wall clock and log a notice if it had drifted by more than
	 * {@link #SIMULATION_DIFFERENCE_LOGGING_THRESHOLD} milliseconds. If the drift exceeds an hour
	 * the simulator jumps to "one hour ago" instead of replaying the full gap, since replaying
	 * many hours of vehicle motion is rarely useful and is expensive.
	 */
	public void tick() {
		final long totalDifference = System.currentTimeMillis() - getCurrentMillis();
		if (totalDifference >= SIMULATION_DIFFERENCE_LOGGING_THRESHOLD) {
			if (totalDifference > MILLIS_PER_HOUR) {
				// If the simulation is over an hour behind, jump to one hour ago and simulate the last hour
				setCurrentMillis(System.currentTimeMillis() - MILLIS_PER_HOUR);
				sidings.forEach(Siding::clearVehicles);
			}
			final ObjectLongImmutablePair<Integer> ticksAndDuration = Utilities.measureDuration(this::tickUntilCaughtUp);
			log.info(
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

	/**
	 * Schedule a full save on the next tick. Returns immediately.
	 */
	public void save() {
		autoSave = true;
	}

	/**
	 * Stop ticking and perform a final, non-incremental save.
	 */
	public void stop() {
		save(false);
	}

	/**
	 * Compare {@code millis} to the last-tick / current-tick window, accounting for day-wrap.
	 *
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

	/**
	 * Fast-forward the listed depots through one full in-game day in one-second slices, leaving
	 * the simulator's clock unchanged. Used by the dashboard "instant deploy" button so depot
	 * vehicles are immediately spawned on every siding.
	 */
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

	/**
	 * Convenience overload of {@link #instantDeployDepots(ObjectArrayList)} that selects depots by
	 * a name {@code filter} via {@link NameColorDataBase#getDataByName}.
	 *
	 * @param simulator simulator whose depots are scanned (typically {@code this} — kept as a
	 *                  parameter so the method matches the embedding mod's existing signature)
	 * @param filter    case-insensitive name filter
	 */
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

	/**
	 * @return milliseconds after epoch of the first midnight in-game
	 */
	public long getMillisOfGameMidnight() {
		return gameMillisPerDay > 0 && isTimeMoving ? Math.max(0, lastSetGameMillisMidnight - lastSetGameMillisMidnight / gameMillisPerDay * gameMillisPerDay) : 0;
	}

	/**
	 * @return the game hour (0-23)
	 */
	public int getGameHour() {
		return gameMillisPerDay > 0 ? (int) (gameMillis * HOURS_PER_DAY / gameMillisPerDay) : 0;
	}

	/**
	 * Queue a {@link Runnable} to execute on the simulator thread at the start of the next tick.
	 * Used by HTTP servlets and the embedding mod to safely mutate simulator state without
	 * crossing threads.
	 */
	public void run(Runnable runnable) {
		queuedRuns.put(runnable);
	}

	/**
	 * Enqueue a client-to-server message; processed during the next tick.
	 */
	public void sendMessageC2S(QueueObject queueObject) {
		messageQueueC2S.put(queueObject);
	}

	/**
	 * Push a server-to-client message into the outgoing queue. The optional {@code consumer} is
	 * invoked on the simulator thread when the matching response payload of type
	 * {@code responseDataClass} arrives back from the client.
	 */
	public <T extends SerializedDataBase> void sendMessageS2C(String key, SerializedDataBase data, @Nullable Consumer<T> consumer, @Nullable Class<T> responseDataClass) {
		messageQueueS2C.put(new QueueObject(key, data, consumer == null ? null : responseData -> run(() -> consumer.accept(responseData)), responseDataClass));
	}

	/**
	 * Drain all pending S2C messages and feed each into {@code callback}.
	 */
	public void processMessagesS2C(Consumer<QueueObject> callback) {
		messageQueueS2C.process(callback);
	}

	/**
	 * @return whether the entity {@code uuid} is currently riding {@code vehicleId}
	 */
	public boolean isRiding(UUID uuid, long vehicleId) {
		return ridingVehicleIds.getLong(uuid) == vehicleId;
	}

	/**
	 * Record that the entity {@code uuid} has boarded {@code vehicleId}.
	 */
	public void ride(UUID uuid, long vehicleId) {
		ridingVehicleIds.put(uuid, vehicleId);
	}

	/**
	 * Record that the entity {@code uuid} has dismounted whatever vehicle it was riding.
	 */
	public void stopRiding(UUID uuid) {
		ridingVehicleIds.removeLong(uuid);
	}

	/**
	 * @param uuid riding entity to look up
	 * @return the next platform of the vehicle being ridden by {@code uuid}, or {@code null} if
	 * the entity is not riding anything or its vehicle has no upcoming platform.
	 */
	@Nullable
	public Platform getNextPlatformOfRidingVehicle(UUID uuid) {
		final @Nullable Platform[] platform = {null};
		sidings.forEach(siding -> siding.iterateVehiclesAndRidingEntities((vehicleExtraData, vehicleRidingEntity) -> {
			if (vehicleRidingEntity.uuid.equals(uuid)) {
				final Platform checkPlatform = platformIdMap.get(vehicleExtraData.getNextPlatformId());
				if (checkPlatform != null) {
					platform[0] = checkPlatform;
				}
			}
		}));
		return platform[0];
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
				if (!vehiclePositionsForTransportMode.isEmpty()) {
					vehiclePositionsForTransportMode.removeFirst();
				}
				vehiclePositionsForTransportMode.add(new Object2ObjectAVLTreeMap<>());
			});

			rails.forEach(rail -> rail.tick1(this));
			rails.forEach(rail -> rail.tick2(millisElapsed));
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
			landmarks.forEach(Landmark::tick);
			homes.forEach(home -> home.tick(millisElapsed));

			// Process queued runs
			queuedRuns.process(Runnable::run);

			// Directions
			directionsFinder.tick();

			// Process messages
			messageQueueC2S.process(queueObject -> queueObject.runCallback(OperationProcessor.process(queueObject.key, queueObject.data, this)));
		} catch (Throwable e) {
			log.fatal("", e);
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
			final boolean changed8 = save(fileLoaderHomes, useReducedHash);
			final boolean changed9 = save(fileLoaderLandmarks, useReducedHash);
			return changed1 || changed2 || changed3 || changed4 || changed5 || changed6 || changed7 || changed8 || changed9;
		});
		if (changedAndDuration.left() || !useReducedHash) {
			log.info("Save complete for {} in {} second(s)", dimension, (float) changedAndDuration.rightLong() / MILLIS_PER_SECOND);
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
			log.info("- Changed {}: {}", fileLoader.key, changedCount);
		}
		final int deletedCount = saveCounts.rightInt();
		if (deletedCount > 0) {
			log.info("- Deleted {}: {}", fileLoader.key, deletedCount);
		}
		return changedCount > 0 || deletedCount > 0;
	}

	private record FileLoaderHolder(
		FileLoader<Station> fileLoaderStations,
		FileLoader<Platform> fileLoaderPlatforms,
		FileLoader<Siding> fileLoaderSidings,
		FileLoader<Route> fileLoaderRoutes,
		FileLoader<Depot> fileLoaderDepots,
		FileLoader<Lift> fileLoaderLifts,
		FileLoader<Rail> fileLoaderRails,
		FileLoader<Home> fileLoaderHomes,
		FileLoader<Landmark> fileLoaderLandmarks
	) {
	}
}
