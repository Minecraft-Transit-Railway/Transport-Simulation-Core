package org.mtr.core.servlet;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.mtr.core.Main;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonObject;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public final class Webserver {

	private final Server server;
	private final ServerConnector serverConnector;
	private final ServletContextHandler servletContextHandler;

	public Webserver(int port) {
		server = new Server(new QueuedThreadPool(100, 10, 120));
		serverConnector = new ServerConnector(server);
		server.setConnectors(new Connector[]{serverConnector});
		servletContextHandler = new ServletContextHandler();
		server.setHandler(servletContextHandler);
		serverConnector.setPort(port);
	}

	public void addServlet(ServletHolder servletHolder, String path) {
		servletContextHandler.addServlet(servletHolder, path);
	}

	public void start() {
		try {
			server.start();
		} catch (Exception e) {
			Main.logException(e);
		}
	}

	public void stop() {
		try {
			server.stop();
		} catch (Exception e) {
			Main.logException(e);
		}
		try {
			serverConnector.stop();
		} catch (Exception e) {
			Main.logException(e);
		}
	}

	public static void sendPostRequest(String url, JsonObject contentObject, @Nullable Consumer<JsonObject> consumer) {
		try {
			final String content = contentObject.toString();
			final HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
			connection.setRequestMethod("POST");
			connection.setRequestProperty("content-type", "application/json");
			connection.setRequestProperty("content-length", String.valueOf(content.length()));
			connection.setDoOutput(true);

			try (final OutputStream dataOutputStream = connection.getOutputStream()) {
				dataOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
				dataOutputStream.flush();
			}

			if (consumer == null) {
				connection.getResponseCode();
			} else {
				try (final InputStream inputStream = connection.getInputStream()) {
					consumer.accept(Utilities.parseJson(IOUtils.toString(inputStream, StandardCharsets.UTF_8)));
				}
			}

			connection.disconnect();
		} catch (Exception e) {
			Main.logException(e);
		}
	}
}
