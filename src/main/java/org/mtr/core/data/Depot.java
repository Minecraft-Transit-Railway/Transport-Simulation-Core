package org.mtr.core.data;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.msgpack.core.MessagePacker;
import org.mtr.core.Main;
import org.mtr.core.path.PathData;
import org.mtr.core.path.SidingPathFinder;
import org.mtr.core.reader.MessagePackHelper;
import org.mtr.core.reader.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.Angle;
import org.mtr.core.tools.Utilities;

import java.io.IOException;
import java.util.Collections;
import java.util.function.Consumer;

public class Depot extends AreaBase<Depot, Siding> implements Utilities {

	public boolean useRealTime;
	public boolean repeatInfinitely;
	public int cruisingAltitude = DEFAULT_CRUISING_ALTITUDE;

	public final LongArrayList routeIds = new LongArrayList();
	public final ObjectArrayList<PathData> path = new ObjectArrayList<>();

	private final Simulator simulator;
	private final IntArrayList realTimeDepartures = new IntArrayList();
	private final int[] frequencies = new int[HOURS_PER_DAY];
	private final ObjectArrayList<Platform> platformsInRoute = new ObjectArrayList<>();
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

	public <T extends ReaderBase<U, T>, U> Depot(T readerBase, Simulator simulator) {
		super(readerBase);
		this.simulator = simulator;
		updateData(readerBase);
	}

	@Override
	public <T extends ReaderBase<U, T>, U> void updateData(T readerBase) {
		super.updateData(readerBase);

		readerBase.iterateLongArray(KEY_ROUTE_IDS, routeId -> routeIds.add(routeId.longValue()));
		readerBase.iterateReaderArray(KEY_PATH, pathSection -> path.add(new PathData(pathSection)));
		readerBase.unpackBoolean(KEY_USE_REAL_TIME, value -> useRealTime = value);

		final IntArrayList frequenciesArray = new IntArrayList();
		readerBase.iterateIntArray(KEY_FREQUENCIES, frequency -> frequenciesArray.add(frequency.intValue()));
		for (int i = 0; i < Math.min(frequenciesArray.size(), HOURS_PER_DAY); i++) {
			frequencies[i] = frequenciesArray.getInt(i);
		}

		readerBase.iterateIntArray(KEY_DEPARTURES, departure -> realTimeDepartures.add(departure.intValue()));
		readerBase.unpackBoolean(KEY_REPEAT_INFINITELY, value -> repeatInfinitely = value);
		readerBase.unpackInt(KEY_CRUISING_ALTITUDE, value -> cruisingAltitude = value);
	}

	@Override
	public void toMessagePack(MessagePacker messagePacker) throws IOException {
		super.toMessagePack(messagePacker);

		messagePacker.packString(KEY_ROUTE_IDS).packArrayHeader(routeIds.size());
		for (final long routeId : routeIds) {
			messagePacker.packLong(routeId);
		}

		MessagePackHelper.writeMessagePackDataset(messagePacker, sidingPathFinders.isEmpty() ? path : new ObjectArrayList<>(), KEY_PATH);
		messagePacker.packString(KEY_USE_REAL_TIME).packBoolean(useRealTime);
		messagePacker.packString(KEY_REPEAT_INFINITELY).packBoolean(repeatInfinitely);
		messagePacker.packString(KEY_CRUISING_ALTITUDE).packInt(cruisingAltitude);

		messagePacker.packString(KEY_FREQUENCIES).packArrayHeader(HOURS_PER_DAY);
		for (int i = 0; i < HOURS_PER_DAY; i++) {
			messagePacker.packInt(frequencies[i]);
		}

		messagePacker.packString(KEY_DEPARTURES).packArrayHeader(realTimeDepartures.size());
		for (final int departure : realTimeDepartures) {
			messagePacker.packInt(departure);
		}
	}

	@Override
	public int messagePackLength() {
		return super.messagePackLength() + 7;
	}

	@Override
	protected boolean hasTransportMode() {
		return true;
	}

	public void init() {
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
			iterateRoutes(route -> route.platformIds.forEach(platformId -> {
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

	public void iterateRoutes(Consumer<Route> consumer) {
		routeIds.forEach(routeId -> {
			final Route route = simulator.dataCache.routeIdMap.get(routeId);
			if (route != null) {
				consumer.accept(route);
			}
		});
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
			sidingsInDepot.forEach(siding -> siding.schedule.startGeneratingDepartures());
			int sidingIndex = 0;
			for (final int departure : departures) {
				for (int i = 0; i < sidingsInDepot.size(); i++) {
					if (sidingsInDepot.get((sidingIndex + i) % sidingsInDepot.size()).schedule.addDeparture(departure)) {
						sidingIndex++;
						break;
					}
				}
			}
		}
	}
}
