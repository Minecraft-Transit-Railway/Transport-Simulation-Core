package org.mtr.core.data;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntObjectImmutablePair;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.msgpack.core.MessagePacker;
import org.mtr.core.Main;
import org.mtr.core.path.PathData;
import org.mtr.core.path.SidingPathFinder;
import org.mtr.core.reader.MessagePackHelper;
import org.mtr.core.reader.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.Utilities;

import java.io.IOException;
import java.util.Collections;
import java.util.function.Consumer;

public class Depot extends AreaBase<Depot, Siding> implements Utilities {

	public boolean useRealTime;
	public boolean repeatInfinitely;
	public int cruisingAltitude = DEFAULT_CRUISING_ALTITUDE;
	private int departureSearchIndex;

	public final LongArrayList routeIds = new LongArrayList();
	public final ObjectArrayList<PathData> path = new ObjectArrayList<>();

	private final Simulator simulator;
	private final IntArrayList departures = new IntArrayList();
	private final ObjectArrayList<IntObjectImmutablePair<Siding>> actualDepartures = new ObjectArrayList<>();
	private final int[] frequencies = new int[HOURS_PER_DAY];
	private final ObjectArrayList<Platform> platformsInRoute = new ObjectArrayList<>();
	private final ObjectArrayList<SidingPathFinder<Station, Platform, Station, Platform>> sidingPathFinders = new ObjectArrayList<>();

	public static final int DEFAULT_CRUISING_ALTITUDE = 256;
	private static final int CONTINUOUS_MOVEMENT_FREQUENCY = 8000;

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

		readerBase.iterateIntArray(KEY_DEPARTURES, departure -> departures.add(departure.intValue()));
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

		messagePacker.packString(KEY_DEPARTURES).packArrayHeader(departures.size());
		for (final int departure : departures) {
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
		generateActualDepartures();
		savedRails.forEach(Siding::init);
	}

	public void generateMainRoute() {
		if (savedRails.isEmpty()) {
			Main.LOGGER.info(String.format("No sidings in %s", name));
		} else {
			Main.LOGGER.info(String.format("Starting path generation for %s...", name));
			path.clear();
			platformsInRoute.clear();
			sidingPathFinders.clear();

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
				savedRails.forEach(siding -> siding.generateRoute(Utilities.getElement(platformsInRoute, 0), repeatInfinitely ? null : Utilities.getElement(platformsInRoute, -1), platformsInRoute.size(), cruisingAltitude));
			}
		}, () -> Main.LOGGER.info(String.format("Path not found for %s", name)));

		tryToDeploy();
	}

	public void iterateRoutes(Consumer<Route> consumer) {
		routeIds.forEach(routeId -> {
			final Route route = simulator.dataCache.routeIdMap.get(routeId);
			if (route != null) {
				consumer.accept(route);
			}
		});
	}

	public IntArrayList getDepartures(long sidingId) {
		final IntArrayList departures = new IntArrayList();
		actualDepartures.stream().filter(departureData -> departureData.right().id == sidingId).forEach(departureData -> departures.add(departureData.leftInt()));
		return departures;
	}

	private void tryToDeploy() {
		if (!actualDepartures.isEmpty()) {
			for (int i = 0; i < actualDepartures.size(); i++) {
				if (departureSearchIndex >= actualDepartures.size()) {
					departureSearchIndex = 0;
				}

				final IntObjectImmutablePair<Siding> departureData = actualDepartures.get(departureSearchIndex);
				final int match = simulator.matchMillis(departureData.leftInt());

				if (match < 0) {
					departureSearchIndex++;
				} else {
					departureData.right().deployTrain();
					return;
				}
			}
		}
	}

	private void generateActualDepartures() {
		final ObjectArrayList<Siding> sidingsInDepot = new ObjectArrayList<>(savedRails);
		Collections.shuffle(sidingsInDepot);
		Collections.sort(sidingsInDepot);
		final IntArrayList tempDepartures = new IntArrayList();

		if (transportMode.continuousMovement) {
			for (int i = 0; i < MILLIS_PER_DAY; i += CONTINUOUS_MOVEMENT_FREQUENCY) {
				tempDepartures.add(i);
			}
		} else {
			if (useRealTime) {
				tempDepartures.addAll(departures);
			} else {
				final int offsetMillis = (int) ((Main.START_MILLIS - simulator.startingGameDayPercentage * simulator.millisPerGameDay) % MILLIS_PER_DAY);
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
							gameDepartures.add(newDeparture);
						} else {
							break;
						}
					}
				}

				if (!gameDepartures.isEmpty()) {
					final int startDeparture = offsetMillis + Math.round(gameDepartures.getInt(0) / timeRatio);
					int gameDayOffset = 0;
					int gameDepartureIndex = 0;
					while (true) {
						final int newDeparture = offsetMillis + Math.round(gameDepartures.getInt(gameDepartureIndex) / timeRatio) + simulator.millisPerGameDay * gameDayOffset;
						if (newDeparture >= startDeparture + MILLIS_PER_DAY) {
							break;
						}
						tempDepartures.add(newDeparture % MILLIS_PER_DAY);
						gameDepartureIndex++;
						if (gameDepartureIndex == gameDepartures.size()) {
							gameDepartureIndex = 0;
							gameDayOffset++;
						}
					}
				}
			}
		}

		actualDepartures.clear();
		if (!sidingsInDepot.isEmpty()) {
			for (int i = 0; i < tempDepartures.size(); i++) {
				actualDepartures.add(new IntObjectImmutablePair<>(tempDepartures.getInt(i), sidingsInDepot.get(i % sidingsInDepot.size())));
			}
		}
	}
}
