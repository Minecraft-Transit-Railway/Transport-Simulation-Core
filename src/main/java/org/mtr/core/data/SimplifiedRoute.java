package org.mtr.core.data;

import org.mtr.core.generated.data.SimplifiedRouteSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.libraries.it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

public final class SimplifiedRoute extends SimplifiedRouteSchema implements Comparable<SimplifiedRoute> {

	private SimplifiedRoute(Route route) {
		super(route.getId(), route.getName(), route.getColor(), route.getCircularState());
		for (int i = 0; i < route.getRoutePlatforms().size(); i++) {
			final Platform platform = route.getRoutePlatforms().get(i).platform;
			final Station station = platform == null ? null : platform.area;
			final Int2ObjectAVLTreeMap<InterchangeRouteNamesForColor> interchangeRoutes = new Int2ObjectAVLTreeMap<>();

			if (station == null) {
				if (platform != null) {
					addInterchangeRoutes(route.getColor(), interchangeRoutes, platform.routes);
				}
			} else {
				station.savedRails.forEach(stationPlatform -> addInterchangeRoutes(route.getColor(), interchangeRoutes, stationPlatform.routes));
			}

			final SimplifiedRoutePlatform simplifiedRoutePlatform = new SimplifiedRoutePlatform(platform == null ? 0 : platform.getId(), route.getDestination(i), station == null ? "" : station.getName());
			interchangeRoutes.forEach((color, interchangeRouteNamesForColor) -> simplifiedRoutePlatform.addColor(interchangeRouteNamesForColor));
			platforms.add(simplifiedRoutePlatform);
		}
	}

	public SimplifiedRoute(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public int getColor() {
		return (int) (color & 0xFFFFFF);
	}

	public Route.CircularState getCircularState() {
		return circularState;
	}

	public ObjectArrayList<SimplifiedRoutePlatform> getPlatforms() {
		return platforms;
	}

	public int getPlatformIndex(long platformId) {
		for (int i = 0; i < platforms.size(); i++) {
			if (platforms.get(i).getPlatformId() == platformId) {
				return i;
			}
		}
		return -1;
	}

	public static void addToList(ObjectArrayList<SimplifiedRoute> simplifiedRoutes, Route route) {
		if (!route.getHidden()) {
			simplifiedRoutes.add(new SimplifiedRoute(route));
		}
	}

	private static void addInterchangeRoutes(int thisColor, Int2ObjectAVLTreeMap<InterchangeRouteNamesForColor> interchangeRoutes, ObjectAVLTreeSet<Route> routes) {
		routes.forEach(interchangeRoute -> {
			if (interchangeRoute.getColor() != thisColor && !interchangeRoute.getHidden()) {
				interchangeRoutes.computeIfAbsent(interchangeRoute.getColor(), key -> new InterchangeRouteNamesForColor(interchangeRoute.getColor())).addRouteName(interchangeRoute.getName().split("\\|\\|")[0]);
			}
		});
	}

	@Override
	public int compareTo(SimplifiedRoute simplifiedRoute) {
		return color == simplifiedRoute.color ? Long.compare(id, simplifiedRoute.id) : Long.compare(color, simplifiedRoute.color);
	}
}
