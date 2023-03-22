package org.mtr.core.data;

import org.msgpack.core.MessagePacker;
import org.mtr.core.reader.ReaderBase;
import org.mtr.core.tools.Utilities;

import java.io.IOException;
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

	public <T extends ReaderBase<U, T>, U> NameColorDataBase(T readerBase) {
		id = readerBase.getLong(KEY_ID, 0);
		transportMode = EnumHelper.valueOf(TransportMode.TRAIN, readerBase.getString(KEY_TRANSPORT_MODE, ""));
	}

	@Override
	public <T extends ReaderBase<U, T>, U> void updateData(T readerBase) {
		readerBase.unpackString(KEY_NAME, value -> name = value);
		readerBase.unpackInt(KEY_COLOR, value -> color = value);
	}

	@Override
	public void toMessagePack(MessagePacker messagePacker) throws IOException {
		messagePacker.packString(KEY_ID).packLong(id);
		messagePacker.packString(KEY_TRANSPORT_MODE).packString(transportMode.toString());
		messagePacker.packString(KEY_NAME).packString(name);
		messagePacker.packString(KEY_COLOR).packInt(color);
	}

	@Override
	public int messagePackLength() {
		return 4;
	}

	@Override
	public String getHexId() {
		return Utilities.longToPaddedHexString(id);
	}

	public final boolean isTransportMode(TransportMode transportMode) {
		return !hasTransportMode() || this.transportMode == transportMode;
	}

	protected abstract boolean hasTransportMode();

	@Override
	public int compareTo(NameColorDataBase compare) {
		return (name.toLowerCase(Locale.ENGLISH) + color).compareTo((compare.name + compare.color).toLowerCase(Locale.ENGLISH));
	}
}
