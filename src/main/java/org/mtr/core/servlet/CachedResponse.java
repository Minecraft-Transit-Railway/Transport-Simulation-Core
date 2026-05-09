package org.mtr.core.servlet;

import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.mtr.core.simulation.Simulator;

import java.util.function.Function;

@RequiredArgsConstructor
public final class CachedResponse {

	private final Function<Simulator, JsonObject> function;
	private final long lifespan;

	private long expiry;
	@Nullable
	private JsonObject cache;


	public JsonObject get(Simulator simulator) {
		final long currentMillis = System.currentTimeMillis();
		if (cache == null || currentMillis > expiry) {
			cache = function.apply(simulator);
			expiry = currentMillis + lifespan;
		}
		return cache;
	}
}
