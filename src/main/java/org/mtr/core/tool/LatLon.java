package org.mtr.core.tool;

import org.mtr.core.data.Position;

public class LatLon {

	public final double lat;
	public final double lon;

	public static final double MAX_LAT = 90;
	public static final double MAX_LON = 180;
	private static final int EARTH_CIRCUMFERENCE_METERS = 40075017;

	public LatLon(double lat, double lon) {
		this.lat = lat;
		this.lon = lon;
	}

	public LatLon(Position position) {
		this(metersToLat(-position.getZ()), metersToLon(position.getX()));
	}

	public LatLon offset(double latOffset, double lonOffset) {
		return new LatLon(lat + latOffset, lon + lonOffset);
	}

	public static double metersToLat(double meters) {
		return Utilities.clamp(MAX_LAT * 2 * meters / EARTH_CIRCUMFERENCE_METERS, -MAX_LAT, MAX_LAT);
	}

	public static double metersToLon(double meters) {
		return Utilities.clamp(MAX_LON * 2 * meters / EARTH_CIRCUMFERENCE_METERS, -MAX_LON, MAX_LON);
	}
}
