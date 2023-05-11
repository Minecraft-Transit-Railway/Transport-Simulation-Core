package org.mtr.core.data;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.Position;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public class DataCache {

	private long lastRefreshedTime;

	public final Long2ObjectOpenHashMap<Station> stationIdMap = new Long2ObjectOpenHashMap<>();
	public final Long2ObjectOpenHashMap<Platform> platformIdMap = new Long2ObjectOpenHashMap<>();
	public final Long2ObjectOpenHashMap<Siding> sidingIdMap = new Long2ObjectOpenHashMap<>();
	public final Long2ObjectOpenHashMap<Route> routeIdMap = new Long2ObjectOpenHashMap<>();
	public final Long2ObjectOpenHashMap<Depot> depotIdMap = new Long2ObjectOpenHashMap<>();

	public final Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> positionToRailConnections = new Object2ObjectOpenHashMap<>();
	public final Long2ObjectOpenHashMap<Depot> routeIdToOneDepot = new Long2ObjectOpenHashMap<>();
	public final Object2ObjectOpenHashMap<Station, ObjectAVLTreeSet<Station>> stationIdToConnectingStations = new Object2ObjectOpenHashMap<>();
	public final Long2ObjectOpenHashMap<IntArraySet> platformIdToRouteColors = new Long2ObjectOpenHashMap<>();
	public final Int2ObjectOpenHashMap<Route> routeColorMap = new Int2ObjectOpenHashMap<>();
	public final Long2ObjectOpenHashMap<ObjectArraySet<Siding>> platformIdToSidings = new Long2ObjectOpenHashMap<>();

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

			positionToRailConnections.clear();
			simulator.railNodes.forEach(railNode -> positionToRailConnections.put(railNode.position, railNode.connections));

			routeIdToOneDepot.clear();
			platformIdToRouteColors.clear();
			routeColorMap.clear();
			simulator.routes.forEach(route -> {
				route.platformIds.removeIf(platformId -> !platformIdMap.containsKey(platformId.platformId));
				route.platformIds.forEach(platformId -> {
					if (!platformIdToRouteColors.containsKey(platformId.platformId)) {
						platformIdToRouteColors.put(platformId.platformId, new IntArraySet());
					}
					platformIdToRouteColors.get(platformId.platformId).add(route.color);
				});
				routeColorMap.put(route.color, route);
			});
			simulator.depots.forEach(depot -> {
				depot.routeIds.removeIf(routeId -> routeIdMap.get(routeId) == null);
				depot.routeIds.forEach(routeId -> routeIdToOneDepot.put(routeId, depot));
			});

			stationIdToConnectingStations.clear();
			simulator.stations.forEach(station1 -> {
				stationIdToConnectingStations.put(station1, new ObjectAVLTreeSet<>());
				simulator.stations.forEach(station2 -> {
					if (station1 != station2 && station1.intersecting(station2)) {
						stationIdToConnectingStations.get(station1).add(station2);
					}
				});
			});

			simulator.depots.forEach(depot -> depot.iterateRoutes((route, routeIndex) -> route.platformIds.forEach(platformId -> {
				if (!platformIdToSidings.containsKey(platformId.platformId)) {
					platformIdToSidings.put(platformId.platformId, new ObjectArraySet<>());
				}
				platformIdToSidings.get(platformId.platformId).addAll(depot.savedRails);
			})));
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
