package org.mtr.core.servlet;

import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.serializer.SerializedDataBase;
import org.mtr.core.serializer.WriterBase;

import javax.annotation.Nullable;
import java.util.function.Consumer;

public final class QueueObject {

	public final Operation operation;
	public final SerializedDataBase data;
	@Nullable
	private final Consumer<SerializedDataBase> callback;

	public <T extends SerializedDataBase> QueueObject(Operation operation, SerializedDataBase data, @Nullable Consumer<T> callback, @Nullable Class<T> reaponseDataClass) {
		this.operation = operation;
		this.data = data;
		this.callback = callback == null || reaponseDataClass == null ? null : serializedDataBase -> {
			if (reaponseDataClass.isInstance(serializedDataBase)) {
				callback.accept(reaponseDataClass.cast(serializedDataBase));
			}
		};
	}

	public void runCallback(@Nullable SerializedDataBase data) {
		if (callback != null) {
			callback.accept(data == null ? new SerializedDataBase() {
				@Override
				public void updateData(ReaderBase readerBase) {
				}

				@Override
				public void serializeData(WriterBase writerBase) {
				}
			} : data);
		}
	}
}
