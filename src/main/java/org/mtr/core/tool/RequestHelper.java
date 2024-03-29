package org.mtr.core.tool;

import org.mtr.core.Main;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.okhttp3.*;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class RequestHelper {

	private Call call;
	private final boolean canInterrupt;
	private final OkHttpClient okHttpClient = new OkHttpClient.Builder().readTimeout(20, TimeUnit.SECONDS).build();

	public RequestHelper(boolean canInterrupt) {
		this.canInterrupt = canInterrupt;
	}

	public void sendPostRequest(String url, JsonObject contentObject, @Nullable Consumer<JsonObject> consumer) {
		sendPostRequest(url, contentObject.toString(), consumer == null ? null : response -> consumer.accept(Utilities.parseJson(response)));
	}

	public void sendPostRequest(String url, String content, @Nullable Consumer<String> consumer) {
		final Request request = new Request.Builder().url(url).post(RequestBody.create(content, MediaType.get("application/json"))).build();

		if (canInterrupt && call != null) {
			call.cancel();
		}

		call = okHttpClient.newCall(request);
		call.enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				Main.LOGGER.error(call.request().url(), e);
			}

			@Override
			public void onResponse(Call call, Response response) {
				try (final ResponseBody responseBody = response.body()) {
					if (consumer != null) {
						consumer.accept(responseBody.string());
					}
				} catch (IOException e) {
					Main.LOGGER.error(call.request().url(), e);
				}
			}
		});
	}
}
