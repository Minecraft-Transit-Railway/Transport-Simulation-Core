package org.mtr.core.serializers;

import org.msgpack.core.MessagePacker;

public final class MessagePackWriter extends WriterBase {

	private final MessagePacker messagePacker;

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
	public MessagePackArrayWriter writeArray(String key, int length) {
		pack(key, () -> messagePacker.packArrayHeader(length));
		return new MessagePackArrayWriter(messagePacker);
	}

	@Override
	public WriterBase writeChild(String key, int length) {
		pack(key, () -> messagePacker.packMapHeader(length));
		return this;
	}

	private void pack(String key, Pack pack) {
		try {
			messagePacker.packString(key);
			pack.pack();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static final class MessagePackArrayWriter extends Array {

		private final MessagePacker messagePacker;

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
		public WriterBase writeChild(int length) {
			pack(() -> messagePacker.packMapHeader(length));
			return new MessagePackWriter(messagePacker);
		}

		private void pack(Pack pack) {
			try {
				pack.pack();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@FunctionalInterface
	private interface Pack {
		void pack() throws Exception;
	}
}
