package org.mtr.core.data;

import org.mtr.core.serializer.SerializedDataBaseWithId;
import org.mtr.core.tool.Utilities;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;

public abstract class TwoPositionsBase implements SerializedDataBaseWithId {

	private String hexId;

	@Override
	public final String getHexId() {
		if (hexId == null) {
			hexId = getHexId(getPosition1(), getPosition2());
		}
		return hexId;
	}

	public final void writePositions(ObjectArraySet<Position> positionsToUpdate) {
		positionsToUpdate.add(getPosition1());
		positionsToUpdate.add(getPosition2());
	}

	protected final boolean matchesPositions(TwoPositionsBase twoPositionsBase) {
		return getPosition1().equals(twoPositionsBase.getPosition1()) && getPosition2().equals(twoPositionsBase.getPosition2()) || getPosition2().equals(twoPositionsBase.getPosition1()) && getPosition1().equals(twoPositionsBase.getPosition2());
	}

	protected abstract Position getPosition1();

	protected abstract Position getPosition2();

	public static String getHexId(Position position1, Position position2) {
		final boolean reversePositions = position1.compareTo(position2) > 0;
		return String.format(
				"%s-%s-%s-%s-%s-%s",
				Utilities.numberToPaddedHexString((reversePositions ? position2 : position1).getX()),
				Utilities.numberToPaddedHexString((reversePositions ? position2 : position1).getY()),
				Utilities.numberToPaddedHexString((reversePositions ? position2 : position1).getZ()),
				Utilities.numberToPaddedHexString((reversePositions ? position1 : position2).getX()),
				Utilities.numberToPaddedHexString((reversePositions ? position1 : position2).getY()),
				Utilities.numberToPaddedHexString((reversePositions ? position1 : position2).getZ())
		);
	}
}
