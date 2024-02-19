package org.mtr.core.serializer;

import org.mtr.core.Main;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.org.msgpack.core.MessagePacker;

import javax.annotation.Nullable;

public final class MessagePackWriter extends WriterBase {

	private final MessagePacker messagePacker;
	private final ObjectArrayList<Pack> instructions = new ObjectArrayList<>();

	public MessagePackWriter(MessagePacker messagePacker) {
		this.messagePacker = messagePacker;
	}

	@Override
	public void writeBoolean(String key, boolean value) {
		pack(key, () -> messagePacker.packBoolean(value));
	}

	@Override
	public void writeInt(String key, int value) {
		pack(key, () -> messagePacker.packInt(value));
	}

	@Override
	public void writeLong(String key, long value) {
		pack(key, () -> messagePacker.packLong(value));
	}

	@Override
	public void writeDouble(String key, double value) {
		pack(key, () -> messagePacker.packDouble(value));
	}

	@Override
	public void writeString(String key, String value) {
		pack(key, () -> messagePacker.packString(value));
	}

	@Override
	public Array writeArray(String key) {
		final MessagePackArrayWriter messagePackerArrayWriter = new MessagePackArrayWriter(messagePacker);
		pack(key, () -> messagePacker.packArrayHeader(messagePackerArrayWriter.instructions.size()), messagePackerArrayWriter::serializePart);
		return messagePackerArrayWriter;
	}

	@Override
	public WriterBase writeChild(String key) {
		final MessagePackWriter messagePackerWriter = new MessagePackWriter(messagePacker);
		pack(key, () -> messagePacker.packMapHeader(messagePackerWriter.instructions.size()), messagePackerWriter::serializePart);
		return messagePackerWriter;
	}

	public void serialize() {
		try {
			messagePacker.packMapHeader(instructions.size());
			serializePart();
		} catch (Exception e) {
			Main.LOGGER.error(e);
		}
	}

	private void pack(String key, Pack instruction) {
		pack(key, instruction, null);
	}

	private void pack(String key, Pack instruction, @Nullable Pack additionalInstructions) {
		instructions.add(() -> {
			messagePacker.packString(key);
			instruction.pack();
			if (additionalInstructions != null) {
				additionalInstructions.pack();
			}
		});
	}

	private void serializePart() throws Exception {
		for (final Pack instruction : instructions) {
			instruction.pack();
		}
	}

	private static final class MessagePackArrayWriter extends Array {

		private final MessagePacker messagePacker;
		private final ObjectArrayList<Pack> instructions = new ObjectArrayList<>();

		private MessagePackArrayWriter(MessagePacker messagePacker) {
			this.messagePacker = messagePacker;
		}

		@Override
		public void writeBoolean(boolean value) {
			pack(() -> messagePacker.packBoolean(value));
		}

		@Override
		public void writeInt(int value) {
			pack(() -> messagePacker.packInt(value));
		}

		@Override
		public void writeLong(long value) {
			pack(() -> messagePacker.packLong(value));
		}

		@Override
		public void writeDouble(double value) {
			pack(() -> messagePacker.packDouble(value));
		}

		@Override
		public void writeString(String value) {
			pack(() -> messagePacker.packString(value));
		}

		@Override
		public WriterBase writeChild() {
			final MessagePackWriter messagePackerWriter = new MessagePackWriter(messagePacker);
			pack(() -> messagePacker.packMapHeader(messagePackerWriter.instructions.size()), messagePackerWriter::serializePart);
			return messagePackerWriter;
		}

		private void pack(Pack instruction) {
			pack(instruction, null);
		}

		private void pack(Pack instruction, @Nullable Pack additionalInstructions) {
			instructions.add(() -> {
				instruction.pack();
				if (additionalInstructions != null) {
					additionalInstructions.pack();
				}
			});
		}

		private void serializePart() throws Exception {
			for (final Pack instruction : instructions) {
				instruction.pack();
			}
		}
	}

	@FunctionalInterface
	private interface Pack {
		void pack() throws Exception;
	}
}
