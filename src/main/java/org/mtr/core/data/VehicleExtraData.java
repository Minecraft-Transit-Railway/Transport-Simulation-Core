package org.mtr.core.data;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.core.generated.VehicleExtraDataSchema;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.tools.Utilities;

import java.util.function.BiConsumer;

public class VehicleExtraData extends VehicleExtraDataSchema {

	private int stopIndex = -1;
	private double oldStoppingPoint;
	private boolean oldDoorTarget;

	public final ObjectImmutableList<PathData> newPath;
	public final ObjectImmutableList<VehicleCar> newVehicleCars;

	private VehicleExtraData(double railLength, double totalVehicleLength, long repeatIndex1, long repeatIndex2, double acceleration, boolean isManualAllowed, double maxManualSpeed, long manualToAutomaticTime, double totalDistance, double defaultPosition, ObjectArrayList<VehicleCar> vehicleCars, ObjectArrayList<PathData> path) {
		super(railLength, totalVehicleLength, repeatIndex1, repeatIndex2, acceleration, isManualAllowed, maxManualSpeed, manualToAutomaticTime, totalDistance, defaultPosition);
		this.path.clear();
		this.path.addAll(path);
		newPath = new ObjectImmutableList<>(path);
		this.vehicleCars.clear();
		this.vehicleCars.addAll(vehicleCars);
		newVehicleCars = new ObjectImmutableList<>(vehicleCars);
	}

	protected VehicleExtraData(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
		newPath = new ObjectImmutableList<>(path);
		newVehicleCars = new ObjectImmutableList<>(vehicleCars);
	}

	public long getPreviousRouteId() {
		return previousRouteId;
	}

	public long getPreviousPlatformId() {
		return previousPlatformId;
	}

	public long getPreviousStationId() {
		return previousStationId;
	}

	public int getPreviousRouteColor() {
		return (int) (previousRouteColor & 0xFFFFFF);
	}

	public String getPreviousRouteName() {
		return previousRouteName;
	}

	public String getPreviousStationName() {
		return previousStationName;
	}

	public String getPreviousRouteDestination() {
		return previousRouteDestination;
	}

	public long getThisRouteId() {
		return thisRouteId;
	}

	public long getThisPlatformId() {
		return thisPlatformId;
	}

	public long getThisStationId() {
		return thisStationId;
	}

	public int getThisRouteColor() {
		return (int) (thisRouteColor & 0xFFFFFF);
	}

	public String getThisRouteName() {
		return thisRouteName;
	}

	public String getThisStationName() {
		return thisStationName;
	}

	public String getThisRouteDestination() {
		return thisRouteDestination;
	}

	public long getNextPlatformId() {
		return nextPlatformId;
	}

	public long getNextStationId() {
		return nextStationId;
	}

	public String getNextStationName() {
		return nextStationName;
	}

	public int getStopIndex() {
		return stopIndex;
	}

	public boolean getIsTerminating() {
		return isTerminating;
	}

	public void iterateInterchanges(BiConsumer<String, InterchangeColorsForStationName> consumer) {
		interchangeColorsForStationNameList.forEach(interchangeColorsForStationName -> consumer.accept(interchangeColorsForStationName.getStationName(), interchangeColorsForStationName));
	}

	protected double getRailLength() {
		return railLength;
	}

	protected double getTotalVehicleLength() {
		return totalVehicleLength;
	}

	protected int getRepeatIndex1() {
		return (int) repeatIndex1;
	}

	protected int getRepeatIndex2() {
		return (int) repeatIndex2;
	}

	protected double getAcceleration() {
		return acceleration;
	}

	protected boolean getIsManualAllowed() {
		return isManualAllowed;
	}

	protected double getMaxManualSpeed() {
		return maxManualSpeed;
	}

	protected long getManualToAutomaticTime() {
		return manualToAutomaticTime;
	}

	protected double getTotalDistance() {
		return totalDistance;
	}

	protected double getDefaultPosition() {
		return defaultPosition;
	}

	protected void setStoppingPoint(double stoppingPoint) {
		this.stoppingPoint = stoppingPoint;
	}

	protected void toggleDoors() {
		doorTarget = !doorTarget;
	}

	protected void openDoors() {
		doorTarget = true;
	}

	protected void closeDoors() {
		doorTarget = false;
	}

	protected int getDoorMultiplier() {
		return doorTarget ? 1 : -1;
	}

	protected boolean checkForUpdate() {
		final boolean needsUpdate = stoppingPoint != oldStoppingPoint || doorTarget != oldDoorTarget;
		oldStoppingPoint = stoppingPoint;
		oldDoorTarget = doorTarget;
		return needsUpdate;
	}

	protected void setRoutePlatformInfo(Depot depot, int currentIndex) {
		if (depot == null) {
			previousRouteId = 0;
			previousPlatformId = 0;
			previousStationId = 0;
			previousRouteColor = 0;
			previousRouteName = "";
			previousStationName = "";
			previousRouteDestination = "";

			thisRouteId = 0;
			thisPlatformId = 0;
			thisStationId = 0;
			thisRouteColor = 0;
			thisRouteName = "";
			thisStationName = "";
			thisRouteDestination = "";

			nextPlatformId = 0;
			nextStationId = 0;
			nextStationName = "";
		} else {
			final int newStopIndex = newPath.get(currentIndex).getStopIndex();
			if (newStopIndex == stopIndex) {
				return;
			} else {
				stopIndex = newStopIndex;
			}

			final VehiclePlatformRouteInfo vehiclePlatformRouteInfo = depot.getVehiclePlatformRouteInfo(newStopIndex);

			previousRouteId = getId(vehiclePlatformRouteInfo.previousRoute);
			previousPlatformId = getId(vehiclePlatformRouteInfo.previousPlatform);
			previousStationId = getStationId(vehiclePlatformRouteInfo.previousPlatform);
			previousRouteColor = getColor(vehiclePlatformRouteInfo.previousRoute);
			previousRouteName = getName(vehiclePlatformRouteInfo.previousRoute);
			previousStationName = getStationName(vehiclePlatformRouteInfo.previousPlatform);
			previousRouteDestination = getRouteDestination(vehiclePlatformRouteInfo.previousRoute, 0);

			thisRouteId = getId(vehiclePlatformRouteInfo.thisRoute);
			thisPlatformId = getId(vehiclePlatformRouteInfo.thisPlatform);
			thisStationId = getStationId(vehiclePlatformRouteInfo.thisPlatform);
			thisRouteColor = getColor(vehiclePlatformRouteInfo.thisRoute);
			thisRouteName = getName(vehiclePlatformRouteInfo.thisRoute);
			thisStationName = getStationName(vehiclePlatformRouteInfo.thisPlatform);
			thisRouteDestination = getRouteDestination(vehiclePlatformRouteInfo.thisRoute, newStopIndex);

			nextPlatformId = getId(vehiclePlatformRouteInfo.nextPlatform);
			nextStationId = getStationId(vehiclePlatformRouteInfo.nextPlatform);
			nextStationName = getStationName(vehiclePlatformRouteInfo.nextPlatform);

			isTerminating = vehiclePlatformRouteInfo.thisRoute != null && stopIndex >= vehiclePlatformRouteInfo.thisRoute.getRoutePlatforms().size() - 1;

			interchangeColorsForStationNameList.clear();
			final Station station = vehiclePlatformRouteInfo.nextPlatform.area;
			if (station != null) {
				station.getInterchangeStationNameToColorToRouteNamesMap(true).forEach((stationName, colorToRouteNames) -> {
					final InterchangeColorsForStationName interchangeColorsForStationName = new InterchangeColorsForStationName(stationName);
					colorToRouteNames.forEach((color, routeNames) -> {
						final InterchangeRouteNamesForColor interchangeRouteNamesForColor = new InterchangeRouteNamesForColor(color);
						interchangeRouteNamesForColor.addRouteNames(routeNames);
						interchangeColorsForStationName.addColor(interchangeRouteNamesForColor);
					});
					interchangeColorsForStationNameList.add(interchangeColorsForStationName);
				});
			}
		}
	}

	public static VehicleExtraData create(
			double railLength, ObjectArrayList<VehicleCar> vehicleCars,
			ObjectArrayList<PathData> pathSidingToMainRoute, ObjectArrayList<PathData> pathMainRoute, ObjectArrayList<PathData> pathMainRouteToSiding, PathData defaultPathData,
			boolean repeatInfinitely, double acceleration, boolean isManualAllowed, double maxManualSpeed, long manualToAutomaticTime
	) {
		final double newRailLength = Siding.getRailLength(railLength);
		final double newTotalVehicleLength = Siding.getTotalVehicleLength(vehicleCars);
		final ObjectArrayList<PathData> path = createPathData(pathSidingToMainRoute, pathMainRoute, pathMainRouteToSiding, repeatInfinitely, defaultPathData);
		final long repeatIndex1 = pathSidingToMainRoute.size();
		final long repeatIndex2 = repeatInfinitely ? repeatIndex1 + pathMainRoute.size() : 0;
		final double newAcceleration = Siding.roundAcceleration(acceleration);
		final double totalDistance = path.isEmpty() ? 0 : Utilities.getElement(path, -1).getEndDistance();
		final double defaultPosition = (newRailLength + newTotalVehicleLength) / 2;
		return new VehicleExtraData(newRailLength, newTotalVehicleLength, repeatIndex1, repeatIndex2, newAcceleration, isManualAllowed, maxManualSpeed, manualToAutomaticTime, totalDistance, defaultPosition, vehicleCars, path);
	}

	private static ObjectArrayList<PathData> createPathData(ObjectArrayList<PathData> pathSidingToMainRoute, ObjectArrayList<PathData> pathMainRoute, ObjectArrayList<PathData> pathMainRouteToSiding, boolean repeatInfinitely, PathData defaultPathData) {
		final ObjectArrayList<PathData> tempPath = new ObjectArrayList<>();
		if (pathSidingToMainRoute.isEmpty() || pathMainRoute.isEmpty() || !repeatInfinitely && pathMainRouteToSiding.isEmpty()) {
			tempPath.add(defaultPathData);
		} else {
			tempPath.addAll(pathSidingToMainRoute);
			tempPath.addAll(pathMainRoute);
			if (repeatInfinitely) {
				final PathData firstPathData = pathMainRoute.get(0);
				final PathData lastPathData = Utilities.getElement(pathMainRoute, -1);
				tempPath.add(new PathData(firstPathData, lastPathData.getStartDistance(), lastPathData.getStartDistance() + firstPathData.getEndDistance() - firstPathData.getStartDistance()));
			} else {
				tempPath.addAll(pathMainRouteToSiding);
			}
		}
		return tempPath;
	}

	private static long getId(NameColorDataBase data) {
		return data == null ? 0 : data.getId();
	}

	private static String getName(NameColorDataBase data) {
		return data == null ? "" : data.getName();
	}

	private static int getColor(NameColorDataBase data) {
		return data == null ? 0 : data.getColor();
	}

	private static long getStationId(Platform platform) {
		return platform == null ? 0 : getId(platform.area);
	}

	private static String getStationName(Platform platform) {
		return platform == null ? "" : getName(platform.area);
	}

	private static String getRouteDestination(Route route, int stopIndex) {
		return route == null ? "" : route.getDestination(stopIndex);
	}

	public static class VehiclePlatformRouteInfo {

		private final Platform previousPlatform;
		private final Platform thisPlatform;
		private final Platform nextPlatform;
		private final Route previousRoute;
		private final Route thisRoute;

		public VehiclePlatformRouteInfo(Platform previousPlatform, Platform thisPlatform, Platform nextPlatform, Route previousRoute, Route thisRoute) {
			this.previousPlatform = previousPlatform;
			this.thisPlatform = thisPlatform;
			this.nextPlatform = nextPlatform;
			this.previousRoute = previousRoute;
			this.thisRoute = thisRoute;
		}
	}
}
