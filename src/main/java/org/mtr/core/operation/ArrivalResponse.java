package org.mtr.core.operation;

import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.data.VehicleCar;
import org.mtr.core.generated.operation.ArrivalResponseSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.function.Consumer;

public final class ArrivalResponse extends ArrivalResponseSchema implements Comparable<ArrivalResponse> {

	public ArrivalResponse(String destination, long arrival, long departure, long deviation, boolean realtime, long departureIndex, int stopIndex, Route route, Platform platform) {
		super(destination, arrival, departure, deviation, realtime, departureIndex, stopIndex == route.getRoutePlatforms().size() - 1, route.getId(), route.getName(), route.getRouteNumber(), route.getColor(), route.getCircularState(), platform.getId(), platform.getName());
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

	public long getDepartureIndex() {
		return departureIndex;
	}

	public boolean getIsTerminating() {
		return isTerminating;
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

	@Override
	public int compareTo(ArrivalResponse arrivalResponse) {
		return Utilities.compare(arrival, arrivalResponse.arrival, () -> Utilities.compare(departureIndex, arrivalResponse.departureIndex, () -> Utilities.compare(platformName, arrivalResponse.platformName, () -> Utilities.compare(routeNumber, arrivalResponse.routeNumber, () -> Utilities.compare(destination, arrivalResponse.destination, () -> 0)))));
	}
}
