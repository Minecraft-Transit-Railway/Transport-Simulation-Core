package org.mtr.core.data;

import org.mtr.core.generated.SavedRailBaseSchema;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.tools.DataFixer;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;

public abstract class SavedRailBase<T extends SavedRailBase<T, U>, U extends AreaBase<U, T>> extends SavedRailBaseSchema {

	public U area;

	public SavedRailBase(Position position1, Position position2, TransportMode transportMode, Data data) {
		super(position1, position2, transportMode, data);
		name = "1";
	}

	public SavedRailBase(ReaderBase readerBase, Data data) {
		super(DataFixer.convertSavedRailBase(readerBase), data);
	}

	@Override
	public boolean isValid() {
		return true;
	}

	public boolean containsPos(Position position) {
		return position1.equals(position) || position2.equals(position);
	}

	public Position getMidPosition() {
		final Position offsetPosition = position1.offset(position2);
		return new Position(offsetPosition.getX() / 2, offsetPosition.getY() / 2, offsetPosition.getZ() / 2);
	}

	public boolean isInvalidSavedRail(Data data) {
		return isInvalidSavedRail(this, data, position1, position2) || isInvalidSavedRail(this, data, position2, position1);
	}

	public Position getRandomPosition() {
		return position1;
	}

	public Position getOtherPosition(Position position) {
		return position.equals(position1) ? position2 : position1;
	}

	public boolean closeTo(Position position, double radius) {
		return Utilities.isBetween(position, position1, position2, radius);
	}

	private static boolean isInvalidSavedRail(SavedRailBase<?, ?> savedRailBase, Data data, Position position1, Position position2) {
		final Rail rail = Data.tryGet(data.positionToRailConnections, position1, position2);
		return rail == null || !rail.isValid(savedRailBase);
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
		final boolean compareIsNumber = isNumber(compare.getName());

		if (thisIsNumber && compareIsNumber) {
			final int floatCompare = Float.compare(Float.parseFloat(name), Float.parseFloat(compare.getName()));
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
