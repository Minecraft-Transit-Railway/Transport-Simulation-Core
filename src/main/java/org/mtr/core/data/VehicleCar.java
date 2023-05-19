package org.mtr.core.data;

import it.unimi.dsi.fastutil.doubles.DoubleImmutableList;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.serializers.WriterBase;
import org.mtr.core.tools.Utilities;

import java.util.ArrayList;
import java.util.List;

public class VehicleCar extends SerializedDataBase {

	public final String vehicleId;
	public final double length;
	public final double width;
	public final double bogie1Position;
	public final double bogie2Position;
	public final boolean hasOneBogie;
	public final DoubleImmutableList doorLeftPositions;
	public final DoubleImmutableList doorRightPositions;

	private static final String KEY_VEHICLE_ID = "vehicle_id";
	private static final String KEY_LENGTH = "length";
	private static final String KEY_WIDTH = "width";
	private static final String KEY_BOGIE_1_POSITION = "bogie_1_position";
	private static final String KEY_BOGIE_2_POSITION = "bogie_2_position";
	private static final String KEY_DOOR_LEFT_POSITIONS = "door_left_positions";
	private static final String KEY_DOOR_RIGHT_POSITIONS = "door_right_positions";

	public VehicleCar(String vehicleId, double length, double width, double bogie1Position, double bogie2Position, double[] doorLeftPositions, double[] doorRightPositions) {
		this.vehicleId = vehicleId;
		this.length = length;
		this.width = width;
		this.bogie1Position = Utilities.clamp(bogie1Position, 0, length);
		this.bogie2Position = Utilities.clamp(bogie2Position, 0, length);
		hasOneBogie = this.bogie1Position == this.bogie2Position;
		this.doorLeftPositions = new DoubleImmutableList(doorLeftPositions);
		this.doorRightPositions = new DoubleImmutableList(doorRightPositions);
	}

	public VehicleCar(ReaderBase readerBase) {
		vehicleId = readerBase.getString(KEY_VEHICLE_ID, "");
		length = readerBase.getDouble(KEY_LENGTH, 0);
		width = readerBase.getDouble(KEY_WIDTH, 0);
		bogie1Position = readerBase.getDouble(KEY_BOGIE_1_POSITION, 0);
		bogie2Position = readerBase.getDouble(KEY_BOGIE_2_POSITION, 0);
		hasOneBogie = bogie1Position == bogie2Position;
		doorLeftPositions = getDoorPositions(readerBase, KEY_DOOR_LEFT_POSITIONS);
		doorRightPositions = getDoorPositions(readerBase, KEY_DOOR_RIGHT_POSITIONS);
	}

	@Override
	public void updateData(ReaderBase readerBase) {
	}

	@Override
	public void serializeData(WriterBase writerBase) {
		writerBase.writeString(KEY_VEHICLE_ID, vehicleId);
		writerBase.writeDouble(KEY_LENGTH, length);
		writerBase.writeDouble(KEY_WIDTH, width);
		writerBase.writeDouble(KEY_BOGIE_1_POSITION, bogie1Position);
		writerBase.writeDouble(KEY_BOGIE_2_POSITION, bogie2Position);
		final WriterBase.Array writerBaseArrayDoorLeftPositions = writerBase.writeArray(KEY_DOOR_LEFT_POSITIONS);
		doorLeftPositions.forEach(writerBaseArrayDoorLeftPositions::writeDouble);
		final WriterBase.Array writerBaseArrayDoorRightPositions = writerBase.writeArray(KEY_DOOR_RIGHT_POSITIONS);
		doorRightPositions.forEach(writerBaseArrayDoorRightPositions::writeDouble);
	}

	@Override
	public String getHexId() {
		return "";
	}

	private static DoubleImmutableList getDoorPositions(ReaderBase readerBase, String key) {
		final List<Double> tempDoorPositions = new ArrayList<>();
		readerBase.iterateDoubleArray(key, tempDoorPositions::add);
		return new DoubleImmutableList(tempDoorPositions);
	}
}
