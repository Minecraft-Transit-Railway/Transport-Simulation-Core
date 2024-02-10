package org.mtr.core.servlet;

import org.mtr.core.Main;
import org.mtr.core.data.*;
import org.mtr.core.integration.Integration;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;

public final class SocketServlet extends ServletBase {

	public SocketServlet(ObjectImmutableList<Simulator> simulators) {
		super(simulators);
	}

	@Override
	protected JsonObject getContent(String endpoint, String data, Object2ObjectAVLTreeMap<String, String> parameters, JsonReader jsonReader, long currentMillis, Simulator simulator) {
		simulator.run(() -> {
			try {
				simulator.clientGroup.saveAndUpdate(jsonReader);

				final double updateRadius = simulator.clientGroup.getUpdateRadius();
				final JsonObject responseObject = new JsonObject();

				simulator.clientGroup.iterateClients(client -> {
					try {
						final ObjectArraySet<Station> stations = new ObjectArraySet<>();
						final ObjectArraySet<Platform> platforms = new ObjectArraySet<>();
						final LongAVLTreeSet platformIds = new LongAVLTreeSet();
						final ObjectArraySet<Depot> depots = new ObjectArraySet<>();
						final ObjectArraySet<Siding> sidings = new ObjectArraySet<>();
						final LongAVLTreeSet sidingIds = new LongAVLTreeSet();
						final ObjectArraySet<SimplifiedRoute> simplifiedRoutes = new ObjectArraySet<>();
						final ObjectArraySet<Rail> rails = new ObjectArraySet<>();

						findNearby(simulator.stations, simulator.platforms, client.getPosition(), updateRadius, stations, platforms, platformIds);
						findNearby(simulator.depots, simulator.sidings, client.getPosition(), updateRadius, depots, sidings, sidingIds);

						platforms.forEach(platform -> platform.routes.forEach(route -> SimplifiedRoute.addToSet(simplifiedRoutes, route)));

						simulator.rails.forEach(rail -> {
							if (rail.closeTo(client.getPosition(), updateRadius)) {
								rails.add(rail);
							}
						});

						// Outbound update packets (not the list operation) should contain simplified routes rather than the actual routes
						// Outbound update packets should also not include lifts because they are dynamically sent to the clients on lift tick
						final Integration integration = new Integration(simulator);
						integration.add(stations, platforms, sidings, null, depots, null, simplifiedRoutes);
						integration.add(rails, null);
						responseObject.add(client.uuid.toString(), Utilities.getJsonObjectFromData(integration));
					} catch (Exception e) {
						Main.logException(e);
					}
				});

				simulator.clientGroup.sendToClient(responseObject);
			} catch (Exception e) {
				Main.logException(e);
			}
		});

		return new JsonObject();
	}

	private static <T extends AreaBase<T, U>, U extends SavedRailBase<U, T>> void findNearby(ObjectAVLTreeSet<T> areaDataList, ObjectAVLTreeSet<U> savedRailDataList, Position position, double radius, ObjectArraySet<T> areas, ObjectArraySet<U> savedRails, LongAVLTreeSet savedRailIds) {
		areaDataList.forEach(area -> {
			if (area.inArea(position, radius)) {
				areas.add(area);
				area.savedRails.forEach(savedRail -> {
					savedRails.add(savedRail);
					savedRailIds.add(savedRail.getId());
				});
			}
		});

		savedRailDataList.forEach(savedRail -> {
			if (!savedRailIds.contains(savedRail.getId()) && savedRail.closeTo(position, radius)) {
				savedRails.add(savedRail);
				savedRailIds.add(savedRail.getId());
			}
		});
	}
}
