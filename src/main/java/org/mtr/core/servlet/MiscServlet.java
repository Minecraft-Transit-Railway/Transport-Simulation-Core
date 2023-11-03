package org.mtr.core.servlet;

import org.mtr.core.Main;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.webserver.Webserver;

import javax.annotation.Nullable;

public class MiscServlet extends ServletBase {

	public MiscServlet(Webserver webserver, String path, ObjectImmutableList<Simulator> simulators) {
		super(webserver, path, simulators);
	}

	@Nullable
	@Override
	protected JsonObject getContent(String endpoint, String data, Object2ObjectAVLTreeMap<String, String> parameters, JsonObject bodyObject, long currentMillis, Simulator simulator) {
		switch (endpoint) {
			case "set-time":
				try {
					simulator.setGameTime(bodyObject.get("gameMillis").getAsLong(), bodyObject.get("millisPerDay").getAsLong());
				} catch (Exception e) {
					Main.logException(e);
				}
				return new JsonObject();
			default:
				return null;
		}
	}
}
