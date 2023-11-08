package org.mtr.core.servlet;

import org.mtr.core.operation.SetTime;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.webserver.Webserver;

public final class SetTimeServlet extends ServletBase<SetTime> {

	public SetTimeServlet(Webserver webserver, String path, ObjectImmutableList<Simulator> simulators) {
		super(webserver, path, SetTime::new, simulators);
	}

	@Override
	protected JsonObject getContent(String endpoint, String data, Object2ObjectAVLTreeMap<String, String> parameters, SetTime body, long currentMillis, Simulator simulator) {
		body.setGameTime(simulator);
		return new JsonObject();
	}
}
