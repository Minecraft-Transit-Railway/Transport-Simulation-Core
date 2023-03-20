package org.mtr.core.data;

import it.unimi.dsi.fastutil.longs.Long2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.msgpack.core.MessagePacker;
import org.mtr.core.Main;
import org.mtr.core.path.PathData;
import org.mtr.core.path.SidingPathFinder;
import org.mtr.core.reader.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Depot extends AreaBase<Depot, Siding> implements Utilities {

	public int pathGenerationSuccessfulSegments;
	public long lastDeployedMillis;
	public boolean useRealTime;
	public boolean repeatInfinitely;
	public int cruisingAltitude = DEFAULT_CRUISING_ALTITUDE;
	private int deployIndex;

	private final Simulator simulator;

	public final List<Long> routeIds = new ArrayList<>();
	public final List<Integer> departures = new ArrayList<>();
	public final List<Integer> actualDepartures = new ArrayList<>();
	public final ObjectArrayList<PathData> path = new ObjectArrayList<>();

	private final int[] frequencies = new int[HOURS_PER_DAY];
	private final Long2ObjectAVLTreeMap<Train> deployableSidings = new Long2ObjectAVLTreeMap<>();
	private final List<Platform> platformsInRoute = new ArrayList<>();
	private final List<SidingPathFinder<Station, Platform, Station, Platform>> sidingPathFinders = new ArrayList<>();

	public static final int DEFAULT_CRUISING_ALTITUDE = 256;
	private static final int CONTINUOUS_MOVEMENT_FREQUENCY = 8000;

	private static final String KEY_ROUTE_IDS = "route_ids";
	private static final String KEY_USE_REAL_TIME = "use_real_time";
	private static final String KEY_FREQUENCIES = "frequencies";
	private static final String KEY_DEPARTURES = "departures";
	private static final String KEY_LAST_DEPLOYED = "last_deployed";
	private static final String KEY_DEPLOY_INDEX = "deploy_index";
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

		readerBase.iterateLongArray(KEY_ROUTE_IDS, routeIds::add);
		readerBase.unpackBoolean(KEY_USE_REAL_TIME, value -> useRealTime = value);

		final List<Integer> frequenciesArray = new ArrayList<>();
		readerBase.iterateIntArray(KEY_FREQUENCIES, frequenciesArray::add);
		for (int i = 0; i < Math.min(frequenciesArray.size(), HOURS_PER_DAY); i++) {
			frequencies[i] = frequenciesArray.get(i);
		}

		readerBase.iterateIntArray(KEY_DEPARTURES, departures::add);
		readerBase.unpackInt(KEY_DEPLOY_INDEX, value -> deployIndex = value);
		readerBase.unpackBoolean(KEY_REPEAT_INFINITELY, value -> repeatInfinitely = value);
		readerBase.unpackInt(KEY_CRUISING_ALTITUDE, value -> cruisingAltitude = value);
		readerBase.unpackLong(KEY_LAST_DEPLOYED, value -> lastDeployedMillis = System.currentTimeMillis() - value);

		generateActualDepartures();
	}

	@Override
	public void toMessagePack(MessagePacker messagePacker) throws IOException {
		super.toMessagePack(messagePacker);

		messagePacker.packString(KEY_ROUTE_IDS).packArrayHeader(routeIds.size());
		for (final long routeId : routeIds) {
			messagePacker.packLong(routeId);
		}

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
		return super.messagePackLength() + 6;
	}

	@Override
	public void toFullMessagePack(MessagePacker messagePacker) throws IOException {
		super.toFullMessagePack(messagePacker);

		messagePacker.packString(KEY_DEPLOY_INDEX).packInt(deployIndex);
		messagePacker.packString(KEY_LAST_DEPLOYED).packLong(System.currentTimeMillis() - lastDeployedMillis);
	}

	@Override
	public int fullMessagePackLength() {
		return super.fullMessagePackLength() + 2;
	}

	@Override
	protected boolean hasTransportMode() {
		return true;
	}

	public void generateMainRoute(DataCache dataCache, Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> rails) {
		path.clear();
		sidingPathFinders.clear();

		routeIds.forEach(routeId -> {
			final Route route = dataCache.routeIdMap.get(routeId.longValue());
			if (route != null) {
				route.platformIds.forEach(platformId -> {
					final Platform platform = dataCache.platformIdMap.get(platformId.platformId);
					if (platform != null && (platformsInRoute.isEmpty() || platform.id != Utilities.getElement(platformsInRoute, -1).id)) {
						platformsInRoute.add(platform);
					}
				});
			}
		});

		for (int i = 0; i < platformsInRoute.size() - 1; i++) {
			sidingPathFinders.add(new SidingPathFinder<>(rails, platformsInRoute.get(i), platformsInRoute.get(i + 1), i + 1));
		}
	}

	public void tick(Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> rails) {
		SidingPathFinder.findPathTick(rails, path, sidingPathFinders, stopIndex -> {
			pathGenerationSuccessfulSegments = stopIndex;

			if (repeatInfinitely) {
				final PathData lastPathData = path.remove(path.size() - 1);
				path.add(new PathData(lastPathData.rail, lastPathData.savedRailBaseId, lastPathData.dwellTimeMillis, lastPathData.startPosition, lastPathData.endPosition, 1));
				if (!lastPathData.endPosition.equals(Utilities.getElement(path, 0).startPosition)) {
					SidingPathFinder.addPathData(rails, path, false, Utilities.getElement(platformsInRoute, 0), path, false, 1);
				}
			}

			if (sidingPathFinders.isEmpty() && !platformsInRoute.isEmpty()) {
				savedRails.forEach(siding -> siding.generateRoute(pathGenerationSuccessfulSegments, rails, Utilities.getElement(platformsInRoute, 0), Utilities.getElement(platformsInRoute, -1), repeatInfinitely, cruisingAltitude));
			}
		});

		if (!deployableSidings.isEmpty() && actualDepartures.stream().anyMatch(simulator::matchMillis)) {
			final ObjectArrayList<Siding> sidingsInDepot = new ObjectArrayList<>(savedRails);
			Collections.shuffle(sidingsInDepot);
			Collections.sort(sidingsInDepot);

			final int sidingsInDepotSize = sidingsInDepot.size();
			for (int i = deployIndex; i < deployIndex + sidingsInDepotSize; i++) {
				final Train train = deployableSidings.get(sidingsInDepot.get(i % sidingsInDepotSize).id);
				if (train != null) {
					lastDeployedMillis = System.currentTimeMillis();
					deployIndex = (deployIndex + 1) % sidingsInDepotSize;
					train.deployTrain();
					break;
				}
			}
		}

		deployableSidings.clear();
	}

	public void requestDeploy(long sidingId, Train train) {
		deployableSidings.put(sidingId, train);
	}

	private void generateActualDepartures() {
		actualDepartures.clear();

		if (transportMode.continuousMovement) {
			for (int i = 0; i < MILLIS_PER_DAY; i += CONTINUOUS_MOVEMENT_FREQUENCY) {
				actualDepartures.add(i);
			}
		} else {
			if (useRealTime) {
				actualDepartures.addAll(departures);
			} else {
				final int offsetMillis = (int) ((Main.START_MILLIS - simulator.startingGameDayPercentage * simulator.millisPerGameDay) % MILLIS_PER_DAY);
				final List<Integer> gameDepartures = new ArrayList<>();

				for (int i = 0; i < HOURS_PER_DAY; i++) {
					if (frequencies[i] == 0) {
						continue;
					}

					final int intervalMillis = 200000 / frequencies[i];
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

				for (int i = 0; i < Math.ceil((float) MILLIS_PER_DAY / simulator.millisPerGameDay); i++) {
					for (int gameDeparture : gameDepartures) {
						actualDepartures.add((offsetMillis + gameDeparture + simulator.millisPerGameDay * i) % MILLIS_PER_DAY);
					}
				}
			}
		}
	}
}
