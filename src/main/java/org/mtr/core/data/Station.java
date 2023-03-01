package org.mtr.core.data;

import org.msgpack.core.MessagePacker;
import org.msgpack.value.Value;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Station extends AreaBase<Station, Platform> {

	public int zone;
	public final Map<String, List<String>> exits = new HashMap<>();

	private static final String KEY_ZONE = "zone";
	private static final String KEY_EXITS = "exits";

	public Station() {
		super();
	}

	public Station(long id) {
		super(id);
	}

	public Station(MessagePackHelper messagePackHelper) {
		super(messagePackHelper);
	}

	@Override
	public void updateData(MessagePackHelper messagePackHelper) {
		super.updateData(messagePackHelper);

		messagePackHelper.unpackInt(KEY_ZONE, value -> zone = value);
		messagePackHelper.iterateMapValue(KEY_EXITS, entry -> {
			final List<String> destinations = new ArrayList<>(entry.getValue().asArrayValue().size());
			for (final Value destination : entry.getValue().asArrayValue()) {
				destinations.add(destination.asStringValue().asString());
			}
			exits.put(entry.getKey().asStringValue().asString(), destinations);
		});
	}

	@Override
	public void toMessagePack(MessagePacker messagePacker) throws IOException {
		super.toMessagePack(messagePacker);

		messagePacker.packString(KEY_ZONE).packInt(zone);
		messagePacker.packString(KEY_EXITS);
		messagePacker.packMapHeader(exits.size());
		for (final Map.Entry<String, List<String>> entry : exits.entrySet()) {
			final String key = entry.getKey();
			final List<String> destinations = entry.getValue();
			messagePacker.packString(key);
			messagePacker.packArrayHeader(destinations.size());
			for (String destination : destinations) {
				messagePacker.packString(destination);
			}
		}
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
			final List<String> existingDestinations = exits.get(oldParent);
			exits.remove(oldParent);
			exits.put(newParent, existingDestinations == null ? new ArrayList<>() : existingDestinations);
		} else {
			exits.put(newParent, new ArrayList<>());
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
