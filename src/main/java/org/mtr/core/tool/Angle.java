package org.mtr.core.tool;

public enum Angle {
	E(0),
	SEE(22.5F),
	SE(45),
	SSE(67.5F),
	S(90),
	SSW(112.5F),
	SW(135),
	SWW(157.5F),
	W(180),
	NWW(202.5F),
	NW(225),
	NNW(247.5F),
	N(270),
	NNE(292.5F),
	NE(315),
	NEE(337.5F);

	public final float angleDegrees;
	public final double angleRadians;
	public final double sin;
	public final double cos;
	public final double tan;
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

	public Angle getOpposite() {
		switch (this) {
			default:
				return W;
			case SEE:
				return NWW;
			case SE:
				return NW;
			case SSE:
				return NNW;
			case S:
				return N;
			case SSW:
				return NNE;
			case SW:
				return NE;
			case SWW:
				return NEE;
			case W:
				return E;
			case NWW:
				return SEE;
			case NW:
				return SE;
			case NNW:
				return SSE;
			case N:
				return S;
			case NNE:
				return SSW;
			case NE:
				return SW;
			case NEE:
				return SWW;
		}
	}

	public Angle getClosest45() {
		switch (this) {
			default:
				return this;
			case NNW:
			case NNE:
				return N;
			case NEE:
			case SEE:
				return E;
			case SSE:
			case SSW:
				return S;
			case SWW:
			case NWW:
				return W;
		}
	}

	public Angle add(Angle angle) {
		return fromAngle(angleDegrees + angle.angleDegrees);
	}

	public Angle sub(Angle angle) {
		return fromAngle(angleDegrees - angle.angleDegrees);
	}

	public boolean isParallel(Angle angle) {
		return this == angle || this == angle.getOpposite();
	}

	public boolean similarFacing(float newAngleDegrees) {
		return similarFacing(angleDegrees, newAngleDegrees);
	}

	public static boolean similarFacing(float angleDegrees1, float angleDegrees2) {
		return Math.abs(normalizeAngle(angleDegrees1 - angleDegrees2)) < DEGREES_IN_CIRCLE / 4F;
	}

	public static int getQuadrant(float angleDegrees, boolean include225) {
		final int factor = include225 ? 1 : 2;
		return (Math.round((normalizeAngle(angleDegrees) + DEGREES_IN_CIRCLE) / ANGLE_INCREMENT / factor) % (QUADRANTS / factor));
	}

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
