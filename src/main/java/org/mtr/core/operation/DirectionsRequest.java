package org.mtr.core.operation;

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
		if (!startStation.isEmpty()) {
			final ObjectArrayList<Station> stations = NameColorDataBase.getDataByName(simulator.stations, startStation);
			if (!stations.isEmpty()) {
				startPosition = stations.get(0).getCenter();
			}
		}
		if (!endStation.isEmpty()) {
			final ObjectArrayList<Station> stations = NameColorDataBase.getDataByName(simulator.stations, endStation);
			if (!stations.isEmpty()) {
				endPosition = stations.get(0).getCenter();
			}
		}
		simulator.addDirectionsPathFinder(startPosition, endPosition, sendResponse);
		return new JsonObject();
	}
}
