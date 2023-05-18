package org.mtr.core.data;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.core.Main;
import org.mtr.core.path.PathData;
import org.mtr.core.path.SidingPathFinder;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.serializers.WriterBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.Angle;
import org.mtr.core.tools.Utilities;

import java.util.Collections;
import java.util.function.BiConsumer;

public class Depot extends AreaBase<Depot, Siding> implements Utilities {

	public boolean useRealTime;
	public boolean repeatInfinitely;
	public int cruisingAltitude = DEFAULT_CRUISING_ALTITUDE;

	public final LongArrayList routeIds = new LongArrayList();
	public final ObjectArrayList<PathData> path = new ObjectArrayList<>();
	public final IntArrayList realTimeDepartures = new IntArrayList();

	private final int[] frequencies = new int[HOURS_PER_DAY];
	private final ObjectArrayList<Platform> platformsInRoute = new ObjectArrayList<>();
	private final ObjectImmutableList<ReaderBase> pathDataReaders;
	private final ObjectArrayList<SidingPathFinder<Station, Platform, Station, Platform>> sidingPathFinders = new ObjectArrayList<>();
	private final LongAVLTreeSet generatingSidingIds = new LongAVLTreeSet();

	public static final int DEFAULT_CRUISING_ALTITUDE = 256;
	public static final int CONTINUOUS_MOVEMENT_FREQUENCY = 8000;

	private static final String KEY_ROUTE_IDS = "route_ids";
	private static final String KEY_PATH = "path";
	private static final String KEY_USE_REAL_TIME = "use_real_time";
	private static final String KEY_FREQUENCIES = "frequencies";
	private static final String KEY_DEPARTURES = "departures";
	private static final String KEY_REPEAT_INFINITELY = "repeat_infinitely";
	private static final String KEY_CRUISING_ALTITUDE = "cruising_altitude";

	public Depot(TransportMode transportMode, Simulator simulator) {
		super(transportMode, simulator);
		pathDataReaders = ObjectImmutableList.of();
	}

	public Depot(ReaderBase readerBase, Simulator simulator) {
		super(readerBase, simulator);
		pathDataReaders = Siding.savePathDataReaderBase(readerBase, KEY_PATH);
		updateData(readerBase);
	}

	@Override
	public void updateData(ReaderBase readerBase) {
		super.updateData(readerBase);

		readerBase.iterateLongArray(KEY_ROUTE_IDS, routeIds::add);
		readerBase.unpackBoolean(KEY_USE_REAL_TIME, value -> useRealTime = value);

		final IntArrayList frequenciesArray = new IntArrayList();
		readerBase.iterateIntArray(KEY_FREQUENCIES, frequenciesArray::add);
		for (int i = 0; i < Math.min(frequenciesArray.size(), HOURS_PER_DAY); i++) {
			setFrequency(i, frequenciesArray.getInt(i));
		}

		readerBase.iterateIntArray(KEY_DEPARTURES, realTimeDepartures::add);
		readerBase.unpackBoolean(KEY_REPEAT_INFINITELY, value -> repeatInfinitely = value);
		readerBase.unpackInt(KEY_CRUISING_ALTITUDE, value -> cruisingAltitude = value);
	}

	@Override
	public void toMessagePack(WriterBase writerBase) {
		super.toMessagePack(writerBase);

		final WriterBase.Array writerBaseArrayRouteIds = writerBase.writeArray(KEY_ROUTE_IDS);
		routeIds.forEach(writerBaseArrayRouteIds::writeLong);

		writerBase.writeDataset(sidingPathFinders.isEmpty() ? path : new ObjectArrayList<>(), KEY_PATH);
		writerBase.writeBoolean(KEY_USE_REAL_TIME, useRealTime);
		writerBase.writeBoolean(KEY_REPEAT_INFINITELY, repeatInfinitely);
		writerBase.writeInt(KEY_CRUISING_ALTITUDE, cruisingAltitude);

		final WriterBase.Array writerBaseArrayFrequencies = writerBase.writeArray(KEY_FREQUENCIES);
		for (int i = 0; i < HOURS_PER_DAY; i++) {
			writerBaseArrayFrequencies.writeInt(frequencies[i]);
		}

		final WriterBase.Array writerBaseArrayRealTimeDepartures = writerBase.writeArray(KEY_DEPARTURES);
		realTimeDepartures.forEach(writerBaseArrayRealTimeDepartures::writeInt);
	}

	@Override
	protected boolean hasTransportMode() {
		return true;
	}

	public void init() {
		Siding.readPathDataReaderBase(pathDataReaders, path, simulator.dataCache);
		savedRails.forEach(Siding::init);
		generatePlatformDirectionsAndWriteDeparturesToSidings();
	}

	public void generateMainRoute() {
		if (savedRails.isEmpty()) {
			Main.LOGGER.info(String.format("No sidings in %s", name));
		} else {
			Main.LOGGER.info(String.format("Starting path generation for %s...", name));
			path.clear();
			platformsInRoute.clear();
			sidingPathFinders.clear();
			generatingSidingIds.clear();

			final long[] previousPlatformId = {0};
			iterateRoutes((route, routeIndex) -> route.platformIds.forEach(platformId -> {
				final Platform platform = simulator.dataCache.platformIdMap.get(platformId.platformId);
				if (platform != null && platform.id != previousPlatformId[0]) {
					platformsInRoute.add(platform);
				}
				previousPlatformId[0] = platformId.platformId;
			}));

			for (int i = 0; i < platformsInRoute.size() - 1; i++) {
				sidingPathFinders.add(new SidingPathFinder<>(simulator.dataCache, platformsInRoute.get(i), platformsInRoute.get(i + 1), i));
			}
		}
	}

	public void tick() {
		SidingPathFinder.findPathTick(path, sidingPathFinders, () -> {
			if (!platformsInRoute.isEmpty()) {
				savedRails.forEach(siding -> {
					siding.generateRoute(Utilities.getElement(platformsInRoute, 0), repeatInfinitely ? null : Utilities.getElement(platformsInRoute, -1), platformsInRoute.size(), cruisingAltitude);
					generatingSidingIds.add(siding.id);
				});
			}
		}, () -> Main.LOGGER.info(String.format("Path not found for %s", name)));
	}

	public void finishGeneratingPath(long sidingId) {
		generatingSidingIds.remove(sidingId);
		if (generatingSidingIds.isEmpty()) {
			Main.LOGGER.info(String.format("Path generation complete for %s", name));
			generatePlatformDirectionsAndWriteDeparturesToSidings();
		}
	}

	public void iterateRoutes(BiConsumer<Route, Integer> consumer) {
		for (int i = 0; i < routeIds.size(); i++) {
			final long routeId = routeIds.getLong(i);
			final Route route = simulator.dataCache.routeIdMap.get(routeId);
			if (route != null) {
				consumer.accept(route, i);
			}
		}
	}

	public void setFrequency(int hour, int frequency) {
		if (hour >= 0 && hour < HOURS_PER_DAY) {
			frequencies[hour] = Math.max(0, frequency);
		}
	}

	/**
	 * The first part generates platform directions (N, NE, etc.) for OBA data.
	 * The second part reads from real-time departures and in-game frequencies and converts them to departures.
	 * Each departure is mapped to a siding and siding time segments must be generated beforehand.
	 * Should only be called during initialization (but after siding initialization) and after path generation of all sidings.
	 */
	private void generatePlatformDirectionsAndWriteDeparturesToSidings() {
		final Long2ObjectOpenHashMap<Angle> platformDirections = new Long2ObjectOpenHashMap<>();

		for (int i = 1; i < path.size(); i++) {
			final long platformId = path.get(i - 1).savedRailBaseId;
			if (platformId != 0) {
				final Angle newAngle = path.get(i).rail.facingStart;
				if (!platformDirections.containsKey(platformId)) {
					platformDirections.put(platformId, newAngle);
				} else if (newAngle != platformDirections.get(platformId)) {
					platformDirections.put(platformId, null);
				}
			}
		}

		platformDirections.forEach((platformId, angle) -> {
			final Platform platform = simulator.dataCache.platformIdMap.get(platformId.longValue());
			if (platform != null) {
				platform.setAngles(id, angle);
			}
		});

		final IntArrayList departures = new IntArrayList();

		if (transportMode.continuousMovement) {
			for (int i = 0; i < savedRails.size(); i += CONTINUOUS_MOVEMENT_FREQUENCY) {
				departures.add(i);
			}
		} else {
			if (useRealTime) {
				departures.addAll(realTimeDepartures);
			} else {
				final int offsetMillis = Math.max(0, (int) (Main.START_MILLIS - Main.START_MILLIS / simulator.millisPerGameDay * simulator.millisPerGameDay - simulator.startingGameDayPercentage * simulator.millisPerGameDay));
				final IntArrayList gameDepartures = new IntArrayList();
				final float timeRatio = (float) MILLIS_PER_DAY / simulator.millisPerGameDay;

				for (int i = 0; i < HOURS_PER_DAY; i++) {
					if (frequencies[i] == 0) {
						continue;
					}

					final int intervalMillis = 14400000 / frequencies[i];
					final int hourMinMillis = MILLIS_PER_HOUR * i;
					final int hourMaxMillis = MILLIS_PER_HOUR * (i + 1);

					while (true) {
						final int newDeparture = Math.max(hourMinMillis, Utilities.getElement(gameDepartures, -1, Integer.MIN_VALUE) + intervalMillis);
						if (newDeparture < hourMaxMillis) {
							departures.add(offsetMillis + Math.round(newDeparture / timeRatio));
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
			for (final int departure : departures) {
				for (int i = 0; i < sidingsInDepot.size(); i++) {
					if (sidingsInDepot.get((sidingIndex + i) % sidingsInDepot.size()).addDeparture(departure)) {
						sidingIndex++;
						break;
					}
				}
			}
		}
	}
}
