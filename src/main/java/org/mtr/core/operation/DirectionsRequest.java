package org.mtr.core.operation;

import org.mtr.core.Main;
import org.mtr.core.data.NameColorDataBase;
import org.mtr.core.data.Position;
import org.mtr.core.data.Station;
import org.mtr.core.generated.operation.DirectionsRequestSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.function.Consumer;

public final class DirectionsRequest extends DirectionsRequestSchema {

	public DirectionsRequest(Position startPosition, Position endPosition) {
		super();
		this.startPosition = startPosition;
		this.endPosition = endPosition;
	}

	public DirectionsRequest(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	@Override
	protected Position getDefaultStartPosition() {
		return new Position(0, 0, 0);
	}

	@Override
	protected Position getDefaultEndPosition() {
		return new Position(0, 0, 0);
	}

	public JsonObject find(Simulator simulator, Consumer<JsonObject> sendResponse) {
		final String startStationName = findStationName(simulator, startStation, position -> startPosition = position);
		final String endStationName = findStationName(simulator, endStation, position -> endPosition = position);
		Main.LOGGER.info(
				"Finding directions between ({}, {}, {}){} and ({}, {}, {}){}",
				startPosition.getX(), startPosition.getY(), startPosition.getZ(), startStationName,
				endPosition.getX(), endPosition.getY(), endPosition.getZ(), endStationName
		);
		simulator.addDirectionsPathFinder(startPosition, endPosition, maxWalkingDistance, sendResponse);
		return new JsonObject();
	}

	private static String findStationName(Simulator simulator, String stationName, Consumer<Position> callback) {
		if (!stationName.isEmpty()) {
			final ObjectArrayList<Station> stations = NameColorDataBase.getDataByName(simulator.stations, stationName);
			if (!stations.isEmpty()) {
				final Station station = stations.get(0);
				callback.accept(station.getCenter());
				return String.format(" (%s)", station.getName());
			}
		}
		return "";
	}
}
