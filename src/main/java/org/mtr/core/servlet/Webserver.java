package org.mtr.core.servlet;

import org.mtr.core.Main;
import org.mtr.libraries.org.eclipse.jetty.server.Connector;
import org.mtr.libraries.org.eclipse.jetty.server.Server;
import org.mtr.libraries.org.eclipse.jetty.server.ServerConnector;
import org.mtr.libraries.org.eclipse.jetty.servlet.ServletContextHandler;
import org.mtr.libraries.org.eclipse.jetty.servlet.ServletHolder;
import org.mtr.libraries.org.eclipse.jetty.util.thread.QueuedThreadPool;

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
			Main.LOGGER.error("", e);
		}
	}

	public void stop() {
		try {
			server.stop();
		} catch (Exception e) {
			Main.LOGGER.error("", e);
		}
		try {
			serverConnector.stop();
		} catch (Exception e) {
			Main.LOGGER.error("", e);
		}
	}
}
