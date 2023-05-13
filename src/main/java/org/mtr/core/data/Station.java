package org.mtr.core.data;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.serializers.WriterBase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Station extends AreaBase<Station, Platform> {

	public int zone;
	public final Object2ObjectArrayMap<String, ObjectArrayList<String>> exits = new Object2ObjectArrayMap<>();

	private static final String KEY_ZONE = "zone";
	private static final String KEY_EXITS = "exits";

	public Station(long id) {
		super(id);
	}

	public Station(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	@Override
	public void updateData(ReaderBase readerBase) {
		super.updateData(readerBase);

		readerBase.unpackInt(KEY_ZONE, value -> zone = value);
		readerBase.iterateKeys(KEY_EXITS, (key, readerBaseMap) -> {
			final ObjectArrayList<String> destinations = new ObjectArrayList<>();
			readerBaseMap.iterateStringArray(key, destinations::add);
			exits.put(key, destinations);
		});
	}

	@Override
	public void toMessagePack(WriterBase writerBase) {
		super.toMessagePack(writerBase);

		writerBase.writeInt(KEY_ZONE, zone);
		final WriterBase writerBaseChild = writerBase.writeChild(KEY_EXITS, exits.size());
		exits.forEach((key, destinations) -> {
			final WriterBase.Array writerBaseArray = writerBaseChild.writeArray(key, destinations.size());
			destinations.forEach(writerBaseArray::writeString);
		});
	}

	@Override
	public int messagePackLength() {
		return super.messagePackLength() + 2;
	}

	@Override
	protected boolean hasTransportMode() {
		return false;
	}

	public Map<String, List<String>> getGeneratedExits() {
		final List<String> exitParents = new ArrayList<>(exits.keySet());
		exitParents.sort(String::compareTo);

		final Map<String, List<String>> generatedExits = new HashMap<>();
		exitParents.forEach(parent -> {
			final String exitLetter = parent.substring(0, 1);
			if (!generatedExits.containsKey(exitLetter)) {
				generatedExits.put(exitLetter, new ArrayList<>());
			}

			generatedExits.get(exitLetter).addAll(exits.get(parent));
			generatedExits.put(parent, exits.get(parent));
		});

		return generatedExits;
	}

	private void setExitParent(String oldParent, String newParent) {
		if (parentExists(oldParent)) {
			final ObjectArrayList<String> existingDestinations = exits.get(oldParent);
			exits.remove(oldParent);
			exits.put(newParent, existingDestinations == null ? new ObjectArrayList<>() : existingDestinations);
		} else {
			exits.put(newParent, new ObjectArrayList<>());
		}
	}

	private boolean parentExists(String parent) {
		return parent != null && exits.containsKey(parent);
	}

	public static long serializeExit(String exit) {
		final char[] characters = exit.toCharArray();
		long code = 0;
		for (final char character : characters) {
			code = code << 8;
			code += character;
		}
		return code;
	}

	public static String deserializeExit(long code) {
		StringBuilder exit = new StringBuilder();
		long charCodes = code;
		while (charCodes > 0) {
			exit.insert(0, (char) (charCodes & 0xFF));
			charCodes = charCodes >> 8;
		}
		return exit.toString();
	}
}
