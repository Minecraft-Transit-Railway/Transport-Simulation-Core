package org.mtr.core.oba;

import com.google.gson.JsonObject;
import org.mtr.core.serializer.SerializedDataBase;
import org.mtr.core.serializer.WriterBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;

import javax.annotation.Nullable;

public final class SingleElement<T extends SerializedDataBase> extends ReferencesBase {

	private T entry;
	private final boolean includeReferences;

	private SingleElement(boolean includeReferences) {
		super(new References());
		this.includeReferences = includeReferences;
	}

	@Override
	public void serializeData(WriterBase writerBase) {
		super.serializeData(writerBase);
		if (entry != null) {
			entry.serializeData(writerBase.writeChild("entry"));
		}
	}

	@Nullable
	@Override
	public JsonObject toJson(Simulator simulator) {
		if (entry == null) {
			return null;
		} else {
			references.build(simulator);
			return Utilities.getJsonObjectFromData(this);
		}
	}

	@Override
	protected boolean isIncludeReferences() {
		return includeReferences;
	}

	public void set(T entry) {
		this.entry = entry;
	}

	public static <T extends SerializedDataBase> SingleElement<T> create(boolean includeReferences, Agency agency) {
		final SingleElement<T> singleElement = new SingleElement<>(includeReferences);
		if (includeReferences) {
			singleElement.references.addAgency(agency);
		}
		return singleElement;
	}
}
