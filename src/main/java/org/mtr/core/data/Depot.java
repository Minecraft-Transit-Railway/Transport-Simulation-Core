package org.mtr.core.data;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.jspecify.annotations.Nullable;
import org.mtr.core.generated.data.DepotSchema;
import org.mtr.core.operation.UpdateDataResponse;
import org.mtr.core.path.SidingPathFinder;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.serializer.WriterBase;
import org.mtr.core.servlet.OperationProcessor;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Utilities;
import org.mtr.legacy.data.DataFixer;

import java.util.Collections;
import java.util.function.IntConsumer;

/**
 * A bundle of {@link Siding}s plus the platform-by-platform route they all serve.
 *
 * <p>Each {@code Depot} owns the path-generation pipeline for its sidings — building the chain
 * of {@link SidingPathFinder}s, the resulting {@link PathData} list, and the per-day departure
 * timetable that feeds individual sidings via {@link Siding#addDeparture(long)}. The depot's
 * configured frequencies + the simulator's in-game day length feed
 * {@link #generatePlatformDirectionsAndWriteDeparturesToSidings()} which is the heart of the
 * timetable computation.</p>
 */
@Log4j2
public final class Depot extends DepotSchema implements Utilities {

	@Nullable
	private OnGenerationComplete onGenerationComplete;
	private long repeatDepartures;

	public final ObjectArrayList<Route> routes = new ObjectArrayList<>();

	@Getter
	private final ObjectArrayList<PathData> path = new ObjectArrayList<>();
	/**
	 * A temporary list to store all platforms of the vehicle instructions as well as the route used to get to each platform. Repeated platforms are ignored.
	 */
	private final ObjectArrayList<PlatformRouteDetails> platformsInRoute = new ObjectArrayList<>();
	private final ObjectArrayList<SidingPathFinder<Station, Platform, Station, Platform>> sidingPathFinders = new ObjectArrayList<>();
	private final LongAVLTreeSet generatingSidingIds = new LongAVLTreeSet();

	/**
	 * Continuous-movement vehicles (e.g. cable cars) depart at fixed-period intervals.
	 */
	public static final int CONTINUOUS_MOVEMENT_FREQUENCY = 8000;
	/**
	 * MTR depot frequencies are expressed in trains-per-hour scaled by this constant so the
	 * timetable maths can stay in integer arithmetic. Equivalent to four hours of milliseconds —
	 * picked so the smallest non-zero frequency (1 train per 4h) maps to {@code intervalMillis = MILLIS_PER_HOUR}.
	 */
	private static final long FREQUENCY_BASE_MILLIS = 4L * MILLIS_PER_HOUR;
	private static final String KEY_PATH = "path";

	/**
	 * Create a new depot for the given transport mode in the specified simulation or client context.
	 */
	public Depot(TransportMode transportMode, Data data) {
		super(transportMode, data);
	}

	/**
	 * Deserialisation constructor used by the wire / on-disk layer.
	 *
	 * @param readerBase source to read persisted data from
	 * @param data       the simulation engine or client data container
	 */
	public Depot(ReaderBase readerBase, Data data) {
		super(readerBase, data);
		readerBase.iterateReaderArray(KEY_PATH, path::clear, readerBaseChild -> path.add(new PathData(readerBaseChild)));
		super.updateData(readerBase);
		DataFixer.unpackDepotDepartures(readerBase, realTimeDepartures);
	}

	@Override
	public void updateData(ReaderBase readerBase) {
		// If this is serverside, don't update these values from an incoming update packet
		final long tempLastGeneratedMillis = lastGeneratedMillis;
		final GeneratedStatus tempLastGeneratedStatus = lastGeneratedStatus;
		final long tempLastGeneratedFailedStartId = lastGeneratedFailedStartId;
		final long tempLastGeneratedFailedEndId = lastGeneratedFailedEndId;
		final long tempLastGeneratedFailedSidingCount = lastGeneratedFailedSidingCount;
		super.updateData(readerBase);
		// Only the simulator-side `Data` is authoritative for generation status; on the client the
		// schema-supplied fields are kept as-is.
		if (data instanceof Simulator) {
			lastGeneratedMillis = tempLastGeneratedMillis;
			lastGeneratedStatus = tempLastGeneratedStatus;
			lastGeneratedFailedStartId = tempLastGeneratedFailedStartId;
			lastGeneratedFailedEndId = tempLastGeneratedFailedEndId;
			lastGeneratedFailedSidingCount = tempLastGeneratedFailedSidingCount;
		}
	}

	@Override
	public void serializeFullData(WriterBase writerBase) {
		super.serializeFullData(writerBase);
		writerBase.writeDataset(path, KEY_PATH);
	}

	/**
	 * Initialise the depot: write path caches, initialise all sidings, and generate the
	 * platform directions and departure timetable.
	 */
	public void init() {
		writePathCache();
		savedRails.forEach(Siding::init); // Sidings not under a depot will be ignored, but it doesn't matter
		generatePlatformDirectionsAndWriteDeparturesToSidings();
	}

	/**
	 * Write path-finding caches for this depot and all its sidings.
	 */
	public void writePathCache() {
		PathData.writePathCache(path, data, transportMode);
		savedRails.forEach(Siding::writePathCache);
	}

	/**
	 * @param useRealTime whether to use wall-clock time ({@code true}) or in-game time ({@code false})
	 *                    for departure scheduling
	 */
	public void setUseRealTime(boolean useRealTime) {
		this.useRealTime = useRealTime;
	}

	/**
	 * Set the departure frequency for a given hour of the day.
	 *
	 * @param hour      hour index (0-23)
	 * @param frequency trains per hour
	 */
	public void setFrequency(int hour, int frequency) {
		if (hour >= 0 && hour < HOURS_PER_DAY) {
			while (frequencies.size() < HOURS_PER_DAY) {
				frequencies.add(0);
			}
			frequencies.set(hour, Math.max(0, frequency));
		}
	}

	/**
	 * @param repeatInfinitely whether the departure schedule should loop forever ({@code true})
	 *                         or respect the in-game day length ({@code false})
	 */
	public void setRepeatInfinitely(boolean repeatInfinitely) {
		this.repeatInfinitely = repeatInfinitely;
	}

	/**
	 * @param cruisingAltitude the altitude at which vehicles in this depot travel
	 */
	public void setCruisingAltitude(long cruisingAltitude) {
		this.cruisingAltitude = cruisingAltitude;
	}

	/**
	 * @return the list of route IDs associated with this depot
	 */
	public LongArrayList getRouteIds() {
		return routeIds;
	}

	/**
	 * @return the timestamp of the last path generation attempt
	 */
	public long getLastGeneratedMillis() {
		return lastGeneratedMillis;
	}

	/**
	 * @return the status of the last path generation attempt
	 */
	public GeneratedStatus getLastGeneratedStatus() {
		return lastGeneratedStatus;
	}

	/**
	 * @param generationStatusConsumer               if path generation failed between two saved rails, this consumer will be called with the saved rail IDs that path generation failed at
	 * @param lastGeneratedFailedSidingCountConsumer if path generation failed between the siding and the main path, this consumer will be called with the number of sidings that couldn't connect to the main path
	 */
	public void getFailedPlatformIds(GenerationStatusConsumer generationStatusConsumer, IntConsumer lastGeneratedFailedSidingCountConsumer) {
		if (lastGeneratedFailedStartId != 0 && lastGeneratedFailedEndId != 0) {
			generationStatusConsumer.accept(lastGeneratedFailedStartId, lastGeneratedFailedEndId);
		}
		if (lastGeneratedFailedSidingCount > 0) {
			lastGeneratedFailedSidingCountConsumer.accept((int) lastGeneratedFailedSidingCount);
		}
	}

	public boolean getRepeatInfinitely() {
		return repeatInfinitely;
	}

	public long getCruisingAltitude() {
		return cruisingAltitude;
	}

	public boolean getUseRealTime() {
		return useRealTime;
	}

	public long getFrequency(int hour) {
		return hour >= 0 && hour < Math.min(HOURS_PER_DAY, frequencies.size()) ? frequencies.getLong(hour) : 0;
	}

	public LongArrayList getRealTimeDepartures() {
		return realTimeDepartures;
	}

	/**
	 * Rebuild the platform-in-route list from the current route data and register this depot
	 * on each referenced route.
	 *
	 * @param routeIdMap map of route ID to Route, used to resolve route references
	 */
	public void writeRouteCache(Long2ObjectOpenHashMap<Route> routeIdMap) {
		routes.clear();
		routeIds.forEach(id -> routes.add(routeIdMap.get(id)));
		for (int i = routes.size() - 1; i >= 0; i--) {
			if (routes.get(i) == null) {
				routeIds.removeLong(i);
				routes.remove(i);
			} else {
				routes.get(i).depots.add(this);
			}
		}

		platformsInRoute.clear();
		long previousPlatformId = 0;
		for (final Route route : routes) {
			for (int i = 0; i < route.getRoutePlatforms().size(); i++) {
				final Platform platform = route.getRoutePlatforms().get(i).platform;
				if (platform != null && platform.getId() != previousPlatformId) {
					// To deal with edge cases (whether the next route starts from the same platform or not), we will always use the "next" data
					// If i == 0, it's the first index of this route, but since we are looking for the "next" data, it's okay to set it as null as the "next" data of the previous route
					platformsInRoute.add(new PlatformRouteDetails(platform, i == 0 ? null : route, i == 0 ? Integer.MAX_VALUE : i - 1));
					previousPlatformId = platform.getId();
				}
			}
		}
	}

	/**
	 * Called every tick to advance depot-level path generation. Invokes the shared path-finding
	 * pipeline and, on completion, generates route and departure data for every siding.
	 */
	public void tick() {
		SidingPathFinder.findPathTick(path, sidingPathFinders, cruisingAltitude, () -> {
			if (!platformsInRoute.isEmpty()) {
				lastGeneratedFailedSidingCount = 0;
				PathData.writePathCache(path, data, transportMode);
				savedRails.forEach(siding -> {
					siding.generateRoute(Utilities.getElement(platformsInRoute, 0).platform, repeatInfinitely ? null : Utilities.getElement(platformsInRoute, -1).platform, platformsInRoute.size(), cruisingAltitude);
					generatingSidingIds.add(siding.getId());
				});
			}
		}, (startSavedRail, endSavedRail) -> updateGenerationStatus(GeneratedStatus.PATH_NOT_FOUND, startSavedRail.getId(), endSavedRail.getId(), "Path not found for %s"));
	}

	/**
	 * Mark a siding's path generation as complete. When all sidings are done the departure
	 * timetable is regenerated.
	 *
	 * @param sidingId the siding whose path generation finished
	 */
	public void finishGeneratingPath(long sidingId) {
		generatingSidingIds.remove(sidingId);
		if (generatingSidingIds.isEmpty()) {
			updateGenerationStatus(GeneratedStatus.SUCCESSFUL, 0, 0, "Path generation complete for %s");
			generatePlatformDirectionsAndWriteDeparturesToSidings();
		}
	}

	/**
	 * Get the platform and route information for a given stop index in the depot's route.
	 * Handles both infinite-repeating and finite schedules.
	 *
	 * @param stopIndex index of the stop to query
	 * @return platform/route info for the requested stop and its neighbours
	 */
	public VehicleExtraData.VehiclePlatformRouteInfo getVehiclePlatformRouteInfo(int stopIndex) {
		final int platformCount = platformsInRoute.size();
		final PlatformRouteDetails previousData;
		final PlatformRouteDetails thisData;
		final PlatformRouteDetails nextData;
		final PlatformRouteDetails nextNextData;

		if (platformCount == 0) {
			previousData = null;
			thisData = null;
			nextData = null;
			nextNextData = null;
		} else if (repeatInfinitely) {
			previousData = Utilities.getElement(platformsInRoute, (stopIndex - 1 + platformCount) % platformCount);
			thisData = Utilities.getElement(platformsInRoute, stopIndex % platformCount);
			nextData = Utilities.getElement(platformsInRoute, (stopIndex + 1) % platformCount);
			nextNextData = Utilities.getElement(platformsInRoute, (stopIndex + 2) % platformCount);
		} else {
			previousData = Utilities.getElement(platformsInRoute, stopIndex - 1);
			thisData = Utilities.getElement(platformsInRoute, stopIndex);
			nextData = Utilities.getElement(platformsInRoute, stopIndex + 1);
			nextNextData = Utilities.getElement(platformsInRoute, stopIndex + 2);
		}

		return new VehicleExtraData.VehiclePlatformRouteInfo(
			previousData == null ? null : previousData.platform,
			thisData == null ? null : thisData.platform,
			nextData == null ? null : nextData.platform,
			thisData == null ? null : thisData.route,
			nextData == null ? null : nextData.route,
			nextNextData == null ? null : nextNextData.route,
			nextData == null ? Integer.MAX_VALUE : nextData.platformIndex
		);
	}

	/**
	 * The first part generates platform directions (N, NE, etc.) for OBA data. The second part reads from real-time departures and in-game frequencies and converts them to departures. Each departure is mapped to a siding and siding time segments must be generated beforehand. Should only be called during initialisation (but after siding initialisation), when setting world time, and after path generation of all sidings.
	 */
	public void generatePlatformDirectionsAndWriteDeparturesToSidings() {
		final Long2ObjectOpenHashMap<@Nullable Angle> platformDirections = new Long2ObjectOpenHashMap<>();

		for (int i = 1; i < path.size(); i++) {
			final long platformId = path.get(i - 1).getSavedRailBaseId();
			if (platformId != 0) {
				final Angle newAngle = path.get(i).getFacingStart();
				if (!platformDirections.containsKey(platformId)) {
					platformDirections.put(platformId, newAngle);
				} else if (newAngle != platformDirections.get(platformId)) {
					platformDirections.put(platformId, null);
				}
			}
		}

		platformDirections.forEach((platformId, angle) -> {
			final Platform platform = data.platformIdMap.get(platformId);
			if (platform != null) {
				platform.setAngles(id, angle);
			}
		});

		final LongArrayList departures = new LongArrayList();
		long repeatIntervalMillis = 0;
		repeatDepartures = 1;

		if (transportMode.continuousMovement) {
			for (int i = 0; i < savedRails.size(); i++) {
				departures.add((long) i * CONTINUOUS_MOVEMENT_FREQUENCY);
			}
		} else {
			if (useRealTime) {
				departures.addAll(realTimeDepartures);
			} else if (data instanceof final Simulator simulator) {
				repeatIntervalMillis = simulator.getGameMillisPerDay();
				long lastDeparture = Long.MIN_VALUE;
				repeatDepartures = getRepeatDeparturesForJourneyTime(simulator);

				for (int i = 0; i < HOURS_PER_DAY; i++) {
					final long frequency = getFrequency(simulator.getScheduleFrequencyHour(i));
					if (frequency == 0) {
						continue;
					}

					final long intervalMillis = FREQUENCY_BASE_MILLIS / frequency;
					final long hourMinMillis = MILLIS_PER_HOUR * i;
					final long hourMaxMillis = MILLIS_PER_HOUR * (i + 1);

					while (true) {
						final long newDeparture = Math.max(hourMinMillis, lastDeparture + intervalMillis);
						if (newDeparture < hourMaxMillis) {
							departures.add(simulator.getSimulationMillisAtGameDayOffset(newDeparture));
							lastDeparture = newDeparture;
						} else {
							break;
						}
					}
				}
			}
		}

		final ObjectArrayList<Siding> sidingsInDepot = new ObjectArrayList<>(savedRails);
		if (!sidingsInDepot.isEmpty()) {
			Collections.shuffle(sidingsInDepot);
			Collections.sort(sidingsInDepot);
			Collections.sort(departures);
			sidingsInDepot.forEach(Siding::startGeneratingDepartures);
			int sidingIndex = 0;
			for (int i = 0; i < repeatDepartures; i++) {
				for (final long departure : departures) {
					for (int j = 0; j < sidingsInDepot.size(); j++) {
						if (sidingsInDepot.get((sidingIndex + j) % sidingsInDepot.size()).addDeparture(departure + repeatIntervalMillis * i)) {
							sidingIndex++;
							break;
						}
					}
				}
			}
		}
	}

	/**
	 * Compute how many in-game day cycles are needed to cover the longest siding journey time,
	 * so that departures repeat enough times to serve the full timetable. Returns 1 when the
	 * game-day length is zero or the highest journey time is zero.
	 */
	private long getRepeatDeparturesForJourneyTime(Simulator simulator) {
		final long gameMillisPerDay = simulator.getGameMillisPerDay();
		if (gameMillisPerDay <= 0) {
			return 1;
		}

		final long highestJourneyTime = savedRails.stream().mapToLong(Siding::getJourneyTime).reduce(0, Math::max);
		return highestJourneyTime == 0 ? 1 : (long) Math.ceil((double) highestJourneyTime / gameMillisPerDay);
	}

	/**
	 * Update the depot's path-generation status fields. These are visible to clients so they
	 * can surface generation progress / errors in the UI.
	 */
	public void updateGenerationStatus(long lastGeneratedMillis, GeneratedStatus lastGeneratedStatus, long lastGeneratedFailedStartId, long lastGeneratedFailedEndId) {
		this.lastGeneratedMillis = lastGeneratedMillis;
		this.lastGeneratedStatus = lastGeneratedStatus;
		this.lastGeneratedFailedStartId = lastGeneratedFailedStartId;
		this.lastGeneratedFailedEndId = lastGeneratedFailedEndId;
	}

	long getRepeatDepartures() {
		return repeatDepartures;
	}

	void sidingPathGenerationFailed() {
		lastGeneratedFailedSidingCount++;
	}

	private void generateMainRoute(OnGenerationComplete newOnGenerationComplete) {
		if (onGenerationComplete != null) {
			onGenerationComplete.accept(true);
		}
		onGenerationComplete = newOnGenerationComplete;

		if (savedRails.isEmpty()) {
			updateGenerationStatus(GeneratedStatus.NO_SIDINGS, 0, 0, "No sidings in %s");
		} else {
			log.info("Starting path generation for {}...", name);
			path.clear();
			sidingPathFinders.clear();
			generatingSidingIds.clear();
			for (int i = 0; i < platformsInRoute.size() - 1; i++) {
				sidingPathFinders.add(new SidingPathFinder<>(data, platformsInRoute.get(i).platform, platformsInRoute.get(i + 1).platform, i));
			}
			if (sidingPathFinders.isEmpty()) {
				updateGenerationStatus(GeneratedStatus.TWO_PLATFORMS_REQUIRED, 0, 0, "At least two platforms are required for path generation");
			}
		}
	}

	private void updateGenerationStatus(GeneratedStatus lastGeneratedStatus, long lastGeneratedFailedStartId, long lastGeneratedFailedEndId, String message) {
		updateGenerationStatus(data.getCurrentMillis(), lastGeneratedStatus, lastGeneratedFailedStartId, lastGeneratedFailedEndId);
		if (onGenerationComplete != null) {
			onGenerationComplete.accept(false);
		}
		onGenerationComplete = null;
		log.info("{}", String.format(message, name));
	}

	/**
	 * Trigger path generation for all depots whose name matches the given filter.
	 *
	 * @param simulator the simulator instance
	 * @param filter    case-insensitive name filter
	 */
	public static void generateDepotsByName(Simulator simulator, String filter) {
		generateDepots(simulator, getDataByName(simulator.depots, filter));
	}

	/**
	 * Trigger path generation for the specified depots. Each depot runs its own path-finding
	 * pipeline; when all are complete a generation status message is sent to clients.
	 *
	 * @param simulator        the simulator instance
	 * @param depotsToGenerate the depots to generate paths for
	 */
	public static void generateDepots(Simulator simulator, ObjectArrayList<Depot> depotsToGenerate) {
		final LongAVLTreeSet idsToGenerate = new LongAVLTreeSet();
		final UpdateDataResponse updateDataResponse = new UpdateDataResponse(simulator);

		depotsToGenerate.forEach(depot -> {
			idsToGenerate.add(depot.getId());
			depot.generateMainRoute(forceComplete -> {
				idsToGenerate.remove(depot.getId());
				updateDataResponse.addDepot(depot);
				if (forceComplete || idsToGenerate.isEmpty()) {
					simulator.sendMessageS2C(OperationProcessor.GENERATION_STATUS_UPDATE, updateDataResponse, null, null);
				}
			});
		});
	}

	/**
	 * Clear (remove) all vehicles from depots matching the given name filter.
	 *
	 * @param simulator the simulator instance
	 * @param filter    case-insensitive name filter
	 */
	public static void clearDepotsByName(Simulator simulator, String filter) {
		clearDepots(getDataByName(simulator.depots, filter));
	}

	/**
	 * Clear (remove) all vehicles from the specified depots.
	 *
	 * @param depotsToClear the depots whose vehicles should be cleared
	 */
	public static void clearDepots(ObjectArrayList<Depot> depotsToClear) {
		depotsToClear.forEach(depot -> depot.savedRails.forEach(Siding::clearVehicles));
	}

	private record PlatformRouteDetails(Platform platform, @Nullable Route route, int platformIndex) {
	}

	@FunctionalInterface
	public interface GenerationStatusConsumer {
		void accept(long lastGeneratedFailedStartId, long lastGeneratedFailedEndId);
	}

	@FunctionalInterface
	private interface OnGenerationComplete {
		void accept(boolean forceComplete);
	}

	public enum GeneratedStatus {
		NONE, SUCCESSFUL, NO_SIDINGS, TWO_PLATFORMS_REQUIRED, PATH_NOT_FOUND
	}
}
