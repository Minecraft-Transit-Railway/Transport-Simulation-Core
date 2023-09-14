package org.mtr.core.data;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashBigSet;
import org.mtr.core.tools.Position;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public class Data {

	public final ObjectAVLTreeSet<Station> stations = new ObjectAVLTreeSet<>();
	public final ObjectAVLTreeSet<Platform> platforms = new ObjectAVLTreeSet<>();
	public final ObjectAVLTreeSet<Siding> sidings = new ObjectAVLTreeSet<>();
	public final ObjectAVLTreeSet<Route> routes = new ObjectAVLTreeSet<>();
	public final ObjectAVLTreeSet<Depot> depots = new ObjectAVLTreeSet<>();
	public final ObjectAVLTreeSet<Lift> lifts = new ObjectAVLTreeSet<>();
	public final ObjectOpenHashBigSet<RailNode> railNodes = new ObjectOpenHashBigSet<>();

	public final Long2ObjectOpenHashMap<Station> stationIdMap = new Long2ObjectOpenHashMap<>();
	public final Long2ObjectOpenHashMap<Platform> platformIdMap = new Long2ObjectOpenHashMap<>();
	public final Long2ObjectOpenHashMap<Siding> sidingIdMap = new Long2ObjectOpenHashMap<>();
	public final Long2ObjectOpenHashMap<Route> routeIdMap = new Long2ObjectOpenHashMap<>();
	public final Long2ObjectOpenHashMap<Depot> depotIdMap = new Long2ObjectOpenHashMap<>();

	public final Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> positionToRailConnections = new Object2ObjectOpenHashMap<>();
	public final Object2ObjectOpenHashMap<Position, ObjectOpenHashBigSet<RailNode>> nodesConnectedToPosition = new Object2ObjectOpenHashMap<>();

	public void sync() {
		try {
			// clear rail connections
			// write rail connections
			positionToRailConnections.clear();
			nodesConnectedToPosition.clear();
			railNodes.forEach(railNode -> {
				final Object2ObjectOpenHashMap<Position, Rail> connections = railNode.getConnectionsAsMap();
				positionToRailConnections.put(railNode.getPosition(), connections);
				connections.forEach((position, rail) -> put(nodesConnectedToPosition, position, railNode, ObjectOpenHashBigSet::new));
			});

			platforms.removeIf(platform -> platform.isInvalidSavedRail(this));
			sidings.removeIf(siding -> siding.isInvalidSavedRail(this));

			mapIds(stationIdMap, stations);
			mapIds(platformIdMap, platforms);
			mapIds(sidingIdMap, sidings);
			mapIds(routeIdMap, routes);
			mapIds(depotIdMap, depots);

			mapAreasAndSavedRails(platforms, stations);
			mapAreasAndSavedRails(sidings, depots);

			// clear platform routes
			// clear platform route colors
			platforms.forEach(platform -> {
				platform.routes.clear();
				platform.routeColors.clear();
			});

			// clear route depots
			// write route platforms
			// write route platform routes
			// write route platform colors
			routes.forEach(route -> {
				route.depots.clear();
				route.getRoutePlatforms().forEach(routePlatformData -> routePlatformData.writePlatformCache(route, platformIdMap));
				route.getRoutePlatforms().removeIf(routePlatformData -> routePlatformData.platform == null);
			});

			// clear depot routes
			// write route depots
			// write depot routes
			// clear all platforms in route
			// write all platforms in route
			depots.forEach(depot -> depot.writeRouteCache(routeIdMap));

			// clear station connections
			// write station connections
			stations.forEach(station1 -> {
				station1.connectedStations.clear();
				stations.forEach(station2 -> {
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

	public static <T, U, V extends Map<T, W>, W extends Collection<U>> void put(V map, T key, U newValue, Supplier<W> innerSetSupplier) {
		final W innerSet = map.get(key);
		final W newInnerSet;
		if (innerSet == null) {
			newInnerSet = innerSetSupplier.get();
			map.put(key, newInnerSet);
		} else {
			newInnerSet = innerSet;
		}
		newInnerSet.add(newValue);
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

	private static <U extends NameColorDataBase> void mapIds(Map<Long, U> map, Set<U> source) {
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
