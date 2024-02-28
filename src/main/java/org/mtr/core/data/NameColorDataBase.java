package org.mtr.core.data;

import org.mtr.core.generated.data.NameColorDataBaseSchema;
import org.mtr.core.generated.data.StationSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.serializer.SerializedDataBaseWithId;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArraySet;

import java.util.Locale;
import java.util.stream.Collectors;

public abstract class NameColorDataBase extends NameColorDataBaseSchema implements SerializedDataBaseWithId, Comparable<NameColorDataBase> {

	private String hexId;

	public NameColorDataBase(TransportMode transportMode, Data data) {
		super(transportMode, data);
	}

	public NameColorDataBase(ReaderBase readerBase, Data data) {
		super(readerBase, data);
	}

	@Override
	public final String getHexId() {
		if (hexId == null) {
			hexId = Utilities.numberToPaddedHexString(id);
		}
		return hexId;
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

	public static <T extends NameColorDataBase> ObjectArrayList<T> getDataByName(ObjectArraySet<T> dataSet, String filter) {
		return dataSet.stream().filter(data -> data.getName().toLowerCase(Locale.ENGLISH).contains(filter.toLowerCase(Locale.ENGLISH).trim())).collect(Collectors.toCollection(ObjectArrayList::new));
	}

	@Override
	public int compareTo(NameColorDataBase compare) {
		return combineNameColorId().compareTo(compare.combineNameColorId());
	}
}
