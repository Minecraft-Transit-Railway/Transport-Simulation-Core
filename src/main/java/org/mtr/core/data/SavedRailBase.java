package org.mtr.core.data;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.msgpack.core.MessagePacker;
import org.mtr.core.tools.DataFixer;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class SavedRailBase<T extends SavedRailBase<T, U>, U extends AreaBase<U, T>> extends NameColorDataBase {

	public U area;
	private int integerValue;
	private final Set<Position> positions = new HashSet<>(2);

	private static final int MAX_INTEGER_VALUE = 1200;
	private static final int DEFAULT_INTEGER_VALUE = 20;
	private static final String KEY_POS_1_X = "pos_1_x";
	private static final String KEY_POS_1_Y = "pos_1_y";
	private static final String KEY_POS_1_Z = "pos_1_z";
	private static final String KEY_POS_2_X = "pos_2_x";
	private static final String KEY_POS_2_Y = "pos_2_y";
	private static final String KEY_POS_2_Z = "pos_2_z";
	private static final String KEY_INTEGER_VALUE = "dwell_time";

	public SavedRailBase(long id, TransportMode transportMode, Position pos1, Position pos2) {
		super(id, transportMode);
		name = "1";
		positions.add(pos1);
		positions.add(pos2);
		integerValue = transportMode.continuousMovement ? 1 : DEFAULT_INTEGER_VALUE;
	}

	public SavedRailBase(TransportMode transportMode, Position pos1, Position pos2) {
		super(transportMode);
		name = "1";
		positions.add(pos1);
		positions.add(pos2);
		integerValue = transportMode.continuousMovement ? 1 : DEFAULT_INTEGER_VALUE;
	}

	public SavedRailBase(MessagePackHelper messagePackHelper) {
		super(messagePackHelper);
	}

	@Override
	public void updateData(MessagePackHelper messagePackHelper) {
		super.updateData(messagePackHelper);

		DataFixer.unpackSavedRailBase(messagePackHelper, positions);

		final long[] newPositions = {0, 0, 0, 0, 0, 0};
		messagePackHelper.unpackLong(KEY_POS_1_X, value -> newPositions[0] = value);
		messagePackHelper.unpackLong(KEY_POS_1_Y, value -> newPositions[1] = value);
		messagePackHelper.unpackLong(KEY_POS_1_Z, value -> newPositions[2] = value);
		messagePackHelper.unpackLong(KEY_POS_2_X, value -> newPositions[3] = value);
		messagePackHelper.unpackLong(KEY_POS_2_Y, value -> newPositions[4] = value);
		messagePackHelper.unpackLong(KEY_POS_2_Z, value -> newPositions[5] = value);
		final Position pos1 = new Position(newPositions[0], newPositions[1], newPositions[2]);
		final Position pos2 = new Position(newPositions[3], newPositions[4], newPositions[5]);
		if (!pos1.equals(pos2)) {
			positions.clear();
			positions.add(pos1);
			positions.add(pos2);
		}

		messagePackHelper.unpackInt(KEY_INTEGER_VALUE, value -> integerValue = transportMode.continuousMovement ? 1 : value);
	}

	@Override
	public void toMessagePack(MessagePacker messagePacker) throws IOException {
		super.toMessagePack(messagePacker);

		messagePacker.packString(KEY_POS_1_X).packLong(getPosition(0).x);
		messagePacker.packString(KEY_POS_1_Y).packLong(getPosition(0).y);
		messagePacker.packString(KEY_POS_1_Z).packLong(getPosition(0).z);
		messagePacker.packString(KEY_POS_2_X).packLong(getPosition(1).x);
		messagePacker.packString(KEY_POS_2_Y).packLong(getPosition(1).y);
		messagePacker.packString(KEY_POS_2_Z).packLong(getPosition(1).z);
		messagePacker.packString(KEY_INTEGER_VALUE).packInt(integerValue);
	}

	@Override
	public int messagePackLength() {
		return super.messagePackLength() + 7;
	}

	@Override
	protected final boolean hasTransportMode() {
		return true;
	}

	public boolean containsPos(Position pos) {
		return positions.contains(pos);
	}

	public Position getMidPosition() {
		return getMidPosition(false);
	}

	public Position getMidPosition(boolean zeroY) {
		final Position pos = getPosition(0).offset(getPosition(1));
		return new Position(pos.x / 2, zeroY ? 0 : pos.y / 2, pos.z / 2);
	}

	public Position getAnyPos() {
		return getPosition(0);
	}

	public boolean isInvalidSavedRail(Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> rails) {
		final Position pos1 = getPosition(0);
		final Position pos2 = getPosition(1);
		return isInvalidSavedRail(rails, pos1, pos2) || isInvalidSavedRail(rails, pos2, pos1);
	}

	public List<Position> getOrderedPositions(Position pos, boolean reverse) {
		final Position pos1 = getPosition(0);
		final Position pos2 = getPosition(1);
		final double d1 = pos1.distManhattan(pos);
		final double d2 = pos2.distManhattan(pos);
		final List<Position> orderedPositions = new ArrayList<>();
		if (d2 > d1 == reverse) {
			orderedPositions.add(pos2);
			orderedPositions.add(pos1);
		} else {
			orderedPositions.add(pos1);
			orderedPositions.add(pos2);
		}
		return orderedPositions;
	}

	public Position getOtherPosition(Position pos) {
		final Position pos1 = getPosition(0);
		final Position pos2 = getPosition(1);
		return pos.equals(pos1) ? pos2 : pos1;
	}

	public int getIntegerValue() {
		if (integerValue <= 0 || integerValue > MAX_INTEGER_VALUE) {
			integerValue = DEFAULT_INTEGER_VALUE;
		}
		return transportMode.continuousMovement ? 1 : integerValue;
	}

	private Position getPosition(int index) {
		return positions.size() > index ? new ArrayList<>(positions).get(index) : new Position(0, 0, 0);
	}

	public static boolean isInvalidSavedRail(Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> rails, Position pos1, Position pos2) {
		return !Utilities.containsRail(rails, pos1, pos2) || !rails.get(pos1).get(pos2).railType.hasSavedRail;
	}

	private static boolean isNumber(String text) {
		try {
			Double.parseDouble(text);
			return true;
		} catch (Exception e) {
			return false;
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
