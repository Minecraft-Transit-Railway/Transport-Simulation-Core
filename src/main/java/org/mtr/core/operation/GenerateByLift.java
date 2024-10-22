package org.mtr.core.operation;

import org.mtr.core.data.Data;
import org.mtr.core.data.Lift;
import org.mtr.core.serializer.JsonReader;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

public final class GenerateByLift {

	private final Data data;
	private final Lift lift;

	public GenerateByLift(JsonReader jsonReader, Data data) {
		this.data = data;
		lift = new Lift(jsonReader, data);
	}

	public void generate() {
		final ObjectArrayList<Lift> liftsToModify = UpdateDataRequest.getAndRemoveMatchingLifts(data, lift);
		liftsToModify.add(lift);
		liftsToModify.get(0).setFloors(lift);
		data.lifts.add(liftsToModify.get(0));
		data.sync();
	}
}
