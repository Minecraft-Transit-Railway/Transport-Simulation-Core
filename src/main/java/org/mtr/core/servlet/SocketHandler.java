package org.mtr.core.servlet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.core.Main;
import org.mtr.core.data.*;
import org.mtr.core.serializers.JsonReader;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;
import org.mtr.webserver.Webserver;

public class SocketHandler {

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
							final JsonObject clientObject = new JsonObject();
							final ObjectArrayList<Station> stations = new ObjectArrayList<>();
							final ObjectArrayList<Platform> platforms = new ObjectArrayList<>();
							final LongAVLTreeSet platformIds = new LongAVLTreeSet();
							final ObjectArrayList<Depot> depots = new ObjectArrayList<>();
							final ObjectArrayList<Siding> sidings = new ObjectArrayList<>();
							final LongAVLTreeSet sidingIds = new LongAVLTreeSet();
							final ObjectArrayList<Route> routes = new ObjectArrayList<>();
							final ObjectArrayList<Rail> rails = new ObjectArrayList<>();

							findNearby(simulator.stations, simulator.platforms, client.getPosition(), updateRadius, stations, platforms, platformIds);
							findNearby(simulator.depots, simulator.sidings, client.getPosition(), updateRadius, depots, sidings, sidingIds);

							platforms.forEach(platform -> routes.addAll(platform.routes));

							simulator.rails.forEach(rail -> {
								if (rail.closeTo(client.getPosition(), updateRadius)) {
									rails.add(rail);
								}
							});

							addDataSetToJsonObject(stations, clientObject, "stations");
							addDataSetToJsonObject(platforms, clientObject, "platforms");
							addDataSetToJsonObject(sidings, clientObject, "sidings");
							addDataSetToJsonObject(routes, clientObject, "routes");
							addDataSetToJsonObject(depots, clientObject, "depots");
							addDataSetToJsonObject(rails, clientObject, "rails");

							responseObject.add(client.uuid.toString(), clientObject);
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

	private static <T extends AreaBase<T, U>, U extends SavedRailBase<U, T>> void findNearby(ObjectAVLTreeSet<T> areaDataList, ObjectAVLTreeSet<U> savedRailDataList, Position position, double radius, ObjectArrayList<T> areas, ObjectArrayList<U> savedRails, LongAVLTreeSet savedRailIds) {
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

	private static <T extends SerializedDataBase> void addDataSetToJsonObject(ObjectArrayList<T> dataSet, JsonObject jsonObject, String key) {
		final JsonArray jsonArray = new JsonArray();
		dataSet.forEach(data -> jsonArray.add(Utilities.getJsonObjectFromData(data)));
		jsonObject.add(key, jsonArray);
	}
}
