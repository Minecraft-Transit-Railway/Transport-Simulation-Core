package org.mtr.core.data;

import org.mtr.core.generated.RouteSchema;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.tools.DataFixer;
import org.mtr.core.tools.Utilities;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Locale;

public final class Route extends RouteSchema {

	public final ObjectArrayList<Depot> depots = new ObjectArrayList<>();

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
				platform = Utilities.getElement(routePlatformData, -1).platform;
			}

			return platform != null && platform.area != null ? String.format("%s%s%s", circularState.emoji, circularState.emoji.isEmpty() ? "" : " ", platform.area.getName()) : "";
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

	public JsonObject getOBARouteElement() {
		final JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("agencyId", "1");
		jsonObject.addProperty("color", getColorHex());
		jsonObject.addProperty("description", Utilities.formatName(name));
		jsonObject.addProperty("id", getColorHex());
		jsonObject.addProperty("longName", Utilities.formatName(name));
		jsonObject.addProperty("shortName", Utilities.formatName(routeNumber));
		jsonObject.addProperty("textColor", "");
		jsonObject.addProperty("type", getGtfsType());
		jsonObject.addProperty("url", "");
		return jsonObject;
	}

	public static boolean destinationIsReset(String destination) {
		return destination.equals("\\r") || destination.equals("\\reset");
	}

	private int getGtfsType() {
		switch (transportMode) {
			case TRAIN:
				return routeType == RouteType.LIGHT_RAIL ? 0 : 2;
			case BOAT:
				return 4;
			case CABLE_CAR:
				return 6;
			default:
				return 3;
		}
	}

	public enum CircularState {
		NONE(""), CLOCKWISE("\u21A9"), ANTICLOCKWISE("\u21AA");

		private final String emoji;

		CircularState(String emoji) {
			this.emoji = emoji;
		}
	}
}
