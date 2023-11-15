package org.mtr.core.operation;

import org.mtr.core.data.Platform;
import org.mtr.core.generated.operation.ArrivalsRequestSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongImmutableList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Collections;

public final class ArrivalsRequest extends ArrivalsRequestSchema {

	public ArrivalsRequest(LongImmutableList platformIds, int count, int page, boolean realtimeOnly) {
		super(count, page, realtimeOnly);
		this.platformIds.addAll(platformIds);
	}

	public ArrivalsRequest(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public JsonObject getArrivals(Simulator simulator, long currentMillis) {
		final ObjectArrayList<ArrivalResponse> arrivalResponseList = new ObjectArrayList<>();

		platformIds.forEach(platformId -> {
			final Platform platform = simulator.platformIdMap.get(platformId);
			if (platform != null) {
				platform.routes.forEach(route -> route.depots.forEach(depot -> depot.savedRails.forEach(siding -> siding.getArrivals(currentMillis, platform, realtimeOnly, (page + 1) * count, arrivalResponseList))));
			}
		});

		Collections.sort(arrivalResponseList);
		final ArrivalsResponse arrivalsResponse = new ArrivalsResponse();
		final int startingIndex = (int) (page * count);

		for (int i = startingIndex; i < Math.min(arrivalResponseList.size(), startingIndex + count); i++) {
			arrivalsResponse.add(arrivalResponseList.get(i));
		}

		return Utilities.getJsonObjectFromData(arrivalsResponse);
	}
}
