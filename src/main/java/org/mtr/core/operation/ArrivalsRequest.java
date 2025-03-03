package org.mtr.core.operation;

import org.mtr.core.data.Platform;
import org.mtr.core.data.Station;
import org.mtr.core.generated.operation.ArrivalsRequestSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;
import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import it.unimi.dsi.fastutil.longs.LongImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Collections;

public final class ArrivalsRequest extends ArrivalsRequestSchema {

	public ArrivalsRequest(LongImmutableList platformIds, int maxCountPerPlatform, int maxCountTotal) {
		super(maxCountPerPlatform, maxCountTotal);
		this.platformIds.addAll(platformIds);
	}

	public ArrivalsRequest(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public ArrivalsResponse getArrivals(Simulator simulator) {
		final ObjectArrayList<ArrivalResponse> arrivalResponseList = new ObjectArrayList<>();
		final ObjectAVLTreeSet<String> visitedKeys = new ObjectAVLTreeSet<>();
		final LongAVLTreeSet allPlatformIds = new LongAVLTreeSet();

		allPlatformIds.addAll(platformIds);
		platformIdsHex.forEach(platformIdHex -> allPlatformIds.add(parseHexId(platformIdHex)));
		stationIds.forEach(stationId -> iteratePlatformIds(simulator, stationId, allPlatformIds::add));
		stationIdsHex.forEach(stationIdHex -> iteratePlatformIds(simulator, parseHexId(stationIdHex), allPlatformIds::add));

		allPlatformIds.forEach(platformId -> {
			final Platform platform = simulator.platformIdMap.get(platformId);
			if (platform != null) {
				platform.routes.forEach(route -> route.depots.forEach(depot -> depot.savedRails.forEach(siding -> {
					final String key = String.format("%s_%s", platformId, siding.getId());
					if (!visitedKeys.contains(key)) {
						visitedKeys.add(key);
						siding.getArrivals(simulator.getCurrentMillis(), platform, maxCountPerPlatform, arrivalResponseList);
					}
				})));
			}
		});

		Collections.sort(arrivalResponseList);
		final ArrivalsResponse arrivalsResponse = new ArrivalsResponse(simulator.getCurrentMillis());

		for (int i = 0; i < (maxCountTotal <= 0 ? arrivalResponseList.size() : Math.min(arrivalResponseList.size(), maxCountTotal)); i++) {
			arrivalsResponse.add(arrivalResponseList.get(i));
		}

		return arrivalsResponse;
	}

	private static long parseHexId(String id) {
		try {
			return Long.parseUnsignedLong(id, 16);
		} catch (Exception ignored) {
			return 0;
		}
	}

	private static void iteratePlatformIds(Simulator simulator, long stationId, LongConsumer consumer) {
		final Station station = simulator.stationIdMap.get(stationId);
		if (station != null) {
			station.savedRails.forEach(platform -> consumer.accept(platform.getId()));
		}
	}
}
