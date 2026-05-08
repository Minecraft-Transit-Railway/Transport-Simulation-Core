package org.mtr.core.map;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.extern.log4j.Log4j2;
import org.jspecify.annotations.Nullable;
import org.mtr.core.data.*;
import org.mtr.core.data.Client;
import org.mtr.core.data.Station;
import org.mtr.core.generated.map.DirectionsRequestSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Wire request for the dashboard's "find directions" feature, mapping to {@code DirectionsFinder}
 * inside the simulator. Carries either explicit world coordinates or a station-name / client-uuid
 * to resolve those coordinates from at request time.
 */
@Log4j2
public final class DirectionsRequest extends DirectionsRequestSchema {

	/** Optional callback fed the structured response (map view). */
	@Nullable
	public final Consumer<DirectionsResponse> callback1;
	/** Optional callback fed only the per-leg passenger directions list (mod overlay use). */
	@Nullable
	public final Consumer<ObjectArrayList<PassengerDirection>> callback2;

	/**
	 * Construct a request from explicit start/end positions (the in-process fast path).
	 */
	public DirectionsRequest(Position startPosition, Position endPosition, long startTime, @Nullable Consumer<DirectionsResponse> callback1, @Nullable Consumer<ObjectArrayList<PassengerDirection>> callback2) {
		super(startTime);
		startPositionX = startPosition.getX();
		startPositionY = startPosition.getY();
		startPositionZ = startPosition.getZ();
		endPositionX = endPosition.getX();
		endPositionY = endPosition.getY();
		endPositionZ = endPosition.getZ();
		this.callback1 = callback1;
		this.callback2 = callback2;
	}

	/** Deserialisation constructor used by the wire layer. */
	public DirectionsRequest(ReaderBase readerBase, @Nullable Consumer<DirectionsResponse> callback1, @Nullable Consumer<ObjectArrayList<PassengerDirection>> callback2) {
		super(readerBase);
		updateData(readerBase);
		this.callback1 = callback1;
		this.callback2 = callback2;
	}

	/**
	 * Resolve the request's start position against {@code simulator}, preferring station-name and
	 * client-uuid lookups over the raw fallback coordinates.
	 */
	public Position getStartPosition(Simulator simulator) {
		return getPosition(simulator, startPositionX, startPositionY, startPositionZ, startStationName, startClientId);
	}

	/** @see #getStartPosition(Simulator) */
	public Position getEndPosition(Simulator simulator) {
		return getPosition(simulator, endPositionX, endPositionY, endPositionZ, endStationName, endClientId);
	}

	/** @return the configured start time (simulator wall-clock millis) */
	public long getStartTime() {
		return startTime;
	}

	private Position getPosition(Simulator simulator, long x, long y, long z, String stationName, String clientId) {
		if (!stationName.isEmpty()) {
			final Station station = simulator.stations.stream()
				.filter(checkStation -> checkStation.getName().equalsIgnoreCase(stationName) || Arrays.stream(checkStation.getName().split("\\|")).anyMatch(namePart -> namePart.equalsIgnoreCase(stationName)))
				.findFirst()
				.orElse(null);
			if (station != null) {
				long xTotal = 0;
				long yTotal = 0;
				long zTotal = 0;
				for (final Platform platform : station.savedRails) {
					final Position position = platform.getMidPosition();
					xTotal += position.getX();
					yTotal += position.getY();
					zTotal += position.getZ();
				}
				final int count = station.savedRails.size();
				return new Position(xTotal / count, yTotal / count, zTotal / count);
			}
		}

		if (!clientId.isEmpty()) {
			try {
				final UUID uuid = UUID.fromString(clientId);
				final Platform platform = simulator.getNextPlatformOfRidingVehicle(uuid);
				if (platform == null) {
					for (final Client client : simulator.clients) {
						if (client.uuid.equals(uuid)) {
							return client.getPosition();
						}
					}
				} else {
					return platform.getMidPosition();
				}
			} catch (Exception e) {
				// Bad UUID string — fall through to the raw coordinate fallback. (§3.14)
				log.debug("Failed to resolve clientId {}; falling back to raw coordinates", clientId, e);
			}
		}

		return new Position(x, y, z);
	}
}
