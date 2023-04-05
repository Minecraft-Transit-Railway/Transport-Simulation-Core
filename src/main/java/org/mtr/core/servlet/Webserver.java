package org.mtr.core.servlet;

import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.mtr.core.Main;
import org.mtr.core.simulation.Simulator;

import java.net.URL;

public final class Webserver {

	private final ObjectImmutableList<Simulator> simulators;
	private final Server server;
	private final ServerConnector serverConnector;

	public Webserver(int port, ObjectImmutableList<Simulator> simulators) {
		this.simulators = simulators;
		server = new Server(new QueuedThreadPool(100, 10, 120));
		serverConnector = new ServerConnector(server);
		server.setConnectors(new Connector[]{serverConnector});
		serverConnector.setPort(port);
		final ServletContextHandler servletContextHandler = new ServletContextHandler();
		server.setHandler(servletContextHandler);

		final URL url = Main.class.getResource("/assets/website/");
		if (url != null) {
			try {
				servletContextHandler.setBaseResource(Resource.newResource(url.toURI()));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		OBAServlet.setGetSimulator(this::getSimulator);
		servletContextHandler.addServlet(OBAServlet.class, "/oba/api/where/*");

		final ServletHolder servletHolder = new ServletHolder("default", DefaultServlet.class);
		servletHolder.setInitParameter("dirAllowed", "true");
		servletHolder.setInitParameter("cacheControl", "max-age=0,public");
		servletContextHandler.addServlet(servletHolder, "/");

		try {
			server.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void stop() {
		try {
			server.stop();
			serverConnector.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private Simulator getSimulator(int index) {
		if (index >= 0 && index < simulators.size()) {
			return simulators.get(index);
		} else {
			return null;
		}
	}
}
