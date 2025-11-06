package org.mtr.core.directions;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.core.data.PassengerDirection;
import org.mtr.core.data.Position;
import org.mtr.core.map.DirectionsResponse;

import javax.annotation.Nullable;
import java.util.function.Consumer;

public record Request(
		Position startPosition,
		Position endPosition,
		long startTime,
		Long2ObjectOpenHashMap<Connection> earliestConnections,
		Long2LongOpenHashMap walkingDistancesToEnd,
		@Nullable Consumer<DirectionsResponse> callback1,
		@Nullable Consumer<ObjectArrayList<PassengerDirection>> callback2
) {
}
