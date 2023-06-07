package org.mtr.core.data;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.Position;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public class DataCache {

	public final Long2ObjectOpenHashMap<Station> stationIdMap = new Long2ObjectOpenHashMap<>();
	public final Long2ObjectOpenHashMap<Platform> platformIdMap = new Long2ObjectOpenHashMap<>();
	public final Long2ObjectOpenHashMap<Siding> sidingIdMap = new Long2ObjectOpenHashMap<>();
	public final Long2ObjectOpenHashMap<Route> routeIdMap = new Long2ObjectOpenHashMap<>();
	public final Long2ObjectOpenHashMap<Depot> depotIdMap = new Long2ObjectOpenHashMap<>();

	public final Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> positionToRailConnections = new Object2ObjectOpenHashMap<>();

	private final Simulator simulator;

	public DataCache(Simulator simulator) {
		this.simulator = simulator;
	}

	public final void sync() {
		try {
			mapIds(stationIdMap, simulator.stations);
			mapIds(platformIdMap, simulator.platforms);
			mapIds(sidingIdMap, simulator.sidings);
			mapIds(routeIdMap, simulator.routes);
			mapIds(depotIdMap, simulator.depots);

			mapAreasAndSavedRails(simulator.platforms, simulator.stations);
			mapAreasAndSavedRails(simulator.sidings, simulator.depots);

			// clear rail connections
			// write rail connections
			positionToRailConnections.clear();
			simulator.railNodes.forEach(railNode -> positionToRailConnections.put(railNode.getPosition(), railNode.getConnectionsAsMap()));

			// clear platform routes
			// clear platform route colors
			simulator.platforms.forEach(platform -> {
				platform.routes.clear();
				platform.routeColors.clear();
			});

			// clear route depots
			// write route platforms
			// write route platform routes
			// write route platform colors
			simulator.routes.forEach(route -> {
				route.depots.clear();
				route.getRoutePlatforms().forEach(routePlatformData -> routePlatformData.writePlatformCache(route, platformIdMap));
				route.getRoutePlatforms().removeIf(routePlatformData -> routePlatformData.platform == null);
			});

			// clear depot routes
			// write route depots
			// write depot routes
			// clear all platforms in route
			// write all platforms in route
			simulator.depots.forEach(depot -> depot.writeRouteCache(routeIdMap));

			// clear station connections
			// write station connections
			simulator.stations.forEach(station1 -> {
				station1.connectedStations.clear();
				simulator.stations.forEach(station2 -> {
					if (station1 != station2 && station1.intersecting(station2)) {
						station1.connectedStations.add(station2);
					}
				});
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static <T, U, V extends Map<T, U>, W extends Map<T, V>> U tryGet(W map, T key1, T key2, U defaultValue) {
		final U result = tryGet(map, key1, key2);
		return result == null ? defaultValue : result;
	}

	public static <T, U, V extends Map<T, U>, W extends Map<T, V>> U tryGet(W map, T key1, T key2) {
		final Map<T, U> innerMap = map.get(key1);
		if (innerMap == null) {
			return null;
		} else {
			return innerMap.get(key2);
		}
	}

	public static <T, U, V extends Map<T, W>, W extends Map<T, U>> void put(V map, T key1, T key2, Function<U, U> putValue, Supplier<W> innerMapSupplier) {
		final W innerMap = map.get(key1);
		final W newInnerMap;
		if (innerMap == null) {
			newInnerMap = innerMapSupplier.get();
			map.put(key1, newInnerMap);
		} else {
			newInnerMap = innerMap;
		}
		newInnerMap.put(key2, putValue.apply(newInnerMap.get(key2)));
	}

	protected static <U extends NameColorDataBase> void mapIds(Map<Long, U> map, Set<U> source) {
		map.clear();
		source.forEach(data -> map.put(data.getId(), data));
	}

	private static <U extends SavedRailBase<U, V>, V extends AreaBase<V, U>> void mapAreasAndSavedRails(ObjectAVLTreeSet<U> savedRails, ObjectAVLTreeSet<V> areas) {
		areas.forEach(area -> area.savedRails.clear());
		savedRails.forEach(savedRail -> {
			savedRail.area = null;
			final Position pos = savedRail.getMidPosition();
			for (final V area : areas) {
				if (area.isTransportMode(savedRail) && area.inArea(pos)) {
					savedRail.area = area;
					area.savedRails.add(savedRail);
					break;
				}
			}
		});
	}
}
