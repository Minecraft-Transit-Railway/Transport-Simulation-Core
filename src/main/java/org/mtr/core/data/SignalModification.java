package org.mtr.core.data;

import org.mtr.core.generated.data.SignalModificationSchema;
import org.mtr.core.serializer.ReaderBase;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public final class SignalModification extends SignalModificationSchema {

	public SignalModification(Position position1, Position position2, boolean clearAll) {
		super(position1, position2, clearAll);
	}

	public SignalModification(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	@Override
	public boolean isValid() {
		return !position1.equals(position2);
	}

	@Override
	protected Position getPosition1() {
		return position1;
	}

	@Override
	protected Position getPosition2() {
		return position2;
	}

	public void applyModificationToRail(Data data, ObjectArrayList<Rail> railsToUpdate) {
		final Rail rail = data.railIdMap.get(getHexId());
		if (rail != null) {
			rail.applyModification(this);
			railsToUpdate.add(rail);
		}
	}

	public void putColorToAdd(int color) {
		signalColorsAdd.add(color);
	}

	public void putColorToRemove(int color) {
		signalColorsRemove.add(color);
	}

	boolean getIsClearAll() {
		return clearAll;
	}

	LongArrayList getSignalColorsAdd() {
		return signalColorsAdd;
	}

	LongArrayList getSignalColorsRemove() {
		return signalColorsRemove;
	}
}
