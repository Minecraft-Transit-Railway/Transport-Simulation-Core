package org.mtr.core.servlet;

import com.google.gson.JsonObject;
import org.mtr.core.simulation.Simulator;

import javax.servlet.AsyncContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

public abstract class ServletBase extends HttpServlet {

	private static Function<Integer, Simulator> getSimulator;

	public void sendResponse(HttpServletRequest request, HttpServletResponse response) {
		final AsyncContext asyncContext = request.startAsync();
		final long currentMillis = System.currentTimeMillis();

		if (getSimulator == null) {
			sendResponse(response, asyncContext, buildResponseObject(currentMillis, ResponseCode.EXCEPTION));
		} else {
			int dimension = 0;
			try {
				dimension = Integer.parseInt(request.getParameter("dimension"));
			} catch (Exception ignored) {
			}

			final Simulator simulator = getSimulator.apply(dimension);
			if (simulator == null) {
				sendResponse(response, asyncContext, buildResponseObject(currentMillis, ResponseCode.INVALID, "Invalid dimension"));
			} else {
				final String endpoint;
				final String data;
				final String path = request.getPathInfo();
				if (path != null && !path.isEmpty()) {
					final String[] pathSplit = path.substring(1).split("\\.")[0].split("/");
					endpoint = pathSplit[0];
					data = pathSplit.length > 1 ? pathSplit[1] : "";
				} else {
					endpoint = "";
					data = "";
				}
				simulator.run(() -> sendResponse(response, asyncContext, getContent(endpoint, data, request, currentMillis, simulator)));
			}
		}
	}

	public static JsonObject buildResponseObject(long currentMillis, JsonObject dataObject, Object... parameters) {
		if (dataObject == null) {
			return buildResponseObject(currentMillis, ResponseCode.NOT_FOUND, parameters);
		} else {
			return buildResponseObject(currentMillis, dataObject, ResponseCode.SUCCESS);
		}
	}

	public static JsonObject buildResponseObject(long currentMillis, ResponseCode responseCode, Object... parameters) {
		return buildResponseObject(currentMillis, null, responseCode, parameters);
	}

	public abstract JsonObject getContent(String endpoint, String data, HttpServletRequest request, long currentMillis, Simulator simulator);

	private static void sendResponse(HttpServletResponse response, AsyncContext asyncContext, JsonObject responseObject) {
		final ByteBuffer contentByteBuffer = ByteBuffer.wrap(responseObject.toString().getBytes(StandardCharsets.UTF_8));
		response.addHeader("Access-Control-Allow-Origin", "*");
		response.addHeader("Content-Type", "application/json");

		try {
			final ServletOutputStream servletOutputStream = response.getOutputStream();
			servletOutputStream.setWriteListener(new WriteListener() {
				@Override
				public void onWritePossible() throws IOException {
					while (servletOutputStream.isReady()) {
						if (!contentByteBuffer.hasRemaining()) {
							response.setStatus(200);
							asyncContext.complete();
							return;
						}
						servletOutputStream.write(contentByteBuffer.get());
					}
				}

				@Override
				public void onError(Throwable t) {
					asyncContext.complete();
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static JsonObject buildResponseObject(long currentMillis, JsonObject dataObject, ResponseCode responseCode, Object... parameters) {
		final JsonObject responseObject = new JsonObject();
		responseObject.addProperty("code", responseCode.code);
		responseObject.addProperty("currentTime", currentMillis);
		if (dataObject != null) {
			responseObject.add("data", dataObject);
		}
		responseObject.addProperty("text", responseCode.getMessage(parameters));
		responseObject.addProperty("version", 2);
		return responseObject;
	}

	public static void setGetSimulator(Function<Integer, Simulator> function) {
		getSimulator = function;
	}
}
