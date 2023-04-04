package org.mtr.core.data;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.Position;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class DataCache {

	private long lastRefreshedTime;

	public final Long2ObjectOpenHashMap<Station> stationIdMap = new Long2ObjectOpenHashMap<>();
	public final Long2ObjectOpenHashMap<Platform> platformIdMap = new Long2ObjectOpenHashMap<>();
	public final Long2ObjectOpenHashMap<Siding> sidingIdMap = new Long2ObjectOpenHashMap<>();
	public final Long2ObjectOpenHashMap<Route> routeIdMap = new Long2ObjectOpenHashMap<>();
	public final Long2ObjectOpenHashMap<Depot> depotIdMap = new Long2ObjectOpenHashMap<>();
	public final Long2ObjectOpenHashMap<Lift> liftsIdMap = new Long2ObjectOpenHashMap<>();

	public final Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> positionToRailConnections = new Object2ObjectOpenHashMap<>();
	public final Long2ObjectOpenHashMap<Depot> routeIdToOneDepot = new Long2ObjectOpenHashMap<>();
	public final Map<Station, Set<Station>> stationIdToConnectingStations = new HashMap<>();

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
			mapIds(liftsIdMap, simulator.lifts);

			positionToRailConnections.clear();
			simulator.railNodes.forEach(railNode -> positionToRailConnections.put(railNode.position, railNode.connections));

			routeIdToOneDepot.clear();
			simulator.routes.forEach(route -> route.platformIds.removeIf(platformId -> !platformIdMap.containsKey(platformId.platformId)));
			simulator.depots.forEach(depot -> {
				depot.routeIds.removeIf(routeId -> routeIdMap.get(routeId) == null);
				depot.routeIds.forEach(routeId -> routeIdToOneDepot.put(routeId, depot));
			});

			stationIdToConnectingStations.clear();
			simulator.stations.forEach(station1 -> {
				stationIdToConnectingStations.put(station1, new HashSet<>());
				simulator.stations.forEach(station2 -> {
					if (station1 != station2 && station1.intersecting(station2)) {
						stationIdToConnectingStations.get(station1).add(station2);
					}
				});
			});

			mapAreasAndSavedRails(simulator.platforms, simulator.stations);
			mapAreasAndSavedRails(simulator.sidings, simulator.depots);
		} catch (Exception e) {
			e.printStackTrace();
		}

		lastRefreshedTime = System.currentTimeMillis();
	}

	public boolean needsRefresh(long cachedRefreshTime) {
		return lastRefreshedTime > cachedRefreshTime;
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

	public static <U> void put(Long2ObjectOpenHashMap<Long2ObjectOpenHashMap<U>> map, long key1, long key2, Function<U, U> putValue) {
		final Long2ObjectOpenHashMap<U> innerMap = map.get(key1);
		final Long2ObjectOpenHashMap<U> newInnerMap;
		if (innerMap == null) {
			newInnerMap = new Long2ObjectOpenHashMap<>();
			map.put(key1, newInnerMap);
		} else {
			newInnerMap = innerMap;
		}
		newInnerMap.put(key2, putValue.apply(newInnerMap.get(key2)));
	}

	protected static <U extends NameColorDataBase> void mapIds(Map<Long, U> map, Set<U> source) {
		map.clear();
		source.forEach(data -> map.put(data.id, data));
	}

	private static <U extends SavedRailBase<U, V>, V extends AreaBase<V, U>> void mapAreasAndSavedRails(ObjectAVLTreeSet<U> savedRails, ObjectAVLTreeSet<V> areas) {
		areas.forEach(area -> area.savedRails.clear());
		savedRails.forEach(savedRail -> {
			savedRail.area = null;
			final Position pos = savedRail.getMidPosition();
			for (final V area : areas) {
				if (area.isTransportMode(savedRail.transportMode) && area.inArea(pos.x, pos.z)) {
					savedRail.area = area;
					area.savedRails.add(savedRail);
					break;
				}
			}
		});
	}
}
