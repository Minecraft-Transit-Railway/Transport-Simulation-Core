package org.mtr.core.data;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.booleans.BooleanBooleanImmutablePair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.mtr.core.generated.data.VehicleExtraDataSchema;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.tool.Utilities;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class VehicleExtraData extends VehicleExtraDataSchema {

	private int stopIndex = -1;
	private double oldStoppingPoint;
	private boolean oldDoorTarget;
	private long oldPowerLevel;
	private double oldSpeedTarget;
	private boolean oldIsCurrentlyManual;
	private boolean hasRidingEntityUpdate;

	public final ObjectImmutableList<PathData> immutablePath;
	public final ObjectImmutableList<VehicleCar> immutableVehicleCars;

	private VehicleExtraData(long depotId, long sidingId, double railLength, double totalVehicleLength, long repeatIndex1, long repeatIndex2, double acceleration, double deceleration, boolean isManualAllowed, double maxManualSpeed, long manualToAutomaticTime, double totalDistance, double defaultPosition, ObjectArrayList<VehicleCar> vehicleCars, ObjectArrayList<PathData> path) {
		super(depotId, sidingId, railLength, totalVehicleLength, repeatIndex1, repeatIndex2, acceleration, deceleration, isManualAllowed, maxManualSpeed, manualToAutomaticTime, totalDistance, defaultPosition);
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
				newVehicleExtraData.path.add(0, path.getFirst());
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

	public long getDepotId() {
		return depotId;
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

	public String getPreviousRouteNumber() {
		return previousRouteNumber;
	}

	public RouteType getPreviousRouteType() {
		return previousRouteType;
	}

	public Route.CircularState getPreviousRouteCircularState() {
		return previousRouteCircularState;
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

	public String getThisRouteNumber() {
		return thisRouteNumber;
	}

	public RouteType getThisRouteType() {
		return thisRouteType;
	}

	public Route.CircularState getThisRouteCircularState() {
		return thisRouteCircularState;
	}

	public String getThisStationName() {
		return thisStationName;
	}

	public String getThisRouteDestination() {
		return thisRouteDestination;
	}

	public long getNextRouteId() {
		return nextRouteId;
	}

	public long getNextPlatformId() {
		return nextPlatformId;
	}

	public long getNextStationId() {
		return nextStationId;
	}

	public int getNextRouteColor() {
		return (int) (nextRouteColor & 0xFFFFFF);
	}

	public String getNextRouteName() {
		return nextRouteName;
	}

	public String getNextRouteNumber() {
		return nextRouteNumber;
	}

	public RouteType getNextRouteType() {
		return nextRouteType;
	}

	public Route.CircularState getNextRouteCircularState() {
		return nextRouteCircularState;
	}

	public String getNextStationName() {
		return nextStationName;
	}

	public String getNextRouteDestination() {
		return nextRouteDestination;
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

	public double getDeceleration() {
		return deceleration;
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

	public double getStoppingPoint() {
		return stoppingPoint;
	}

	public int getPowerLevel() {
		return (int) powerLevel;
	}

	public double getSpeedTarget() {
		return speedTarget;
	}

	public boolean getIsCurrentlyManual() {
		return isCurrentlyManual;
	}

	public double getTotalVehicleLength() {
		return totalVehicleLength;
	}

	public double getMaxManualSpeed() {
		return maxManualSpeed;
	}

	public boolean getIsManualAllowed() {
		return isManualAllowed;
	}

	protected double getRailLength() {
		return railLength;
	}

	protected int getRepeatIndex1() {
		return (int) repeatIndex1;
	}

	protected int getRepeatIndex2() {
		return (int) repeatIndex2;
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

	protected void setPowerLevel(int powerLevel) {
		this.powerLevel = powerLevel;
	}

	protected void setSpeedTarget(double speedTarget) {
		this.speedTarget = speedTarget;
	}

	protected void setIsCurrentlyManual(boolean isCurrentlyManual) {
		this.isCurrentlyManual = isCurrentlyManual;
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
		final boolean needsUpdate = Math.abs(stoppingPoint - oldStoppingPoint) > 0.01 || doorTarget != oldDoorTarget || powerLevel != oldPowerLevel || Math.abs(speedTarget - oldSpeedTarget) > 0.01 || isCurrentlyManual != oldIsCurrentlyManual || hasRidingEntityUpdate;
		oldStoppingPoint = stoppingPoint;
		oldDoorTarget = doorTarget;
		oldPowerLevel = powerLevel;
		oldSpeedTarget = speedTarget;
		oldIsCurrentlyManual = isCurrentlyManual;
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
			previousRouteNumber = "";
			previousRouteType = RouteType.NORMAL;
			previousRouteCircularState = Route.CircularState.NONE;
			previousStationName = "";
			previousRouteDestination = "";

			thisRouteId = 0;
			thisPlatformId = 0;
			thisStationId = 0;
			thisRouteColor = 0;
			thisRouteName = "";
			thisRouteNumber = "";
			thisRouteType = RouteType.NORMAL;
			thisRouteCircularState = Route.CircularState.NONE;
			thisStationName = "";
			thisRouteDestination = "";

			nextRouteId = 0;
			nextPlatformId = 0;
			nextStationId = 0;
			nextRouteColor = 0;
			nextRouteName = "";
			nextRouteNumber = "";
			nextRouteType = RouteType.NORMAL;
			nextRouteCircularState = Route.CircularState.NONE;
			nextStationName = "";
			nextRouteDestination = "";
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
			previousRouteNumber = getRouteNumber(vehiclePlatformRouteInfo.previousRoute);
			previousRouteType = getRouteType(vehiclePlatformRouteInfo.previousRoute);
			previousRouteCircularState = getRouteCircularState(vehiclePlatformRouteInfo.previousRoute);
			previousStationName = getStationName(vehiclePlatformRouteInfo.previousPlatform);
			previousRouteDestination = getRouteDestination(vehiclePlatformRouteInfo.previousRoute, 0);

			thisRouteId = getId(vehiclePlatformRouteInfo.thisRoute);
			thisPlatformId = getId(vehiclePlatformRouteInfo.thisPlatform);
			thisStationId = getStationId(vehiclePlatformRouteInfo.thisPlatform);
			thisRouteColor = getColor(vehiclePlatformRouteInfo.thisRoute);
			thisRouteName = getName(vehiclePlatformRouteInfo.thisRoute);
			thisRouteNumber = getRouteNumber(vehiclePlatformRouteInfo.thisRoute);
			thisRouteType = getRouteType(vehiclePlatformRouteInfo.thisRoute);
			thisRouteCircularState = getRouteCircularState(vehiclePlatformRouteInfo.thisRoute);
			thisStationName = getStationName(vehiclePlatformRouteInfo.thisPlatform);
			thisRouteDestination = getRouteDestination(vehiclePlatformRouteInfo.thisRoute, vehiclePlatformRouteInfo.platformIndexInRoute);

			nextRouteId = getId(vehiclePlatformRouteInfo.nextRoute);
			nextPlatformId = getId(vehiclePlatformRouteInfo.nextPlatform);
			nextStationId = getStationId(vehiclePlatformRouteInfo.nextPlatform);
			nextRouteColor = getColor(vehiclePlatformRouteInfo.nextRoute);
			nextRouteName = getName(vehiclePlatformRouteInfo.nextRoute);
			nextRouteNumber = getRouteNumber(vehiclePlatformRouteInfo.nextRoute);
			nextRouteType = getRouteType(vehiclePlatformRouteInfo.nextRoute);
			nextRouteCircularState = getRouteCircularState(vehiclePlatformRouteInfo.nextRoute);
			nextStationName = getStationName(vehiclePlatformRouteInfo.nextPlatform);
			nextRouteDestination = getRouteDestination(vehiclePlatformRouteInfo.nextRoute, 0);

			isTerminating = vehiclePlatformRouteInfo.thisRoute != null && vehiclePlatformRouteInfo.platformIndexInRoute >= vehiclePlatformRouteInfo.thisRoute.getRoutePlatforms().size() - 1;

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

	BooleanBooleanImmutablePair containsDriverAndDoorOverride() {
		boolean containsDriver = false;
		boolean doorOverride = false;
		for (final VehicleRidingEntity vehicleRidingEntity : ridingEntities) {
			if (vehicleRidingEntity.isDriver()) {
				containsDriver = true;
			}
			if (vehicleRidingEntity.getDoorOverride()) {
				doorOverride = true;
			}
			if (containsDriver && doorOverride) {
				break;
			}
		}
		return new BooleanBooleanImmutablePair(containsDriver, doorOverride);
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

	boolean hasRidingEntity(UUID uuid) {
		return ridingEntities.stream().anyMatch(vehicleRidingEntity -> vehicleRidingEntity.uuid.equals(uuid));
	}

	public static VehicleExtraData create(
			long depotId, long sidingId, double railLength, ObjectArrayList<VehicleCar> vehicleCars,
			ObjectArrayList<PathData> pathSidingToMainRoute, ObjectArrayList<PathData> pathMainRoute, ObjectArrayList<PathData> pathMainRouteToSiding, PathData defaultPathData,
			boolean repeatInfinitely, double acceleration, double deceleration, boolean isManualAllowed, double maxManualSpeed, long manualToAutomaticTime
	) {
		final double newRailLength = Siding.getRailLength(railLength);
		final double newTotalVehicleLength = Siding.getTotalVehicleLength(vehicleCars);
		final ObjectArrayList<PathData> path = createPathData(pathSidingToMainRoute, pathMainRoute, pathMainRouteToSiding, repeatInfinitely, defaultPathData);
		final long repeatIndex1 = pathSidingToMainRoute.size();
		final long repeatIndex2 = repeatInfinitely ? repeatIndex1 + pathMainRoute.size() : 0;
		final double newAcceleration = Siding.roundAcceleration(acceleration);
		final double newDeceleration = Siding.roundAcceleration(deceleration);
		final double totalDistance = path.isEmpty() ? 0 : repeatInfinitely && repeatIndex2 < path.size() ? Utilities.getElement(path, (int) repeatIndex2).getStartDistance() : Utilities.getElement(path, -1).getEndDistance();
		final double defaultPosition = (newRailLength + newTotalVehicleLength) / 2;
		return new VehicleExtraData(depotId, sidingId, newRailLength, newTotalVehicleLength, repeatIndex1, repeatIndex2, newAcceleration, newDeceleration, isManualAllowed, Math.max(Utilities.kilometersPerHourToMetersPerMillisecond(1), maxManualSpeed), manualToAutomaticTime, totalDistance, defaultPosition, vehicleCars, path);
	}

	private static ObjectArrayList<PathData> createPathData(ObjectArrayList<PathData> pathSidingToMainRoute, ObjectArrayList<PathData> pathMainRoute, ObjectArrayList<PathData> pathMainRouteToSiding, boolean repeatInfinitely, PathData defaultPathData) {
		final ObjectArrayList<PathData> tempPath = new ObjectArrayList<>();
		if (pathSidingToMainRoute.isEmpty() || pathMainRoute.isEmpty() || !repeatInfinitely && pathMainRouteToSiding.isEmpty()) {
			tempPath.add(defaultPathData);
		} else {
			tempPath.addAll(pathSidingToMainRoute);
			tempPath.addAll(pathMainRoute);
			if (repeatInfinitely) {
				tempPath.add(new PathData(new PathData(new JsonReader(new JsonObject())), Utilities.getElement(pathMainRoute, -1).getEndDistance(), Double.MAX_VALUE));
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

	private static String getRouteNumber(@Nullable Route route) {
		return route == null ? "" : route.getRouteNumber();
	}

	private static RouteType getRouteType(@Nullable Route route) {
		return route == null ? RouteType.NORMAL : route.getRouteType();
	}

	private static Route.CircularState getRouteCircularState(@Nullable Route route) {
		return route == null ? Route.CircularState.NONE : route.getCircularState();
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
		private final Route nextRoute;
		private final int platformIndexInRoute;

		public VehiclePlatformRouteInfo(@Nullable Platform previousPlatform, @Nullable Platform thisPlatform, @Nullable Platform nextPlatform, @Nullable Route previousRoute, @Nullable Route thisRoute, @Nullable Route nextRoute, int platformIndexInRoute) {
			this.previousPlatform = previousPlatform;
			this.thisPlatform = thisPlatform;
			this.nextPlatform = nextPlatform;
			this.previousRoute = previousRoute;
			this.thisRoute = thisRoute;
			this.nextRoute = nextRoute;
			this.platformIndexInRoute = platformIndexInRoute;
		}
	}
}
