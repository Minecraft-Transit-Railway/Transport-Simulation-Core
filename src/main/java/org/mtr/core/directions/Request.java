package org.mtr.core.directions;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.mtr.core.data.Position;
import org.mtr.core.map.DirectionsResponse;

import java.util.function.Consumer;

public record Request(Position startPosition, Position endPosition, long startTime, Long2ObjectOpenHashMap<Connection> earliestConnections, Long2LongOpenHashMap walkingDistancesToEnd, Consumer<DirectionsResponse> callback) {
}
