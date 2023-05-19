package org.mtr.core.data;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.serializers.WriterBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.Utilities;

public class Route extends NameColorDataBase {

	public RouteType routeType = RouteType.NORMAL;
	public boolean hasRouteNumber;
	public String routeNumber = "";
	public boolean isHidden;
	public boolean disableNextStationAnnouncements;
	public CircularState circularState = CircularState.NONE;
	public final ObjectArrayList<RoutePlatform> routePlatforms = new ObjectArrayList<>();

	private static final String KEY_ROUTE_TYPE = "route_type";
	private static final String KEY_HAS_ROUTE_NUMBER = "is_light_rail_route";
	private static final String KEY_ROUTE_NUMBER = "light_rail_route_number";
	private static final String KEY_IS_ROUTE_HIDDEN = "is_route_hidden";
	private static final String KEY_DISABLE_NEXT_STATION_ANNOUNCEMENTS = "disable_next_station_announcements";
	private static final String KEY_CIRCULAR_STATE = "circular_state";
	private static final String KEY_PLATFORM_IDS = "platform_ids";
	private static final String KEY_CUSTOM_DESTINATIONS = "custom_destinations";

	public Route(TransportMode transportMode, Simulator simulator) {
		super(transportMode, simulator);
	}

	public Route(ReaderBase readerBase, Simulator simulator) {
		super(readerBase, simulator);
		updateData(readerBase);
	}

	@Override
	public void updateData(ReaderBase readerBase) {
		super.updateData(readerBase);
		readerBase.unpackString(KEY_ROUTE_TYPE, value -> routeType = EnumHelper.valueOf(RouteType.NORMAL, value));
		readerBase.unpackBoolean(KEY_HAS_ROUTE_NUMBER, value -> hasRouteNumber = value);
		readerBase.unpackString(KEY_ROUTE_NUMBER, value -> routeNumber = value);
		readerBase.unpackBoolean(KEY_IS_ROUTE_HIDDEN, value -> isHidden = value);
		readerBase.unpackBoolean(KEY_DISABLE_NEXT_STATION_ANNOUNCEMENTS, value -> disableNextStationAnnouncements = value);
		readerBase.unpackString(KEY_CIRCULAR_STATE, value -> circularState = EnumHelper.valueOf(CircularState.NONE, value));
		readerBase.iterateLongArray(KEY_PLATFORM_IDS, platformId -> routePlatforms.add(new RoutePlatform(platformId)));

		final ObjectArrayList<String> customDestinations = new ObjectArrayList<>();
		readerBase.iterateStringArray(KEY_CUSTOM_DESTINATIONS, customDestinations::add);
		for (int i = 0; i < Math.min(routePlatforms.size(), customDestinations.size()); i++) {
			routePlatforms.get(i).customDestination = customDestinations.get(i);
		}
	}

	@Override
	public void serializeData(WriterBase writerBase) {
		super.serializeData(writerBase);
		serializeRouteType(writerBase);
		serializeHasRouteNumber(writerBase);
		serializeRouteNumber(writerBase);
		serializeIsRouteHidden(writerBase);
		serializeDisableNextStationAnnouncements(writerBase);
		serializeCircularState(writerBase);
		serializeRoutePlatforms(writerBase);
	}

	@Override
	protected boolean hasTransportMode() {
		return true;
	}

	public void serializeRouteType(WriterBase writerBase) {
		writerBase.writeString(KEY_ROUTE_TYPE, routeType.toString());
	}

	public void serializeHasRouteNumber(WriterBase writerBase) {
		writerBase.writeBoolean(KEY_HAS_ROUTE_NUMBER, hasRouteNumber);
	}

	public void serializeRouteNumber(WriterBase writerBase) {
		writerBase.writeString(KEY_ROUTE_NUMBER, routeNumber);
	}

	public void serializeIsRouteHidden(WriterBase writerBase) {
		writerBase.writeBoolean(KEY_IS_ROUTE_HIDDEN, isHidden);
	}

	public void serializeDisableNextStationAnnouncements(WriterBase writerBase) {
		writerBase.writeBoolean(KEY_DISABLE_NEXT_STATION_ANNOUNCEMENTS, disableNextStationAnnouncements);
	}

	public void serializeCircularState(WriterBase writerBase) {
		writerBase.writeString(KEY_CIRCULAR_STATE, circularState.toString());
	}

	public void serializeRoutePlatforms(WriterBase writerBase) {
		final WriterBase.Array writerBaseArrayPlatformIds = writerBase.writeArray(KEY_PLATFORM_IDS);
		routePlatforms.forEach(routePlatform -> writerBaseArrayPlatformIds.writeLong(routePlatform.platformId));
		final WriterBase.Array writerBaseArrayCustomDestinations = writerBase.writeArray(KEY_CUSTOM_DESTINATIONS);
		routePlatforms.forEach(routePlatform -> writerBaseArrayCustomDestinations.writeString(routePlatform.customDestination));
	}

	public String getDestination(DataCache dataCache, int index) {
		for (int i = Math.min(routePlatforms.size() - 1, index); i >= 0; i--) {
			final String customDestination = routePlatforms.get(i).customDestination;
			if (destinationIsReset(customDestination)) {
				break;
			} else if (!customDestination.isEmpty()) {
				return customDestination;
			}
		}

		if (routePlatforms.isEmpty()) {
			return "";
		} else {
			Platform platform = null;

			if (circularState != CircularState.NONE) {
				for (int i = index + 1; i < routePlatforms.size(); i++) {
					platform = dataCache.platformIdMap.get(routePlatforms.get(i).platformId);
					if (platform != null && platform.area != null && platform.area.savedRails.stream().anyMatch(checkPlatform -> dataCache.platformIdToRouteColors.getOrDefault(checkPlatform.id, new IntArraySet()).intStream().anyMatch(checkColor -> checkColor != color))) {
						break;
					}
				}
			}

			if (platform == null) {
				platform = dataCache.platformIdMap.get(Utilities.getElement(routePlatforms, -1).platformId);
			}

			return platform != null && platform.area != null ? String.format("%s%s%s", circularState.emoji, circularState.emoji.isEmpty() ? "" : " ", platform.area.name) : "";
		}
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

	public static class RoutePlatform {

		public String customDestination;
		public final long platformId;

		public RoutePlatform(long platformId) {
			this.platformId = platformId;
			customDestination = "";
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
