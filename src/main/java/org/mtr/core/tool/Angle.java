package org.mtr.core.tool;

/**
 * The 16 compass directions used throughout the simulation, at 22.5&deg; increments.
 * Each enum value pre-computes its {@link #angleDegrees} / {@link #angleRadians} and the
 * trig functions ({@link #sin}, {@link #cos}, {@link #tan}, {@link #halfTan}) so the hot
 * path can read them as fields instead of recomputing them every tick.
 *
 * <p>Bearings are measured clockwise from east ({@link #E} = 0&deg;) using Minecraft's
 * world-axis convention rather than navigation north-zero, so {@link #N} sits at 270&deg;
 * and {@link #S} at 90&deg;.</p>
 */
public enum Angle {
	/**
	 * East &mdash; +X.
	 */
	E(0),
	/**
	 * South-east-east &mdash; 22.5&deg;.
	 */
	SEE(22.5F),
	/**
	 * South-east &mdash; 45&deg;.
	 */
	SE(45),
	/**
	 * South-south-east &mdash; 67.5&deg;.
	 */
	SSE(67.5F),
	/**
	 * South &mdash; +Z, 90&deg;.
	 */
	S(90),
	/**
	 * South-south-west &mdash; 112.5&deg;.
	 */
	SSW(112.5F),
	/**
	 * South-west &mdash; 135&deg;.
	 */
	SW(135),
	/**
	 * South-west-west &mdash; 157.5&deg;.
	 */
	SWW(157.5F),
	/**
	 * West &mdash; -X, 180&deg;.
	 */
	W(180),
	/**
	 * North-west-west &mdash; 202.5&deg;.
	 */
	NWW(202.5F),
	/**
	 * North-west &mdash; 225&deg;.
	 */
	NW(225),
	/**
	 * North-north-west &mdash; 247.5&deg;.
	 */
	NNW(247.5F),
	/**
	 * North &mdash; -Z, 270&deg;.
	 */
	N(270),
	/**
	 * North-north-east &mdash; 292.5&deg;.
	 */
	NNE(292.5F),
	/**
	 * North-east &mdash; 315&deg;.
	 */
	NE(315),
	/**
	 * North-east-east &mdash; 337.5&deg;.
	 */
	NEE(337.5F);

	/**
	 * The bearing in degrees, normalised to the half-open range {@code [-180, 180)}.
	 */
	public final float angleDegrees;
	/**
	 * {@link #angleDegrees} in radians.
	 */
	public final double angleRadians;
	/**
	 * {@code Math.sin(angleRadians)}, pre-computed.
	 */
	public final double sin;
	/**
	 * {@code Math.cos(angleRadians)}, pre-computed.
	 */
	public final double cos;
	/**
	 * {@code Math.tan(angleRadians)}, pre-computed.
	 */
	public final double tan;
	/**
	 * {@code Math.tan(angleRadians / 2)}, pre-computed (used for half-angle geometry).
	 */
	public final double halfTan;

	private static final int DEGREES_IN_CIRCLE = 360;
	private static final int QUADRANTS = values().length;
	private static final float ANGLE_INCREMENT = (float) DEGREES_IN_CIRCLE / QUADRANTS;

	Angle(float angleDegrees) {
		this.angleDegrees = normalizeAngle(angleDegrees);
		angleRadians = Math.toRadians(this.angleDegrees);
		sin = Math.sin(angleRadians);
		cos = Math.cos(angleRadians);
		tan = Math.tan(angleRadians);
		halfTan = Math.tan(angleRadians / 2);
	}

	/**
	 * @return the angle 180&deg; from this one (so {@link #N} returns {@link #S}, etc.)
	 */
	public Angle getOpposite() {
		return switch (this) {
			case E -> W;
			case SEE -> NWW;
			case SE -> NW;
			case SSE -> NNW;
			case S -> N;
			case SSW -> NNE;
			case SW -> NE;
			case SWW -> NEE;
			case W -> E;
			case NWW -> SEE;
			case NW -> SE;
			case NNW -> SSE;
			case N -> S;
			case NNE -> SSW;
			case NE -> SW;
			case NEE -> SWW;
		};
	}

	/**
	 * Snap this angle to the nearest 45&deg; cardinal / ordinal direction.
	 *
	 * @return the closest of {@link #N}, {@link #NE}, {@link #E}, {@link #SE}, {@link #S},
	 * {@link #SW}, {@link #W}, {@link #NW}; values that are already on a 45&deg;
	 * multiple are returned unchanged
	 */
	public Angle getClosest45() {
		return switch (this) {
			case NNW, NNE -> N;
			case NEE, SEE -> E;
			case SSE, SSW -> S;
			case SWW, NWW -> W;
			default -> this;
		};
	}

	/**
	 * @param angle the angle to add
	 * @return the {@link Angle} closest to {@code (this + angle)} modulo 360&deg;
	 */
	public Angle add(Angle angle) {
		return fromAngle(angleDegrees + angle.angleDegrees);
	}

	/**
	 * @param angle the angle to subtract
	 * @return the {@link Angle} closest to {@code (this - angle)} modulo 360&deg;
	 */
	public Angle sub(Angle angle) {
		return fromAngle(angleDegrees - angle.angleDegrees);
	}

	/**
	 * @param angle the angle to compare against
	 * @return {@code true} when {@code angle} points the same way or 180&deg; opposite
	 */
	public boolean isParallel(Angle angle) {
		return this == angle || this == angle.getOpposite();
	}

	public boolean similarFacing(float newAngleDegrees) {
		return similarFacing(angleDegrees, newAngleDegrees);
	}

	/**
	 * @param angleDegrees1 first bearing, in degrees
	 * @param angleDegrees2 second bearing, in degrees
	 * @return {@code true} when the two bearings differ by less than 90&deg;
	 */
	public static boolean similarFacing(float angleDegrees1, float angleDegrees2) {
		return Math.abs(normalizeAngle(angleDegrees1 - angleDegrees2)) < DEGREES_IN_CIRCLE / 4F;
	}

	/**
	 * Bucket a bearing into one of 16 (or 8, when {@code include225} is {@code false}) sectors.
	 *
	 * @param angleDegrees the bearing to bucket, in degrees
	 * @param include225   {@code true} to use 22.5&deg; sectors (16 buckets), {@code false} for 45&deg; sectors (8 buckets)
	 * @return the integer sector index, in {@code [0, 16)} or {@code [0, 8)}
	 */
	public static int getQuadrant(float angleDegrees, boolean include225) {
		final int factor = include225 ? 1 : 2;
		return (Math.round((normalizeAngle(angleDegrees) + DEGREES_IN_CIRCLE) / ANGLE_INCREMENT / factor) % (QUADRANTS / factor));
	}

	/**
	 * @param angleDegrees the bearing to snap, in degrees
	 * @return the {@link Angle} value closest to {@code angleDegrees}
	 */
	public static Angle fromAngle(float angleDegrees) {
		return Angle.values()[getQuadrant(angleDegrees, true)];
	}

	private static float normalizeAngle(float angleDegrees) {
		int additional = 0;
		while (angleDegrees + additional < -DEGREES_IN_CIRCLE / 2F) {
			additional += DEGREES_IN_CIRCLE;
		}
		while (angleDegrees + additional >= DEGREES_IN_CIRCLE / 2F) {
			additional -= DEGREES_IN_CIRCLE;
		}
		return angleDegrees + additional;
	}
}
