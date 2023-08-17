package org.mtr.core.data;

import org.mtr.core.generated.NameColorDataBaseSchema;
import org.mtr.core.generated.StationSchema;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.tools.Utilities;

import java.util.Locale;

public abstract class NameColorDataBase extends NameColorDataBaseSchema implements SerializedDataBaseWithId, Comparable<NameColorDataBase> {

	public NameColorDataBase(TransportMode transportMode, Data data) {
		super(transportMode, data);
	}

	public NameColorDataBase(ReaderBase readerBase, Data data) {
		super(readerBase, data);
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

	public final TransportMode getTransportMode() {
		return transportMode;
	}

	public final void setName(String newName) {
		name = newName;
	}

	public final void setColor(int newColor) {
		color = newColor & 0xFFFFFF;
	}

	public final boolean isTransportMode(NameColorDataBase data) {
		return noTransportMode() || data.noTransportMode() || data.transportMode == transportMode;
	}

	public final boolean isTransportMode(TransportMode transportMode) {
		return noTransportMode() || this.transportMode == transportMode;
	}

	private String combineNameColorId() {
		return (name + color + id).toLowerCase(Locale.ENGLISH);
	}

	private boolean noTransportMode() {
		return this instanceof StationSchema;
	}

	@Override
	public int compareTo(NameColorDataBase compare) {
		return combineNameColorId().compareTo(compare.combineNameColorId());
	}
}
