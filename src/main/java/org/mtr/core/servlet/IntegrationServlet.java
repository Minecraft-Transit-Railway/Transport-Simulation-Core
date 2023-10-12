package org.mtr.core.servlet;

import org.mtr.core.data.EnumHelper;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.webserver.Webserver;

import java.util.Locale;

public class IntegrationServlet extends ServletBase {

	public IntegrationServlet(Webserver webserver, String path, ObjectImmutableList<Simulator> simulators) {
		super(webserver, path, simulators);
	}

	@Override
	public JsonObject getContent(String endpoint, String data, Object2ObjectAVLTreeMap<String, String> parameters, JsonObject bodyObject, long currentMillis, Simulator simulator) {
		final JsonObject returnObject = getInnerContent(endpoint, data, parameters, bodyObject, currentMillis, simulator);
		returnObject.add("metadata", bodyObject.get("metadata"));
		return returnObject;
	}

	private JsonObject getInnerContent(String endpoint, String data, Object2ObjectAVLTreeMap<String, String> parameters, JsonObject bodyObject, long currentMillis, Simulator simulator) {
		final IntegrationResponse integrationResponse = new IntegrationResponse(data, parameters, bodyObject, currentMillis, simulator);
		switch (EnumHelper.valueOf(Operation.UPDATE, endpoint.toUpperCase(Locale.ROOT))) {
			case UPDATE:
				return integrationResponse.update();
			case GET:
				return integrationResponse.get();
			case DELETE:
				return integrationResponse.delete();
			case GENERATE:
				return integrationResponse.generate();
			case CLEAR:
				return integrationResponse.clear();
			case LIST:
				return integrationResponse.list();
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
