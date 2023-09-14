package org.mtr.core.data;

import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import org.mtr.core.generated.RailSchema;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.*;

public final class Rail extends RailSchema {

	public final Angle facingStart;
	public final Angle facingEnd;
	public final double speedLimitMetersPerMillisecond;

	private static final double ACCEPT_THRESHOLD = 1E-4;
	private static final int MIN_RADIUS = 2;
	private static final int CABLE_CURVATURE_SCALE = 1000;
	private static final int MAX_CABLE_DIP = 8;

	public static Rail copy(Position posStart, Position posEnd, Rail rail, Simulator simulator, ObjectAVLTreeSet<Platform> platformsToAdd, ObjectAVLTreeSet<Siding> sidingsToAdd) {
		final Rail newRail = newRail(posStart, rail.facingStart, posEnd, rail.facingEnd, rail.speedLimit, rail.shapeStart, rail.shapeEnd, rail.isPlatform, rail.isSiding, rail.canAccelerate, rail.canTurnBack, rail.canHaveSignal, rail.transportMode);
		if (newRail.isValid()) {
			if (newRail.isPlatform && simulator.platforms.stream().noneMatch(platform -> platform.containsPos(posStart) && platform.containsPos(posEnd))) {
				final Platform platform = new Platform(posStart, posEnd, newRail.transportMode, simulator);
				simulator.platforms.add(platform);
				platformsToAdd.add(platform);
			}
			if (newRail.isSiding && simulator.sidings.stream().noneMatch(siding -> siding.containsPos(posStart) && siding.containsPos(posEnd))) {
				final Siding siding = new Siding(posStart, posEnd, newRail.getLength(), newRail.transportMode, simulator);
				simulator.sidings.add(siding);
				sidingsToAdd.add(siding);
			}
			return newRail;
		} else {
			return null;
		}
	}

	public static Rail newRail(Position posStart, Angle facingStart, Position posEnd, Angle facingEnd, long speedLimit, Shape shapeStart, Shape shapeEnd, boolean isPlatform, boolean isSavedRail, boolean canAccelerate, boolean canHaveSignal, TransportMode transportMode) {
		return newRail(posStart, facingStart, posEnd, facingEnd, speedLimit, shapeStart, shapeEnd, isPlatform, isSavedRail, canAccelerate, false, canHaveSignal, transportMode);
	}

	public static Rail newTurnBackRail(Position posStart, Angle facingStart, Position posEnd, Angle facingEnd, Shape shapeStart, Shape shapeEnd, TransportMode transportMode) {
		return newRail(posStart, facingStart, posEnd, facingEnd, 80, shapeStart, shapeEnd, false, false, false, true, false, transportMode);
	}

	public static Rail newPlatformRail(Position posStart, Angle facingStart, Position posEnd, Angle facingEnd, Shape shapeStart, Shape shapeEnd, TransportMode transportMode) {
		return newPlatformOrSidingRail(posStart, facingStart, posEnd, facingEnd, true, shapeStart, shapeEnd, transportMode);
	}

	public static Rail newSidingRail(Position posStart, Angle facingStart, Position posEnd, Angle facingEnd, Shape shapeStart, Shape shapeEnd, TransportMode transportMode) {
		return newPlatformOrSidingRail(posStart, facingStart, posEnd, facingEnd, false, shapeStart, shapeEnd, transportMode);
	}

	private static Rail newPlatformOrSidingRail(Position posStart, Angle facingStart, Position posEnd, Angle facingEnd, boolean isPlatform, Shape shapeStart, Shape shapeEnd, TransportMode transportMode) {
		return newRail(posStart, facingStart, posEnd, facingEnd, isPlatform ? 80 : 40, shapeStart, shapeEnd, isPlatform, !isPlatform, false, false, true, transportMode);
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

	private static Rail newRail(Position posStart, Angle facingStart, Position posEnd, Angle facingEnd, long speedLimit, Shape shape1, Shape shape2, boolean isPlatform, boolean isSiding, boolean canAccelerate, boolean canTurnBack, boolean canHaveSignal, TransportMode transportMode) {
		final long xStart = posStart.getX();
		final long zStart = posStart.getZ();
		final long xEnd = posEnd.getX();
		final long zEnd = posEnd.getZ();

		// Coordinate system translation and rotation
		final Vector vecDifference = new Vector(posEnd.getX() - posStart.getX(), 0, posEnd.getZ() - posStart.getZ());
		final Vector vecDifferenceRotated = vecDifference.rotateY((float) facingStart.angleRadians);

		final double h1;
		final double k1;
		final double h2;
		final double k2;
		final double r1;
		final double r2;
		final double tStart1;
		final double tEnd1;
		final double tStart2;
		final double tEnd2;
		final boolean reverseT1;
		final boolean reverseT2;
		final boolean isStraight1;
		final boolean isStraight2;

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

		return new Rail(h1, k1, h2, k2, r1, r2, tStart1, tEnd1, tStart2, tEnd2, posStart.getY(), posEnd.getY(), reverseT1, reverseT2, isStraight1, isStraight2, speedLimit, shape1, shape2, isPlatform, isSiding, canAccelerate, canTurnBack, canHaveSignal, transportMode);
	}

	private Rail(double h1, double k1, double h2, double k2, double r1, double r2, double tStart1, double tEnd1, double tStart2, double tEnd2, long yStart, long yEnd, boolean reverseT1, boolean reverseT2, boolean isStraight1, boolean isStraight2, long speedLimit, Rail.Shape shapeStart, Rail.Shape shapeEnd, boolean isPlatform, boolean isSiding, boolean canAccelerate, boolean canTurnBack, boolean canHaveSignal, TransportMode transportMode) {
		super(h1, k1, h2, k2, r1, r2, tStart1, tEnd1, tStart2, tEnd2, yStart, yEnd, reverseT1, reverseT2, isStraight1, isStraight2, speedLimit, shapeStart, shapeEnd, isPlatform, isSiding, canAccelerate, canTurnBack, canHaveSignal, transportMode);
		speedLimitMetersPerMillisecond = Utilities.kilometersPerHourToMetersPerMillisecond(speedLimit);
		facingStart = getRailAngle(false);
		facingEnd = getRailAngle(true);
	}

	public Rail(ReaderBase readerBase) {
		super(DataFixer.convertRail(readerBase));
		speedLimitMetersPerMillisecond = Utilities.kilometersPerHourToMetersPerMillisecond(speedLimit);
		facingStart = getRailAngle(false);
		facingEnd = getRailAngle(true);
		updateData(readerBase);
	}

	public TransportMode getTransportMode() {
		return transportMode;
	}

	public long getSpeedLimitKilometersPerHour() {
		return speedLimit;
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

	public Vector getPosition(double rawValue) {
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

	public boolean closeTo(Position position, double radius) {
		return Utilities.isBetween(position, getPosition(0), getPosition(getLength()), radius);
	}

	public void render(RenderRail callback, float offsetRadius1, float offsetRadius2) {
		renderSegment(h1, k1, r1, tStart1, tEnd1, 0, offsetRadius1, offsetRadius2, reverseT1, isStraight1, callback);
		renderSegment(h2, k2, r2, tStart2, tEnd2, Math.abs(tEnd1 - tStart1), offsetRadius1, offsetRadius2, reverseT2, isStraight2, callback);
	}

	public boolean goodRadius() {
		return (isStraight1 || r1 > MIN_RADIUS - ACCEPT_THRESHOLD) && (isStraight2 || r2 > MIN_RADIUS - ACCEPT_THRESHOLD);
	}

	/**
	 * A rail is valid if all the following conditions are met:
	 * <ul>
	 * <li>Speed must be greater than 0</li>
	 * <li>All values can't be zero</li>
	 * <li>Make sure the directions of the start and end of the rail makes sense</li>
	 * <li>The rail is either a platform, a siding, neither, but not both</li>
	 * </ul>
	 *
	 * @return whether the above conditions are met
	 */
	public boolean isValid() {
		return speedLimit > 0
				&& (h1 != 0 || k1 != 0 || h2 != 0 || k2 != 0 || r1 != 0 || r2 != 0 || tStart1 != 0 || tStart2 != 0 || tEnd1 != 0 || tEnd2 != 0)
				&& facingStart == getRailAngle(false)
				&& facingEnd == getRailAngle(true)
				&& (!isPlatform || !isSiding);
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

	private static Vector getPositionXZ(double h, double k, double r, double t, double radiusOffset, boolean isStraight) {
		if (isStraight) {
			return new Vector(h * t + k * ((Math.abs(h) >= 0.5 && Math.abs(k) >= 0.5 ? 0 : r) + radiusOffset) + 0.5, 0, k * t + h * (r - radiusOffset) + 0.5);
		} else {
			return new Vector(h + (r + radiusOffset) * Math.cos(t / r) + 0.5, 0, k + (r + radiusOffset) * Math.sin(t / r) + 0.5);
		}
	}

	private void renderSegment(double h, double k, double r, double tStart, double tEnd, double rawValueOffset, float offsetRadius1, float offsetRadius2, boolean reverseT, boolean isStraight, RenderRail callback) {
		final double count = Math.abs(tEnd - tStart);
		final double increment = count / Math.round(count);

		for (double i = 0; i < count - 0.1; i += increment) {
			final double t1 = (reverseT ? -1 : 1) * i + tStart;
			final double t2 = (reverseT ? -1 : 1) * (i + increment) + tStart;
			final Vector corner1 = getPositionXZ(h, k, r, t1, offsetRadius1, isStraight);
			final Vector corner2 = getPositionXZ(h, k, r, t1, offsetRadius2, isStraight);
			final Vector corner3 = getPositionXZ(h, k, r, t2, offsetRadius2, isStraight);
			final Vector corner4 = getPositionXZ(h, k, r, t2, offsetRadius1, isStraight);

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
		final Vector pos1 = getPosition(start);
		final Vector pos2 = getPosition(end);
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
