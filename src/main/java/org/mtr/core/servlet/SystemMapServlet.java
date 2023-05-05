package org.mtr.core.servlet;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.core.simulation.Simulator;
import org.mtr.webserver.Webserver;

public class SystemMapServlet extends ServletBase {

	public SystemMapServlet(Webserver webserver, String path, ObjectImmutableList<Simulator> simulators) {
		super(webserver, path, simulators);
	}

	@Override
	public JsonObject getContent(String endpoint, String data, Object2ObjectAVLTreeMap<String, String> parameters, long currentMillis, Simulator simulator) {
		final SystemMapResponse systemMapResponse = new SystemMapResponse(data, parameters, currentMillis, simulator);
		switch (endpoint) {
			case "stations-and-routes":
				return systemMapResponse.getData();
			default:
				return null;
		}
	}
}
