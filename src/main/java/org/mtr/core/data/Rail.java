package org.mtr.core.data;

import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.mtr.core.generated.RailSchema;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.tools.Angle;
import org.mtr.core.tools.DataFixer;
import org.mtr.core.tools.Position;
import org.mtr.core.tools.Utilities;

import javax.annotation.Nullable;

public final class Rail extends RailSchema implements SerializedDataBaseWithId {

	public final double speedLimit1MetersPerMillisecond;
	public final double speedLimit2MetersPerMillisecond;
	public final RailMath railMath;

	private final ObjectOpenHashSet<Rail> connectedRails1 = new ObjectOpenHashSet<>();
	private final ObjectOpenHashSet<Rail> connectedRails2 = new ObjectOpenHashSet<>();
	private final LongAVLTreeSet blockedVehicleIds = new LongAVLTreeSet();
	private final LongAVLTreeSet blockedVehicleIdsOld = new LongAVLTreeSet();
	private final boolean reversePositions;

	public static Rail newRail(Position position1, Angle angle1, Shape shape1, Position position2, Angle angle2, Shape shape2, long speedLimit1, long speedLimit2, boolean isPlatform, boolean isSiding, boolean canAccelerate, boolean canHaveSignal, TransportMode transportMode) {
		return new Rail(position1, angle1, shape1, position2, angle2, shape2, speedLimit1, speedLimit2, isPlatform, isSiding, canAccelerate, false, canHaveSignal, transportMode);
	}

	public static Rail newTurnBackRail(Position position1, Angle angle1, Shape shape1, Position position2, Angle angle2, Shape shape2, TransportMode transportMode) {
		return new Rail(position1, angle1, shape1, position2, angle2, shape2, 80, 80, false, false, false, true, false, transportMode);
	}

	public static Rail newPlatformRail(Position position1, Angle angle1, Shape shape1, Position position2, Angle angle2, Shape shape2, TransportMode transportMode) {
		return newPlatformOrSidingRail(position1, angle1, shape1, position2, angle2, shape2, true, transportMode);
	}

	public static Rail newSidingRail(Position position1, Angle angle1, Shape shape1, Position position2, Angle angle2, Shape shape2, TransportMode transportMode) {
		return newPlatformOrSidingRail(position1, angle1, shape1, position2, angle2, shape2, false, transportMode);
	}

	private static Rail newPlatformOrSidingRail(Position position1, Angle angle1, Shape shape1, Position position2, Angle angle2, Shape shape2, boolean isPlatform, TransportMode transportMode) {
		final long speedLimit = isPlatform ? 80 : 40;
		return new Rail(position1, angle1, shape1, position2, angle2, shape2, speedLimit, speedLimit, isPlatform, !isPlatform, false, false, true, transportMode);
	}

	private Rail(
			Position position1, Angle angle1, Rail.Shape shape1,
			Position position2, Angle angle2, Rail.Shape shape2,
			long speedLimit1, long speedLimit2,
			boolean isPlatform, boolean isSiding, boolean canAccelerate, boolean canTurnBack, boolean canHaveSignal, TransportMode transportMode
	) {
		super(position1, angle1, shape1, position2, angle2, shape2, speedLimit1, speedLimit2, isPlatform, isSiding, canAccelerate, canTurnBack, canHaveSignal, transportMode);
		reversePositions = position1.compareTo(position2) > 0;
		railMath = reversePositions ? new RailMath(position2, angle2, shape2, position1, angle1, shape1) : new RailMath(position1, angle1, shape1, position2, angle2, shape2);
		speedLimit1MetersPerMillisecond = Utilities.kilometersPerHourToMetersPerMillisecond(speedLimit1);
		speedLimit2MetersPerMillisecond = Utilities.kilometersPerHourToMetersPerMillisecond(speedLimit2);
	}

	public Rail(ReaderBase readerBase) {
		super(DataFixer.convertRail(readerBase));
		reversePositions = position1.compareTo(position2) > 0;
		railMath = reversePositions ? new RailMath(position2, angle2, shape2, position1, angle1, shape1) : new RailMath(position1, angle1, shape1, position2, angle2, shape2);
		speedLimit1MetersPerMillisecond = Utilities.kilometersPerHourToMetersPerMillisecond(speedLimit1);
		speedLimit2MetersPerMillisecond = Utilities.kilometersPerHourToMetersPerMillisecond(speedLimit2);
		updateData(readerBase);
	}

	@Override
	public String getHexId() {
		return String.format(
				"%s-%s-%s-%s-%s-%s",
				Utilities.numberToPaddedHexString((reversePositions ? position2 : position1).getX()),
				Utilities.numberToPaddedHexString((reversePositions ? position2 : position1).getY()),
				Utilities.numberToPaddedHexString((reversePositions ? position2 : position1).getZ()),
				Utilities.numberToPaddedHexString((reversePositions ? position1 : position2).getX()),
				Utilities.numberToPaddedHexString((reversePositions ? position1 : position2).getY()),
				Utilities.numberToPaddedHexString((reversePositions ? position1 : position2).getZ())
		);
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

	public boolean closeTo(Position position, double radius) {
		return Utilities.isBetween(position, position1, position2, radius);
	}

	public void tick() {
		blockedVehicleIdsOld.clear();
		blockedVehicleIdsOld.addAll(blockedVehicleIds);
		blockedVehicleIds.clear();
	}

	@Nullable
	public Rail getRailFromData(Data data, ObjectOpenHashSet<Position> positionsToUpdate) {
		final Rail rail = data.railIdMap.get(getHexId());
		if (rail != null) {
			positionsToUpdate.add(position1);
			positionsToUpdate.add(position2);
		}
		return rail;
	}

	public void checkOrCreatePlatform(Data data, ObjectAVLTreeSet<Platform> platformsToAdd, ObjectAVLTreeSet<Siding> sidingsToAdd) {
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

	boolean isBlocked(long vehicleId) {
		return isBlocked(vehicleId, signalColors, ObjectOpenHashSet.of(this));
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
		positionsToRail.getOrDefault(position, new Object2ObjectOpenHashMap<>()).forEach((connectedPosition, rail) -> {
			if (!equals(rail)) {
				connectedRails.add(rail);
			}
		});
	}

	private boolean isBlocked(long vehicleId, LongArrayList blockedColors, ObjectOpenHashSet<Rail> visitedRails) {
		if (!blockedColors.isEmpty() && isNotBlocked(blockedVehicleIds, vehicleId) && isNotBlocked(blockedVehicleIdsOld, vehicleId)) {
			blockedVehicleIds.add(vehicleId);
			connectedRails1.forEach(rail -> tryBlock(vehicleId, blockedColors, visitedRails, rail));
			connectedRails2.forEach(rail -> tryBlock(vehicleId, blockedColors, visitedRails, rail));
			return true;
		} else {
			return false;
		}
	}

	private static void tryBlock(long vehicleId, LongArrayList blockedColors, ObjectOpenHashSet<Rail> visitedRails, Rail rail) {
		if (!visitedRails.contains(rail)) {
			visitedRails.add(rail);
			final LongArrayList newBlockedColors = new LongArrayList();
			rail.signalColors.forEach(color -> {
				if (blockedColors.contains(color)) {
					newBlockedColors.add(color);
				}
			});
			rail.isBlocked(vehicleId, newBlockedColors, visitedRails);
		}
	}

	private static boolean isNotBlocked(LongAVLTreeSet blockedVehicleIds, long vehicleId) {
		return blockedVehicleIds.isEmpty() || blockedVehicleIds.size() == 1 && blockedVehicleIds.firstLong() == vehicleId;
	}

	@FunctionalInterface
	public interface RenderRail {
		void renderRail(double x1, double z1, double x2, double z2, double x3, double z3, double x4, double z4, double y1, double y2);
	}

	public enum Shape {CURVE, STRAIGHT}
}
