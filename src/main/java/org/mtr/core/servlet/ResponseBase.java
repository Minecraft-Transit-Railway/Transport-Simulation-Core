package org.mtr.core.servlet;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.core.simulation.Simulator;

public abstract class ResponseBase {

	protected final String data;
	protected final Object2ObjectAVLTreeMap<String, String> parameters;
	protected final JsonObject bodyObject;
	protected final long currentMillis;
	protected final Simulator simulator;

	public ResponseBase(String data, Object2ObjectAVLTreeMap<String, String> parameters, JsonObject bodyObject, long currentMillis, Simulator simulator) {
		this.data = data;
		this.parameters = parameters;
		this.bodyObject = bodyObject;
		this.currentMillis = currentMillis;
		this.simulator = simulator;
	}
}
