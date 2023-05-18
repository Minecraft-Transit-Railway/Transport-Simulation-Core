package org.mtr.core.data;

import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.serializers.WriterBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.DataFixer;
import org.mtr.core.tools.Position;

public abstract class SavedRailBase<T extends SavedRailBase<T, U>, U extends AreaBase<U, T>> extends NameColorDataBase {

	public U area;
	private int timeValue;
	public final ObjectObjectImmutablePair<Position, Position> positions;

	private static final int MAX_TIME_VALUE = 600000;
	private static final int DEFAULT_TIME_VALUE = 10000;
	private static final String KEY_POS_1_X = "pos_1_x";
	private static final String KEY_POS_1_Y = "pos_1_y";
	private static final String KEY_POS_1_Z = "pos_1_z";
	private static final String KEY_POS_2_X = "pos_2_x";
	private static final String KEY_POS_2_Y = "pos_2_y";
	private static final String KEY_POS_2_Z = "pos_2_z";
	private static final String KEY_TIME_VALUE = "time_value";

	public SavedRailBase(TransportMode transportMode, Position pos1, Position pos2, Simulator simulator) {
		super(transportMode, simulator);
		name = "1";
		positions = createPositions(pos1, pos2);
	}

	public SavedRailBase(ReaderBase readerBase, Simulator simulator) {
		super(readerBase, simulator);

		final long[] newPositions = {0, 0, 0, 0, 0, 0};
		DataFixer.unpackSavedRailBase(readerBase, position -> {
			newPositions[0] = position.x;
			newPositions[1] = position.y;
			newPositions[2] = position.z;
		}, position -> {
			newPositions[3] = position.x;
			newPositions[4] = position.y;
			newPositions[5] = position.z;
		});
		readerBase.unpackLong(KEY_POS_1_X, value -> newPositions[0] = value);
		readerBase.unpackLong(KEY_POS_1_Y, value -> newPositions[1] = value);
		readerBase.unpackLong(KEY_POS_1_Z, value -> newPositions[2] = value);
		readerBase.unpackLong(KEY_POS_2_X, value -> newPositions[3] = value);
		readerBase.unpackLong(KEY_POS_2_Y, value -> newPositions[4] = value);
		readerBase.unpackLong(KEY_POS_2_Z, value -> newPositions[5] = value);
		final Position position1 = new Position(newPositions[0], newPositions[1], newPositions[2]);
		final Position position2 = new Position(newPositions[3], newPositions[4], newPositions[5]);
		positions = createPositions(position1, position2);
	}

	@Override
	public void updateData(ReaderBase readerBase) {
		super.updateData(readerBase);

		readerBase.unpackInt(KEY_TIME_VALUE, value -> timeValue = value);
		DataFixer.unpackDwellTime(readerBase, value -> timeValue = value);
	}

	@Override
	public void toMessagePack(WriterBase writerBase) {
		super.toMessagePack(writerBase);

		writerBase.writeLong(KEY_POS_1_X, positions.left().x);
		writerBase.writeLong(KEY_POS_1_Y, positions.left().y);
		writerBase.writeLong(KEY_POS_1_Z, positions.left().z);
		writerBase.writeLong(KEY_POS_2_X, positions.right().x);
		writerBase.writeLong(KEY_POS_2_Y, positions.right().y);
		writerBase.writeLong(KEY_POS_2_Z, positions.right().z);
		writerBase.writeInt(KEY_TIME_VALUE, timeValue);
	}

	@Override
	protected final boolean hasTransportMode() {
		return true;
	}

	public boolean containsPos(Position pos) {
		return positions.left().equals(pos) || positions.right().equals(pos);
	}

	public Position getMidPosition() {
		final Position offsetPosition = positions.left().offset(positions.right());
		return new Position(offsetPosition.x / 2, offsetPosition.y / 2, offsetPosition.z / 2);
	}

	public boolean isInvalidSavedRail(DataCache dataCache) {
		final Position position1 = positions.left();
		final Position position2 = positions.right();
		return isInvalidSavedRail(dataCache, position1, position2) || isInvalidSavedRail(dataCache, position2, position1);
	}

	public Position getOtherPosition(Position position) {
		final Position position1 = positions.left();
		final Position position2 = positions.right();
		return position.equals(position1) ? position2 : position1;
	}

	public void setTimeValue(int timeValue) {
		this.timeValue = timeValue;
	}

	public int getTimeValueMillis() {
		if (timeValue <= 0 || timeValue > MAX_TIME_VALUE) {
			timeValue = DEFAULT_TIME_VALUE;
		}
		return transportMode.continuousMovement ? 1 : timeValue;
	}

	public static boolean isInvalidSavedRail(DataCache dataCache, Position position1, Position position2) {
		final Rail rail = DataCache.tryGet(dataCache.positionToRailConnections, position1, position2);
		return rail == null || !rail.hasSavedRail;
	}

	private static boolean isNumber(String text) {
		try {
			Double.parseDouble(text);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private static ObjectObjectImmutablePair<Position, Position> createPositions(Position position1, Position position2) {
		if (position1.compareTo(position2) > 0) {
			return new ObjectObjectImmutablePair<>(position1, position2);
		} else {
			return new ObjectObjectImmutablePair<>(position2, position1);
		}
	}

	@Override
	public int compareTo(NameColorDataBase compare) {
		final boolean thisIsNumber = isNumber(name);
		final boolean compareIsNumber = isNumber(compare.name);

		if (thisIsNumber && compareIsNumber) {
			final int floatCompare = Float.compare(Float.parseFloat(name), Float.parseFloat(compare.name));
			return floatCompare == 0 ? super.compareTo(compare) : floatCompare;
		} else if (thisIsNumber) {
			return -1;
		} else if (compareIsNumber) {
			return 1;
		} else {
			return super.compareTo(compare);
		}
	}
}
