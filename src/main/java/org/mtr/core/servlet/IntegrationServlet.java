package org.mtr.core.servlet;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.core.simulation.Simulator;
import org.mtr.webserver.Webserver;

public class IntegrationServlet extends ServletBase {

	public IntegrationServlet(Webserver webserver, String path, ObjectImmutableList<Simulator> simulators) {
		super(webserver, path, simulators);
	}

	@Override
	public JsonObject getContent(String endpoint, String data, Object2ObjectAVLTreeMap<String, String> parameters, JsonObject bodyObject, long currentMillis, Simulator simulator) {
		final IntegrationResponse integrationResponse = new IntegrationResponse(data, parameters, bodyObject, currentMillis, simulator);
		switch (endpoint) {
			case "update":
				return integrationResponse.update();
			default:
				return null;
		}
	}
}
