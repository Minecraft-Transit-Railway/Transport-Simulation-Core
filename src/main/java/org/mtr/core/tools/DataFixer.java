package org.mtr.core.tools;

import it.unimi.dsi.fastutil.ints.IntConsumer;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.Value;
import org.mtr.core.data.*;
import org.mtr.core.serializers.MessagePackReader;
import org.mtr.core.serializers.MessagePackWriter;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.serializers.WriterBase;

import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class DataFixer {

	private static final int PACKED_X_LENGTH = 26;
	private static final int PACKED_Z_LENGTH = PACKED_X_LENGTH;
	private static final int PACKED_Y_LENGTH = 64 - PACKED_X_LENGTH - PACKED_Z_LENGTH;
	private static final int Z_OFFSET = PACKED_Y_LENGTH;
	private static final int X_OFFSET = PACKED_Y_LENGTH + PACKED_Z_LENGTH;

	public static void unpackAreaBasePositions(ReaderBase readerBase, BiConsumer<Position, Position> consumer) {
		readerBase.unpackInt("x_min", xMin -> readerBase.unpackInt("z_min", zMin -> readerBase.unpackInt("x_max", xMax -> readerBase.unpackInt("z_max", zMax -> consumer.accept(new Position(xMin, Long.MIN_VALUE, zMin), new Position(xMax, Long.MAX_VALUE, zMax))))));
	}

	public static void unpackPlatformDwellTime(ReaderBase readerBase, IntConsumer consumer) {
		readerBase.unpackInt("dwell_time", value -> consumer.accept(value * 500));
	}

	public static ReaderBase convertRail(ReaderBase readerBase) {
		packExtra(readerBase, messagePackWriter -> readerBase.unpackString("rail_type", value -> {
			final RailType railType = EnumHelper.valueOf(RailType.IRON, value);
			if (railType != RailType.NONE) {
				messagePackWriter.writeLong("speedLimit", railType.speedLimitKilometersPerHour);
				final String shapeString = (railType.railSlopeStyle == RailSlopeStyle.CURVE ? Rail.Shape.CURVE : Rail.Shape.STRAIGHT).toString();
				messagePackWriter.writeString("shapeStart", shapeString);
				messagePackWriter.writeString("shapeEnd", shapeString);
				messagePackWriter.writeBoolean("isSavedRail", railType.hasSavedRail);
				messagePackWriter.writeBoolean("canAccelerate", railType.canAccelerate);
				messagePackWriter.writeBoolean("canTurnBack", railType == RailType.TURN_BACK);
				messagePackWriter.writeBoolean("canHaveSignal", railType.canHaveSignal);
			}
		}));
		return readerBase;
	}

	public static ReaderBase convertRailNode(ReaderBase readerBase) {
		packExtra(readerBase, messagePackWriter -> {
			readerBase.unpackLong("node_pos", value -> convertPosition(value).serializeData(messagePackWriter.writeChild("position")));
			final ObjectArrayList<RailNodeConnection> connections = new ObjectArrayList<>();
			readerBase.iterateReaderArray("rail_connections", readerBaseChild -> connections.add(new RailNodeConnection(readerBaseChild)));
			if (!connections.isEmpty()) {
				messagePackWriter.writeDataset(connections, "connections");
			}
		});
		return readerBase;
	}

	public static ReaderBase convertRailNodeConnection(ReaderBase readerBase) {
		packExtra(readerBase, messagePackWriter -> readerBase.unpackLong("node_pos", value -> {
			new Rail(readerBase).serializeData(messagePackWriter.writeChild("rail"));
			convertPosition(value).serializeData(messagePackWriter.writeChild("position"));
		}));
		return readerBase;
	}

	public static ReaderBase convertRoute(ReaderBase readerBase) {
		packExtra(readerBase, messagePackWriter -> {
			final LongArrayList platformIds = new LongArrayList();
			readerBase.iterateLongArray("platform_ids", platformIds::add);

			if (!platformIds.isEmpty()) {
				final ObjectArrayList<String> customDestinations = new ObjectArrayList<>();
				readerBase.iterateStringArray("custom_destinations", customDestinations::add);
				final WriterBase.Array arrayWriter = messagePackWriter.writeArray("routePlatformData");

				for (int i = 0; i < platformIds.size(); i++) {
					final WriterBase childWriter = arrayWriter.writeChild();
					childWriter.writeLong("platformId", platformIds.getLong(i));
					if (i < customDestinations.size()) {
						childWriter.writeString("customDestination", customDestinations.get(i));
					}
				}
			}

			final boolean[] isLightRailRoute = {false};
			readerBase.unpackBoolean("is_light_rail_route", value -> isLightRailRoute[0] = value);
			readerBase.unpackString("light_rail_route_number", value -> messagePackWriter.writeString("routeNumber", isLightRailRoute[0] ? value : ""));
			readerBase.unpackBoolean("is_route_hidden", value -> messagePackWriter.writeBoolean("hidden", value));
		});
		return readerBase;
	}

	public static ReaderBase convertSavedRailBase(ReaderBase readerBase) {
		packExtra(readerBase, messagePackWriter -> {
			readerBase.unpackLong("pos_1", value -> convertPosition(value).serializeData(messagePackWriter.writeChild("position1")));
			readerBase.unpackLong("pos_2", value -> convertPosition(value).serializeData(messagePackWriter.writeChild("position2")));
		});
		return readerBase;
	}

	public static ReaderBase convertSiding(ReaderBase readerBase) {
		packExtra(readerBase, messagePackWriter -> {
			readerBase.unpackInt("dwell_time", value -> messagePackWriter.writeLong("manualToAutomaticTime", value * 500L));
			// meters/tick^2 to meters/millisecond^2
			readerBase.unpackDouble("acceleration_constant", value -> messagePackWriter.writeDouble("acceleration", value / 50D / 50D));
			readerBase.unpackInt("max_manual_speed", value -> {
				if (value >= 0 && value <= RailType.DIAMOND.ordinal()) {
					messagePackWriter.writeDouble("maxManualSpeed", RailType.values()[value].speedLimitMetersPerMillisecond);
				}
			});
		});
		return readerBase;
	}

	public static void unpackSidingVehicleCars(ReaderBase readerBase, TransportMode transportMode, double railLength, ObjectArrayList<VehicleCar> vehicleCars) {
		readerBase.unpackString("train_type", baseTrainType -> readerBase.unpackString("train_custom_id", trainId -> {
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
				for (int i = 0; i < trainCars; i++) {
					vehicleCars.add(new VehicleCar(trainId, trainLength, trainWidth, 0, 0));
				}
			} catch (Exception ignored) {
			}
		}));
	}

	public static void unpackSidingMaxVehicles(ReaderBase readerBase, IntConsumer consumer) {
		readerBase.unpackBoolean("is_manual", value1 -> {
			if (value1) {
				consumer.accept(-1);
			} else {
				readerBase.unpackBoolean("unlimited_trains", value2 -> {
					if (value2) {
						consumer.accept(0);
					} else {
						readerBase.unpackInt("max_trains", value3 -> consumer.accept(value3 + 1));
					}
				});
			}
		});
	}

	public static ReaderBase convertStation(ReaderBase readerBase) {
		packExtra(readerBase, messagePackWriter -> readerBase.unpackInt("zone", value -> messagePackWriter.writeLong("zone1", value)));
		return readerBase;
	}

	public static void readerBaseConvertKey(String key, Value value, Object2ObjectArrayMap<String, Value> map) {
		map.put(key, value);
		final String[] keySplit = key.split("_");

		if (keySplit.length == 0) {
			return;
		}

		final StringBuilder stringBuilder = new StringBuilder(keySplit[0]);
		for (int i = 1; i < keySplit.length; i++) {
			final String keyPart = keySplit[i];
			if (keyPart.length() > 0) {
				stringBuilder.append(keyPart.substring(0, 1).toUpperCase(Locale.ENGLISH));
				stringBuilder.append(keyPart.substring(1));
			}
		}

		map.put(stringBuilder.toString(), value);
	}

	private static Position convertPosition(long packedPosition) {
		return new Position(
				(int) (packedPosition << 64 - X_OFFSET - PACKED_X_LENGTH >> 64 - PACKED_X_LENGTH),
				(int) (packedPosition << 64 - PACKED_Y_LENGTH >> 64 - PACKED_Y_LENGTH),
				(int) (packedPosition << 64 - Z_OFFSET - PACKED_Z_LENGTH >> 64 - PACKED_Z_LENGTH)
		);
	}

	private static void packExtra(ReaderBase readerBase, Consumer<MessagePackWriter> consumer) {
		try (final MessageBufferPacker messageBufferPacker = MessagePack.newDefaultBufferPacker()) {
			final MessagePackWriter messagePackWriter = new MessagePackWriter(messageBufferPacker);
			consumer.accept(messagePackWriter);
			messagePackWriter.serialize();
			try (final MessageUnpacker messageUnpacker = MessagePack.newDefaultUnpacker(messageBufferPacker.toByteArray())) {
				readerBase.merge(new MessagePackReader(messageUnpacker));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
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
