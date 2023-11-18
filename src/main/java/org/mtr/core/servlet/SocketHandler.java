package org.mtr.core.servlet;

import org.mtr.core.Main;
import org.mtr.core.data.*;
import org.mtr.core.integration.Integration;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.webserver.Webserver;

public final class SocketHandler {

	private static final String CHANNEL = "update";

	public static void register(Webserver webserver, ObjectImmutableList<Simulator> simulators) {
		webserver.addSocketListener(CHANNEL, (socketIOClient, id, jsonObject) -> {
			final Simulator simulator = simulators.get(jsonObject.get("dimension").getAsInt());
			simulator.run(() -> {
				try {
					simulator.clientGroup.updateData(new JsonReader(jsonObject));
					simulator.clientGroup.setSendToClient(webserver, socketIOClient, CHANNEL);
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
							final Integration integration = new Integration();
							integration.add(stations, platforms, sidings, null, depots, simplifiedRoutes);
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
		});
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
