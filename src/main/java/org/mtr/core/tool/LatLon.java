package org.mtr.core.tool;

import org.mtr.core.data.Position;

/**
 * A geographic latitude / longitude pair, in degrees.
 *
 * <p>Used by the OBA endpoints (which speak the OneBusAway specification's lat/lon shape) and by
 * the dashboard's geographic projection. World-block-space {@link Position}s are projected to
 * lat/lon by treating the world as a Mercator-equivalent flat surface scaled to Earth's
 * circumference — sufficient for visual map placement, not for geodesy.</p>
 *
 * @param lat latitude in degrees, clamped to {@code [-90, 90]}
 * @param lon longitude in degrees, clamped to {@code [-180, 180]}
 */
public record LatLon(double lat, double lon) {

	/**
	 * Maximum absolute latitude (the poles), in degrees.
	 */
	public static final double MAX_LAT = 90;
	/**
	 * Maximum absolute longitude (the antimeridian), in degrees.
	 */
	public static final double MAX_LON = 180;
	/**
	 * Earth's equatorial circumference in metres, used as the projection scale.
	 */
	private static final int EARTH_CIRCUMFERENCE_METERS = 40075017;

	/**
	 * Project a world-block {@link Position} to lat/lon.
	 *
	 * <p>The world's {@code -Z} axis is mapped to {@code +lat} (north) and {@code +X} to
	 * {@code +lon} (east), matching Minecraft's compass conventions.</p>
	 */
	public LatLon(Position position) {
		this(metersToLat(-position.getZ()), metersToLon(position.getX()));
	}

	/**
	 * @return a new {@link LatLon} translated by {@code (latOffset, lonOffset)}.
	 */
	public LatLon offset(double latOffset, double lonOffset) {
		return new LatLon(lat + latOffset, lon + lonOffset);
	}

	/**
	 * @return {@code meters} converted to a latitude delta in degrees, clamped to {@code [-MAX_LAT, MAX_LAT]}.
	 */
	public static double metersToLat(double meters) {
		return Utilities.clampSafe(MAX_LAT * 2 * meters / EARTH_CIRCUMFERENCE_METERS, -MAX_LAT, MAX_LAT);
	}

	/**
	 * @return {@code meters} converted to a longitude delta in degrees, clamped to {@code [-MAX_LON, MAX_LON]}.
	 */
	public static double metersToLon(double meters) {
		return Utilities.clampSafe(MAX_LON * 2 * meters / EARTH_CIRCUMFERENCE_METERS, -MAX_LON, MAX_LON);
	}
}
