package org.mtr.core.data;

import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.serializers.WriterBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.Utilities;

import java.util.Locale;
import java.util.Random;

public abstract class NameColorDataBase extends SerializedDataBase implements Comparable<NameColorDataBase> {

	public final long id;
	public final TransportMode transportMode;
	public final Simulator simulator;
	public String name = "";
	public int color;

	private static final String KEY_ID = "id";
	private static final String KEY_TRANSPORT_MODE = "transport_mode";
	private static final String KEY_NAME = "name";
	private static final String KEY_COLOR = "color";

	public NameColorDataBase(TransportMode transportMode, Simulator simulator) {
		id = generateId();
		this.transportMode = transportMode;
		this.simulator = simulator;
	}

	public NameColorDataBase(ReaderBase readerBase, Simulator simulator) {
		id = readerBase.getLong(KEY_ID, generateId());
		transportMode = EnumHelper.valueOf(TransportMode.TRAIN, readerBase.getString(KEY_TRANSPORT_MODE, ""));
		this.simulator = simulator;
	}

	@Override
	public void updateData(ReaderBase readerBase) {
		readerBase.unpackString(KEY_NAME, value -> name = value);
		readerBase.unpackInt(KEY_COLOR, value -> color = value);
	}

	@Override
	public void serializeData(WriterBase writerBase) {
		writerBase.writeLong(KEY_ID, id);
		writerBase.writeString(KEY_TRANSPORT_MODE, transportMode.toString());
		serializeName(writerBase);
		serializeColor(writerBase);
	}

	@Override
	public String getHexId() {
		return Utilities.numberToPaddedHexString(id);
	}

	public final void serializeName(WriterBase writerBase) {
		writerBase.writeString(KEY_NAME, name);
	}

	public final void serializeColor(WriterBase writerBase) {
		writerBase.writeInt(KEY_COLOR, color);
	}

	public final String getColorHex() {
		return Utilities.numberToPaddedHexString(color, 6);
	}

	public final boolean isTransportMode(TransportMode transportMode) {
		return !hasTransportMode() || this.transportMode == transportMode;
	}

	protected abstract boolean hasTransportMode();

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
