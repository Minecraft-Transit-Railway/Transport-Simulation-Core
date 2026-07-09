package org.mtr.core.data;

import com.google.gson.JsonObject;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;

import java.nio.file.Path;
import java.nio.file.Paths;

public interface TestUtilities {

	Path TEST_DIRECTORY = Paths.get("build/test-data");
	int PORT = 8889; // We don't want to conflict with Minecraft Transit Railway using port 8888 by default
	Logger LOGGER = LogManager.getLogger(TestUtilities.class);

	static Simulator getDefaultSimulator() {
		return new Simulator("test", new String[]{"test"}, TEST_DIRECTORY, false);
	}

	static JsonObject sendHttpDataRequest(String endpoint, JsonObject bodyObject) {
		return sendHttpRequest(String.format("http://localhost:%s/mtr/api/data/%s", PORT, endpoint), bodyObject);
	}

	static JsonObject sendHttpRequest(String uri, @Nullable JsonObject bodyObject) {
		final HttpUriRequest httpUriRequest = bodyObject == null ? new HttpGet(uri) : new HttpPost(uri);

		if (httpUriRequest instanceof HttpPost) {
			try {
				((HttpPost) httpUriRequest).setEntity(new StringEntity(bodyObject.toString()));
			} catch (Exception e) {
				LOGGER.error("Failed to attach JSON body to POST request", e);
			}
		}

		JsonObject responseObject = new JsonObject();

		try (final CloseableHttpClient httpClient = HttpClients.createDefault()) {
			try (final CloseableHttpResponse response = httpClient.execute(httpUriRequest)) {
				responseObject = Utilities.parseJson(EntityUtils.toString(response.getEntity()));
			}
		} catch (Exception e) {
			LOGGER.error("Failed to execute HTTP request to {}", uri, e);
		}

		return responseObject;
	}
}
