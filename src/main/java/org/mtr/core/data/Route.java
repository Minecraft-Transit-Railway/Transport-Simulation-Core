package org.mtr.core.data;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.core.generated.data.RouteSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.tool.Utilities;
import org.mtr.legacy.data.DataFixer;

import java.util.Locale;

/**
 * An ordered sequence of {@link Platform}s that together form a single line / direction
 * passengers can travel along.
 *
 * <p>Routes are owned by one or more {@link Depot}s (the depots that dispatch vehicles along
 * them) and reference platforms by id; {@link #durations} caches the per-segment travel time
 * computed from the latest {@link Siding} simulation so the schedule view does not have to
 * recompute it on every read.</p>
 */
public final class Route extends RouteSchema {

	public final ObjectArrayList<Depot> depots = new ObjectArrayList<>();
	public final LongArrayList durations = new LongArrayList();

	public Route(TransportMode transportMode, Data data) {
		super(transportMode, data);
	}

	public Route(ReaderBase readerBase, Data data) {
		super(DataFixer.convertRoute(readerBase), data);
		updateData(readerBase);
	}

	@Override
	public boolean isValid() {
		return !name.isEmpty();
	}

	public ObjectArrayList<RoutePlatformData> getRoutePlatforms() {
		return routePlatformData;
	}

	public String getRouteNumber() {
		return routeNumber;
	}

	public boolean getHidden() {
		return hidden;
	}

	public RouteType getRouteType() {
		return routeType;
	}

	public String getRouteTypeKey() {
		return String.format("%s_%s", transportMode, routeType).toLowerCase(Locale.ENGLISH);
	}

	public CircularState getCircularState() {
		return circularState;
	}

	public String getDestination(int index) {
		for (int i = Math.min(routePlatformData.size() - 1, index); i >= 0; i--) {
			final String customDestination = routePlatformData.get(i).getCustomDestination();
			if (destinationIsReset(customDestination)) {
				break;
			} else if (!customDestination.isEmpty()) {
				return customDestination;
			}
		}

		if (routePlatformData.isEmpty()) {
			return "";
		} else {
			Platform platform = null;

			if (circularState != CircularState.NONE) {
				for (int i = index + 1; i < routePlatformData.size(); i++) {
					platform = routePlatformData.get(i).platform;
					if (platform != null && platform.area != null && platform.area.savedRails.stream().anyMatch(checkPlatform -> checkPlatform.routeColors.size() > 1 || !checkPlatform.routeColors.isEmpty() && !checkPlatform.routeColors.contains(getColor()))) {
						break;
					}
				}
			}

			if (platform == null) {
				final RoutePlatformData lastRoutePlatformData = Utilities.getElement(routePlatformData, -1);
				if (lastRoutePlatformData != null) {
					platform = lastRoutePlatformData.platform;
				}
			}

			return platform != null && platform.area != null ? platform.area.getName() : "";
		}
	}

	public void setRouteNumber(String routeNumber) {
		this.routeNumber = routeNumber;
	}

	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}

	public void setCircularState(CircularState circularState) {
		this.circularState = circularState;
	}

	public void setRouteType(RouteType routeType) {
		this.routeType = routeType;
	}

	public org.mtr.core.oba.Route getOBARouteElement() {
		return new org.mtr.core.oba.Route(getColorHex(), Utilities.formatName(routeNumber), Utilities.formatName(name), Utilities.formatName(name), getGtfsType(), getColorHex());
	}

	public static boolean destinationIsReset(String destination) {
		return destination.equals("\\r") || destination.equals("\\reset");
	}

	private int getGtfsType() {
		return switch (transportMode) {
			case TRAIN -> routeType == RouteType.LIGHT_RAIL ? 0 : 2;
			case BOAT -> 4;
			case CABLE_CAR -> 6;
			default -> 3;
		};
	}

	public enum CircularState {
		NONE(""), CLOCKWISE("\u21A9"), ANTICLOCKWISE("\u21AA");

		public final String emoji;

		CircularState(String emoji) {
			this.emoji = emoji;
		}
	}
}
