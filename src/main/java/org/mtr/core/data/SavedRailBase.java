package org.mtr.core.data;

import org.mtr.core.generated.data.SavedRailBaseSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.tool.DataFixer;
import org.mtr.core.tool.Utilities;

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
		final Rail rail = Data.tryGet(data.positionsToRail, position1, position2);
		return rail == null || this instanceof Platform && !rail.isPlatform() || this instanceof Siding && !rail.isSiding();
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

	public double getApproximateClosestDistance(Position position, Data data) {
		final Rail rail = Data.tryGet(data.positionsToRail, position1, position2);
		if (rail == null) {
			return Double.MAX_VALUE;
		} else {
			final double[] previousPosition = {0, 0, 0};
			final double[] closestDistance = {Double.MAX_VALUE};

			rail.railMath.render((x1, z1, x2, z2, x3, z3, x4, z4, y1, y2) -> {
				iterateAndCheckDistance(x1, y1, z1, previousPosition, position, closestDistance);
				iterateAndCheckDistance(x3, y2, z3, previousPosition, position, closestDistance);
			}, 0, 0);

			return closestDistance[0];
		}
	}

	private static void iterateAndCheckDistance(double x, double y, double z, double[] previousPosition, Position position, double[] closestDistance) {
		if (x != previousPosition[0] || y != previousPosition[1] || z != previousPosition[2]) {
			previousPosition[0] = x;
			previousPosition[1] = y;
			previousPosition[2] = z;
			final Position newPosition = new Position((long) Math.floor(x), (long) Math.floor(y), (long) Math.floor(z));
			final long newDistance = newPosition.manhattanDistance(position);
			if (newDistance < closestDistance[0]) {
				closestDistance[0] = newDistance;
			}
		}
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
