package org.mtr.core.data;

import org.mtr.core.Main;
import org.mtr.core.serializer.SerializedDataBaseWithId;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.*;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class Data {

	public final ObjectAVLTreeSet<Station> stations = new ObjectAVLTreeSet<>();
	public final ObjectAVLTreeSet<Platform> platforms = new ObjectAVLTreeSet<>();
	public final ObjectAVLTreeSet<Siding> sidings = new ObjectAVLTreeSet<>();
	public final ObjectAVLTreeSet<Route> routes = new ObjectAVLTreeSet<>();
	public final ObjectAVLTreeSet<Depot> depots = new ObjectAVLTreeSet<>();
	public final ObjectAVLTreeSet<Lift> lifts = new ObjectAVLTreeSet<>();
	public final ObjectOpenHashBigSet<Rail> rails = new ObjectOpenHashBigSet<>();

	public final Long2ObjectOpenHashMap<Station> stationIdMap = new Long2ObjectOpenHashMap<>();
	public final Long2ObjectOpenHashMap<Platform> platformIdMap = new Long2ObjectOpenHashMap<>();
	public final Long2ObjectOpenHashMap<Siding> sidingIdMap = new Long2ObjectOpenHashMap<>();
	public final Long2ObjectOpenHashMap<Route> routeIdMap = new Long2ObjectOpenHashMap<>();
	public final Long2ObjectOpenHashMap<Depot> depotIdMap = new Long2ObjectOpenHashMap<>();
	public final Long2ObjectOpenHashMap<Lift> liftIdMap = new Long2ObjectOpenHashMap<>();
	public final Object2ObjectOpenHashMap<String, Rail> railIdMap = new Object2ObjectOpenHashMap<>();

	public final Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> positionsToRail = new Object2ObjectOpenHashMap<>();

	public void sync() {
		try {
			// clear rail connections
			// write rail connections
			positionsToRail.clear();
			rails.forEach(rail -> rail.writePositionsToRailCache(positionsToRail));
			rails.forEach(rail -> rail.writeConnectedRailsCacheFromMap(positionsToRail));

			if (this instanceof Simulator) {
				platforms.removeIf(platform -> platform.isInvalidSavedRail(this));
				sidings.removeIf(siding -> siding.isInvalidSavedRail(this));
			}

			mapIds(stationIdMap, stations);
			mapIds(platformIdMap, platforms);
			mapIds(sidingIdMap, sidings);
			mapIds(routeIdMap, routes);
			mapIds(depotIdMap, depots);
			mapIds(liftIdMap, lifts);
			mapIds(railIdMap, rails);

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
			// write path data cache
			depots.forEach(depot -> {
				depot.writeRouteCache(routeIdMap);
				depot.writePathCache(false);
			});

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
			Main.logException(e);
		}
	}

	public static <T, U, V, W extends Map<T, X>, X extends Map<U, V>> V tryGet(W map, T key1, U key2, V defaultValue) {
		final V result = tryGet(map, key1, key2);
		return result == null ? defaultValue : result;
	}

	public static <T, U, V, W extends Map<T, X>, X extends Map<U, V>> V tryGet(W map, T key1, U key2) {
		final Map<U, V> innerMap = map.get(key1);
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

	public static <T, U, V extends Map<T, W>, W extends Collection<U>, X extends Collection<U>> void put(V map, T key, X newValue, Supplier<W> innerSetSupplier) {
		final W innerSet = map.get(key);
		final W newInnerSet;
		if (innerSet == null) {
			newInnerSet = innerSetSupplier.get();
			map.put(key, newInnerSet);
		} else {
			newInnerSet = innerSet;
		}
		newInnerSet.addAll(newValue);
	}

	public static <T, U, V, W extends Map<T, X>, X extends Map<U, V>> void put(W map, T key1, U key2, Function<V, V> putValue, Supplier<X> innerMapSupplier) {
		final X innerMap = map.get(key1);
		final X newInnerMap;
		if (innerMap == null) {
			newInnerMap = innerMapSupplier.get();
			map.put(key1, newInnerMap);
		} else {
			newInnerMap = innerMap;
		}
		newInnerMap.put(key2, putValue.apply(newInnerMap.get(key2)));
	}

	private static <U extends NameColorDataBase> void mapIds(Long2ObjectMap<U> map, ObjectSet<U> source) {
		map.clear();
		source.forEach(data -> map.put(data.getId(), data));
	}

	private static <U extends SerializedDataBaseWithId> void mapIds(Object2ObjectMap<String, U> map, ObjectSet<U> source) {
		map.clear();
		source.forEach(data -> map.put(data.getHexId(), data));
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
