package org.mtr.core.servlet;

import com.google.gson.JsonObject;
import io.netty.handler.codec.http.HttpResponseStatus;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.core.simulation.Simulator;
import org.mtr.webserver.Webserver;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public abstract class ServletBase {

	protected ServletBase(Webserver webserver, String path, ObjectImmutableList<Simulator> simulators) {
		webserver.addHttpListener(path, (queryStringDecoder, bodyObject, sendResponse) -> {
			final long currentMillis = System.currentTimeMillis();

			int dimension = 0;
			try {
				dimension = Integer.parseInt(queryStringDecoder.parameters().get("dimension").get(0));
			} catch (Exception ignored) {
			}

			if (dimension < 0 || dimension >= simulators.size()) {
				buildResponseObject(sendResponse, currentMillis, null, HttpResponseStatus.BAD_REQUEST, "Invalid Dimension");
			} else {
				final Simulator simulator = simulators.get(dimension);
				final String endpoint;
				final String data;
				final String extraPath = queryStringDecoder.path().replace(path, "");
				if (!extraPath.isEmpty()) {
					final String[] pathSplit = extraPath.substring(1).split("\\.")[0].split("/");
					endpoint = pathSplit.length > 3 ? pathSplit[3] : "";
					data = pathSplit.length > 4 ? pathSplit[4] : "";
				} else {
					endpoint = "";
					data = "";
				}
				final Object2ObjectAVLTreeMap<String, String> parameters = new Object2ObjectAVLTreeMap<>();
				queryStringDecoder.parameters().forEach((key, values) -> {
					if (!values.isEmpty()) {
						parameters.put(key, values.get(0));
					}
				});
				simulator.run(() -> {
					final JsonObject jsonObject = getContent(endpoint, data, parameters, bodyObject, currentMillis, simulator);
					buildResponseObject(sendResponse, currentMillis, jsonObject, jsonObject == null ? HttpResponseStatus.NOT_FOUND : HttpResponseStatus.OK, endpoint, data);
				});
			}
		});
	}

	@Nullable
	protected abstract JsonObject getContent(String endpoint, String data, Object2ObjectAVLTreeMap<String, String> parameters, JsonObject bodyObject, long currentMillis, Simulator simulator);

	private static void buildResponseObject(BiConsumer<JsonObject, HttpResponseStatus> sendResponse, long currentMillis, @Nullable JsonObject dataObject, HttpResponseStatus httpResponseStatus, String... parameters) {
		final JsonObject responseObject = new JsonObject();
		responseObject.addProperty("code", httpResponseStatus.code());
		responseObject.addProperty("currentTime", currentMillis);
		if (dataObject != null) {
			responseObject.add("data", dataObject);
		}
		final StringBuilder reasonPhrase = new StringBuilder(httpResponseStatus.reasonPhrase());
		final String trimmedParameters = Arrays.stream(parameters).filter(parameter -> !parameter.isEmpty()).collect(Collectors.joining(", "));
		if (!trimmedParameters.isEmpty()) {
			reasonPhrase.append(" - ").append(trimmedParameters);
		}
		responseObject.addProperty("text", reasonPhrase.toString());
		responseObject.addProperty("version", 2);
		sendResponse.accept(responseObject, httpResponseStatus);
	}
}
