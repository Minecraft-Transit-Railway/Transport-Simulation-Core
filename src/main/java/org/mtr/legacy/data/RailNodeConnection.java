package org.mtr.legacy.data;

import org.mtr.core.data.Position;
import org.mtr.core.data.TransportMode;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.EnumHelper;
import org.mtr.core.tool.Utilities;
import org.mtr.core.tool.Vector;
import org.mtr.legacy.generated.data.RailNodeConnectionSchema;

public final class RailNodeConnection extends RailNodeConnectionSchema {

	public RailNodeConnection(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public Position getEndPosition() {
		return DataFixer.fromLong(node_pos);
	}

	public long getEndPositionLong() {
		return node_pos;
	}

	public Angle getStartAngle() {
		return getAngle(false);
	}

	public Angle getEndAngle() {
		return getAngle(true);
	}

	public DataFixer.RailType getRailType() {
		return EnumHelper.valueOf(DataFixer.RailType.WOODEN, rail_type);
	}

	public TransportMode getTransportMode() {
		return transportMode;
	}

	public String getModelKey() {
		return model_key;
	}

	public boolean getIsSecondaryDirection() {
		return is_secondary_dir;
	}

	public double getVerticalRadius() {
		return vertical_curve_radius;
	}

	private Angle getAngle(boolean reverse) {
		final Vector vector1 = getPosition(0, reverse);
		final Vector vector2 = getPosition(0.1, reverse);
		return Angle.fromAngle((float) Math.toDegrees(Math.atan2(vector2.z - vector1.z, vector2.x - vector1.x)));
	}

	private Vector getPosition(double rawValue, boolean reverse) {
		final double count1 = Math.abs(t_end_1 - t_start_1);
		final double count2 = Math.abs(t_end_2 - t_start_2);
		final double clampedValue = Utilities.clamp(rawValue, 0, count1 + count2);
		final double value = reverse ? count1 + count2 - clampedValue : clampedValue;

		if (value <= count1) {
			return getPositionXZ(h_1, k_1, r_1, (reverse_t_1 ? -1 : 1) * value + t_start_1, is_straight_1);
		} else {
			return getPositionXZ(h_2, k_2, r_2, (reverse_t_2 ? -1 : 1) * (value - count1) + t_start_2, is_straight_2);
		}
	}

	private static Vector getPositionXZ(double h, double k, double r, double t, boolean isStraight) {
		if (isStraight) {
			return new Vector(h * t + k * ((Math.abs(h) >= 0.5 && Math.abs(k) >= 0.5 ? 0 : r)) + 0.5, 0, k * t + h * r + 0.5);
		} else {
			return new Vector(h + r * Math.cos(t / r) + 0.5, 0, k + r * Math.sin(t / r) + 0.5);
		}
	}
}
