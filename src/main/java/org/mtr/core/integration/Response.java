package org.mtr.core.integration;

import org.mtr.core.generated.integration.ResponseSchema;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonObject;

import javax.annotation.Nullable;

public final class Response extends ResponseSchema {

	public final JsonObject data;

	public Response(int code, long currentTime, String text, @Nullable JsonObject data) {
		super(code, currentTime, text, 1);
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
