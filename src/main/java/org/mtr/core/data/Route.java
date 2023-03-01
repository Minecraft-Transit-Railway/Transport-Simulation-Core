package org.mtr.core.data;

import org.msgpack.core.MessagePacker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Route extends NameColorDataBase {

	public RouteType routeType;
	public boolean isLightRailRoute;
	public boolean isHidden;
	public boolean disableNextStationAnnouncements;
	public CircularState circularState;
	public String lightRailRouteNumber;
	public final List<RoutePlatform> platformIds = new ArrayList<>();

	private static final String KEY_PLATFORM_IDS = "platform_ids";
	private static final String KEY_CUSTOM_DESTINATIONS = "custom_destinations";
	private static final String KEY_ROUTE_TYPE = "route_type";
	private static final String KEY_IS_LIGHT_RAIL_ROUTE = "is_light_rail_route";
	private static final String KEY_LIGHT_RAIL_ROUTE_NUMBER = "light_rail_route_number";
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
		lightRailRouteNumber = "";
		isHidden = false;
		disableNextStationAnnouncements = false;
	}

	public Route(MessagePackHelper messagePackHelper) {
		super(messagePackHelper);
	}

	@Override
	public void updateData(MessagePackHelper messagePackHelper) {
		super.updateData(messagePackHelper);

		messagePackHelper.iterateArrayValue(KEY_PLATFORM_IDS, platformId -> platformIds.add(new RoutePlatform(platformId.asIntegerValue().asLong())));

		final List<String> customDestinations = new ArrayList<>();
		messagePackHelper.iterateArrayValue(KEY_CUSTOM_DESTINATIONS, customDestination -> customDestinations.add(customDestination.asStringValue().asString()));

		for (int i = 0; i < Math.min(platformIds.size(), customDestinations.size()); i++) {
			platformIds.get(i).customDestination = customDestinations.get(i);
		}

		messagePackHelper.unpackString(KEY_ROUTE_TYPE, value -> routeType = EnumHelper.valueOf(RouteType.NORMAL, value));
		messagePackHelper.unpackBoolean(KEY_IS_LIGHT_RAIL_ROUTE, value -> isLightRailRoute = value);
		messagePackHelper.unpackBoolean(KEY_IS_ROUTE_HIDDEN, value -> isHidden = value);
		messagePackHelper.unpackBoolean(KEY_DISABLE_NEXT_STATION_ANNOUNCEMENTS, value -> disableNextStationAnnouncements = value);
		messagePackHelper.unpackString(KEY_LIGHT_RAIL_ROUTE_NUMBER, value -> lightRailRouteNumber = value);
		messagePackHelper.unpackString(KEY_CIRCULAR_STATE, value -> circularState = EnumHelper.valueOf(CircularState.NONE, value));
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
		messagePacker.packString(KEY_LIGHT_RAIL_ROUTE_NUMBER).packString(lightRailRouteNumber);
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

	public int getPlatformIdIndex(long platformId) {
		for (int i = 0; i < platformIds.size(); i++) {
			if (platformIds.get(i).platformId == platformId) {
				return i;
			}
		}
		return -1;
	}

	public boolean containsPlatformId(long platformId) {
		return getPlatformIdIndex(platformId) >= 0;
	}

	public long getFirstPlatformId() {
		return platformIds.isEmpty() ? 0 : platformIds.get(0).platformId;
	}

	public long getLastPlatformId() {
		return platformIds.isEmpty() ? 0 : platformIds.get(platformIds.size() - 1).platformId;
	}

	public String getDestination(int index) {
		for (int i = Math.min(platformIds.size() - 1, index); i >= 0; i--) {
			final String customDestination = platformIds.get(i).customDestination;
			if (Route.destinationIsReset(customDestination)) {
				return null;
			} else if (!customDestination.isEmpty()) {
				return customDestination;
			}
		}
		return null;
	}

	public static boolean destinationIsReset(String destination) {
		return destination.equals("\\r") || destination.equals("\\reset");
	}

	public static class RoutePlatform {

		public String customDestination;
		public final long platformId;

		public RoutePlatform(long platformId) {
			this.platformId = platformId;
			customDestination = "";
		}
	}

	public enum CircularState {NONE, CLOCKWISE, ANTICLOCKWISE}
}
