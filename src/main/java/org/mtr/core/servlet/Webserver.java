package org.mtr.core.servlet;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.mtr.core.Main;

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
}
