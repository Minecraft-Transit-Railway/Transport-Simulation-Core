package org.mtr.core.servlet;

import lombok.extern.log4j.Log4j2;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

/**
 * Thin wrapper around an embedded Jetty {@link Server} that hosts the simulator's HTTP surface.
 *
 * <p>Created and owned by {@link org.mtr.core.Main}. Servlets are registered up-front via
 * {@link #addServlet(ServletHolder, String)}; once {@link #start()} is called the server begins
 * accepting requests and {@link #stop()} cleanly shuts both the {@link Server} and its
 * {@link ServerConnector} down. Start / stop failures are logged rather than propagated so a
 * webserver outage never crashes the surrounding simulator.</p>
 */
@Log4j2
public final class Webserver {

	private final Server server;
	private final ServerConnector serverConnector;
	private final ServletContextHandler servletContextHandler;

	/**
	 * Maximum size of the Jetty thread pool — chosen to comfortably outpace dashboard polling.
	 */
	private static final int MAX_THREADS = 100;
	/**
	 * Minimum (always-running) size of the Jetty thread pool.
	 */
	private static final int MIN_THREADS = 10;
	/**
	 * Idle worker timeout in milliseconds before the pool reaps a thread.
	 */
	private static final int IDLE_TIMEOUT_MILLIS = 120;

	/**
	 * Build a webserver bound to {@code port}. The server is not started until {@link #start()}.
	 *
	 * @param port TCP port to listen on
	 */
	public Webserver(int port) {
		server = new Server(new QueuedThreadPool(MAX_THREADS, MIN_THREADS, IDLE_TIMEOUT_MILLIS));
		serverConnector = new ServerConnector(server);
		server.setConnectors(new Connector[]{serverConnector});
		servletContextHandler = new ServletContextHandler();
		server.setHandler(servletContextHandler);
		serverConnector.setPort(port);
	}

	/**
	 * Register {@code servletHolder} under {@code path}. Must be called before {@link #start()}.
	 *
	 * @param servletHolder Jetty wrapper around the servlet instance
	 * @param path          URL pattern (e.g. {@code "/mtr/api/map/*"})
	 */
	public void addServlet(ServletHolder servletHolder, String path) {
		servletContextHandler.addServlet(servletHolder, path);
	}

	/**
	 * Start accepting HTTP requests. Errors are logged; the simulator keeps running.
	 */
	public void start() {
		try {
			server.start();
		} catch (Exception e) {
			log.error("Failed to start webserver", e);
		}
	}

	/**
	 * Stop the Jetty {@link Server} and its connector. Each step is independently guarded so a
	 * failure in one does not leave the other half-running.
	 */
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
