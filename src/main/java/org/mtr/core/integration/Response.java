package org.mtr.core.integration;

import com.google.gson.JsonObject;
import org.mtr.core.generated.integration.ResponseSchema;
import org.mtr.core.tool.Utilities;

import javax.annotation.Nullable;

public final class Response extends ResponseSchema {

	public final JsonObject data;

	public Response(int code, String text, @Nullable JsonObject data) {
		super(code, System.currentTimeMillis(), text, 1);
		this.data = data;
	}

	public JsonObject getJson() {
		final JsonObject jsonObject = Utilities.getJsonObjectFromData(this);
		if (data != null) {
			jsonObject.add("data", data);
		}
		return jsonObject;
	}
}
