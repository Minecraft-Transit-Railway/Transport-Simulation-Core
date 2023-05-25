package org.mtr.core.data;

import org.mtr.core.generated.NameColorDataBaseSchema;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.Utilities;

import java.util.Locale;
import java.util.Random;

public abstract class NameColorDataBase extends NameColorDataBaseSchema implements SerializedDataBase, Comparable<NameColorDataBase> {

	public NameColorDataBase(TransportMode transportMode, Simulator simulator) {
		super(transportMode, simulator);
	}

	public NameColorDataBase(ReaderBase readerBase, Simulator simulator) {
		super(readerBase, simulator);
	}

	@Override
	public final String getHexId() {
		return Utilities.numberToPaddedHexString(id);
	}

	public final long getId() {
		return id;
	}

	public final String getName() {
		return name;
	}

	public final int getColor() {
		return (int) (color & 0xFFFFFF);
	}

	public final String getColorHex() {
		return Utilities.numberToPaddedHexString(color, 6);
	}

	public final void setColor(int newColor) {
		color = newColor & 0xFFFFFF;
	}

	public final boolean isTransportMode(TransportMode transportMode) {
		return noTransportMode() || this.transportMode == transportMode;
	}

	public final boolean isTransportMode(NameColorDataBase data) {
		return noTransportMode() || data.noTransportMode() || data.transportMode == transportMode;
	}

	protected abstract boolean noTransportMode();

	private String combineNameColorId() {
		return (name + color + id).toLowerCase(Locale.ENGLISH);
	}

	private static long generateId() {
		return new Random().nextLong();
	}

	@Override
	public int compareTo(NameColorDataBase compare) {
		return combineNameColorId().compareTo(compare.combineNameColorId());
	}
}
