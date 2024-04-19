package org.mtr.core.tool;

import org.mtr.core.Main;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.okhttp3.*;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class RequestHelper {

	private Call call;
	private final boolean canInterrupt;
	private final OkHttpClient okHttpClient = new OkHttpClient.Builder().connectTimeout(2, TimeUnit.SECONDS).writeTimeout(2, TimeUnit.SECONDS).readTimeout(2, TimeUnit.SECONDS).build();

	public RequestHelper(boolean canInterrupt) {
		this.canInterrupt = canInterrupt;
	}

	public void sendPostRequest(String url, JsonObject contentObject, @Nullable Consumer<JsonObject> consumer) {
		sendRequest(url, contentObject.toString(), consumer == null ? null : response -> consumer.accept(Utilities.parseJson(response)));
	}

	public void sendRequest(String url, @Nullable String content, @Nullable Consumer<String> consumer) {
		final Request.Builder requestBuilder = new Request.Builder().url(url);
		final Request request;
		if (content == null) {
			request = requestBuilder.get().build();
		} else {
			request = requestBuilder.post(RequestBody.create(content, MediaType.get("application/json"))).build();
		}

		if (canInterrupt && call != null) {
			call.cancel();
		}

		call = okHttpClient.newCall(request);
		call.enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException e) {
				if (!(e instanceof InterruptedIOException)) {
					Main.LOGGER.error(call.request().url(), e);
				}
			}

			@Override
			public void onResponse(Call call, Response response) {
				try (final ResponseBody responseBody = response.body()) {
					if (consumer != null) {
						consumer.accept(responseBody.string());
					}
				} catch (IOException e) {
					if (!(e instanceof InterruptedIOException)) {
						Main.LOGGER.error(call.request().url(), e);
					}
				}
			}
		});
	}
}
