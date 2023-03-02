package org.mtr.core.tools;

import org.mtr.core.data.EnumHelper;
import org.mtr.core.data.MessagePackHelper;
import org.mtr.core.data.Rail;

import java.util.Set;
import java.util.function.Consumer;

public class DataFixer {

	private static final String KEY_POS_1 = "pos_1";
	private static final String KEY_POS_2 = "pos_2";
	private static final String KEY_NODE_POS = "node_pos";
	private static final String KEY_RAIL_TYPE = "rail_type";
	private static final String KEY_MAX_MANUAL_SPEED = "max_manual_speed";
	private static final String KEY_ACCELERATION_CONSTANT = "acceleration_constant";

	private static final int PACKED_X_LENGTH = 26;
	private static final int PACKED_Z_LENGTH = PACKED_X_LENGTH;
	private static final int PACKED_Y_LENGTH = 64 - PACKED_X_LENGTH - PACKED_Z_LENGTH;
	private static final int Z_OFFSET = PACKED_Y_LENGTH;
	private static final int X_OFFSET = PACKED_Y_LENGTH + PACKED_Z_LENGTH;

	public static void unpackSavedRailBase(MessagePackHelper messagePackHelper, Set<Position> positions) {
		messagePackHelper.unpackLong(KEY_POS_1, value -> positions.add(convertCoordinates(value)));
		messagePackHelper.unpackLong(KEY_POS_2, value -> positions.add(convertCoordinates(value)));
	}

	public static void unpackRailEntry(MessagePackHelper messagePackHelper, Consumer<Position> consumer) {
		messagePackHelper.unpackLong(KEY_NODE_POS, value -> consumer.accept(convertCoordinates(value)));
	}

	public static Position convertCoordinates(long packedPosition) {
		return new Position(
				(int) (packedPosition << 64 - X_OFFSET - PACKED_X_LENGTH >> 64 - PACKED_X_LENGTH),
				(int) (packedPosition << 64 - PACKED_Y_LENGTH >> 64 - PACKED_Y_LENGTH),
				(int) (packedPosition << 64 - Z_OFFSET - PACKED_Z_LENGTH >> 64 - PACKED_Z_LENGTH)
		);
	}

	public static void convertRailType(MessagePackHelper messagePackHelper, HexConsumer<Integer, Rail.Shape, Boolean, Boolean, Boolean, Boolean> consumer) {
		messagePackHelper.unpackString(KEY_RAIL_TYPE, value -> {
			final RailType railType = EnumHelper.valueOf(RailType.IRON, value);
			consumer.accept(
					railType.speedLimitKilometersPerHour,
					railType.railSlopeStyle == RailSlopeStyle.CURVE ? Rail.Shape.CURVE : Rail.Shape.STRAIGHT,
					railType.hasSavedRail,
					railType.canAccelerate,
					railType == RailType.TURN_BACK,
					railType.canHaveSignal
			);
		});
	}

	public static void unpackMaxManualSpeed(MessagePackHelper messagePackHelper, Consumer<Double> consumer) {
		messagePackHelper.unpackInt(KEY_MAX_MANUAL_SPEED, value -> {
			if (value >= 0 && value <= RailType.DIAMOND.ordinal()) {
				consumer.accept(RailType.values()[value].speedLimitMetersPerMillisecond);
			}
		});
	}

	public static void unpackAcceleration(MessagePackHelper messagePackHelper, Consumer<Double> consumer) {
		// meters/tick^2 to meters/millisecond^2
		messagePackHelper.unpackInt(KEY_ACCELERATION_CONSTANT, value -> consumer.accept(value / 50D / 50D));
	}

	@FunctionalInterface
	public interface HexConsumer<T, U, V, W, X, Y> {
		void accept(T t, U u, V v, W w, X x, Y y);
	}

	private enum RailType {
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
		public final double speedLimitMetersPerMillisecond;
		public final boolean hasSavedRail;
		public final boolean canAccelerate;
		public final boolean canHaveSignal;
		public final RailSlopeStyle railSlopeStyle;

		RailType(int speedLimitKilometersPerHour, boolean hasSavedRail, boolean canAccelerate, boolean canHaveSignal, RailSlopeStyle railSlopeStyle) {
			this.speedLimitKilometersPerHour = speedLimitKilometersPerHour;
			speedLimitMetersPerMillisecond = Utilities.kilometersPerHourToMetersPerMillisecond(speedLimitKilometersPerHour);
			this.hasSavedRail = hasSavedRail;
			this.canAccelerate = canAccelerate;
			this.canHaveSignal = canHaveSignal;
			this.railSlopeStyle = railSlopeStyle;
		}
	}

	private enum RailSlopeStyle {CURVE, CABLE}
}
