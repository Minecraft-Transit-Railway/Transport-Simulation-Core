package org.mtr.core.data;

import java.util.Locale;

public class VehicleType {

	public final String baseVehicleType;

	VehicleType(String baseVehicleType) {
		this.baseVehicleType = baseVehicleType;
	}

	public static TransportMode getTransportMode(String vehicleType) {
		final TransportMode[] returnTransportMode = {TransportMode.TRAIN};
		splitVehicleType(vehicleType, ((transportMode, length, width) -> returnTransportMode[0] = transportMode));
		return returnTransportMode[0];
	}

	public static int getSpacing(String vehicleType) {
		final int[] returnLength = {1};
		splitVehicleType(vehicleType, ((transportMode, length, width) -> returnLength[0] = length));
		return returnLength[0] + 1;
	}

	public static int getWidth(String vehicleType) {
		final int[] returnWidth = {1};
		splitVehicleType(vehicleType, ((transportMode, length, width) -> returnWidth[0] = width));
		return returnWidth[0];
	}

	private static void splitVehicleType(String vehicleType, VehicleTypeCallback vehicleTypeCallback) {
		for (final TransportMode transportMode : TransportMode.values()) {
			final String checkString = transportMode.toString().toLowerCase(Locale.ENGLISH) + "_";

			if (vehicleType.toLowerCase(Locale.ENGLISH).startsWith(checkString)) {
				final String[] remainingSplit = vehicleType.substring(checkString.length()).split("_");
				int length = 1;
				int width = 1;

				try {
					length = Integer.parseInt(remainingSplit[0]);
					width = Integer.parseInt(remainingSplit[1]);
				} catch (Exception e) {
					e.printStackTrace();
				}

				vehicleTypeCallback.vehicleTypeCallback(transportMode, Math.max(length, 1), Math.max(width, 1));
				return;
			}
		}
	}

	@FunctionalInterface
	private interface VehicleTypeCallback {
		void vehicleTypeCallback(TransportMode transportMode, int length, int width);
	}
}
