package org.mtr.core.servlet;

import org.mtr.core.integration.Integration;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.EnumHelper;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.webserver.Webserver;

import java.util.Locale;

public final class IntegrationServlet extends ServletBase<Integration> {

	public IntegrationServlet(Webserver webserver, String path, ObjectImmutableList<Simulator> simulators) {
		super(webserver, path, Integration::new, simulators);
	}

	@Override
	public JsonObject getContent(String endpoint, String data, Object2ObjectAVLTreeMap<String, String> parameters, Integration body, long currentMillis, Simulator simulator) {
		final IntegrationResponse integrationResponse = new IntegrationResponse(data, parameters, body, currentMillis, simulator);
		switch (EnumHelper.valueOf(Operation.UPDATE, endpoint.toUpperCase(Locale.ROOT))) {
			case UPDATE:
				return Utilities.getJsonObjectFromData(integrationResponse.update());
			case GET:
				return Utilities.getJsonObjectFromData(integrationResponse.get());
			case DELETE:
				return Utilities.getJsonObjectFromData(integrationResponse.delete());
			case GENERATE:
				return Utilities.getJsonObjectFromData(integrationResponse.generate());
			case CLEAR:
				return Utilities.getJsonObjectFromData(integrationResponse.clear());
			case LIST:
				return Utilities.getJsonObjectFromData(integrationResponse.list());
			default:
				return new JsonObject();
		}
	}

	public enum Operation {
		UPDATE, GET, DELETE, GENERATE, CLEAR, LIST;

		public String getEndpoint() {
			return toString().toLowerCase(Locale.ENGLISH);
		}
	}
}
