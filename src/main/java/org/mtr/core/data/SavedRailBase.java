package org.mtr.core.data;

import org.mtr.core.generated.SavedRailBaseSchema;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.Position;

public abstract class SavedRailBase<T extends SavedRailBase<T, U>, U extends AreaBase<U, T>> extends SavedRailBaseSchema {

	public U area;

	public SavedRailBase(Position position1, Position position2, TransportMode transportMode, Simulator simulator) {
		super(position1, position2, transportMode, simulator);
		name = "1";
	}

	public SavedRailBase(ReaderBase readerBase, Simulator simulator) {
		super(readerBase, simulator);
	}

	public boolean containsPos(Position position) {
		return position1.equals(position) || position2.equals(position);
	}

	public Position getMidPosition() {
		final Position offsetPosition = position1.offset(position2);
		return new Position(offsetPosition.getX() / 2, offsetPosition.getY() / 2, offsetPosition.getZ() / 2);
	}

	public boolean isInvalidSavedRail(DataCache dataCache) {
		return isInvalidSavedRail(dataCache, position1, position2) || isInvalidSavedRail(dataCache, position2, position1);
	}

	public Position getRandomPosition() {
		return position1;
	}

	public Position getOtherPosition(Position position) {
		return position.equals(position1) ? position2 : position1;
	}

	public static boolean isInvalidSavedRail(DataCache dataCache, Position position1, Position position2) {
		final Rail rail = DataCache.tryGet(dataCache.positionToRailConnections, position1, position2);
		return rail == null || !rail.isSavedRail();
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
