package org.mtr.core.servlet;

import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.webserver.Webserver;

import javax.annotation.Nullable;

public class SystemMapServlet extends ServletBase {

	public SystemMapServlet(Webserver webserver, String path, ObjectImmutableList<Simulator> simulators) {
		super(webserver, path, simulators);
	}

	@Nullable
	@Override
	public JsonObject getContent(String endpoint, String data, Object2ObjectAVLTreeMap<String, String> parameters, JsonObject bodyObject, long currentMillis, Simulator simulator) {
		final SystemMapResponse systemMapResponse = new SystemMapResponse(data, parameters, bodyObject, currentMillis, simulator);
		switch (endpoint) {
			case "stations-and-routes":
				return systemMapResponse.getData();
			default:
				return null;
		}
	}
}
