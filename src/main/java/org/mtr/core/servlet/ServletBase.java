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
		final JsonObject responseObject;

		if (getSimulator == null) {
			responseObject = buildResponseObject(ResponseCode.EXCEPTION);
		} else {
			int dimension = 0;
			try {
				dimension = Integer.parseInt(request.getParameter("dimension"));
			} catch (Exception ignored) {
			}
			final Simulator simulator = getSimulator.apply(dimension);
			responseObject = simulator == null ? buildResponseObject(ResponseCode.INVALID, "Invalid dimension") : getContent(request, simulator);
		}

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

	public JsonObject buildResponseObject(JsonObject dataObject) {
		return buildResponseObject(dataObject, ResponseCode.SUCCESS);
	}

	public JsonObject buildResponseObject(ResponseCode responseCode, Object... parameters) {
		return buildResponseObject(null, responseCode, parameters);
	}

	public abstract JsonObject getContent(HttpServletRequest request, Simulator simulator);

	private JsonObject buildResponseObject(JsonObject dataObject, ResponseCode responseCode, Object... parameters) {
		final JsonObject responseObject = new JsonObject();
		responseObject.addProperty("code", responseCode.code);
		responseObject.addProperty("currentTime", System.currentTimeMillis());
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
