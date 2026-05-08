package org.mtr.core.servlet;

import lombok.extern.log4j.Log4j2;
import org.mtr.libraries.org.eclipse.jetty.server.Connector;
import org.mtr.libraries.org.eclipse.jetty.server.Server;
import org.mtr.libraries.org.eclipse.jetty.server.ServerConnector;
import org.mtr.libraries.org.eclipse.jetty.servlet.ServletContextHandler;
import org.mtr.libraries.org.eclipse.jetty.servlet.ServletHolder;
import org.mtr.libraries.org.eclipse.jetty.util.thread.QueuedThreadPool;

@Log4j2
public final class Webserver {

	private final Server server;
	private final ServerConnector serverConnector;
	private final ServletContextHandler servletContextHandler;

	private static final int MAX_THREADS = 100;
	private static final int MIN_THREADS = 10;
	private static final int IDLE_TIMEOUT_MILLIS = 120;

	public Webserver(int port) {
		server = new Server(new QueuedThreadPool(MAX_THREADS, MIN_THREADS, IDLE_TIMEOUT_MILLIS));
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
			log.error("Failed to start webserver", e);
		}
	}

	public void stop() {
		try {
			server.stop();
		} catch (Exception e) {
			log.error("Failed to stop webserver", e);
		}
		try {
			serverConnector.stop();
		} catch (Exception e) {
			log.error("Failed to stop server connector", e);
		}
	}
}
