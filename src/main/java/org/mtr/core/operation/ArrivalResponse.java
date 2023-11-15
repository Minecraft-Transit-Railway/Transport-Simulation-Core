package org.mtr.core.operation;

import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.data.VehicleCar;
import org.mtr.core.generated.operation.ArrivalResponseSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.function.Consumer;
import java.util.function.IntSupplier;

public final class ArrivalResponse extends ArrivalResponseSchema implements Comparable<ArrivalResponse> {

	public ArrivalResponse(String destination, long arrival, long departure, long deviation, boolean realtime, long index, Route route, Platform platform) {
		super(destination, arrival, departure, deviation, realtime, index, route.getId(), route.getName(), route.getRouteNumber(), route.getColor(), route.getCircularState(), platform.getId(), platform.getName());
	}

	public ArrivalResponse(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public String getDestination() {
		return destination;
	}

	public long getArrival() {
		return arrival;
	}

	public long getDeparture() {
		return departure;
	}

	public long getDeviation() {
		return deviation;
	}

	public boolean getRealtime() {
		return realtime;
	}

	public long getIndex() {
		return index;
	}

	public long getRouteId() {
		return routeId;
	}

	public String getRouteName() {
		return routeName;
	}

	public String getRouteNumber() {
		return routeNumber;
	}

	public long getRouteColor() {
		return routeColor;
	}

	public Route.CircularState getCircularState() {
		return circularState;
	}

	public long getPlatformId() {
		return platformId;
	}

	public String getPlatformName() {
		return platformName;
	}

	public void iterateCarDetails(Consumer<CarDetails> consumer) {
		cars.forEach(consumer);
	}

	public void setCarDetails(ObjectArrayList<VehicleCar> vehicleCars) {
		vehicleCars.forEach(vehicleCar -> cars.add(new CarDetails(vehicleCar.getVehicleId(), 0)));
	}

	private static int compare(long value1, long value2, IntSupplier ifZero) {
		final int result = Long.compare(value1, value2);
		return result == 0 ? ifZero.getAsInt() : result;
	}

	private static int compare(String value1, String value2, IntSupplier ifZero) {
		try {
			return compare(Long.parseLong(value1), Long.parseLong(value2), ifZero);
		} catch (Exception ignored) {
			final int result = value1.compareTo(value2);
			return result == 0 ? ifZero.getAsInt() : result;
		}
	}

	@Override
	public int compareTo(ArrivalResponse arrivalResponse) {
		return compare(arrival, arrivalResponse.arrival, () -> compare(platformName, arrivalResponse.platformName, () -> compare(routeNumber, arrivalResponse.routeNumber, () -> compare(destination, arrivalResponse.destination, () -> 0))));
	}
}
