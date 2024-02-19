package org.mtr.core.servlet;

import org.mtr.core.Main;
import org.mtr.core.integration.Response;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.com.google.gson.JsonElement;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;
import org.mtr.libraries.io.netty.handler.codec.http.HttpResponseStatus;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;

import javax.annotation.Nullable;
import javax.servlet.AsyncContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

public abstract class ServletBase extends HttpServlet {

	private final ObjectImmutableList<Simulator> simulators;

	protected ServletBase(ObjectImmutableList<Simulator> simulators) {
		this.simulators = simulators;
	}

	@Override
	protected void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException {
		doPost(httpServletRequest, httpServletResponse);
	}

	@Override
	protected void doPost(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException {
		final AsyncContext asyncContext = httpServletRequest.startAsync();
		asyncContext.setTimeout(0);
		final long currentMillis = System.currentTimeMillis();
		final JsonElement jsonElement = JsonParser.parseReader(httpServletRequest.getReader());
		final JsonReader jsonReader = new JsonReader(jsonElement.isJsonNull() ? new JsonObject() : jsonElement);

		if (tryGetParameter(httpServletRequest, "dimensions").equals("all")) {
			simulators.forEach(simulator -> run(httpServletRequest, null, null, jsonReader, currentMillis, simulator));
			buildResponseObject(httpServletResponse, asyncContext, currentMillis, null, HttpResponseStatus.OK);
		} else {
			int dimension = 0;
			try {
				dimension = Integer.parseInt(tryGetParameter(httpServletRequest, "dimension"));
			} catch (Exception ignored) {
			}

			if (dimension < 0 || dimension >= simulators.size()) {
				buildResponseObject(httpServletResponse, asyncContext, currentMillis, null, HttpResponseStatus.BAD_REQUEST, "Invalid Dimension");
			} else {
				run(httpServletRequest, httpServletResponse, asyncContext, jsonReader, currentMillis, simulators.get(dimension));
			}
		}
	}

	@Nullable
	protected abstract JsonObject getContent(String endpoint, String data, Object2ObjectAVLTreeMap<String, String> parameters, JsonReader jsonReader, long currentMillis, Simulator simulator);

	private void run(HttpServletRequest httpServletRequest, @Nullable HttpServletResponse httpServletResponse, @Nullable AsyncContext asyncContext, JsonReader jsonReader, long currentMillis, Simulator simulator) {
		final String endpoint;
		final String data;
		final String path = httpServletRequest.getPathInfo();
		if (path != null) {
			final String[] pathSplit = path.substring(1).split("\\.")[0].split("/");
			endpoint = pathSplit.length > 0 ? pathSplit[0] : "";
			data = pathSplit.length > 1 ? pathSplit[1] : "";
		} else {
			endpoint = "";
			data = "";
		}

		final Object2ObjectAVLTreeMap<String, String> parameters = new Object2ObjectAVLTreeMap<>();
		httpServletRequest.getParameterMap().forEach((key, values) -> {
			if (values.length > 0) {
				parameters.put(key, values[0]);
			}
		});

		simulator.run(() -> {
			final JsonObject jsonObject = getContent(endpoint, data, parameters, jsonReader, currentMillis, simulator);
			if (httpServletResponse != null && asyncContext != null) {
				buildResponseObject(httpServletResponse, asyncContext, currentMillis, jsonObject, jsonObject == null ? HttpResponseStatus.NOT_FOUND : HttpResponseStatus.OK, endpoint, data);
			}
		});
	}

	public static void sendResponse(HttpServletResponse httpServletResponse, AsyncContext asyncContext, String content, String contentType, HttpResponseStatus httpResponseStatus) {
		final ByteBuffer byteBuffer = ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8));
		httpServletResponse.addHeader("Content-Type", contentType);
		try {
			final ServletOutputStream servletOutputStream = httpServletResponse.getOutputStream();
			servletOutputStream.setWriteListener(new WriteListener() {
				@Override
				public void onWritePossible() throws IOException {
					while (servletOutputStream.isReady()) {
						if (!byteBuffer.hasRemaining()) {
							httpServletResponse.setStatus(httpResponseStatus.code());
							asyncContext.complete();
							return;
						}
						servletOutputStream.write(byteBuffer.get());
					}
				}

				@Override
				public void onError(Throwable throwable) {
					asyncContext.complete();
				}
			});
		} catch (IOException e) {
			Main.LOGGER.error(e);
		}
	}

	public static String getMimeType(String fileName) {
		final String[] fileNameSplit = fileName.split("\\.");
		final String fileExtension = fileNameSplit.length == 0 ? "" : fileNameSplit[fileNameSplit.length - 1];
		switch (fileExtension) {
			case "js":
				return "text/javascript";
			case "json":
				return "application/json";
			default:
				return "text/" + fileExtension;
		}
	}

	protected static String removeLastSlash(String text) {
		if (text.isEmpty()) {
			return text;
		} else if (text.charAt(text.length() - 1) == '/') {
			return text.substring(0, text.length() - 1);
		} else {
			return text;
		}
	}

	private static void buildResponseObject(HttpServletResponse httpServletResponse, AsyncContext asyncContext, long currentMillis, @Nullable JsonObject data, HttpResponseStatus httpResponseStatus, String... parameters) {
		final StringBuilder reasonPhrase = new StringBuilder(httpResponseStatus.reasonPhrase());
		final String trimmedParameters = Arrays.stream(parameters).filter(parameter -> !parameter.isEmpty()).collect(Collectors.joining(", "));
		if (!trimmedParameters.isEmpty()) {
			reasonPhrase.append(" - ").append(trimmedParameters);
		}
		sendResponse(httpServletResponse, asyncContext, new Response(httpResponseStatus.code(), currentMillis, reasonPhrase.toString(), data).getJson().toString(), getMimeType("json"), httpResponseStatus);
	}

	private static String tryGetParameter(HttpServletRequest httpServletRequest, String parameter) {
		return httpServletRequest.getParameterMap().getOrDefault(parameter, new String[]{""})[0];
	}
}
