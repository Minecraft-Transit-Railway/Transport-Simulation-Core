package org.mtr.core.data;

import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.core.generated.StationSchema;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.tools.DataFixer;

import java.util.Collections;
import java.util.function.Function;

public final class Station extends StationSchema {

	public final ObjectAVLTreeSet<Station> connectedStations = new ObjectAVLTreeSet<>();

	public Station(Data data) {
		super(TransportMode.values()[0], data);
	}

	public Station(ReaderBase readerBase, Data data) {
		super(DataFixer.convertStation(readerBase), data);
		updateData(readerBase);
	}

	public long getZone1() {
		return zone1;
	}

	public long getZone2() {
		return zone2;
	}

	public long getZone3() {
		return zone3;
	}

	public void setZone1(long zone1) {
		this.zone1 = zone1;
	}

	public void setZone2(long zone2) {
		this.zone2 = zone2;
	}

	public void setZone3(long zone3) {
		this.zone3 = zone3;
	}

	public Object2ObjectAVLTreeMap<Station, Int2ObjectAVLTreeMap<ObjectArrayList<Route>>> getInterchangeStationToColorToRoutesMap(boolean includeConnectingStations) {
		final Object2ObjectAVLTreeMap<Station, Int2ObjectAVLTreeMap<ObjectArrayList<Route>>> stationToColorToRoutesMap = new Object2ObjectAVLTreeMap<>();
		getInterchangeRoutes(includeConnectingStations, stationToColorToRoutesMap, null, station -> station, route -> route);
		return stationToColorToRoutesMap;
	}

	public Object2ObjectAVLTreeMap<String, Int2ObjectAVLTreeMap<ObjectArrayList<String>>> getInterchangeStationNameToColorToRouteNamesMap(boolean includeConnectingStations) {
		final Object2ObjectAVLTreeMap<String, Int2ObjectAVLTreeMap<ObjectArrayList<String>>> stationToColorToRoutesMap = new Object2ObjectAVLTreeMap<>();
		getInterchangeRoutes(includeConnectingStations, stationToColorToRoutesMap, null, Station::getName, route -> String.format("%s||%s", route.getName().split("\\|\\|")[0], route.getRouteNumber()));
		return stationToColorToRoutesMap;
	}

	public ObjectAVLTreeSet<Route> getOneInterchangeRouteFromEachColor(boolean includeConnectingStations) {
		final ObjectAVLTreeSet<Route> oneRouteFromEachColor = new ObjectAVLTreeSet<>();
		getInterchangeRoutes(includeConnectingStations, new Object2ObjectAVLTreeMap<>(), oneRouteFromEachColor, station -> station, route -> route);
		return oneRouteFromEachColor;
	}

	private <T, U extends Comparable<? super U>> void getInterchangeRoutes(boolean includeConnectingStations, Object2ObjectAVLTreeMap<T, Int2ObjectAVLTreeMap<ObjectArrayList<U>>> stationToColorToRoutesMap, ObjectAVLTreeSet<U> oneRouteFromEachColor, Function<Station, T> stationMapper, Function<Route, U> routeMapper) {
		final ObjectArrayList<Station> stations = new ObjectArrayList<>();
		if (includeConnectingStations) {
			stations.addAll(connectedStations);
			Collections.sort(stations);
		}

		stations.add(0, this);
		stations.forEach(station -> {
			final Int2ObjectAVLTreeMap<ObjectArrayList<U>> colorToRouteMap = new Int2ObjectAVLTreeMap<>();
			station.savedRails.forEach(platform -> platform.routes.forEach(route -> {
				colorToRouteMap.computeIfAbsent(route.getColor(), routes -> new ObjectArrayList<>());
				final U newRoute = routeMapper.apply(route);
				final ObjectArrayList<U> newRoutes = colorToRouteMap.get(route.getColor());
				if (!newRoutes.contains(newRoute)) {
					newRoutes.add(routeMapper.apply(route));
				}
			}));

			if (!colorToRouteMap.isEmpty()) {
				colorToRouteMap.forEach((color, routes) -> {
					Collections.sort(routes);
					if (oneRouteFromEachColor != null) {
						oneRouteFromEachColor.add(routes.get(0));
					}
				});
				stationToColorToRoutesMap.put(stationMapper.apply(station), colorToRouteMap);
			}
		});
	}
}
