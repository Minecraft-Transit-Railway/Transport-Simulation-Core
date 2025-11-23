package org.mtr.core.data;

import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Utilities;
import org.mtr.core.tool.Vector;

public class RailMath {

	public final long minX;
	public final long minY;
	public final long minZ;
	public final long maxY;
	public final long maxX;
	public final long maxZ;

	private final Rail.Shape shape;
	private final double verticalRadius;
	private final int tiltPoints;
	private final double tiltAngle1;
	private final double tiltAngleDistance1a;
	private final double tiltAngle1a;
	private final double tiltAngle1b;
	private final double tiltAngleDistance1b;
	private final double tiltAngleMiddle;
	private final double tiltAngleDistance2b;
	private final double tiltAngle2b;
	private final double tiltAngle2a;
	private final double tiltAngleDistance2a;
	private final double tiltAngle2;
	private final double h1;
	private final double k1;
	private final double h2;
	private final double k2;
	private final double r1;
	private final double r2;
	private final double tStart1;
	private final double tEnd1;
	private final double tStart2;
	private final double tEnd2;
	private final long yStart;
	private final long yEnd;
	private final boolean reverseT1;
	private final boolean reverseT2;
	private final boolean isStraight1;
	private final boolean isStraight2;

	private static final double ACCEPT_THRESHOLD = 1E-4;
	private static final int CABLE_CURVATURE_SCALE = 1000;
	private static final int MAX_CABLE_DIP = 8;

	// for curves:
	// x = h + r*cos(T)
	// z = k + r*sin(T)
	// for straight lines (both k and r >= 0.5):
	// x = h*T
	// z = k*T + h*r
	// for straight lines (otherwise):
	// x = h*T + k*r
	// z = k*T + h*r
	public RailMath(
			Position position1, Angle angle1,
			Position position2, Angle angle2,
			Rail.Shape shape, double verticalRadius, int tiltPoints,
			double tiltAngleDegrees1, double tiltAngleDistance1a, double tiltAngleDegrees1a, double tiltAngleDegrees1b, double tiltAngleDistance1b, double tiltAngleDegreesMiddle, double tiltAngleDistance2b, double tiltAngleDegrees2b, double tiltAngleDegrees2a, double tiltAngleDistance2a, double tiltAngleDegrees2
	) {
		final long xStart = position1.getX();
		final long zStart = position1.getZ();
		final long xEnd = position2.getX();
		final long zEnd = position2.getZ();

		// Coordinate system translation and rotation
		final Vector vecDifference = new Vector(position2.getX() - position1.getX(), 0, position2.getZ() - position1.getZ());
		final Vector vecDifferenceRotated = vecDifference.rotateY((float) angle1.angleRadians);

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
		final double deltaForward = vecDifferenceRotated.z();
		final double deltaSide = vecDifferenceRotated.x();
		if (angle1.isParallel(angle2)) { // 1
			if (Math.abs(deltaForward) < ACCEPT_THRESHOLD) { // 1. a.
				h1 = angle1.cos;
				k1 = angle1.sin;
				if (Math.abs(h1) >= 0.5 && Math.abs(k1) >= 0.5) {
					r1 = (h1 * zStart - k1 * xStart) / h1 / h1;
					tStart1 = xStart / h1;
					tEnd1 = xEnd / h1;
				} else {
					final double div = angle1.add(angle1).cos;
					r1 = (h1 * zStart - k1 * xStart) / div;
					tStart1 = (h1 * xStart - k1 * zStart) / div;
					tEnd1 = (h1 * xEnd - k1 * zEnd) / div;
				}
				h2 = 0;
				k2 = 0;
				r2 = 0;
				reverseT1 = tStart1 > tEnd1;
				reverseT2 = false;
				isStraight1 = true;
				isStraight2 = true;
				tStart2 = 0;
				tEnd2 = 0;
			} else { // 1. b
				if (Math.abs(deltaSide) > ACCEPT_THRESHOLD) {
					final double radius = (deltaForward * deltaForward + deltaSide * deltaSide) / (4 * deltaForward);
					r1 = Math.abs(radius);
					r2 = r1;
					h1 = xStart - radius * angle1.sin;
					k1 = zStart + radius * angle1.cos;
					h2 = xEnd - radius * angle2.sin;
					k2 = zEnd + radius * angle2.cos;
					reverseT1 = deltaForward < 0 != deltaSide < 0;
					reverseT2 = !reverseT1;
					tStart1 = getTBounds(xStart, h1, zStart, k1, r1);
					tEnd1 = getTBounds(xStart + vecDifference.x() / 2, h1, zStart + vecDifference.z() / 2, k1, r1, tStart1, reverseT1);
					tStart2 = getTBounds(xStart + vecDifference.x() / 2, h2, zStart + vecDifference.z() / 2, k2, r2);
					tEnd2 = getTBounds(xEnd, h2, zEnd, k2, r2, tStart2, reverseT2);
					isStraight1 = false;
					isStraight2 = false;
				} else {
					// Banned node perpendicular to the rail nodes direction
					h1 = 0;
					k1 = 0;
					h2 = 0;
					k2 = 0;
					r1 = 0;
					r2 = 0;
					tStart1 = 0;
					tStart2 = 0;
					tEnd1 = 0;
					tEnd2 = 0;
					reverseT1 = false;
					reverseT2 = false;
					isStraight1 = true;
					isStraight2 = true;
				}
			}
		} else { // 3.
			// Check if it needs invert
			final Angle newAngle1 = vecDifferenceRotated.x() < -ACCEPT_THRESHOLD ? angle1.getOpposite() : angle1;
			final Angle newAngle2 = angle2.cos * vecDifference.x() + angle2.sin * vecDifference.z() < -ACCEPT_THRESHOLD ? angle2.getOpposite() : angle2;
			final double angleForward = Math.atan2(deltaForward, deltaSide);
			final Angle railAngleDifference = newAngle2.sub(newAngle1);
			final double angleDifference = railAngleDifference.angleRadians;

			if (Math.signum(angleForward) == Math.signum(angleDifference)) {
				final double absAngleForward = Math.abs(angleForward);

				if (absAngleForward - Math.abs(angleDifference / 2) < ACCEPT_THRESHOLD) { // Segment First
					final double offsetSide = Math.abs(deltaForward / railAngleDifference.halfTan);
					final double remainingSide = deltaSide - offsetSide;
					final double deltaXEnd = xStart + remainingSide * newAngle1.cos;
					final double deltaZEnd = zStart + remainingSide * newAngle1.sin;
					h1 = newAngle1.cos;
					k1 = newAngle1.sin;
					if (Math.abs(h1) >= 0.5 && Math.abs(k1) >= 0.5) {
						r1 = (h1 * zStart - k1 * xStart) / h1 / h1;
						tStart1 = xStart / h1;
						tEnd1 = deltaXEnd / h1;
					} else {
						final double div = newAngle1.add(newAngle1).cos;
						r1 = (h1 * zStart - k1 * xStart) / div;
						tStart1 = (h1 * xStart - k1 * zStart) / div;
						tEnd1 = (h1 * deltaXEnd - k1 * deltaZEnd) / div;
					}
					isStraight1 = true;
					reverseT1 = tStart1 > tEnd1;
					final double radius = deltaForward / (1 - railAngleDifference.cos);
					r2 = Math.abs(radius);
					h2 = deltaXEnd - radius * newAngle1.sin;
					k2 = deltaZEnd + radius * newAngle1.cos;
					reverseT2 = (deltaForward < 0);
					tStart2 = getTBounds(deltaXEnd, h2, deltaZEnd, k2, r2);
					tEnd2 = getTBounds(xEnd, h2, zEnd, k2, r2, tStart2, reverseT2);
					isStraight2 = false;
				} else if (absAngleForward - Math.abs(angleDifference) < ACCEPT_THRESHOLD) { // Circle First
					final double crossSide = deltaForward / railAngleDifference.tan;
					final double remainingSide = (deltaSide - crossSide) * (1 + railAngleDifference.cos);
					final double remainingForward = (deltaSide - crossSide) * (railAngleDifference.sin);
					final double deltaXEnd = xStart + remainingSide * newAngle1.cos - remainingForward * newAngle1.sin;
					final double deltaZEnd = zStart + remainingSide * newAngle1.sin + remainingForward * newAngle1.cos;
					final double radius = (deltaSide - deltaForward / railAngleDifference.tan) / railAngleDifference.halfTan;
					r1 = Math.abs(radius);
					h1 = xStart - radius * newAngle1.sin;
					k1 = zStart + radius * newAngle1.cos;
					isStraight1 = false;
					reverseT1 = (deltaForward < 0);
					tStart1 = getTBounds(xStart, h1, zStart, k1, r1);
					tEnd1 = getTBounds(deltaXEnd, h1, deltaZEnd, k1, r1, tStart1, reverseT1);
					h2 = newAngle2.cos;
					k2 = newAngle2.sin;
					if (Math.abs(h2) >= 0.5 && Math.abs(k2) >= 0.5) {
						r2 = (h2 * deltaZEnd - k2 * deltaXEnd) / h2 / h2;
						tStart2 = deltaXEnd / h2;
						tEnd2 = xEnd / h2;
					} else {
						final double div = newAngle2.add(newAngle2).cos;
						r2 = (h2 * deltaZEnd - k2 * deltaXEnd) / div;
						tStart2 = (h2 * deltaXEnd - k2 * deltaZEnd) / div;
						tEnd2 = (h2 * xEnd - k2 * zEnd) / div;
					}
					isStraight2 = true;
					reverseT2 = tStart2 > tEnd2;
				} else { // Out of available range
					// TODO complex one. Normally we don't need it.
					h1 = 0;
					k1 = 0;
					h2 = 0;
					k2 = 0;
					r1 = 0;
					r2 = 0;
					tStart1 = 0;
					tStart2 = 0;
					tEnd1 = 0;
					tEnd2 = 0;
					reverseT1 = false;
					reverseT2 = false;
					isStraight1 = true;
					isStraight2 = true;
				}
			} else {
				// TODO 3. b. If not -> r = very complex one. Normally we don't need it.
				h1 = 0;
				k1 = 0;
				h2 = 0;
				k2 = 0;
				r1 = 0;
				r2 = 0;
				tStart1 = 0;
				tStart2 = 0;
				tEnd1 = 0;
				tEnd2 = 0;
				reverseT1 = false;
				reverseT2 = false;
				isStraight1 = true;
				isStraight2 = true;
			}
		}

		yStart = position1.getY();
		yEnd = position2.getY();
		this.shape = shape;
		this.verticalRadius = Math.min(verticalRadius, getMaxVerticalRadius());
		this.tiltPoints = tiltPoints;
		this.tiltAngle1 = Math.toRadians(tiltAngleDegrees1);
		this.tiltAngleDistance1a = tiltAngleDistance1a;
		this.tiltAngle1a = Math.toRadians(tiltAngleDegrees1a);
		this.tiltAngle1b = Math.toRadians(tiltAngleDegrees1b);
		this.tiltAngleDistance1b = tiltAngleDistance1b;
		this.tiltAngleMiddle = Math.toRadians(tiltAngleDegreesMiddle);
		this.tiltAngleDistance2b = tiltAngleDistance2b;
		this.tiltAngle2b = Math.toRadians(tiltAngleDegrees2b);
		this.tiltAngle2a = Math.toRadians(tiltAngleDegrees2a);
		this.tiltAngleDistance2a = tiltAngleDistance2a;
		this.tiltAngle2 = Math.toRadians(tiltAngleDegrees2);

		// Calculate bounds (for culling)
		final double[] bounds = new double[]{Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
		render((x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, tiltAngle) -> {
			bounds[0] = Math.min(x1, bounds[0]);
			bounds[0] = Math.min(x2, bounds[0]);
			bounds[0] = Math.min(x3, bounds[0]);
			bounds[0] = Math.min(x4, bounds[0]);
			bounds[1] = Math.min(y1, bounds[1]);
			bounds[1] = Math.min(y2, bounds[1]);
			bounds[1] = Math.min(y3, bounds[1]);
			bounds[1] = Math.min(y4, bounds[1]);
			bounds[2] = Math.min(z1, bounds[2]);
			bounds[2] = Math.min(z2, bounds[2]);
			bounds[2] = Math.min(z3, bounds[2]);
			bounds[2] = Math.min(z4, bounds[2]);
			bounds[3] = Math.max(x1, bounds[3]);
			bounds[3] = Math.max(x2, bounds[3]);
			bounds[3] = Math.max(x3, bounds[3]);
			bounds[3] = Math.max(x4, bounds[3]);
			bounds[4] = Math.max(y1, bounds[4]);
			bounds[4] = Math.max(y2, bounds[4]);
			bounds[4] = Math.max(y3, bounds[4]);
			bounds[4] = Math.max(y4, bounds[4]);
			bounds[5] = Math.max(z1, bounds[5]);
			bounds[5] = Math.max(z2, bounds[5]);
			bounds[5] = Math.max(z3, bounds[5]);
			bounds[5] = Math.max(z4, bounds[5]);
		}, 0.1, 0, 0);
		minX = bounds[0] > bounds[3] ? 0 : (long) Math.floor(bounds[0]);
		minY = bounds[1] > bounds[4] ? 0 : (long) Math.floor(bounds[1]);
		minZ = bounds[2] > bounds[5] ? 0 : (long) Math.floor(bounds[2]);
		maxX = bounds[3] < bounds[0] ? 0 : (long) Math.ceil(bounds[3]);
		maxY = bounds[4] < bounds[1] ? 0 : (long) Math.ceil(bounds[4]);
		maxZ = bounds[5] < bounds[2] ? 0 : (long) Math.ceil(bounds[5]);
	}

	public Vector getPosition(double rawValue, boolean reverse) {
		final double count1 = getLength1();
		final double count2 = getLength2();
		final double clampedValue = Utilities.clampSafe(rawValue, 0, count1 + count2);
		final double value = reverse ? count1 + count2 - clampedValue : clampedValue;
		final double y = getPositionY(value);

		if (value <= count1) {
			return getPosition(h1, k1, r1, (reverseT1 ? -1 : 1) * value + tStart1, y, 0, isStraight1);
		} else {
			return getPosition(h2, k2, r2, (reverseT2 ? -1 : 1) * (value - count1) + tStart2, y, 0, isStraight2);
		}
	}

	public double getTiltAngle(double rawValue, boolean reverse) {
		final double length = getLength();
		final double clampedValue = Utilities.clampSafe(rawValue, 0, length);
		final double value = reverse ? length - clampedValue : clampedValue;
		final ObjectImmutableList<DoubleDoubleImmutablePair> tiltPointsAndAngles = getTiltPointsAndAngles(reverse);

		for (int i = 1; i < tiltPointsAndAngles.size(); i++) {
			final DoubleDoubleImmutablePair previousTiltPointAndAngle = tiltPointsAndAngles.get(i - 1);
			final DoubleDoubleImmutablePair thisTiltPointAndAngle = tiltPointsAndAngles.get(i);
			final double point1 = previousTiltPointAndAngle.leftDouble();
			final double point2 = thisTiltPointAndAngle.leftDouble();
			if (i == tiltPointsAndAngles.size() - 1 || value < point2) {
				final double tiltAngle1 = previousTiltPointAndAngle.rightDouble();
				final double tiltAngle2 = thisTiltPointAndAngle.rightDouble();
				return Utilities.circularClamp(Utilities.getValueFromPercentage(
						(value - point1) / (point2 - point1),
						tiltAngle1,
						Utilities.circularClamp(tiltAngle2, tiltAngle1 - Math.PI, tiltAngle1 + Math.PI, 2 * Math.PI)
				), -Math.PI, Math.PI, 2 * Math.PI) * (reverse ? -1 : 1);
			}
		}

		return 0;
	}

	public double getLength() {
		return getLength1() + getLength2();
	}

	public Rail.Shape getShape() {
		return shape;
	}

	public DoubleDoubleImmutablePair getHorizontalRadii() {
		return new DoubleDoubleImmutablePair(isStraight1 ? 0 : Math.abs(r1), isStraight2 ? 0 : Math.abs(r2));
	}

	public double getVerticalRadius() {
		return verticalRadius;
	}

	public double getMaxVerticalRadius() {
		final double length = getLength();
		final double height = yEnd - yStart;
		return Math.floor((length * length + height * height) * 100 / Math.abs(4 * height)) / 100;
	}

	public void render(RenderRail callback, double interval, float offsetRadius1, float offsetRadius2) {
		renderSegment(h1, k1, r1, tStart1, tEnd1, 0, interval, offsetRadius1, offsetRadius2, reverseT1, isStraight1, callback);
		renderSegment(h2, k2, r2, tStart2, tEnd2, Math.abs(tEnd1 - tStart1), interval, offsetRadius1, offsetRadius2, reverseT2, isStraight2, callback);
	}

	ObjectImmutableList<DoubleDoubleImmutablePair> getTiltPointsAndAngles(boolean reversed) {
		final double count1 = getLength1();
		final double count2 = getLength2();
		final double length = count1 + count2;

		if (length == 0) {
			return ObjectImmutableList.of();
		}

		final double middlePoint = count1 == 0 || count2 == 0 ? length / 2 : count1;

		switch (tiltPoints) {
			case 3:
				return createList(
						reversed,
						new DoubleDoubleImmutablePair(0, tiltAngle1),
						new DoubleDoubleImmutablePair(middlePoint, tiltAngleMiddle),
						new DoubleDoubleImmutablePair(length, tiltAngle2)
				);
			case 4:
				return createList(
						reversed,
						new DoubleDoubleImmutablePair(0, tiltAngle1),
						new DoubleDoubleImmutablePair(Utilities.clampSafe(tiltAngleDistance1a, 0, middlePoint), tiltAngle1a),
						new DoubleDoubleImmutablePair(Utilities.clampSafe(length - tiltAngleDistance2a, middlePoint, length), tiltAngle2a),
						new DoubleDoubleImmutablePair(length, tiltAngle2)
				);
			case 5:
				return createList(
						reversed,
						new DoubleDoubleImmutablePair(0, tiltAngle1),
						new DoubleDoubleImmutablePair(Utilities.clampSafe(tiltAngleDistance1a, 0, middlePoint), tiltAngle1a),
						new DoubleDoubleImmutablePair(middlePoint, tiltAngleMiddle),
						new DoubleDoubleImmutablePair(Utilities.clampSafe(length - tiltAngleDistance2a, middlePoint, length), tiltAngle2a),
						new DoubleDoubleImmutablePair(length, tiltAngle2)
				);
			case 6:
			case 7:
				double distance1a = Utilities.clampSafe(tiltAngleDistance1a, 0, middlePoint);
				double distance1b = Utilities.clampSafe(middlePoint - tiltAngleDistance1b, 0, middlePoint);
				double distance2b = Utilities.clampSafe(middlePoint + tiltAngleDistance2b, middlePoint, length);
				double distance2a = Utilities.clampSafe(length - tiltAngleDistance2a, middlePoint, length);

				if (distance1a > distance1b) {
					final double average = Utilities.getAverage(distance1a, distance1b);
					distance1a = average;
					distance1b = average;
				}

				if (distance2b > distance2a) {
					final double average = Utilities.getAverage(distance2a, distance2b);
					distance2a = average;
					distance2b = average;
				}

				return tiltPoints == 6 ? createList(
						reversed,
						new DoubleDoubleImmutablePair(0, tiltAngle1),
						new DoubleDoubleImmutablePair(distance1a, tiltAngle1a),
						new DoubleDoubleImmutablePair(distance1b, tiltAngle1b),
						new DoubleDoubleImmutablePair(distance2b, tiltAngle2b),
						new DoubleDoubleImmutablePair(distance2a, tiltAngle2a),
						new DoubleDoubleImmutablePair(length, tiltAngle2)
				) : createList(
						reversed,
						new DoubleDoubleImmutablePair(0, tiltAngle1),
						new DoubleDoubleImmutablePair(distance1a, tiltAngle1a),
						new DoubleDoubleImmutablePair(distance1b, tiltAngle1b),
						new DoubleDoubleImmutablePair(middlePoint, tiltAngleMiddle),
						new DoubleDoubleImmutablePair(distance2b, tiltAngle2b),
						new DoubleDoubleImmutablePair(distance2a, tiltAngle2a),
						new DoubleDoubleImmutablePair(length, tiltAngle2)
				);
			default:
				return createList(
						reversed,
						new DoubleDoubleImmutablePair(0, tiltAngle1),
						new DoubleDoubleImmutablePair(length, tiltAngle2)
				);
		}
	}

	boolean isValid() {
		return h1 != 0 || k1 != 0 || h2 != 0 || k2 != 0 || r1 != 0 || r2 != 0 || tStart1 != 0 || tStart2 != 0 || tEnd1 != 0 || tEnd2 != 0;
	}

	private double getLength1() {
		return Math.abs(tEnd1 - tStart1);
	}

	private double getLength2() {
		return Math.abs(tEnd2 - tStart2);
	}

	private void renderSegment(double h, double k, double r, double tStart, double tEnd, double rawValueOffset, double interval, float offsetRadius1, float offsetRadius2, boolean reverseT, boolean isStraight, RenderRail callback) {
		final double count = Math.abs(tEnd - tStart);
		final double increment = count < 0.5 || interval <= 0 ? 0.5 : count / Math.round(count) * interval;
		Vector previousCorner1 = null;
		Vector previousCorner2 = null;

		for (double i = 0; i < count + increment - 0.001; i += increment) {
			final double t = (reverseT ? -1 : 1) * i + tStart;
			final double y = getPositionY(i + rawValueOffset);
			final double tiltAngle = getTiltAngle(i + rawValueOffset, false);

			final Vector center = getPosition(h, k, r, t, y, 0, isStraight);
			final Vector corner1 = offsetRadius2 == 0 ? center : applyTiltAngleOffset(getPosition(h, k, r, t, y, offsetRadius2, isStraight), center, -tiltAngle * (reverseT ? -1 : 1));
			final Vector corner2 = offsetRadius1 == 0 ? center : applyTiltAngleOffset(getPosition(h, k, r, t, y, offsetRadius1, isStraight), center, tiltAngle * (reverseT ? -1 : 1));

			if (previousCorner1 != null) {
				callback.renderRail(
						previousCorner1.x(), previousCorner1.y(), previousCorner1.z(),
						previousCorner2.x(), previousCorner2.y(), previousCorner2.z(),
						corner1.x(), corner1.y(), corner1.z(),
						corner2.x(), corner2.y(), corner2.z(),
						tiltAngle
				);
			}

			previousCorner1 = corner2;
			previousCorner2 = corner1;
		}
	}

	private double getPositionY(double value) {
		if (yStart == yEnd) {
			return yStart;
		}

		final double length = getLength();

		switch (shape) {
			case TWO_RADII:
				// Copied from NTE
				if (verticalRadius <= 0) {
					return (value / length) * (yEnd - yStart) + yStart;
				} else {
					final double vTheta = getVTheta();
					final double curveLength = Math.sin(vTheta) * verticalRadius;
					final double curveHeight = (1 - Math.cos(vTheta)) * verticalRadius;
					final int sign = yStart < yEnd ? 1 : -1;

					if (value < curveLength) {
						return sign * (verticalRadius - Math.sqrt(verticalRadius * verticalRadius - value * value)) + yStart;
					} else if (value > length - curveLength) {
						final double r = length - value;
						return -sign * (verticalRadius - Math.sqrt(verticalRadius * verticalRadius - r * r)) + yEnd;
					} else {
						return sign * (((value - curveLength) / (length - 2 * curveLength)) * (Math.abs(yEnd - yStart) - 2 * curveHeight) + curveHeight) + yStart;
					}
				}
			case CABLE:
				if (value < 0.5) {
					return yStart;
				} else if (value > length - 0.5) {
					return yEnd;
				}

				final double cableOffsetValue = value - 0.5;
				final double offsetLength = length - 1;
				final double posY = yStart + (yEnd - yStart) * cableOffsetValue / offsetLength;
				final double dip = offsetLength * offsetLength / 4 / CABLE_CURVATURE_SCALE;
				return posY + (dip > MAX_CABLE_DIP ? MAX_CABLE_DIP / dip : 1) * (cableOffsetValue - offsetLength) * cableOffsetValue / CABLE_CURVATURE_SCALE;
			default:
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

	private double getVTheta() {
		final double height = Math.abs(yEnd - yStart);
		final double length = getLength();
		return 2 * Math.atan2(Math.sqrt(height * height - 4 * verticalRadius * height + length * length) - length, height - 4 * verticalRadius);
	}

	private static Vector getPosition(double h, double k, double r, double t, double y, double radiusOffset, boolean isStraight) {
		if (isStraight) {
			return new Vector(h * t + k * ((Math.abs(h) >= 0.5 && Math.abs(k) >= 0.5 ? 0 : r) + radiusOffset) + 0.5, y, k * t + h * (r - radiusOffset) + 0.5);
		} else {
			return new Vector(h + (r + radiusOffset) * Math.cos(t / r) + 0.5, y, k + (r + radiusOffset) * Math.sin(t / r) + 0.5);
		}
	}

	private static Vector applyTiltAngleOffset(Vector corner, Vector center, double tiltAngle) {
		final Vector offset = new Vector(corner.x() - center.x(), corner.y() - center.y(), corner.z() - center.z());
		final double angle = Math.atan2(offset.z(), offset.x());
		return center.add(offset.rotateY(angle).rotateZ(tiltAngle).rotateY(-angle));
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

	private static ObjectImmutableList<DoubleDoubleImmutablePair> createList(boolean reversed, DoubleDoubleImmutablePair... data) {
		if (reversed) {
			final DoubleDoubleImmutablePair[] result = new DoubleDoubleImmutablePair[data.length];
			for (int i = 0; i < data.length; i++) {
				result[i] = data[data.length - i - 1];
			}
			return ObjectImmutableList.of(result);
		} else {
			return ObjectImmutableList.of(data);
		}
	}

	@FunctionalInterface
	public interface RenderRail {
		void renderRail(double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4, double tiltAngle);
	}
}
