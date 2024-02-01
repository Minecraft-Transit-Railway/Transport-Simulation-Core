package org.mtr.core.data;

import org.mtr.core.generated.data.VehicleExtraDataSchema;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import javax.annotation.Nullable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class VehicleExtraData extends VehicleExtraDataSchema {

	private int stopIndex = -1;
	private double oldStoppingPoint;
	private boolean oldDoorTarget;
	private boolean hasRidingEntityUpdate;

	public final ObjectImmutableList<PathData> immutablePath;
	public final ObjectImmutableList<VehicleCar> immutableVehicleCars;

	private VehicleExtraData(long sidingId, double railLength, double totalVehicleLength, long repeatIndex1, long repeatIndex2, double acceleration, boolean isManualAllowed, double maxManualSpeed, long manualToAutomaticTime, double totalDistance, double defaultPosition, ObjectArrayList<VehicleCar> vehicleCars, ObjectArrayList<PathData> path, double brakingPower) {
		super(sidingId, railLength, totalVehicleLength, repeatIndex1, repeatIndex2, acceleration, isManualAllowed, maxManualSpeed, manualToAutomaticTime, totalDistance, defaultPosition, brakingPower);
		this.path.clear();
		this.path.addAll(path);
		immutablePath = new ObjectImmutableList<>(path);
		this.vehicleCars.clear();
		this.vehicleCars.addAll(vehicleCars);
		immutableVehicleCars = new ObjectImmutableList<>(vehicleCars);
	}

	public VehicleExtraData(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
		immutablePath = new ObjectImmutableList<>(path);
		immutableVehicleCars = new ObjectImmutableList<>(vehicleCars);
	}

	public VehicleExtraData copy(int pathUpdateIndex) {
		final VehicleExtraData newVehicleExtraData = new VehicleExtraData(new JsonReader(Utilities.getJsonObjectFromData(this)));
		newVehicleExtraData.path.clear();

		for (int i = pathUpdateIndex; i <= path.size(); i++) {
			if (i == path.size() && !path.isEmpty()) {
				newVehicleExtraData.path.add(0, path.get(0));
			} else {
				final PathData pathData = path.get(i);
				if (i == pathUpdateIndex || pathData.getStartDistance() <= stoppingPoint) {
					newVehicleExtraData.path.add(pathData);
				} else {
					break;
				}
			}
		}

		return newVehicleExtraData;
	}

	public long getSidingId() {
		return sidingId;
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

	public double getAcceleration() {
		return acceleration;
	}

	public double getBrakingPower() {
		return brakingPower;
	}

	public void iterateInterchanges(BiConsumer<String, InterchangeColorsForStationName> consumer) {
		interchangeColorsForStationNameList.forEach(interchangeColorsForStationName -> consumer.accept(interchangeColorsForStationName.getStationName(), interchangeColorsForStationName));
	}

	public void iterateRidingEntities(Consumer<VehicleRidingEntity> consumer) {
		ridingEntities.forEach(consumer);
	}

	public int getDoorMultiplier() {
		return doorTarget ? 1 : -1;
	}

	protected double getStoppingPoint() {
		return stoppingPoint;
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

	protected boolean checkForUpdate() {
		final boolean needsUpdate = Math.abs(stoppingPoint - oldStoppingPoint) > 0.01 || doorTarget != oldDoorTarget || hasRidingEntityUpdate;
		oldStoppingPoint = stoppingPoint;
		oldDoorTarget = doorTarget;
		hasRidingEntityUpdate = false;
		return needsUpdate;
	}

	protected void setRoutePlatformInfo(@Nullable Depot depot, int currentIndex) {
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
			final int newStopIndex = immutablePath.get(currentIndex).getStopIndex();
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
			final Station station = vehiclePlatformRouteInfo.nextPlatform == null ? null : vehiclePlatformRouteInfo.nextPlatform.area;
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

	void removeRidingEntitiesIf(Predicate<VehicleRidingEntity> predicate) {
		if (ridingEntities.removeIf(predicate)) {
			hasRidingEntityUpdate = true;
		}
	}

	void addRidingEntities(ObjectOpenHashSet<VehicleRidingEntity> vehicleRidingEntitiesToAdd) {
		if (ridingEntities.addAll(vehicleRidingEntitiesToAdd)) {
			hasRidingEntityUpdate = true;
		}
	}

	public static VehicleExtraData create(
			long sidingId, double railLength, ObjectArrayList<VehicleCar> vehicleCars,
			ObjectArrayList<PathData> pathSidingToMainRoute, ObjectArrayList<PathData> pathMainRoute, ObjectArrayList<PathData> pathMainRouteToSiding, PathData defaultPathData,
			boolean repeatInfinitely, double acceleration, boolean isManualAllowed, double maxManualSpeed, long manualToAutomaticTime, double brakingPower
	) {
		final double newRailLength = Siding.getRailLength(railLength);
		final double newTotalVehicleLength = Siding.getTotalVehicleLength(vehicleCars);
		final ObjectArrayList<PathData> path = createPathData(pathSidingToMainRoute, pathMainRoute, pathMainRouteToSiding, repeatInfinitely, defaultPathData);
		final long repeatIndex1 = pathSidingToMainRoute.size();
		final long repeatIndex2 = repeatInfinitely ? repeatIndex1 + pathMainRoute.size() : 0;
		final double newAcceleration = Siding.roundAcceleration(acceleration);
		final double newBrakingPower = Siding.roundAcceleration(brakingPower);
		final double totalDistance = path.isEmpty() ? 0 : Utilities.getElement(path, -1).getEndDistance();
		final double defaultPosition = (newRailLength + newTotalVehicleLength) / 2;
		return new VehicleExtraData(sidingId, newRailLength, newTotalVehicleLength, repeatIndex1, repeatIndex2, newAcceleration, isManualAllowed, maxManualSpeed, manualToAutomaticTime, totalDistance, defaultPosition, vehicleCars, path, newBrakingPower);
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

	private static long getId(@Nullable NameColorDataBase data) {
		return data == null ? 0 : data.getId();
	}

	private static String getName(@Nullable NameColorDataBase data) {
		return data == null ? "" : data.getName();
	}

	private static int getColor(@Nullable NameColorDataBase data) {
		return data == null ? 0 : data.getColor();
	}

	private static long getStationId(@Nullable Platform platform) {
		return platform == null ? 0 : getId(platform.area);
	}

	private static String getStationName(@Nullable Platform platform) {
		return platform == null ? "" : getName(platform.area);
	}

	private static String getRouteDestination(@Nullable Route route, int stopIndex) {
		return route == null ? "" : route.getDestination(stopIndex);
	}

	public static class VehiclePlatformRouteInfo {

		private final Platform previousPlatform;
		private final Platform thisPlatform;
		private final Platform nextPlatform;
		private final Route previousRoute;
		private final Route thisRoute;

		public VehiclePlatformRouteInfo(@Nullable Platform previousPlatform, @Nullable Platform thisPlatform, @Nullable Platform nextPlatform, @Nullable Route previousRoute, @Nullable Route thisRoute) {
			this.previousPlatform = previousPlatform;
			this.thisPlatform = thisPlatform;
			this.nextPlatform = nextPlatform;
			this.previousRoute = previousRoute;
			this.thisRoute = thisRoute;
		}
	}
}
