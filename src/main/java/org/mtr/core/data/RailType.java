package org.mtr.core.data;

public enum RailType {
	WOODEN(20, false, true, true, RailSlopeStyle.CURVE),
	STONE(40, false, true, true, RailSlopeStyle.CURVE),
	EMERALD(60, false, true, true, RailSlopeStyle.CURVE),
	IRON(80, false, true, true, RailSlopeStyle.CURVE),
	OBSIDIAN(120, false, true, true, RailSlopeStyle.CURVE),
	BLAZE(160, false, true, true, RailSlopeStyle.CURVE),
	QUARTZ(200, false, true, true, RailSlopeStyle.CURVE),
	DIAMOND(300, false, true, true, RailSlopeStyle.CURVE),
	PLATFORM(80, true, false, true, RailSlopeStyle.CURVE),
	SIDING(40, true, false, true, RailSlopeStyle.CURVE),
	TURN_BACK(80, false, false, true, RailSlopeStyle.CURVE),
	CABLE_CAR(30, false, true, true, RailSlopeStyle.CABLE),
	CABLE_CAR_STATION(2, false, true, true, RailSlopeStyle.CURVE),
	RUNWAY(300, false, true, false, RailSlopeStyle.CURVE),
	AIRPLANE_DUMMY(900, false, true, false, RailSlopeStyle.CURVE),
	NONE(20, false, false, true, RailSlopeStyle.CURVE);

	public final int speedLimitKilometersPerHour;
	public final float speedLimitMetersPerSecond;
	public final boolean hasSavedRail;
	public final boolean canAccelerate;
	public final boolean hasSignal;
	public final RailSlopeStyle railSlopeStyle;

	RailType(int speedLimitKilometersPerHour, boolean hasSavedRail, boolean canAccelerate, boolean hasSignal, RailSlopeStyle railSlopeStyle) {
		this.speedLimitKilometersPerHour = speedLimitKilometersPerHour;
		speedLimitMetersPerSecond = speedLimitKilometersPerHour / 3.6F;
		this.hasSavedRail = hasSavedRail;
		this.canAccelerate = canAccelerate;
		this.hasSignal = hasSignal;
		this.railSlopeStyle = railSlopeStyle;
	}

	public static float getDefaultMaxBlocksPerTick(TransportMode transportMode) {
		return (transportMode.continuousMovement ? CABLE_CAR_STATION : WOODEN).speedLimitMetersPerSecond;
	}

	public enum RailSlopeStyle {CURVE, CABLE}
}
