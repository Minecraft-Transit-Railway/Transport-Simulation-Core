package org.mtr.core.data;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.msgpack.core.MessagePacker;
import org.mtr.core.path.PathData;
import org.mtr.core.tools.Utilities;
import org.mtr.core.tools.Vec3;

import java.io.IOException;
import java.util.*;

public class Train extends NameColorDataBase {

	protected float speed;
	protected double railProgress;
	protected boolean doorTarget;
	protected float doorValue;
	protected float elapsedDwellMillis;
	protected int nextStoppingIndex;
	protected int nextPlatformIndex;
	protected boolean reversed;
	protected boolean isOnRoute = false;
	protected boolean isCurrentlyManual;
	protected int manualNotch;
	private boolean canDeploy;

	public final long sidingId;
	public final String baseVehicleType;
	public final String trainId;
	public final TransportMode transportMode;
	public final int spacing;
	public final int width;
	public final int trainCars;
	public final float accelerationConstant;
	public final boolean isManualAllowed;
	public final int maxManualSpeed;
	public final int manualToAutomaticTime;

	protected final ObjectArrayList<PathData> path = new ObjectArrayList<>();
	protected final List<Double> distances;
	protected final int repeatIndex1;
	protected final int repeatIndex2;
	protected final Set<UUID> ridingEntities = new HashSet<>();

	private final float railLength;

	public static final float ACCELERATION_DEFAULT = 0.01F; // m/tick^2
	public static final float MAX_ACCELERATION = 0.05F; // m/tick^2
	public static final float MIN_ACCELERATION = 0.001F; // m/tick^2
	public static final int DOOR_MOVE_TIME = 64;
	protected static final int MAX_CHECK_DISTANCE = 32;
	protected static final int DOOR_DELAY = 20;

	private static final String KEY_SPEED = "speed";
	private static final String KEY_RAIL_PROGRESS = "rail_progress";
	private static final String KEY_ELAPSED_DWELL_TICKS = "stop_counter";
	private static final String KEY_NEXT_STOPPING_INDEX = "next_stopping_index";
	private static final String KEY_NEXT_PLATFORM_INDEX = "next_platform_index";
	private static final String KEY_REVERSED = "reversed";
	private static final String KEY_IS_CURRENTLY_MANUAL = "is_currently_manual";
	private static final String KEY_IS_ON_ROUTE = "is_on_route";
	private static final String KEY_BASE_VEHICLE_TYPE = "train_type";
	private static final String KEY_TRAIN_ID = "train_custom_id";
	private static final String KEY_RIDING_ENTITIES = "riding_entities";

	public Train(
			long id, long sidingId, float railLength, String baseVehicleType, String trainId,
			ObjectArrayList<PathData> pathSidingToMainRoute, ObjectArrayList<PathData> pathMainRoute, ObjectArrayList<PathData> pathMainRouteToSiding, List<Double> distances,
			float accelerationConstant, boolean isManualAllowed, int maxManualSpeed, int manualToAutomaticTime
	) {
		super(id);

		this.sidingId = sidingId;
		this.railLength = Siding.getRailLength(railLength);
		this.baseVehicleType = baseVehicleType;
		this.trainId = trainId;

		transportMode = VehicleType.getTransportMode(baseVehicleType);
		spacing = VehicleType.getSpacing(baseVehicleType);
		width = VehicleType.getWidth(baseVehicleType);
		trainCars = Siding.getVehicleCars(transportMode, railLength, spacing);

		path.addAll(pathSidingToMainRoute);
		path.addAll(pathMainRoute);
		path.addAll(pathMainRouteToSiding);
		this.distances = distances;
		repeatIndex1 = pathSidingToMainRoute.size();
		repeatIndex2 = repeatIndex1 + pathMainRoute.size();

		final float tempAccelerationConstant = Utilities.round(accelerationConstant, 3);
		this.accelerationConstant = tempAccelerationConstant <= 0 ? ACCELERATION_DEFAULT : tempAccelerationConstant;
		this.isManualAllowed = isManualAllowed;
		this.maxManualSpeed = maxManualSpeed;
		this.manualToAutomaticTime = manualToAutomaticTime;

		isCurrentlyManual = isManualAllowed;
	}

	public Train(
			long sidingId, float railLength,
			ObjectArrayList<PathData> pathSidingToMainRoute, ObjectArrayList<PathData> pathMainRoute, ObjectArrayList<PathData> pathMainRouteToSiding, List<Double> distances,
			float accelerationConstant, boolean isManualAllowed, int maxManualSpeed, int manualToAutomaticTime,
			MessagePackHelper messagePackHelper
	) {
		super(messagePackHelper);

		this.sidingId = sidingId;
		this.railLength = Siding.getRailLength(railLength);

		path.addAll(pathSidingToMainRoute);
		path.addAll(pathMainRoute);
		path.addAll(pathMainRouteToSiding);
		this.distances = distances;
		repeatIndex1 = pathSidingToMainRoute.size();
		repeatIndex2 = repeatIndex1 + pathMainRoute.size();

		this.accelerationConstant = accelerationConstant;
		this.isManualAllowed = isManualAllowed;
		this.maxManualSpeed = maxManualSpeed;
		this.manualToAutomaticTime = manualToAutomaticTime;

		baseVehicleType = messagePackHelper.getString(KEY_BASE_VEHICLE_TYPE, "").toLowerCase(Locale.ENGLISH);
		final String tempTrainId = messagePackHelper.getString(KEY_TRAIN_ID, "").toLowerCase(Locale.ENGLISH);
		trainId = tempTrainId.isEmpty() ? baseVehicleType : tempTrainId;
		transportMode = VehicleType.getTransportMode(baseVehicleType);
		spacing = VehicleType.getSpacing(baseVehicleType);
		width = VehicleType.getWidth(baseVehicleType);
		trainCars = Siding.getVehicleCars(transportMode, railLength, spacing);
	}

	@Override
	public void updateData(MessagePackHelper messagePackHelper) {
		super.updateData(messagePackHelper);

		messagePackHelper.unpackFloat(KEY_SPEED, value -> speed = value);
		messagePackHelper.unpackDouble(KEY_RAIL_PROGRESS, value -> railProgress = value);
		messagePackHelper.unpackFloat(KEY_ELAPSED_DWELL_TICKS, value -> elapsedDwellMillis = value);
		messagePackHelper.unpackInt(KEY_NEXT_STOPPING_INDEX, value -> nextStoppingIndex = value);
		messagePackHelper.unpackInt(KEY_NEXT_PLATFORM_INDEX, value -> nextPlatformIndex = value);
		messagePackHelper.unpackBoolean(KEY_REVERSED, value -> reversed = value);
		messagePackHelper.unpackBoolean(KEY_IS_CURRENTLY_MANUAL, value -> isCurrentlyManual = value);
		messagePackHelper.unpackBoolean(KEY_IS_ON_ROUTE, value -> isOnRoute = value);
		messagePackHelper.iterateArrayValue(KEY_RIDING_ENTITIES, value -> ridingEntities.add(UUID.fromString(value.asStringValue().asString())));
	}

	@Override
	public void toMessagePack(MessagePacker messagePacker) throws IOException {
		super.toMessagePack(messagePacker);

		messagePacker.packString(KEY_SPEED).packFloat(speed);
		messagePacker.packString(KEY_RAIL_PROGRESS).packDouble(railProgress);
		messagePacker.packString(KEY_ELAPSED_DWELL_TICKS).packFloat(elapsedDwellMillis);
		messagePacker.packString(KEY_NEXT_STOPPING_INDEX).packLong(nextStoppingIndex);
		messagePacker.packString(KEY_NEXT_PLATFORM_INDEX).packLong(nextPlatformIndex);
		messagePacker.packString(KEY_REVERSED).packBoolean(reversed);
		messagePacker.packString(KEY_TRAIN_ID).packString(trainId);
		messagePacker.packString(KEY_BASE_VEHICLE_TYPE).packString(baseVehicleType);
		messagePacker.packString(KEY_IS_CURRENTLY_MANUAL).packBoolean(isCurrentlyManual);
		messagePacker.packString(KEY_IS_ON_ROUTE).packBoolean(isOnRoute);
		messagePacker.packString(KEY_RIDING_ENTITIES).packArrayHeader(ridingEntities.size());
		for (final UUID uuid : ridingEntities) {
			messagePacker.packString(uuid.toString());
		}
	}

	@Override
	public int messagePackLength() {
		return super.messagePackLength() + 11;
	}

	@Override
	protected final boolean hasTransportMode() {
		return false;
	}

	public final boolean getIsOnRoute() {
		return isOnRoute;
	}

	public final double getRailProgress() {
		return railProgress;
	}

	public final boolean closeToDepot(int trainDistance) {
		return !isOnRoute || railProgress < trainDistance + railLength;
	}

	public final boolean isCurrentlyManual() {
		return isCurrentlyManual;
	}

	public boolean changeManualSpeed(boolean isAccelerate) {
		if (doorValue == 0 && isAccelerate && manualNotch >= -2 && manualNotch < 2) {
			manualNotch++;
			return true;
		} else if (!isAccelerate && manualNotch > -2) {
			manualNotch--;
			return true;
		} else {
			return false;
		}
	}

	public boolean toggleDoors() {
		if (speed == 0) {
			doorTarget = !doorTarget;
			manualNotch = -2;
			return true;
		} else {
			doorTarget = false;
			return false;
		}
	}

	public final int getIndex(int car, int trainSpacing, boolean roundDown) {
		return getIndex(getRailProgress(car, trainSpacing), roundDown);
	}

	public final int getIndex(double tempRailProgress, boolean roundDown) {
		for (int i = 0; i < path.size(); i++) {
			final double tempDistance = distances.get(i);
			if (tempRailProgress < tempDistance || roundDown && tempRailProgress == tempDistance) {
				return i;
			}
		}
		return path.size() - 1;
	}

	public final float getRailSpeed(int railIndex) {
		final RailType thisRail = path.get(railIndex).rail.railType;
		final float railSpeed;
		if (thisRail.canAccelerate) {
			railSpeed = thisRail.speedLimitMetersPerSecond;
		} else {
			final RailType lastRail = railIndex > 0 ? path.get(railIndex - 1).rail.railType : thisRail;
			railSpeed = Math.max(lastRail.canAccelerate ? lastRail.speedLimitMetersPerSecond : RailType.getDefaultMaxMetersPerSecond(transportMode), speed);
		}
		return railSpeed;
	}

	public final float getSpeed() {
		return speed;
	}

	public final float getDoorValue() {
		return doorValue;
	}

	public final float getElapsedDwellMillis() {
		return elapsedDwellMillis;
	}

	public final boolean isReversed() {
		return reversed;
	}

	public final boolean isOnRoute() {
		return isOnRoute;
	}

	public int getTotalDwellMillis() {
		return path.get(nextStoppingIndex).dwellTime * 500;
	}

	public void deployTrain() {
		canDeploy = true;
	}

	protected final void simulateTrain(float ticksElapsed, Depot depot) {
		try {
			if (nextStoppingIndex >= path.size()) {
				return;
			}

			final boolean tempDoorOpen;
			final float tempDoorValue;
			final int totalDwellTicks = getTotalDwellMillis();

			if (!isOnRoute) {
				railProgress = (railLength + trainCars * spacing) / 2;
				reversed = false;
				tempDoorOpen = false;
				tempDoorValue = 0;
				speed = 0;
				nextStoppingIndex = 0;

				if (!isCurrentlyManual && canDeploy(depot) || isCurrentlyManual && manualNotch > 0) {
					startUp(trainCars, spacing, isOppositeRail());
				}
			} else {
				final float newAcceleration = accelerationConstant * ticksElapsed;

				if (railProgress >= distances.get(distances.size() - 1) - (railLength - trainCars * spacing) / 2) {
					isOnRoute = false;
					manualNotch = -2;
					ridingEntities.clear();
					tempDoorOpen = false;
					tempDoorValue = 0;
				} else {
					if (speed <= 0) {
						speed = 0;

						final boolean isOppositeRail = isOppositeRail();
						final boolean railBlocked = isRailBlocked(getIndex(0, spacing, true) + (isOppositeRail ? 2 : 1));

						if (totalDwellTicks == 0) {
							tempDoorOpen = false;
						} else {
							if (elapsedDwellMillis == 0 && isRepeat() && getIndex(railProgress, false) >= repeatIndex2 && distances.size() > repeatIndex1) {
								if (path.get(repeatIndex2).isOppositeRail(path.get(repeatIndex1))) {
									railProgress = distances.get(repeatIndex1 - 1) + trainCars * spacing;
									reversed = !reversed;
								} else {
									railProgress = distances.get(repeatIndex1);
								}
							}

							if (elapsedDwellMillis < totalDwellTicks - DOOR_MOVE_TIME - DOOR_DELAY - ticksElapsed || !railBlocked) {
								elapsedDwellMillis += ticksElapsed;
							}

							tempDoorOpen = openDoors();
						}

						if ((isCurrentlyManual || elapsedDwellMillis >= totalDwellTicks) && !railBlocked && (!isCurrentlyManual || manualNotch > 0)) {
							startUp(trainCars, spacing, isOppositeRail);
						}
					} else {
						final int checkIndex = getIndex(0, spacing, true) + 1;
						if (isRailBlocked(checkIndex)) {
							nextStoppingIndex = checkIndex - 1;
						} else if (nextPlatformIndex > 0 && nextPlatformIndex < path.size()) {
							nextStoppingIndex = nextPlatformIndex;
							if (manualNotch < -2) {
								manualNotch = 0;
							}
						}

						final double stoppingDistance = distances.get(nextStoppingIndex) - railProgress;
						if (!transportMode.continuousMovement && stoppingDistance < 0.5 * speed * speed / accelerationConstant) {
							speed = stoppingDistance <= 0 ? Train.ACCELERATION_DEFAULT : (float) Math.max(speed - (0.5 * speed * speed / stoppingDistance) * ticksElapsed, Train.ACCELERATION_DEFAULT);
							manualNotch = -3;
						} else {
							if (isCurrentlyManual) {
								if (manualNotch >= -2) {
									final RailType railType = convertMaxManualSpeed(maxManualSpeed);
									speed = Utilities.clamp(speed + manualNotch * newAcceleration / 2, 0, railType == null ? RailType.IRON.speedLimitMetersPerSecond : railType.speedLimitMetersPerSecond);
								}
							} else {
								final float railSpeed = getRailSpeed(getIndex(0, spacing, false));
								if (speed < railSpeed) {
									speed = Math.min(speed + newAcceleration, railSpeed);
									manualNotch = 2;
								} else if (speed > railSpeed) {
									speed = Math.max(speed - newAcceleration, railSpeed);
									manualNotch = -2;
								} else {
									manualNotch = 0;
								}
							}
						}

						tempDoorOpen = transportMode.continuousMovement && openDoors();
					}

					railProgress += speed * ticksElapsed;
					if (!transportMode.continuousMovement && railProgress > distances.get(nextStoppingIndex)) {
						railProgress = distances.get(nextStoppingIndex);
						speed = 0;
						manualNotch = -2;
					}

					tempDoorValue = Utilities.clamp(doorValue + ticksElapsed * (doorTarget ? 1 : -1) / DOOR_MOVE_TIME, 0, 1);
				}
			}

			doorTarget = tempDoorOpen;
			doorValue = tempDoorValue;
			if (doorTarget || doorValue != 0) {
				manualNotch = -2;
			}

			if (!path.isEmpty()) {
				final Vec3[] positions = new Vec3[trainCars + 1];
				for (int i = 0; i <= trainCars; i++) {
					positions[i] = getRoutePosition(reversed ? trainCars - i : i, spacing);
				}

				if (handlePositions(positions, ticksElapsed)) {
					final double[] prevX = {0};
					final double[] prevY = {0};
					final double[] prevZ = {0};
					final float[] prevYaw = {0};
					final float[] prevPitch = {0};

					for (int i = 0; i < trainCars; i++) {
						final int ridingCar = i;
						calculateCar(positions, i, totalDwellTicks, (x, y, z, yaw, pitch, realSpacing) -> {
							simulateCar(
									ridingCar, ticksElapsed,
									x, y, z,
									yaw, pitch,
									prevX[0], prevY[0], prevZ[0],
									prevYaw[0], prevPitch[0], realSpacing
							);
							prevX[0] = x;
							prevY[0] = y;
							prevZ[0] = z;
							prevYaw[0] = yaw;
							prevPitch[0] = pitch;
						});
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	protected final void calculateCar(Vec3[] positions, int index, int dwellTicks, CalculateCarCallback calculateCarCallback) {
		final Vec3 pos1 = positions[index];
		final Vec3 pos2 = positions[index + 1];

		if (pos1 != null && pos2 != null) {
			final double x = getAverage(pos1.x, pos2.x);
			final double y = getAverage(pos1.y, pos2.y) + 1;
			final double z = getAverage(pos1.z, pos2.z);

			final double realSpacing = pos2.distanceTo(pos1);
			final float yaw = (float) Math.atan2(pos2.x - pos1.x, pos2.z - pos1.z);
			final float pitch = realSpacing == 0 ? 0 : (float) Math.asin((pos2.y - pos1.y) / realSpacing);

			calculateCarCallback.calculateCarCallback(x, y, z, yaw, pitch, realSpacing);
		}
	}

	protected void startUp(int trainCars, int trainSpacing, boolean isOppositeRail) {
		canDeploy = false;
		isOnRoute = true;
		elapsedDwellMillis = 0;
		speed = Train.ACCELERATION_DEFAULT;
		if (isOppositeRail) {
			railProgress += trainCars * trainSpacing;
			reversed = !reversed;
		}
		nextStoppingIndex = getNextStoppingIndex();
		doorTarget = false;
		doorValue = 0;
		nextPlatformIndex = nextStoppingIndex;
	}

	protected boolean canDeploy(Depot depot) {
		if (path.size() > 1 && depot != null) {
			depot.requestDeploy(sidingId, this);
		}
		return canDeploy;
	}

	protected boolean openDoors() {
		return doorTarget;
	}

	protected float getModelZOffset() {
		return 0;
	}

	protected boolean isRepeat() {
		return repeatIndex1 > 0 && repeatIndex2 > 0;
	}

	protected void simulateCar(
			int ridingCar, float ticksElapsed,
			double carX, double carY, double carZ, float carYaw, float carPitch,
			double prevCarX, double prevCarY, double prevCarZ, float prevCarYaw, float prevCarPitch, double realSpacing
	) {
	}

	protected boolean handlePositions(Vec3[] positions, float ticksElapsed) {
		return true;
	}

	protected boolean isRailBlocked(int checkIndex) {
		return false;
	}

	private boolean isOppositeRail() {
		return path.size() > nextStoppingIndex + 1 && railProgress == distances.get(nextStoppingIndex) && path.get(nextStoppingIndex).isOppositeRail(path.get(nextStoppingIndex + 1));
	}

	private double getRailProgress(int car, int trainSpacing) {
		return railProgress - car * trainSpacing;
	}

	private Vec3 getRoutePosition(int car, int trainSpacing) {
		final double tempRailProgress = Math.max(getRailProgress(car, trainSpacing) - getModelZOffset(), 0);
		final int index = getIndex(tempRailProgress, false);
		return path.get(index).rail.getPosition(tempRailProgress - (index == 0 ? 0 : distances.get(index - 1))).add(0, transportMode.railOffset, 0);
	}

	private int getNextStoppingIndex() {
		final int headIndex = getIndex(0, 0, false);
		for (int i = headIndex; i < path.size(); i++) {
			if (path.get(i).dwellTime > 0) {
				return i;
			}
		}
		return path.size() - 1;
	}

	public static double getAverage(double a, double b) {
		return (a + b) / 2;
	}

	public static RailType convertMaxManualSpeed(int maxManualSpeed) {
		if (maxManualSpeed >= 0 && maxManualSpeed <= RailType.DIAMOND.ordinal()) {
			return RailType.values()[maxManualSpeed];
		} else {
			return null;
		}
	}

	@FunctionalInterface
	protected interface CalculateCarCallback {
		void calculateCarCallback(double x, double y, double z, float yaw, float pitch, double realSpacing);
	}
}
