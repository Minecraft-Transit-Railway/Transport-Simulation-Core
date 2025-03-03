package org.mtr.core.servlet;

import com.google.gson.JsonObject;
import org.mtr.core.simulation.Simulator;

import javax.annotation.Nullable;
import java.util.function.Function;

public final class CachedResponse {

	private long expiry;
	@Nullable
	private JsonObject cache;

	private final Function<Simulator, JsonObject> function;
	private final long lifespan;

	public CachedResponse(Function<Simulator, JsonObject> function, long lifespan) {
		this.function = function;
		this.lifespan = lifespan;
	}

	public JsonObject get(Simulator simulator) {
		final long currentMillis = System.currentTimeMillis();
		if (cache == null || currentMillis > expiry) {
			cache = function.apply(simulator);
			expiry = currentMillis + lifespan;
		}
		return cache;
	}
}
