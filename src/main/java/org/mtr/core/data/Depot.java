package org.mtr.core.data;

import org.mtr.core.Main;
import org.mtr.core.generated.data.DepotSchema;
import org.mtr.core.operation.UpdateDataResponse;
import org.mtr.core.path.SidingPathFinder;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.serializer.WriterBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Utilities;
import org.mtr.legacy.data.DataFixer;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.function.Consumer;

public final class Depot extends DepotSchema implements Utilities {

	private OnGenerationComplete onGenerationComplete;

	public final ObjectArrayList<Route> routes = new ObjectArrayList<>();

	private final ObjectArrayList<PathData> path = new ObjectArrayList<>();
	/**
	 * A temporary list to store all platforms of the vehicle instructions as well as the route used to get to each platform.
	 * Repeated platforms are ignored.
	 */
	private final ObjectArrayList<ObjectObjectImmutablePair<Platform, Route>> platformsInRoute = new ObjectArrayList<>();
	private final ObjectArrayList<SidingPathFinder<Station, Platform, Station, Platform>> sidingPathFinders = new ObjectArrayList<>();
	private final LongAVLTreeSet generatingSidingIds = new LongAVLTreeSet();

	public static final int CONTINUOUS_MOVEMENT_FREQUENCY = 8000;
	private static final String KEY_PATH = "path";

	public Depot(TransportMode transportMode, Data data) {
		super(transportMode, data);
	}

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
		super.updateData(readerBase);
		if (data instanceof Simulator) {
			lastGeneratedMillis = tempLastGeneratedMillis;
			lastGeneratedStatus = tempLastGeneratedStatus;
			lastGeneratedFailedStartId = tempLastGeneratedFailedStartId;
			lastGeneratedFailedEndId = tempLastGeneratedFailedEndId;
		}
	}

	@Override
	public void serializeFullData(WriterBase writerBase) {
		super.serializeFullData(writerBase);
		writerBase.writeDataset(path, KEY_PATH);
	}

	public void init() {
		writePathCache();
		savedRails.forEach(Siding::init); // Sidings not under a depot will be ignored, but it doesn't matter
		generatePlatformDirectionsAndWriteDeparturesToSidings();
	}

	public void writePathCache() {
		PathData.writePathCache(path, data, transportMode);
		savedRails.forEach(Siding::writePathCache);
	}

	public void setUseRealTime(boolean useRealTime) {
		this.useRealTime = useRealTime;
	}

	public void setFrequency(int hour, int frequency) {
		if (hour >= 0 && hour < HOURS_PER_DAY) {
			while (frequencies.size() < HOURS_PER_DAY) {
				frequencies.add(0);
			}
			frequencies.set(hour, Math.max(0, frequency));
		}
	}

	public void setRepeatInfinitely(boolean repeatInfinitely) {
		this.repeatInfinitely = repeatInfinitely;
	}

	public void setCruisingAltitude(long cruisingAltitude) {
		this.cruisingAltitude = cruisingAltitude;
	}

	public LongArrayList getRouteIds() {
		return routeIds;
	}

	public long getLastGeneratedMillis() {
		return lastGeneratedMillis;
	}

	public GeneratedStatus getLastGeneratedStatus() {
		return lastGeneratedStatus;
	}

	/**
	 * @param generationStatusConsumer if path generation failed between two saved rails, this consumer will be called with the saved rail IDs that path generation failed at
	 */
	public void getFailedPlatformIds(GenerationStatusConsumer generationStatusConsumer) {
		if (lastGeneratedFailedStartId != 0 && lastGeneratedFailedEndId != 0) {
			generationStatusConsumer.accept(lastGeneratedFailedStartId, lastGeneratedFailedEndId);
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

	public ObjectArrayList<PathData> getPath() {
		return path;
	}

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
					platformsInRoute.add(new ObjectObjectImmutablePair<>(platform, i == 0 ? null : route));
					previousPlatformId = platform.getId();
				}
			}
		}
	}

	public void tick() {
		SidingPathFinder.findPathTick(path, sidingPathFinders, cruisingAltitude, () -> {
			if (!platformsInRoute.isEmpty()) {
				savedRails.forEach(siding -> {
					siding.generateRoute(Utilities.getElement(platformsInRoute, 0).left(), repeatInfinitely ? null : Utilities.getElement(platformsInRoute, -1).left(), platformsInRoute.size(), cruisingAltitude);
					generatingSidingIds.add(siding.getId());
				});
			}
		}, (startSavedRail, endSavedRail) -> updateGenerationStatus(GeneratedStatus.PATH_NOT_FOUND, startSavedRail.getId(), endSavedRail.getId(), "Path not found for %s"));
	}

	public void finishGeneratingPath(long sidingId) {
		generatingSidingIds.remove(sidingId);
		if (generatingSidingIds.isEmpty()) {
			updateGenerationStatus(GeneratedStatus.SUCCESSFUL, 0, 0, "Path generation complete for %s");
			generatePlatformDirectionsAndWriteDeparturesToSidings();
		}
	}

	public VehicleExtraData.VehiclePlatformRouteInfo getVehiclePlatformRouteInfo(int stopIndex) {
		final int platformCount = platformsInRoute.size();
		final ObjectObjectImmutablePair<Platform, Route> previousData;
		final ObjectObjectImmutablePair<Platform, Route> thisData;
		final ObjectObjectImmutablePair<Platform, Route> nextData;
		final ObjectObjectImmutablePair<Platform, Route> nextNextData;

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
				previousData == null ? null : previousData.left(),
				thisData == null ? null : thisData.left(),
				nextData == null ? null : nextData.left(),
				thisData == null ? null : thisData.right(),
				nextData == null ? null : nextData.right(),
				nextNextData == null ? null : nextNextData.right()
		);
	}

	/**
	 * The first part generates platform directions (N, NE, etc.) for OBA data.
	 * The second part reads from real-time departures and in-game frequencies and converts them to departures.
	 * Each departure is mapped to a siding and siding time segments must be generated beforehand.
	 * Should only be called during initialization (but after siding initialization), when setting world time, and after path generation of all sidings.
	 */
	public void generatePlatformDirectionsAndWriteDeparturesToSidings() {
		final Long2ObjectOpenHashMap<Angle> platformDirections = new Long2ObjectOpenHashMap<>();

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
			final Platform platform = data.platformIdMap.get(platformId.longValue());
			if (platform != null) {
				platform.setAngles(id, angle);
			}
		});

		final LongArrayList departures = new LongArrayList();

		if (transportMode.continuousMovement) {
			for (int i = 0; i < savedRails.size(); i += CONTINUOUS_MOVEMENT_FREQUENCY) {
				departures.add(i);
			}
		} else {
			if (useRealTime) {
				departures.addAll(realTimeDepartures);
			} else if (data instanceof Simulator && ((Simulator) data).getGameMillisPerDay() > 0) {
				final Simulator simulator = (Simulator) data;
				final long offsetMillis = simulator.getMillisOfGameMidnight();
				final LongArrayList gameDepartures = new LongArrayList();

				for (int i = 0; i < HOURS_PER_DAY; i++) {
					if (getFrequency(i) == 0) {
						continue;
					}

					final long intervalMillis = 14400000 / getFrequency(i);
					final long hourMinMillis = MILLIS_PER_HOUR * i;
					final long hourMaxMillis = MILLIS_PER_HOUR * (i + 1);

					while (true) {
						final long newDeparture = Math.max(hourMinMillis, Utilities.getElement(gameDepartures, -1, Long.MIN_VALUE) + intervalMillis);
						if (newDeparture < hourMaxMillis) {
							departures.add(offsetMillis + newDeparture * simulator.getGameMillisPerDay() / MILLIS_PER_DAY);
							gameDepartures.add(newDeparture);
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
			sidingsInDepot.forEach(Siding::startGeneratingDepartures);
			int sidingIndex = 0;
			for (final long departure : departures) {
				for (int i = 0; i < sidingsInDepot.size(); i++) {
					if (sidingsInDepot.get((sidingIndex + i) % sidingsInDepot.size()).addDeparture(departure)) {
						sidingIndex++;
						break;
					}
				}
			}
		}
	}

	public void updateGenerationStatus(long lastGeneratedMillis, GeneratedStatus lastGeneratedStatus, long lastGeneratedFailedStartId, long lastGeneratedFailedEndId) {
		this.lastGeneratedMillis = lastGeneratedMillis;
		this.lastGeneratedStatus = lastGeneratedStatus;
		this.lastGeneratedFailedStartId = lastGeneratedFailedStartId;
		this.lastGeneratedFailedEndId = lastGeneratedFailedEndId;
	}

	private void generateMainRoute(OnGenerationComplete newOnGenerationComplete) {
		if (onGenerationComplete != null) {
			onGenerationComplete.accept(true);
		}
		onGenerationComplete = newOnGenerationComplete;

		if (savedRails.isEmpty()) {
			updateGenerationStatus(GeneratedStatus.NO_SIDINGS, 0, 0, "No sidings in %s");
		} else {
			Main.LOGGER.info(String.format("Starting path generation for %s...", name));
			path.clear();
			sidingPathFinders.clear();
			generatingSidingIds.clear();
			for (int i = 0; i < platformsInRoute.size() - 1; i++) {
				sidingPathFinders.add(new SidingPathFinder<>(data, platformsInRoute.get(i).left(), platformsInRoute.get(i + 1).left(), i));
			}
			if (sidingPathFinders.isEmpty()) {
				updateGenerationStatus(GeneratedStatus.TWO_PLATFORMS_REQUIRED, 0, 0, "At least two platforms are required for path generation");
			}
		}
	}

	private void updateGenerationStatus(GeneratedStatus lastGeneratedStatus, long lastGeneratedFailedStartId, long lastGeneratedFailedEndId, String message) {
		updateGenerationStatus(System.currentTimeMillis(), lastGeneratedStatus, lastGeneratedFailedStartId, lastGeneratedFailedEndId);
		onGenerationComplete.accept(false);
		onGenerationComplete = null;
		Main.LOGGER.info(String.format(message, name));
	}

	public static void generateDepotsByName(Simulator simulator, String filter, @Nullable Consumer<JsonObject> sendResponse) {
		generateDepots(simulator, getDataByName(simulator.depots, filter), sendResponse);
	}

	public static void generateDepots(Simulator simulator, ObjectArrayList<Depot> depotsToGenerate, @Nullable Consumer<JsonObject> sendResponse) {
		final LongAVLTreeSet idsToGenerate = new LongAVLTreeSet();
		final UpdateDataResponse updateDataResponse = new UpdateDataResponse(simulator);

		depotsToGenerate.forEach(depot -> {
			idsToGenerate.add(depot.getId());
			depot.generateMainRoute(forceComplete -> {
				idsToGenerate.remove(depot.getId());
				updateDataResponse.addDepot(depot);
				if (sendResponse != null && (forceComplete || idsToGenerate.isEmpty())) {
					sendResponse.accept(Utilities.getJsonObjectFromData(updateDataResponse));
				}
			});
		});
	}

	public static void clearDepotsByName(Simulator simulator, String filter) {
		clearDepots(getDataByName(simulator.depots, filter));
	}

	public static void clearDepots(ObjectArrayList<Depot> depotsToClear) {
		depotsToClear.forEach(depot -> depot.savedRails.forEach(Siding::clearVehicles));
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
