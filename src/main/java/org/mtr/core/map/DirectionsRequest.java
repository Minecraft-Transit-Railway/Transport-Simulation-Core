package org.mtr.core.map;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.core.data.*;
import org.mtr.core.data.Client;
import org.mtr.core.data.Station;
import org.mtr.core.generated.map.DirectionsRequestSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Consumer;

public final class DirectionsRequest extends DirectionsRequestSchema {

	@Nullable
	public final Consumer<DirectionsResponse> callback1;
	@Nullable
	public final Consumer<ObjectArrayList<PassengerDirection>> callback2;

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

	public DirectionsRequest(ReaderBase readerBase, @Nullable Consumer<DirectionsResponse> callback1, @Nullable Consumer<ObjectArrayList<PassengerDirection>> callback2) {
		super(readerBase);
		updateData(readerBase);
		this.callback1 = callback1;
		this.callback2 = callback2;
	}

	public Position getStartPosition(Simulator simulator) {
		return getPosition(simulator, startPositionX, startPositionY, startPositionZ, startStationName, startClientId);
	}

	public Position getEndPosition(Simulator simulator) {
		return getPosition(simulator, endPositionX, endPositionY, endPositionZ, endStationName, endClientId);
	}

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
			} catch (Exception ignored) {
			}
		}

		return new Position(x, y, z);
	}
}
