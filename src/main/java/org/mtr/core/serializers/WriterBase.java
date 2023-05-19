package org.mtr.core.serializers;

import org.mtr.core.data.SerializedDataBase;

import java.util.Collection;

public abstract class WriterBase {

	public abstract void writeBoolean(String key, boolean value);

	public abstract void writeInt(String key, int value);

	public abstract void writeLong(String key, long value);

	public abstract void writeDouble(String key, double value);

	public abstract void writeString(String key, String value);

	public abstract Array writeArray(String key);

	public abstract WriterBase writeChild(String key);

	public final void writeDataset(Collection<? extends SerializedDataBase> dataSet, String key) {
		final WriterBase.Array writerBaseArray = writeArray(key);
		dataSet.forEach(data -> data.serializeData(writerBaseArray.writeChild()));
	}

	public abstract static class Array {

		public abstract void writeBoolean(boolean value);

		public abstract void writeInt(int value);

		public abstract void writeLong(long value);

		public abstract void writeDouble(double value);

		public abstract void writeString(String value);

		public abstract WriterBase writeChild();
	}
}
