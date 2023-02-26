package org.mtr.core.data;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.msgpack.core.MessagePacker;
import org.mtr.core.path.PathData;
import org.mtr.core.path.SidingPathFinder;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Depot extends AreaBase implements IReducedSaveData {

	public int pathGenerationSuccessfulSegments;
	public long lastDeployedMillis;
	public boolean useRealTime;
	public boolean repeatInfinitely;
	public int cruisingAltitude = DEFAULT_CRUISING_ALTITUDE;
	private int deployIndex;
	private int departureOffset;
	private boolean isDirty = true;

	public final List<Long> routeIds = new ArrayList<>();
	public final Map<Long, Map<Long, Float>> platformTimes = new HashMap<>();
	public final List<Integer> departures = new ArrayList<>();
	public final List<Integer> tempDepartures = new ArrayList<>();
	public final ObjectArrayList<PathData> path = new ObjectArrayList<>();

	private final int[] frequencies = new int[HOURS_IN_DAY];
	private final Map<Long, Train> deployableSidings = new HashMap<>();
	private final List<SavedRailBase> platformsInRoute = new ArrayList<>();
	private final List<SidingPathFinder> sidingPathFinders = new ArrayList<>();

	public static final int HOURS_IN_DAY = 24;
	public static final int TRAIN_FREQUENCY_MULTIPLIER = 4;
	public static final int TICKS_PER_HOUR = 1000;
	public static final int MILLIS_PER_TICK = 50;
	public static final int MILLISECONDS_PER_DAY = HOURS_IN_DAY * 60 * 60 * 1000;
	public static final int DEFAULT_CRUISING_ALTITUDE = 256;
	private static final int TICKS_PER_DAY = HOURS_IN_DAY * TICKS_PER_HOUR;
	private static final int CONTINUOUS_MOVEMENT_FREQUENCY = 8000;
	private static final int THRESHOLD_ABOVE_MAX_BUILD_HEIGHT = 64;

	private static final String KEY_ROUTE_IDS = "route_ids";
	private static final String KEY_USE_REAL_TIME = "use_real_time";
	private static final String KEY_FREQUENCIES = "frequencies";
	private static final String KEY_DEPARTURES = "departures";
	private static final String KEY_LAST_DEPLOYED = "last_deployed";
	private static final String KEY_DEPLOY_INDEX = "deploy_index";
	private static final String KEY_REPEAT_INFINITELY = "repeat_infinitely";
	private static final String KEY_CRUISING_ALTITUDE = "cruising_altitude";

	public Depot(TransportMode transportMode) {
		super(transportMode);
	}

	public Depot(long id, TransportMode transportMode) {
		super(id, transportMode);
	}

	public Depot(MessagePackHelper messagePackHelper) {
		super(messagePackHelper);
	}

	@Override
	public void updateData(MessagePackHelper messagePackHelper) {
		super.updateData(messagePackHelper);

		messagePackHelper.iterateArrayValue(KEY_ROUTE_IDS, routeId -> routeIds.add(routeId.asIntegerValue().asLong()));
		messagePackHelper.unpackBoolean(KEY_USE_REAL_TIME, value -> useRealTime = value);

		final List<Integer> frequenciesArray = new ArrayList<>();
		messagePackHelper.iterateArrayValue(KEY_FREQUENCIES, value -> frequenciesArray.add(value.asIntegerValue().asInt()));
		for (int i = 0; i < Math.min(frequenciesArray.size(), HOURS_IN_DAY); i++) {
			frequencies[i] = frequenciesArray.get(i);
		}

		messagePackHelper.iterateArrayValue(KEY_DEPARTURES, departure -> departures.add(departure.asIntegerValue().asInt()));
		messagePackHelper.unpackInt(KEY_DEPLOY_INDEX, value -> deployIndex = value);
		messagePackHelper.unpackBoolean(KEY_REPEAT_INFINITELY, value -> repeatInfinitely = value);
		messagePackHelper.unpackInt(KEY_CRUISING_ALTITUDE, value -> cruisingAltitude = value);
		messagePackHelper.unpackLong(KEY_LAST_DEPLOYED, value -> lastDeployedMillis = System.currentTimeMillis() - value);
	}

	@Override
	public void toMessagePack(MessagePacker messagePacker) throws IOException {
		toReducedMessagePack(messagePacker);
		messagePacker.packString(KEY_DEPLOY_INDEX).packInt(deployIndex);
		messagePacker.packString(KEY_LAST_DEPLOYED).packLong(System.currentTimeMillis() - lastDeployedMillis);
	}

	@Override
	public void toReducedMessagePack(MessagePacker messagePacker) throws IOException {
		super.toMessagePack(messagePacker);

		messagePacker.packString(KEY_ROUTE_IDS).packArrayHeader(routeIds.size());
		for (final long routeId : routeIds) {
			messagePacker.packLong(routeId);
		}

		messagePacker.packString(KEY_USE_REAL_TIME).packBoolean(useRealTime);
		messagePacker.packString(KEY_REPEAT_INFINITELY).packBoolean(repeatInfinitely);
		messagePacker.packString(KEY_CRUISING_ALTITUDE).packInt(cruisingAltitude);

		messagePacker.packString(KEY_FREQUENCIES).packArrayHeader(HOURS_IN_DAY);
		for (int i = 0; i < HOURS_IN_DAY; i++) {
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
	public int reducedMessagePackLength() {
		return messagePackLength() - 2;
	}

	@Override
	protected boolean hasTransportMode() {
		return true;
	}

	public int getFrequency(int index) {
		if (index >= 0 && index < frequencies.length) {
			return frequencies[index];
		} else {
			return 0;
		}
	}

	public void setFrequency(int newFrequency, int index) {
		if (index >= 0 && index < frequencies.length) {
			frequencies[index] = newFrequency;
		}
		isDirty = true;
	}

	public void generateMainRoute(DataCache dataCache, Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> rails, Set<Siding> sidings, Consumer<Thread> callback) {
		path.clear();
		sidingPathFinders.clear();

		routeIds.forEach(routeId -> {
			final Route route = dataCache.routeIdMap.get(routeId);
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
			sidingPathFinders.add(new SidingPathFinder(rails, platformsInRoute.get(i), platformsInRoute.get(i + 1), i + 1));
		}
	}

	public void tick(ObjectOpenHashSet<Siding> sidings, Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> rails) {
		SidingPathFinder.findPathTick(rails, path, sidingPathFinders, stopIndex -> {
			pathGenerationSuccessfulSegments = stopIndex;

			if (repeatInfinitely) {
				final PathData lastPathData = path.remove(path.size() - 1);
				path.add(new PathData(lastPathData.rail, lastPathData.savedRailBaseId, lastPathData.dwellTime, lastPathData.startPosition, lastPathData.endPosition, 1));
				if (!lastPathData.endPosition.equals(Utilities.getElement(path, 0).startPosition)) {
					SidingPathFinder.addPathData(rails, path, false, Utilities.getElement(platformsInRoute, 0), path, false, 1);
				}
			}

			if (sidingPathFinders.isEmpty() && !platformsInRoute.isEmpty()) {
				sidings.forEach(siding -> {
					final Position position = siding.getMidPosition();
					if (siding.isTransportMode(transportMode) && inArea(position.x, position.z)) {
						siding.generateRoute(this, pathGenerationSuccessfulSegments, rails, Utilities.getElement(platformsInRoute, 0), Utilities.getElement(platformsInRoute, -1), repeatInfinitely, cruisingAltitude);
					}
				});
			}
		});
	}

	public void requestDeploy(long sidingId, Train train) {
		deployableSidings.put(sidingId, train);
	}

	public void deployTrain(ObjectArrayList<Siding> sidings) {
		if (isDirty) {
			generateTempDepartures();
		}

		if (!deployableSidings.isEmpty() && getMillisUntilDeploy(1) == 0) {
			final List<Siding> sidingsInDepot = sidings.stream().filter(siding -> {
				final Position sidingPosition = siding.getMidPosition();
				return siding.isTransportMode(transportMode) && inArea(sidingPosition.x, sidingPosition.z);
			}).sorted().collect(Collectors.toList());

			final int sidingsInDepotSize = sidingsInDepot.size();
			for (int i = deployIndex; i < deployIndex + sidingsInDepotSize; i++) {
				final Train train = deployableSidings.get(sidingsInDepot.get(i % sidingsInDepotSize).id);
				if (train != null) {
					lastDeployedMillis = System.currentTimeMillis();
					deployIndex++;
					if (deployIndex >= sidingsInDepotSize) {
						deployIndex = 0;
					}
					train.deployTrain();
					break;
				}
			}
		}

		departureOffset = 0;
		deployableSidings.clear();
	}

	public int getNextDepartureMillis() {
		departureOffset++;
		final int millisUntilDeploy = getMillisUntilDeploy(departureOffset);
		return millisUntilDeploy >= 0 ? millisUntilDeploy : -1;
	}

	public int getMillisUntilDeploy(int offset) {
		return getMillisUntilDeploy(offset, 0);
	}

	public int getMillisUntilDeploy(int offset, int currentTimeOffset) {
		final long millis = (System.currentTimeMillis() + currentTimeOffset) % MILLISECONDS_PER_DAY;
		for (int i = 0; i < tempDepartures.size(); i++) {
			final long thisDeparture = tempDepartures.get(i);
			final long nextDeparture = wrapTime(tempDepartures.get((i + 1) % tempDepartures.size()), thisDeparture);
			final long newMillis = wrapTime(millis, thisDeparture);
			if (newMillis > thisDeparture && newMillis <= nextDeparture) {
				if (offset > 1) {
					if (offset <= tempDepartures.size()) {
						return (int) (wrapTime(tempDepartures.get((i + offset) % tempDepartures.size()), millis) - millis);
					}
				} else {
					return wrapTime(lastDeployedMillis + currentTimeOffset, newMillis) - MILLISECONDS_PER_DAY >= thisDeparture ? (int) (nextDeparture - newMillis) : 0;
				}
			}
		}
		return -1;
	}

	public void generateTempDepartures() {
		tempDepartures.clear();

		if (useRealTime && !transportMode.continuousMovement) {
			tempDepartures.addAll(departures);
		} else {
			int millisOffset = 0;
			while (millisOffset < MILLISECONDS_PER_DAY) {
				final int tempFrequency = getFrequency(getHour(millisOffset));
				if (tempFrequency == 0 && !transportMode.continuousMovement) {
					millisOffset = (int) (Math.floor((float) millisOffset / MILLIS_PER_TICK / TICKS_PER_HOUR) + 1) * TICKS_PER_HOUR * MILLIS_PER_TICK;
				} else {
					tempDepartures.add((int) ((lastDeployedMillis + millisOffset) % MILLISECONDS_PER_DAY));
					millisOffset += transportMode.continuousMovement ? CONTINUOUS_MOVEMENT_FREQUENCY : TICKS_PER_HOUR * MILLIS_PER_TICK * TRAIN_FREQUENCY_MULTIPLIER / tempFrequency;
				}
			}
			tempDepartures.sort(Integer::compareTo);
		}

		isDirty = false;
	}

	private static int getHour(int offsetMillis) {
		return 0;
	}

	private static long wrapTime(long time, long mustBeGreaterThan) {
		long newTime = time % MILLISECONDS_PER_DAY;
		while (newTime <= mustBeGreaterThan) {
			newTime += MILLISECONDS_PER_DAY;
		}
		return newTime;
	}
}
