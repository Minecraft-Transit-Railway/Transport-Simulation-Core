package org.mtr.core.oba;

import org.mtr.core.generated.oba.ListElementSchema;
import org.mtr.core.serializer.SerializedDataBase;
import org.mtr.core.serializer.WriterBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

public final class ListElement<T extends SerializedDataBase> extends ListElementSchema {

	private final ObjectArrayList<T> list = new ObjectArrayList<>();
	private final boolean includeReferences;

	public static final int MAX_ENTRIES = 100;

	private ListElement(boolean includeReferences) {
		super(false, new References());
		this.includeReferences = includeReferences;
	}

	@Override
	public void serializeData(WriterBase writerBase) {
		super.serializeData(writerBase);
		writerBase.writeDataset(list, "list");
	}

	@Override
	public JsonObject toJson(Simulator simulator) {
		references.build(simulator);
		return Utilities.getJsonObjectFromData(this);
	}

	@Override
	protected boolean isIncludeReferences() {
		return includeReferences;
	}

	public boolean add(T entry) {
		if (list.size() == MAX_ENTRIES) {
			limitedExceeded = true;
			return false;
		} else {
			list.add(entry);
			return true;
		}
	}

	public static <T extends SerializedDataBase> ListElement<T> create(boolean includeReferences, Agency agency) {
		final ListElement<T> listElement = new ListElement<>(includeReferences);
		if (includeReferences) {
			listElement.references.addAgency(agency);
		}
		return listElement;
	}
}
