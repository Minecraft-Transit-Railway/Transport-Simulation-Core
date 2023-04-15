package org.mtr.core.data;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import org.msgpack.core.MessagePacker;
import org.mtr.core.reader.ReaderBase;
import org.mtr.core.tools.Utilities;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Route extends NameColorDataBase {

	public RouteType routeType;
	public boolean isLightRailRoute;
	public boolean isHidden;
	public boolean disableNextStationAnnouncements;
	public CircularState circularState;
	public String routeNumber;
	public final List<RoutePlatform> platformIds = new ArrayList<>();

	private static final String KEY_PLATFORM_IDS = "platform_ids";
	private static final String KEY_CUSTOM_DESTINATIONS = "custom_destinations";
	private static final String KEY_ROUTE_TYPE = "route_type";
	private static final String KEY_IS_LIGHT_RAIL_ROUTE = "is_light_rail_route";
	private static final String KEY_ROUTE_NUMBER = "light_rail_route_number";
	private static final String KEY_IS_ROUTE_HIDDEN = "is_route_hidden";
	private static final String KEY_DISABLE_NEXT_STATION_ANNOUNCEMENTS = "disable_next_station_announcements";
	private static final String KEY_CIRCULAR_STATE = "circular_state";

	public Route(TransportMode transportMode) {
		this(0, transportMode);
	}

	public Route(long id, TransportMode transportMode) {
		super(id, transportMode);
		routeType = RouteType.NORMAL;
		isLightRailRoute = false;
		circularState = CircularState.NONE;
		routeNumber = "";
		isHidden = false;
		disableNextStationAnnouncements = false;
	}

	public <T extends ReaderBase<U, T>, U> Route(T readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	@Override
	public <T extends ReaderBase<U, T>, U> void updateData(T readerBase) {
		super.updateData(readerBase);

		readerBase.iterateLongArray(KEY_PLATFORM_IDS, platformId -> platformIds.add(new RoutePlatform(platformId)));

		final List<String> customDestinations = new ArrayList<>();
		readerBase.iterateStringArray(KEY_CUSTOM_DESTINATIONS, customDestinations::add);

		for (int i = 0; i < Math.min(platformIds.size(), customDestinations.size()); i++) {
			platformIds.get(i).customDestination = customDestinations.get(i);
		}

		readerBase.unpackString(KEY_ROUTE_TYPE, value -> routeType = EnumHelper.valueOf(RouteType.NORMAL, value));
		readerBase.unpackBoolean(KEY_IS_LIGHT_RAIL_ROUTE, value -> isLightRailRoute = value);
		readerBase.unpackBoolean(KEY_IS_ROUTE_HIDDEN, value -> isHidden = value);
		readerBase.unpackBoolean(KEY_DISABLE_NEXT_STATION_ANNOUNCEMENTS, value -> disableNextStationAnnouncements = value);
		readerBase.unpackString(KEY_ROUTE_NUMBER, value -> routeNumber = value);
		readerBase.unpackString(KEY_CIRCULAR_STATE, value -> circularState = EnumHelper.valueOf(CircularState.NONE, value));
	}

	@Override
	public void toMessagePack(MessagePacker messagePacker) throws IOException {
		super.toMessagePack(messagePacker);

		messagePacker.packString(KEY_PLATFORM_IDS).packArrayHeader(platformIds.size());
		for (final RoutePlatform routePlatform : platformIds) {
			messagePacker.packLong(routePlatform.platformId);
		}

		messagePacker.packString(KEY_CUSTOM_DESTINATIONS).packArrayHeader(platformIds.size());
		for (final RoutePlatform routePlatform : platformIds) {
			messagePacker.packString(routePlatform.customDestination);
		}

		messagePacker.packString(KEY_ROUTE_TYPE).packString(routeType.toString());
		messagePacker.packString(KEY_IS_LIGHT_RAIL_ROUTE).packBoolean(isLightRailRoute);
		messagePacker.packString(KEY_IS_ROUTE_HIDDEN).packBoolean(isHidden);
		messagePacker.packString(KEY_DISABLE_NEXT_STATION_ANNOUNCEMENTS).packBoolean(disableNextStationAnnouncements);
		messagePacker.packString(KEY_ROUTE_NUMBER).packString(routeNumber);
		messagePacker.packString(KEY_CIRCULAR_STATE).packString(circularState.toString());
	}

	@Override
	public int messagePackLength() {
		return super.messagePackLength() + 8;
	}

	@Override
	protected boolean hasTransportMode() {
		return true;
	}

	public String getColorHex() {
		return Utilities.numberToPaddedHexString(color, 6);
	}

	public int getPlatformIdIndex(long platformId) {
		for (int i = 0; i < platformIds.size(); i++) {
			if (platformIds.get(i).platformId == platformId) {
				return i;
			}
		}
		return -1;
	}

	public String getDestination(DataCache dataCache, int index) {
		for (int i = Math.min(platformIds.size() - 1, index); i >= 0; i--) {
			final String customDestination = platformIds.get(i).customDestination;
			if (destinationIsReset(customDestination)) {
				break;
			} else if (!customDestination.isEmpty()) {
				return customDestination;
			}
		}

		if (platformIds.isEmpty()) {
			return "";
		} else {
			Platform platform = null;

			if (circularState != CircularState.NONE) {
				for (int i = index + 1; i < platformIds.size(); i++) {
					platform = dataCache.platformIdMap.get(platformIds.get(i).platformId);
					if (platform != null && platform.area != null && platform.area.savedRails.stream().anyMatch(checkPlatform -> dataCache.platformIdToRouteColors.getOrDefault(checkPlatform.id, new IntArraySet()).intStream().anyMatch(checkColor -> checkColor != color))) {
						break;
					}
				}
			}

			if (platform == null) {
				platform = dataCache.platformIdMap.get(Utilities.getElement(platformIds, -1).platformId);
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
