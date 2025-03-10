package org.mtr.core.data;

import org.mtr.core.generated.data.RailSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Utilities;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.longs.Long2LongAVLTreeMap;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import it.unimi.dsi.fastutil.objects.*;

public final class Rail extends RailSchema {

	public final double speedLimit1MetersPerMillisecond;
	public final double speedLimit2MetersPerMillisecond;
	public final RailMath railMath;

	private final ObjectOpenHashSet<Rail> connectedRails1 = new ObjectOpenHashSet<>();
	private final ObjectOpenHashSet<Rail> connectedRails2 = new ObjectOpenHashSet<>();
	private final Long2LongAVLTreeMap blockedVehicleIds = new Long2LongAVLTreeMap();
	private final Long2LongAVLTreeMap blockedVehicleIdsOld = new Long2LongAVLTreeMap();
	private final boolean reversePositions;

	public static Rail newRail(Position position1, Angle angle1, Position position2, Angle angle2, Shape shape, double verticalRadius, ObjectArrayList<String> styles, long speedLimit1, long speedLimit2, boolean isPlatform, boolean isSiding, boolean canAccelerate, boolean canConnectRemotely, boolean canHaveSignal, TransportMode transportMode) {
		return new Rail(position1, angle1, position2, angle2, shape, verticalRadius, styles, speedLimit1, speedLimit2, isPlatform, isSiding, canAccelerate, false, canConnectRemotely, canHaveSignal, transportMode);
	}

	public static Rail newTurnBackRail(Position position1, Angle angle1, Position position2, Angle angle2, Shape shape, double verticalRadius, ObjectArrayList<String> styles, TransportMode transportMode) {
		return new Rail(position1, angle1, position2, angle2, shape, verticalRadius, styles, 80, 80, false, false, false, true, false, false, transportMode);
	}

	public static Rail newPlatformRail(Position position1, Angle angle1, Position position2, Angle angle2, Shape shape, double verticalRadius, ObjectArrayList<String> styles, TransportMode transportMode) {
		return newPlatformOrSidingRail(position1, angle1, position2, angle2, shape, verticalRadius, styles, true, transportMode);
	}

	public static Rail newSidingRail(Position position1, Angle angle1, Position position2, Angle angle2, Shape shape, double verticalRadius, ObjectArrayList<String> styles, TransportMode transportMode) {
		return newPlatformOrSidingRail(position1, angle1, position2, angle2, shape, verticalRadius, styles, false, transportMode);
	}

	private static Rail newPlatformOrSidingRail(Position position1, Angle angle1, Position position2, Angle angle2, Shape shape, double verticalRadius, ObjectArrayList<String> styles, boolean isPlatform, TransportMode transportMode) {
		final long speedLimit = isPlatform ? 80 : 40;
		return new Rail(position1, angle1, position2, angle2, shape, verticalRadius, styles, speedLimit, speedLimit, isPlatform, !isPlatform, false, false, false, true, transportMode);
	}

	public static Rail copy(Rail rail, Shape newShape, double newVerticalRadius) {
		return new Rail(
				rail.position1, rail.angle1,
				rail.position2, rail.angle2,
				newShape, newVerticalRadius, rail.styles, rail.speedLimit1, rail.speedLimit2,
				rail.isPlatform, rail.isSiding, rail.canAccelerate, rail.canTurnBack, rail.canConnectRemotely, rail.canHaveSignal, rail.transportMode
		);
	}

	public static Rail copy(Rail rail, ObjectArrayList<String> newStyles) {
		return new Rail(
				rail.position1, rail.angle1,
				rail.position2, rail.angle2,
				rail.shape, rail.verticalRadius, newStyles, rail.speedLimit1, rail.speedLimit2,
				rail.isPlatform, rail.isSiding, rail.canAccelerate, rail.canTurnBack, rail.canConnectRemotely, rail.canHaveSignal, rail.transportMode
		);
	}

	private Rail(
			Position position1, Angle angle1,
			Position position2, Angle angle2,
			Rail.Shape shape, double verticalRadius, ObjectArrayList<String> styles, long speedLimit1, long speedLimit2,
			boolean isPlatform, boolean isSiding, boolean canAccelerate, boolean canTurnBack, boolean canConnectRemotely, boolean canHaveSignal, TransportMode transportMode
	) {
		super(position1, angle1, position2, angle2, shape, verticalRadius, speedLimit1, speedLimit2, isPlatform, isSiding, canAccelerate, canTurnBack, canConnectRemotely, canHaveSignal, transportMode);
		reversePositions = position1.compareTo(position2) > 0;
		railMath = reversePositions ? new RailMath(position2, angle2, position1, angle1, shape, verticalRadius) : new RailMath(position1, angle1, position2, angle2, shape, verticalRadius);
		speedLimit1MetersPerMillisecond = Utilities.kilometersPerHourToMetersPerMillisecond(speedLimit1);
		speedLimit2MetersPerMillisecond = Utilities.kilometersPerHourToMetersPerMillisecond(speedLimit2);
		this.styles.addAll(styles);
		stylesMigratedLegacy = true;
	}

	public Rail(ReaderBase readerBase) {
		super(readerBase);
		reversePositions = position1.compareTo(position2) > 0;
		railMath = reversePositions ? new RailMath(position2, angle2, position1, angle1, shape, verticalRadius) : new RailMath(position1, angle1, position2, angle2, shape, verticalRadius);
		speedLimit1MetersPerMillisecond = Utilities.kilometersPerHourToMetersPerMillisecond(speedLimit1);
		speedLimit2MetersPerMillisecond = Utilities.kilometersPerHourToMetersPerMillisecond(speedLimit2);
		updateData(readerBase);
	}

	/**
	 * A rail is valid if all the following conditions are met:
	 * <ul>
	 * <li>At least one of the speeds must be greater than 0</li>
	 * <li>Rail shape must be valid</li>
	 * <li>The rail is either a platform, a siding, neither, but not both</li>
	 * </ul>
	 *
	 * @return whether the above conditions are met
	 */
	@Override
	public boolean isValid() {
		return (speedLimit1 > 0 || speedLimit2 > 0) && railMath.isValid() && (!isPlatform || !isSiding);
	}

	@Override
	protected Position getPosition1() {
		return position1;
	}

	@Override
	protected Position getPosition2() {
		return position2;
	}

	public TransportMode getTransportMode() {
		return transportMode;
	}

	public Angle getStartAngle(boolean reversed) {
		return reversePositions == reversed ? angle1 : angle2;
	}

	public Angle getStartAngle(Position startPosition) {
		return position1.equals(startPosition) ? angle1 : angle2;
	}

	public double getSpeedLimitMetersPerMillisecond(boolean reversed) {
		return reversePositions == reversed ? speedLimit1MetersPerMillisecond : speedLimit2MetersPerMillisecond;
	}

	public double getSpeedLimitMetersPerMillisecond(Position startPosition) {
		return position1.equals(startPosition) ? speedLimit1MetersPerMillisecond : speedLimit2MetersPerMillisecond;
	}

	public long getSpeedLimitKilometersPerHour(boolean reversed) {
		return reversePositions == reversed ? speedLimit1 : speedLimit2;
	}

	public boolean canAccelerate() {
		return canAccelerate;
	}

	public boolean isPlatform() {
		return isPlatform;
	}

	public boolean isSiding() {
		return isSiding;
	}

	public boolean canTurnBack() {
		return canTurnBack;
	}

	public boolean canConnectRemotely() {
		return canConnectRemotely;
	}

	public boolean closeTo(Position position, double radius) {
		final long halfDistance = position1.manhattanDistance(position2) / 2;
		final Position centerPosition = new Position((position1.getX() + position2.getX()) / 2, (position1.getY() + position2.getY()) / 2, (position1.getZ() + position2.getZ()) / 2);
		return Utilities.isBetween(position, centerPosition.offset(-halfDistance, -halfDistance, -halfDistance), centerPosition.offset(halfDistance, halfDistance, halfDistance), radius);
	}

	public void tick(Simulator simulator) {
		final boolean needsUpdate = !Utilities.sameItems(blockedVehicleIds.keySet(), blockedVehicleIdsOld.keySet());
		simulator.clients.values().forEach(client -> {
			if (closeTo(client.getPosition(), client.getUpdateRadius())) {
				client.update(this, needsUpdate);
			}
		});

		blockedVehicleIdsOld.clear();
		blockedVehicleIdsOld.putAll(blockedVehicleIds);
		blockedVehicleIds.clear();
	}

	public void checkOrCreateSavedRail(Data data, ObjectArrayList<Platform> platformsToAdd, ObjectArrayList<Siding> sidingsToAdd) {
		if (isPlatform && data.platforms.stream().noneMatch(platform -> platform.containsPos(position1) && platform.containsPos(position2))) {
			final Platform platform = new Platform(position1, position2, transportMode, data);
			data.platforms.add(platform);
			platformsToAdd.add(platform);
		}
		if (isSiding && data.sidings.stream().noneMatch(siding -> siding.containsPos(position1) && siding.containsPos(position2))) {
			final Siding siding = new Siding(position1, position2, railMath.getLength(), transportMode, data);
			data.sidings.add(siding);
			sidingsToAdd.add(siding);
		}
	}

	public ObjectImmutableList<String> getStyles() {
		return new ObjectImmutableList<>(styles);
	}

	public IntAVLTreeSet getSignalColors() {
		final IntAVLTreeSet returnSet = new IntAVLTreeSet();
		signalColors.forEach(color -> returnSet.add((int) color));
		return returnSet;
	}

	public void iterateBlockedSignalColors(LongConsumer consumer) {
		blockedVehicleIds.keySet().forEach(consumer);
	}

	public void applyModification(SignalModification signalModification) {
		if (matchesPositions(signalModification)) {
			if (signalModification.getIsClearAll()) {
				signalColors.clear();
			} else {
				signalColors.removeIf(signalModification.getSignalColorsRemove()::contains);
			}
			signalModification.getSignalColorsAdd().forEach(signalColors::add);
		}
	}

	/**
	 * If the rail hasn't been migrated yet, add the default style (to prevent it from not rendering)
	 */
	public void checkMigrationStatus() {
		if (!stylesMigratedLegacy && styles.isEmpty()) {
			styles.add("default");
			stylesMigratedLegacy = true;
		}
	}

	boolean isBlocked(long vehicleId, boolean reserveRail) {
		if (signalColors.isEmpty() || isNotBlocked(blockedVehicleIds, vehicleId) && isNotBlocked(blockedVehicleIdsOld, vehicleId)) {
			if (reserveRail) {
				signalColors.forEach(color -> reserveRail(vehicleId, color, new ObjectOpenHashSet<>(), this));
			}
			return false;
		} else {
			return true;
		}
	}

	void writePositionsToRailCache(Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> positionsToRail) {
		Data.put(positionsToRail, position1, position2, oldValue -> this, Object2ObjectOpenHashMap::new);
		Data.put(positionsToRail, position2, position1, oldValue -> this, Object2ObjectOpenHashMap::new);
	}

	void writeConnectedRailsCacheFromMap(Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> positionsToRail) {
		writeConnectedRailsCacheFromMap(positionsToRail, position1, connectedRails1);
		writeConnectedRailsCacheFromMap(positionsToRail, position2, connectedRails2);
	}

	private void writeConnectedRailsCacheFromMap(Object2ObjectOpenHashMap<Position, Object2ObjectOpenHashMap<Position, Rail>> positionsToRail, Position position, ObjectOpenHashSet<Rail> connectedRails) {
		connectedRails.clear();
		positionsToRail.getOrDefault(position, new Object2ObjectOpenHashMap<>()).forEach((connectedPosition, rail) -> {
			if (!equals(rail)) {
				connectedRails.add(rail);
			}
		});
	}

	/**
	 * A helper method that can be used for finding appropriate angles when creating rails.
	 *
	 * @return angles that make sense for the two supplied positions
	 */
	public static ObjectObjectImmutablePair<Angle, Angle> getAngles(Position positionStart, float angle1, Position positionEnd, float angle2) {
		final float angleDifference = (float) Math.toDegrees(Math.atan2(positionEnd.getZ() - positionStart.getZ(), positionEnd.getX() - positionStart.getX()));
		return new ObjectObjectImmutablePair<>(
				Angle.fromAngle(angle1 + (Angle.similarFacing(angleDifference, angle1) ? 0 : 180)),
				Angle.fromAngle(angle2 + (Angle.similarFacing(angleDifference, angle2) ? 180 : 0))
		);
	}

	private static void reserveRail(long vehicleId, long color, ObjectOpenHashSet<Rail> visitedRails, Rail rail) {
		if (!visitedRails.contains(rail) && rail.signalColors.contains(color)) {
			rail.blockedVehicleIds.put(color, vehicleId);
			visitedRails.add(rail);
			rail.connectedRails1.forEach(connectedRail -> reserveRail(vehicleId, color, visitedRails, connectedRail));
			rail.connectedRails2.forEach(connectedRail -> reserveRail(vehicleId, color, visitedRails, connectedRail));
		}
	}

	private static boolean isNotBlocked(Long2LongAVLTreeMap blockedVehicleIds, long vehicleId) {
		return blockedVehicleIds.values().longStream().allMatch(blockedVehicleId -> blockedVehicleId == vehicleId);
	}

	public enum Shape {QUADRATIC, TWO_RADII, CABLE}
}
