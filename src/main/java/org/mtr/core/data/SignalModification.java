package org.mtr.core.data;

import org.mtr.core.generated.data.SignalModificationSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

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

	public void applyModificationToRail(Data data, ObjectOpenHashSet<Rail> railsToUpdate, ObjectOpenHashSet<Position> positionsToUpdate) {
		final Rail rail = getRailFromData(data, positionsToUpdate);
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
