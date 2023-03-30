package org.mtr.core.data;

import org.msgpack.core.MessagePacker;
import org.mtr.core.reader.ReaderBase;
import org.mtr.core.tools.*;

import java.io.IOException;

public class Rail extends SerializedDataBase {

	public final Angle facingStart;
	public final Angle facingEnd;
	public final double speedLimitKilometersPerHour;
	public final double speedLimitMetersPerMillisecond;
	public final Shape shapeStart;
	public final Shape shapeEnd;
	public final boolean hasSavedRail;
	public final boolean canAccelerate;
	public final boolean canTurnBack;
	public final boolean canHaveSignal;
	public final TransportMode transportMode;
	public final boolean validRail;
	private final double h1, k1, r1, tStart1, tEnd1;
	private final double h2, k2, r2, tStart2, tEnd2;
	private final long yStart, yEnd;
	private final boolean reverseT1, isStraight1, reverseT2, isStraight2;

	private static final double ACCEPT_THRESHOLD = 1E-4;
	private static final int MIN_RADIUS = 2;
	private static final int CABLE_CURVATURE_SCALE = 1000;
	private static final int MAX_CABLE_DIP = 8;

	private static final String KEY_H_1 = "h_1";
	private static final String KEY_K_1 = "k_1";
	private static final String KEY_H_2 = "h_2";
	private static final String KEY_K_2 = "k_2";
	private static final String KEY_R_1 = "r_1";
	private static final String KEY_R_2 = "r_2";
	private static final String KEY_T_START_1 = "t_start_1";
	private static final String KEY_T_END_1 = "t_end_1";
	private static final String KEY_T_START_2 = "t_start_2";
	private static final String KEY_T_END_2 = "t_end_2";
	private static final String KEY_Y_START = "y_start";
	private static final String KEY_Y_END = "y_end";
	private static final String KEY_REVERSE_T_1 = "reverse_t_1";
	private static final String KEY_IS_STRAIGHT_1 = "is_straight_1";
	private static final String KEY_REVERSE_T_2 = "reverse_t_2";
	private static final String KEY_IS_STRAIGHT_2 = "is_straight_2";
	private static final String KEY_SPEED_LIMIT_KILOMETERS_PER_HOUR = "speed_limit_kilometers_per_hour";
	private static final String KEY_SHAPE_START = "shape_start";
	private static final String KEY_SHAPE_END = "shape_end";
	private static final String KEY_HAS_SAVED_RAIL = "has_saved_rail";
	private static final String KEY_CAN_ACCELERATE = "can_accelerate";
	private static final String KEY_CAN_TURN_BACK = "can_turn_back";
	private static final String KEY_CAN_HAVE_SIGNAL = "can_have_signal";
	private static final String KEY_TRANSPORT_MODE = "transport_mode";

	public static Rail newRail(Position posStart, Angle facingStart, Position posEnd, Angle facingEnd, double speedLimitKilometersPerHour, Shape shapeStart, Shape shapeEnd, boolean hasSavedRail, boolean canAccelerate, boolean canHaveSignal, TransportMode transportMode) {
		return new Rail(posStart, facingStart, posEnd, facingEnd, speedLimitKilometersPerHour, shapeStart, shapeEnd, hasSavedRail, canAccelerate, false, canHaveSignal, transportMode);
	}

	public static Rail newTurnBackRail(Position posStart, Angle facingStart, Position posEnd, Angle facingEnd, Shape shapeStart, Shape shapeEnd, TransportMode transportMode) {
		return new Rail(posStart, facingStart, posEnd, facingEnd, 80, shapeStart, shapeEnd, false, false, true, false, transportMode);
	}

	public static Rail newPlatformRail(Position posStart, Angle facingStart, Position posEnd, Angle facingEnd, Shape shapeStart, Shape shapeEnd, TransportMode transportMode) {
		return newPlatformOrSidingRail(posStart, facingStart, posEnd, facingEnd, true, shapeStart, shapeEnd, transportMode);
	}

	public static Rail newSidingRail(Position posStart, Angle facingStart, Position posEnd, Angle facingEnd, Shape shapeStart, Shape shapeEnd, TransportMode transportMode) {
		return newPlatformOrSidingRail(posStart, facingStart, posEnd, facingEnd, false, shapeStart, shapeEnd, transportMode);
	}

	private static Rail newPlatformOrSidingRail(Position posStart, Angle facingStart, Position posEnd, Angle facingEnd, boolean isPlatform, Shape shapeStart, Shape shapeEnd, TransportMode transportMode) {
		return new Rail(posStart, facingStart, posEnd, facingEnd, isPlatform ? 80 : 40, shapeStart, shapeEnd, true, false, false, true, transportMode);
	}

	// for curves:
	// x = h + r*cos(T)
	// z = k + r*sin(T)
	// for straight lines (both k and r >= 0.5):
	// x = h*T
	// z = k*T + h*r
	// for straight lines (otherwise):
	// x = h*T + k*r
	// z = k*T + h*r

	private Rail(Position posStart, Angle facingStart, Position posEnd, Angle facingEnd, double speedLimitKilometersPerHour, Shape shapeStart, Shape shapeEnd, boolean hasSavedRail, boolean canAccelerate, boolean canTurnBack, boolean canHaveSignal, TransportMode transportMode) {
		this.facingStart = facingStart;
		this.facingEnd = facingEnd;
		this.speedLimitKilometersPerHour = speedLimitKilometersPerHour;
		speedLimitMetersPerMillisecond = Utilities.kilometersPerHourToMetersPerMillisecond(speedLimitKilometersPerHour);
		this.shapeStart = shapeStart;
		this.shapeEnd = shapeEnd;
		this.hasSavedRail = hasSavedRail;
		this.canAccelerate = canAccelerate;
		this.canTurnBack = canTurnBack;
		this.canHaveSignal = canHaveSignal;
		this.transportMode = transportMode;
		validRail = true;
		yStart = posStart.y;
		yEnd = posEnd.y;

		final long xStart = posStart.x;
		final long zStart = posStart.z;
		final long xEnd = posEnd.x;
		final long zEnd = posEnd.z;

		// Coordinate system translation and rotation
		final Vec3 vecDifference = new Vec3(posEnd.x - posStart.x, 0, posEnd.z - posStart.z);
		final Vec3 vecDifferenceRotated = vecDifference.rotateY((float) facingStart.angleRadians);

		// First we check the Delta Side > 0
		// 1. If they are same angle
		// 1. a. If aligned -> Use One Segment
		// 1. b. If not aligned -> Use two Circle, r = (dv^2 + dp^2) / (4dv).
		// 2. If they are right angle -> r = min ( dx,dz ), work around, actually equation 3. can be used.
		// 3. Check if one segment and one circle is available
		// 3. a. If available -> (Segment First) r2 = dv / ( sin(diff) * tan(diff/2) ) = dv / ( 1 - cos(diff)
		// 							for case 2, diff = 90 degrees, r = dv
		//					-> (Circle First) r1 = ( dp - dv / tan(diff) ) / tan (diff/2)
		// TODO 3. b. If not -> r = very complex one. In this case, we need two circles to connect.
		final double deltaForward = vecDifferenceRotated.z;
		final double deltaSide = vecDifferenceRotated.x;
		if (facingStart.isParallel(facingEnd)) { // 1
			if (Math.abs(deltaForward) < ACCEPT_THRESHOLD) { // 1. a.
				h1 = facingStart.cos;
				k1 = facingStart.sin;
				if (Math.abs(h1) >= 0.5 && Math.abs(k1) >= 0.5) {
					r1 = (h1 * zStart - k1 * xStart) / h1 / h1;
					tStart1 = xStart / h1;
					tEnd1 = xEnd / h1;
				} else {
					final double div = facingStart.add(facingStart).cos;
					r1 = (h1 * zStart - k1 * xStart) / div;
					tStart1 = (h1 * xStart - k1 * zStart) / div;
					tEnd1 = (h1 * xEnd - k1 * zEnd) / div;
				}
				h2 = k2 = r2 = 0;
				reverseT1 = tStart1 > tEnd1;
				reverseT2 = false;
				isStraight1 = isStraight2 = true;
				tStart2 = tEnd2 = 0;
			} else { // 1. b
				if (Math.abs(deltaSide) > ACCEPT_THRESHOLD) {
					final double radius = (deltaForward * deltaForward + deltaSide * deltaSide) / (4 * deltaForward);
					r1 = r2 = Math.abs(radius);
					h1 = xStart - radius * facingStart.sin;
					k1 = zStart + radius * facingStart.cos;
					h2 = xEnd - radius * facingEnd.sin;
					k2 = zEnd + radius * facingEnd.cos;
					reverseT1 = deltaForward < 0 != deltaSide < 0;
					reverseT2 = !reverseT1;
					tStart1 = getTBounds(xStart, h1, zStart, k1, r1);
					tEnd1 = getTBounds(xStart + vecDifference.x / 2, h1, zStart + vecDifference.z / 2, k1, r1, tStart1, reverseT1);
					tStart2 = getTBounds(xStart + vecDifference.x / 2, h2, zStart + vecDifference.z / 2, k2, r2);
					tEnd2 = getTBounds(xEnd, h2, zEnd, k2, r2, tStart2, reverseT2);
					isStraight1 = isStraight2 = false;
				} else {
					// Banned node perpendicular to the rail nodes direction
					h1 = k1 = h2 = k2 = r1 = r2 = 0;
					tStart1 = tStart2 = tEnd1 = tEnd2 = 0;
					reverseT1 = false;
					reverseT2 = false;
					isStraight1 = isStraight2 = true;
				}
			}
		} else { // 3.
			// Check if it needs invert
			final Angle newFacingStart = vecDifferenceRotated.x < -ACCEPT_THRESHOLD ? facingStart.getOpposite() : facingStart;
			final Angle newFacingEnd = facingEnd.cos * vecDifference.x + facingEnd.sin * vecDifference.z < -ACCEPT_THRESHOLD ? facingEnd.getOpposite() : facingEnd;
			final double angleForward = Math.atan2(deltaForward, deltaSide);
			final Angle railAngleDifference = newFacingEnd.sub(newFacingStart);
			final double angleDifference = railAngleDifference.angleRadians;

			if (Math.signum(angleForward) == Math.signum(angleDifference)) {
				final double absAngleForward = Math.abs(angleForward);

				if (absAngleForward - Math.abs(angleDifference / 2) < ACCEPT_THRESHOLD) { // Segment First
					final double offsetSide = Math.abs(deltaForward / railAngleDifference.halfTan);
					final double remainingSide = deltaSide - offsetSide;
					final double deltaXEnd = xStart + remainingSide * newFacingStart.cos;
					final double deltaZEnd = zStart + remainingSide * newFacingStart.sin;
					h1 = newFacingStart.cos;
					k1 = newFacingStart.sin;
					if (Math.abs(h1) >= 0.5 && Math.abs(k1) >= 0.5) {
						r1 = (h1 * zStart - k1 * xStart) / h1 / h1;
						tStart1 = xStart / h1;
						tEnd1 = deltaXEnd / h1;
					} else {
						final double div = newFacingStart.add(newFacingStart).cos;
						r1 = (h1 * zStart - k1 * xStart) / div;
						tStart1 = (h1 * xStart - k1 * zStart) / div;
						tEnd1 = (h1 * deltaXEnd - k1 * deltaZEnd) / div;
					}
					isStraight1 = true;
					reverseT1 = tStart1 > tEnd1;
					final double radius = deltaForward / (1 - railAngleDifference.cos);
					r2 = Math.abs(radius);
					h2 = deltaXEnd - radius * newFacingStart.sin;
					k2 = deltaZEnd + radius * newFacingStart.cos;
					reverseT2 = (deltaForward < 0);
					tStart2 = getTBounds(deltaXEnd, h2, deltaZEnd, k2, r2);
					tEnd2 = getTBounds(xEnd, h2, zEnd, k2, r2, tStart2, reverseT2);
					isStraight2 = false;
				} else if (absAngleForward - Math.abs(angleDifference) < ACCEPT_THRESHOLD) { // Circle First
					final double crossSide = deltaForward / railAngleDifference.tan;
					final double remainingSide = (deltaSide - crossSide) * (1 + railAngleDifference.cos);
					final double remainingForward = (deltaSide - crossSide) * (railAngleDifference.sin);
					final double deltaXEnd = xStart + remainingSide * newFacingStart.cos - remainingForward * newFacingStart.sin;
					final double deltaZEnd = zStart + remainingSide * newFacingStart.sin + remainingForward * newFacingStart.cos;
					final double radius = (deltaSide - deltaForward / railAngleDifference.tan) / railAngleDifference.halfTan;
					r1 = Math.abs(radius);
					h1 = xStart - radius * newFacingStart.sin;
					k1 = zStart + radius * newFacingStart.cos;
					isStraight1 = false;
					reverseT1 = (deltaForward < 0);
					tStart1 = getTBounds(xStart, h1, zStart, k1, r1);
					tEnd1 = getTBounds(deltaXEnd, h1, deltaZEnd, k1, r1, tStart1, reverseT1);
					h2 = newFacingEnd.cos;
					k2 = newFacingEnd.sin;
					if (Math.abs(h2) >= 0.5 && Math.abs(k2) >= 0.5) {
						r2 = (h2 * deltaZEnd - k2 * deltaXEnd) / h2 / h2;
						tStart2 = deltaXEnd / h2;
						tEnd2 = xEnd / h2;
					} else {
						final double div = newFacingEnd.add(newFacingEnd).cos;
						r2 = (h2 * deltaZEnd - k2 * deltaXEnd) / div;
						tStart2 = (h2 * deltaXEnd - k2 * deltaZEnd) / div;
						tEnd2 = (h2 * xEnd - k2 * zEnd) / div;
					}
					isStraight2 = true;
					reverseT2 = tStart2 > tEnd2;
				} else { // Out of available range
					// TODO complex one. Normally we don't need it.
					h1 = k1 = h2 = k2 = r1 = r2 = 0;
					tStart1 = tStart2 = tEnd1 = tEnd2 = 0;
					reverseT1 = false;
					reverseT2 = false;
					isStraight1 = isStraight2 = true;
				}
			} else {
				// TODO 3. b. If not -> r = very complex one. Normally we don't need it.
				h1 = k1 = h2 = k2 = r1 = r2 = 0;
				tStart1 = tStart2 = tEnd1 = tEnd2 = 0;
				reverseT1 = false;
				reverseT2 = false;
				isStraight1 = isStraight2 = true;
			}
		}
	}

	public <T extends ReaderBase<U, T>, U> Rail(T readerBase) {
		updateData(readerBase);

		h1 = readerBase.getDouble(KEY_H_1, 0);
		k1 = readerBase.getDouble(KEY_K_1, 0);
		h2 = readerBase.getDouble(KEY_H_2, 0);
		k2 = readerBase.getDouble(KEY_K_2, 0);
		r1 = readerBase.getDouble(KEY_R_1, 0);
		r2 = readerBase.getDouble(KEY_R_2, 0);
		tStart1 = readerBase.getDouble(KEY_T_START_1, 0);
		tEnd1 = readerBase.getDouble(KEY_T_END_1, 0);
		tStart2 = readerBase.getDouble(KEY_T_START_2, 0);
		tEnd2 = readerBase.getDouble(KEY_T_END_2, 0);
		yStart = readerBase.getLong(KEY_Y_START, 0);
		yEnd = readerBase.getLong(KEY_Y_END, 0);
		reverseT1 = readerBase.getBoolean(KEY_REVERSE_T_1, false);
		isStraight1 = readerBase.getBoolean(KEY_IS_STRAIGHT_1, false);
		reverseT2 = readerBase.getBoolean(KEY_REVERSE_T_2, false);
		isStraight2 = readerBase.getBoolean(KEY_IS_STRAIGHT_2, false);

		final double[] tempSpeedLimitKilometersPerHour = {readerBase.getDouble(KEY_SPEED_LIMIT_KILOMETERS_PER_HOUR, 20)};
		final Shape[] tempShapeStart = {EnumHelper.valueOf(Shape.CURVE, readerBase.getString(KEY_SHAPE_START, ""))};
		final Shape[] tempShapeEnd = {EnumHelper.valueOf(Shape.CURVE, readerBase.getString(KEY_SHAPE_END, ""))};
		final boolean[] tempHasSavedRail = {readerBase.getBoolean(KEY_HAS_SAVED_RAIL, false)};
		final boolean[] tempCanAccelerate = {readerBase.getBoolean(KEY_CAN_ACCELERATE, true)};
		final boolean[] tempCanTurnBack = {readerBase.getBoolean(KEY_CAN_TURN_BACK, false)};
		final boolean[] tempCanHaveSignal = {readerBase.getBoolean(KEY_CAN_HAVE_SIGNAL, true)};
		validRail = DataFixer.convertRailType(readerBase, (speedLimitKilometersPerHour, shape, hasSavedRail, canAccelerate, canTurnBack, canHaveSignal) -> {
			tempSpeedLimitKilometersPerHour[0] = speedLimitKilometersPerHour;
			tempShapeStart[0] = shape;
			tempShapeEnd[0] = shape;
			tempHasSavedRail[0] = hasSavedRail;
			tempCanAccelerate[0] = canAccelerate;
			tempCanTurnBack[0] = canTurnBack;
			tempCanHaveSignal[0] = canHaveSignal;
		});
		speedLimitKilometersPerHour = tempSpeedLimitKilometersPerHour[0];
		speedLimitMetersPerMillisecond = Utilities.kilometersPerHourToMetersPerMillisecond(speedLimitKilometersPerHour);
		shapeStart = tempShapeStart[0];
		shapeEnd = tempShapeEnd[0];
		hasSavedRail = tempHasSavedRail[0];
		canAccelerate = tempCanAccelerate[0];
		canTurnBack = tempCanTurnBack[0];
		canHaveSignal = tempCanHaveSignal[0];

		transportMode = EnumHelper.valueOf(TransportMode.TRAIN, readerBase.getString(KEY_TRANSPORT_MODE, ""));

		facingStart = getRailAngle(false);
		facingEnd = getRailAngle(true);
	}

	@Override
	public <T extends ReaderBase<U, T>, U> void updateData(T readerBase) {
	}

	@Override
	public void toMessagePack(MessagePacker messagePacker) throws IOException {
		messagePacker.packString(KEY_H_1).packDouble(h1);
		messagePacker.packString(KEY_K_1).packDouble(k1);
		messagePacker.packString(KEY_H_2).packDouble(h2);
		messagePacker.packString(KEY_K_2).packDouble(k2);
		messagePacker.packString(KEY_R_1).packDouble(r1);
		messagePacker.packString(KEY_R_2).packDouble(r2);
		messagePacker.packString(KEY_T_START_1).packDouble(tStart1);
		messagePacker.packString(KEY_T_END_1).packDouble(tEnd1);
		messagePacker.packString(KEY_T_START_2).packDouble(tStart2);
		messagePacker.packString(KEY_T_END_2).packDouble(tEnd2);
		messagePacker.packString(KEY_Y_START).packDouble(yStart);
		messagePacker.packString(KEY_Y_END).packDouble(yEnd);
		messagePacker.packString(KEY_REVERSE_T_1).packBoolean(reverseT1);
		messagePacker.packString(KEY_IS_STRAIGHT_1).packBoolean(isStraight1);
		messagePacker.packString(KEY_REVERSE_T_2).packBoolean(reverseT2);
		messagePacker.packString(KEY_IS_STRAIGHT_2).packBoolean(isStraight2);
		messagePacker.packString(KEY_SPEED_LIMIT_KILOMETERS_PER_HOUR).packDouble(speedLimitKilometersPerHour);
		messagePacker.packString(KEY_SHAPE_START).packString(shapeStart.toString());
		messagePacker.packString(KEY_SHAPE_END).packString(shapeEnd.toString());
		messagePacker.packString(KEY_TRANSPORT_MODE).packString(transportMode.toString());
	}

	@Override
	public int messagePackLength() {
		return 20;
	}

	@Override
	public String getHexId() {
		return "";
	}

	public Vec3 getPosition(double rawValue) {
		final double count1 = Math.abs(tEnd1 - tStart1);
		final double count2 = Math.abs(tEnd2 - tStart2);
		final double value = Utilities.clamp(rawValue, 0, count1 + count2);
		final double y = getPositionY(value);

		if (value <= count1) {
			return getPositionXZ(h1, k1, r1, (reverseT1 ? -1 : 1) * value + tStart1, 0, isStraight1).add(0, y, 0);
		} else {
			return getPositionXZ(h2, k2, r2, (reverseT2 ? -1 : 1) * (value - count1) + tStart2, 0, isStraight2).add(0, y, 0);
		}
	}

	public double getLength() {
		return Math.abs(tEnd2 - tStart2) + Math.abs(tEnd1 - tStart1);
	}

	public void render(RenderRail callback, float offsetRadius1, float offsetRadius2) {
		renderSegment(h1, k1, r1, tStart1, tEnd1, 0, offsetRadius1, offsetRadius2, reverseT1, isStraight1, callback);
		renderSegment(h2, k2, r2, tStart2, tEnd2, Math.abs(tEnd1 - tStart1), offsetRadius1, offsetRadius2, reverseT2, isStraight2, callback);
	}

	public boolean goodRadius() {
		return (isStraight1 || r1 > MIN_RADIUS - ACCEPT_THRESHOLD) && (isStraight2 || r2 > MIN_RADIUS - ACCEPT_THRESHOLD);
	}

	public boolean isValid() {
		return (h1 != 0 || k1 != 0 || h2 != 0 || k2 != 0 || r1 != 0 || r2 != 0 || tStart1 != 0 || tStart2 != 0 || tEnd1 != 0 || tEnd2 != 0) && facingStart == getRailAngle(false) && facingEnd == getRailAngle(true);
	}

	private double getPositionY(double value) {
		final double length = getLength();

		if (shapeStart == Shape.STRAIGHT) {
			if (value < 0.5) {
				return yStart;
			} else if (value > length - 0.5) {
				return yEnd;
			}

			final double offsetValue = value - 0.5;
			final double offsetLength = length - 1;
			final double posY = yStart + (yEnd - yStart) * offsetValue / offsetLength;
			final double dip = offsetLength * offsetLength / 4 / CABLE_CURVATURE_SCALE;
			return posY + (dip > MAX_CABLE_DIP ? MAX_CABLE_DIP / dip : 1) * (offsetValue - offsetLength) * offsetValue / CABLE_CURVATURE_SCALE;
		} else {
			final double intercept = length / 2;
			final double yChange;
			final double yInitial;
			final double offsetValue;

			if (value < intercept) {
				yChange = (yEnd - yStart) / 2D;
				yInitial = yStart;
				offsetValue = value;
			} else {
				yChange = (yStart - yEnd) / 2D;
				yInitial = yEnd;
				offsetValue = length - value;
			}

			return yChange * offsetValue * offsetValue / (intercept * intercept) + yInitial;
		}
	}

	private static Vec3 getPositionXZ(double h, double k, double r, double t, double radiusOffset, boolean isStraight) {
		if (isStraight) {
			return new Vec3(h * t + k * ((Math.abs(h) >= 0.5 && Math.abs(k) >= 0.5 ? 0 : r) + radiusOffset) + 0.5, 0, k * t + h * (r - radiusOffset) + 0.5);
		} else {
			return new Vec3(h + (r + radiusOffset) * Math.cos(t / r) + 0.5, 0, k + (r + radiusOffset) * Math.sin(t / r) + 0.5);
		}
	}

	private void renderSegment(double h, double k, double r, double tStart, double tEnd, double rawValueOffset, float offsetRadius1, float offsetRadius2, boolean reverseT, boolean isStraight, RenderRail callback) {
		final double count = Math.abs(tEnd - tStart);
		final double increment = count / Math.round(count);

		for (double i = 0; i < count - 0.1; i += increment) {
			final double t1 = (reverseT ? -1 : 1) * i + tStart;
			final double t2 = (reverseT ? -1 : 1) * (i + increment) + tStart;
			final Vec3 corner1 = getPositionXZ(h, k, r, t1, offsetRadius1, isStraight);
			final Vec3 corner2 = getPositionXZ(h, k, r, t1, offsetRadius2, isStraight);
			final Vec3 corner3 = getPositionXZ(h, k, r, t2, offsetRadius2, isStraight);
			final Vec3 corner4 = getPositionXZ(h, k, r, t2, offsetRadius1, isStraight);

			final double y1 = getPositionY(i + rawValueOffset);
			final double y2 = getPositionY(i + increment + rawValueOffset);

			callback.renderRail(corner1.x, corner1.z, corner2.x, corner2.z, corner3.x, corner3.z, corner4.x, corner4.z, y1, y2);
		}
	}

	private Angle getRailAngle(boolean getEnd) {
		final double start;
		final double end;
		if (getEnd) {
			start = getLength();
			end = start - ACCEPT_THRESHOLD;
		} else {
			start = 0;
			end = ACCEPT_THRESHOLD;
		}
		final Vec3 pos1 = getPosition(start);
		final Vec3 pos2 = getPosition(end);
		return Angle.fromAngle((float) Math.toDegrees(Math.atan2(pos2.z - pos1.z, pos2.x - pos1.x)));
	}

	private static double getTBounds(double x, double h, double z, double k, double r) {
		return Math.atan2(z - k, x - h) * r;
	}

	private static double getTBounds(double x, double h, double z, double k, double r, double tStart, boolean reverse) {
		final double t = getTBounds(x, h, z, k, r);
		if (t < tStart && !reverse) {
			return t + 2 * Math.PI * r;
		} else if (t > tStart && reverse) {
			return t - 2 * Math.PI * r;
		} else {
			return t;
		}
	}

	@FunctionalInterface
	public interface RenderRail {
		void renderRail(double x1, double z1, double x2, double z2, double x3, double z3, double x4, double z4, double y1, double y2);
	}

	public enum Shape {CURVE, STRAIGHT}
}
