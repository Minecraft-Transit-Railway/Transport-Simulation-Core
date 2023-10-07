package org.mtr.core.data;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;

import javax.annotation.Nullable;

public abstract class TwoPositionsBase implements SerializedDataBaseWithId {

	@Override
	public final String getHexId() {
		final boolean reversePositions = getPosition1().compareTo(getPosition2()) > 0;
		return String.format(
				"%s-%s-%s-%s-%s-%s",
				Utilities.numberToPaddedHexString((reversePositions ? getPosition2() : getPosition1()).getX()),
				Utilities.numberToPaddedHexString((reversePositions ? getPosition2() : getPosition1()).getY()),
				Utilities.numberToPaddedHexString((reversePositions ? getPosition2() : getPosition1()).getZ()),
				Utilities.numberToPaddedHexString((reversePositions ? getPosition1() : getPosition2()).getX()),
				Utilities.numberToPaddedHexString((reversePositions ? getPosition1() : getPosition2()).getY()),
				Utilities.numberToPaddedHexString((reversePositions ? getPosition1() : getPosition2()).getZ())
		);
	}

	@Nullable
	public final Rail getRailFromData(Data data, ObjectOpenHashSet<Position> positionsToUpdate) {
		final Rail rail = data.railIdMap.get(getHexId());
		if (rail != null) {
			positionsToUpdate.add(getPosition1());
			positionsToUpdate.add(getPosition2());
		}
		return rail;
	}

	protected final boolean matchesPositions(TwoPositionsBase twoPositionsBase) {
		return getPosition1().equals(twoPositionsBase.getPosition1()) && getPosition2().equals(twoPositionsBase.getPosition2()) || getPosition2().equals(twoPositionsBase.getPosition1()) && getPosition1().equals(twoPositionsBase.getPosition2());
	}

	protected abstract Position getPosition1();

	protected abstract Position getPosition2();
}
