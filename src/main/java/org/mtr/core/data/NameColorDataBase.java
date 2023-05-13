package org.mtr.core.data;

import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.serializers.WriterBase;
import org.mtr.core.tools.Utilities;

import java.util.Locale;
import java.util.Random;

public abstract class NameColorDataBase extends SerializedDataBase implements Comparable<NameColorDataBase> {

	public final long id;
	public final TransportMode transportMode;
	public String name = "";
	public int color;

	private static final String KEY_ID = "id";
	private static final String KEY_TRANSPORT_MODE = "transport_mode";
	private static final String KEY_NAME = "name";
	private static final String KEY_COLOR = "color";

	public NameColorDataBase(long id) {
		this(id, TransportMode.TRAIN);
	}

	public NameColorDataBase(long id, TransportMode transportMode) {
		this.id = id == 0 ? new Random().nextLong() : id;
		this.transportMode = transportMode;
	}

	public NameColorDataBase(ReaderBase readerBase) {
		id = readerBase.getLong(KEY_ID, 0);
		transportMode = EnumHelper.valueOf(TransportMode.TRAIN, readerBase.getString(KEY_TRANSPORT_MODE, ""));
	}

	@Override
	public void updateData(ReaderBase readerBase) {
		readerBase.unpackString(KEY_NAME, value -> name = value);
		readerBase.unpackInt(KEY_COLOR, value -> color = value);
	}

	@Override
	public void toMessagePack(WriterBase writerBase) {
		writerBase.writeLong(KEY_ID, id);
		writerBase.writeString(KEY_TRANSPORT_MODE, transportMode.toString());
		writerBase.writeString(KEY_NAME, name);
		writerBase.writeInt(KEY_COLOR, color);
	}

	@Override
	public int messagePackLength() {
		return 4;
	}

	@Override
	public String getHexId() {
		return Utilities.numberToPaddedHexString(id);
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

	@Override
	public int compareTo(NameColorDataBase compare) {
		return combineNameColorId().compareTo(compare.combineNameColorId());
	}
}
