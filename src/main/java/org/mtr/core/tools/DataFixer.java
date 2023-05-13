package org.mtr.core.tools;

import it.unimi.dsi.fastutil.doubles.DoubleConsumer;
import it.unimi.dsi.fastutil.ints.IntConsumer;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.core.data.EnumHelper;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.data.VehicleCar;
import org.mtr.core.serializers.ReaderBase;

import java.util.Locale;
import java.util.function.Consumer;

public class DataFixer {

	private static final String KEY_POS_1 = "pos_1";
	private static final String KEY_POS_2 = "pos_2";
	private static final String KEY_NODE_POS = "node_pos";
	private static final String KEY_RAIL_TYPE = "rail_type";
	private static final String KEY_DWELL_TIME = "dwell_time";
	private static final String KEY_UNLIMITED_VEHICLES = "unlimited_trains";
	private static final String KEY_MAX_VEHICLES = "max_trains";
	private static final String KEY_IS_MANUAL = "is_manual";
	private static final String KEY_MAX_MANUAL_SPEED = "max_manual_speed";
	private static final String KEY_ACCELERATION_CONSTANT = "acceleration_constant";
	private static final String KEY_BASE_TRAIN_TYPE = "train_type";
	private static final String KEY_TRAIN_ID = "train_custom_id";

	private static final int PACKED_X_LENGTH = 26;
	private static final int PACKED_Z_LENGTH = PACKED_X_LENGTH;
	private static final int PACKED_Y_LENGTH = 64 - PACKED_X_LENGTH - PACKED_Z_LENGTH;
	private static final int Z_OFFSET = PACKED_Y_LENGTH;
	private static final int X_OFFSET = PACKED_Y_LENGTH + PACKED_Z_LENGTH;

	public static void unpackSavedRailBase(ReaderBase readerBase, Consumer<Position> consumer1, Consumer<Position> consumer2) {
		readerBase.unpackLong(KEY_POS_1, value -> consumer1.accept(convertCoordinates(value)));
		readerBase.unpackLong(KEY_POS_2, value -> consumer2.accept(convertCoordinates(value)));
	}

	public static void unpackRailEntry(ReaderBase readerBase, Consumer<Position> consumer) {
		readerBase.unpackLong(KEY_NODE_POS, value -> consumer.accept(convertCoordinates(value)));
	}

	public static Position convertCoordinates(long packedPosition) {
		return new Position(
				(int) (packedPosition << 64 - X_OFFSET - PACKED_X_LENGTH >> 64 - PACKED_X_LENGTH),
				(int) (packedPosition << 64 - PACKED_Y_LENGTH >> 64 - PACKED_Y_LENGTH),
				(int) (packedPosition << 64 - Z_OFFSET - PACKED_Z_LENGTH >> 64 - PACKED_Z_LENGTH)
		);
	}

	public static boolean convertRailType(ReaderBase readerBase, HexConsumer<Integer, Rail.Shape, Boolean, Boolean, Boolean, Boolean> consumer) {
		final boolean[] validRail = {true};
		readerBase.unpackString(KEY_RAIL_TYPE, value -> {
			final RailType railType = EnumHelper.valueOf(RailType.IRON, value);
			validRail[0] = railType != RailType.NONE;
			consumer.accept(
					railType.speedLimitKilometersPerHour,
					railType.railSlopeStyle == RailSlopeStyle.CURVE ? Rail.Shape.CURVE : Rail.Shape.STRAIGHT,
					railType.hasSavedRail,
					railType.canAccelerate,
					railType == RailType.TURN_BACK,
					railType.canHaveSignal
			);
		});
		return validRail[0];
	}

	public static void unpackDwellTime(ReaderBase readerBase, IntConsumer consumer) {
		readerBase.unpackInt(KEY_DWELL_TIME, value -> consumer.accept(value * 500));
	}

	public static void unpackMaxVehicles(ReaderBase readerBase, IntConsumer consumer) {
		readerBase.unpackBoolean(KEY_IS_MANUAL, value1 -> {
			if (value1) {
				consumer.accept(-1);
			} else {
				readerBase.unpackBoolean(KEY_UNLIMITED_VEHICLES, value2 -> {
					if (value2) {
						consumer.accept(0);
					} else {
						readerBase.unpackInt(KEY_MAX_VEHICLES, value3 -> consumer.accept(value3 + 1));
					}
				});
			}
		});
	}

	public static void unpackMaxManualSpeed(ReaderBase readerBase, DoubleConsumer consumer) {
		readerBase.unpackInt(KEY_MAX_MANUAL_SPEED, value -> {
			if (value >= 0 && value <= RailType.DIAMOND.ordinal()) {
				consumer.accept(RailType.values()[value].speedLimitMetersPerMillisecond);
			}
		});
	}

	public static void unpackAcceleration(ReaderBase readerBase, DoubleConsumer consumer) {
		// meters/tick^2 to meters/millisecond^2
		readerBase.unpackDouble(KEY_ACCELERATION_CONSTANT, value -> consumer.accept(value / 50D / 50D));
	}

	public static void unpackVehicleCars(ReaderBase readerBase, TransportMode transportMode, double railLength, Consumer<ObjectArrayList<VehicleCar>> consumer) {
		readerBase.unpackString(KEY_BASE_TRAIN_TYPE, baseTrainType -> readerBase.unpackString(KEY_TRAIN_ID, trainId -> {
			try {
				String newBaseTrainType = baseTrainType;
				try {
					newBaseTrainType = Enum.valueOf(TrainType.class, baseTrainType.toUpperCase(Locale.ENGLISH)).baseTrainType;
				} catch (Exception ignored) {
				}
				final String[] trainTypeSplit = newBaseTrainType.split("_");
				final int trainLength = Integer.parseInt(trainTypeSplit[trainTypeSplit.length - 2]) + 1;
				final int trainWidth = Integer.parseInt(trainTypeSplit[trainTypeSplit.length - 1]);
				final int trainCars = Math.min(transportMode.maxLength, (int) Math.floor(railLength / trainLength));
				final ObjectArrayList<VehicleCar> vehicleCars = new ObjectArrayList<>();
				for (int i = 0; i < trainCars; i++) {
					vehicleCars.add(new VehicleCar(trainId, trainLength, trainWidth, 0, 0, new double[0], new double[0]));
				}
				consumer.accept(vehicleCars);
			} catch (Exception ignored) {
			}
		}));
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

	private enum TrainType {
		SP1900("train_24_2"),
		SP1900_SMALL("train_20_2"),
		SP1900_MINI("train_12_2"),
		C1141A("train_24_2"),
		C1141A_SMALL("train_20_2"),
		C1141A_MINI("train_12_2"),
		M_TRAIN("train_24_2"),
		M_TRAIN_SMALL("train_19_2"),
		M_TRAIN_MINI("train_9_2"),
		CM_STOCK("train_24_2"),
		CM_STOCK_SMALL("train_19_2"),
		CM_STOCK_MINI("train_9_2"),
		MLR("train_24_2"),
		MLR_SMALL("train_20_2"),
		MLR_MINI("train_12_2"),
		MLR_CHRISTMAS("train_24_2"),
		MLR_CHRISTMAS_SMALL("train_20_2"),
		MLR_CHRISTMAS_MINI("train_12_2"),
		E44("train_24_2"),
		E44_MINI("train_12_2"),
		R_TRAIN("train_24_2"),
		R_TRAIN_SMALL("train_19_2"),
		R_TRAIN_MINI("train_9_2"),
		DRL("train_24_2"),
		K_TRAIN("train_24_2"),
		K_TRAIN_SMALL("train_19_2"),
		K_TRAIN_MINI("train_9_2"),
		K_TRAIN_TCL("train_24_2"),
		K_TRAIN_TCL_SMALL("train_19_2"),
		K_TRAIN_TCL_MINI("train_9_2"),
		K_TRAIN_AEL("train_24_2"),
		K_TRAIN_AEL_SMALL("train_19_2"),
		K_TRAIN_AEL_MINI("train_9_2"),
		C_TRAIN("train_24_2"),
		C_TRAIN_SMALL("train_19_2"),
		C_TRAIN_MINI("train_9_2"),
		S_TRAIN("train_24_2"),
		S_TRAIN_SMALL("train_19_2"),
		S_TRAIN_MINI("train_9_2"),
		A_TRAIN_TCL("train_24_2"),
		A_TRAIN_TCL_SMALL("train_19_2"),
		A_TRAIN_TCL_MINI("train_9_2"),
		A_TRAIN_AEL("train_24_2"),
		A_TRAIN_AEL_MINI("train_14_2"),
		LIGHT_RAIL_1("train_22_2"),
		LIGHT_RAIL_1_RHT("train_22_2"),
		LIGHT_RAIL_1R("train_22_2"),
		LIGHT_RAIL_1R_RHT("train_22_2"),
		LIGHT_RAIL_2("train_22_2"),
		LIGHT_RAIL_2R("train_22_2"),
		LIGHT_RAIL_2_RHT("train_22_2"),
		LIGHT_RAIL_2R_RHT("train_22_2"),
		LIGHT_RAIL_3("train_22_2"),
		LIGHT_RAIL_3_RHT("train_22_2"),
		LIGHT_RAIL_3R("train_22_2"),
		LIGHT_RAIL_3R_RHT("train_22_2"),
		LIGHT_RAIL_4("train_22_2"),
		LIGHT_RAIL_4_RHT("train_22_2"),
		LIGHT_RAIL_5("train_22_2"),
		LIGHT_RAIL_5_RHT("train_22_2"),
		LIGHT_RAIL_1R_OLD("train_22_2"),
		LIGHT_RAIL_1R_OLD_RHT("train_22_2"),
		LIGHT_RAIL_4_OLD("train_22_2"),
		LIGHT_RAIL_4_OLD_RHT("train_22_2"),
		LIGHT_RAIL_5_OLD("train_22_2"),
		LIGHT_RAIL_5_OLD_RHT("train_22_2"),
		LIGHT_RAIL_1_ORANGE("train_22_2"),
		LIGHT_RAIL_1_ORANGE_RHT("train_22_2"),
		LIGHT_RAIL_1R_ORANGE("train_22_2"),
		LIGHT_RAIL_1R_ORANGE_RHT("train_22_2"),
		LIGHT_RAIL_2_ORANGE("train_22_2"),
		LIGHT_RAIL_2_ORANGE_RHT("train_22_2"),
		LIGHT_RAIL_3_ORANGE("train_22_2"),
		LIGHT_RAIL_3_ORANGE_RHT("train_22_2"),
		LIGHT_RAIL_4_ORANGE("train_22_2"),
		LIGHT_RAIL_4_ORANGE_RHT("train_22_2"),
		LIGHT_RAIL_5_ORANGE("train_22_2"),
		LIGHT_RAIL_5_ORANGE_RHT("train_22_2"),
		LONDON_UNDERGROUND_D78("train_18_2"),
		LONDON_UNDERGROUND_D78_MINI("train_10_2"),
		LONDON_UNDERGROUND_1995("train_19_2"),
		LONDON_UNDERGROUND_1996("train_19_2"),
		R179("train_19_2"),
		R179_MINI("train_9_2"),
		R211("train_19_2"),
		R211_MINI("train_9_2"),
		R211T("train_19_2"),
		R211T_MINI("train_9_2"),
		CLASS_377_SOUTHERN("train_16_2"),
		CLASS_802_GWR("train_24_2"),
		CLASS_802_GWR_MINI("train_18_2"),
		CLASS_802_TPE("train_24_2"),
		CLASS_802_TPE_MINI("train_18_2"),
		MPL_85("train_21_2"),
		BR_423("train_15_2"),
		MINECART("train_1_1"),
		OAK_BOAT("boat_1_1"),
		SPRUCE_BOAT("boat_1_1"),
		BIRCH_BOAT("boat_1_1"),
		JUNGLE_BOAT("boat_1_1"),
		ACACIA_BOAT("boat_1_1"),
		DARK_OAK_BOAT("boat_1_1"),
		NGONG_PING_360_CRYSTAL("cable_car_1_1"),
		NGONG_PING_360_CRYSTAL_RHT("cable_car_1_1"),
		NGONG_PING_360_CRYSTAL_PLUS("cable_car_1_1"),
		NGONG_PING_360_CRYSTAL_PLUS_RHT("cable_car_1_1"),
		NGONG_PING_360_NORMAL_RED("cable_car_1_1"),
		NGONG_PING_360_NORMAL_RED_RHT("cable_car_1_1"),
		NGONG_PING_360_NORMAL_ORANGE("cable_car_1_1"),
		NGONG_PING_360_NORMAL_ORANGE_RHT("cable_car_1_1"),
		NGONG_PING_360_NORMAL_LIGHT_BLUE("cable_car_1_1"),
		NGONG_PING_360_NORMAL_LIGHT_BLUE_RHT("cable_car_1_1"),
		A320("airplane_30_3"),
		FLYING_MINECART("airplane_1_1");

		private final String baseTrainType;

		TrainType(String baseTrainType) {
			this.baseTrainType = baseTrainType;
		}
	}
}
