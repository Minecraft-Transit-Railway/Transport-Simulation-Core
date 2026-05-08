package org.mtr.core.data;

/**
 * There are two usages for this enum:
 * <ul>
 * <li>Lift movement direction</li>
 * <li>Lift button press direction</li>
 * </ul>
 */
public enum LiftDirection {

	/**
	 * <ul>
	 * <li>Lift movement direction: lift is not moving</li>
	 * <li>Lift button press direction: a button is pressed inside the lift</li>
	 * </ul>
	 */
	NONE(0),
	/**
	 * <ul>
	 * <li>Lift movement direction: lift is moving upwards</li>
	 * <li>Lift button press direction: the "up" button is pressed on the outside</li>
	 * </ul>
	 */
	UP(1),
	/**
	 * <ul>
	 * <li>Lift movement direction: lift is moving downwards</li>
	 * <li>Lift button press direction: the "down" button is pressed on the outside</li>
	 * </ul>
	 */
	DOWN(-1);

	public final int sign;

	LiftDirection(int sign) {
		this.sign = sign;
	}

	public static LiftDirection fromDifference(double difference) {
		if (difference == 0) {
			return NONE;
		} else if (difference > 0) {
			return UP;
		} else {
			return DOWN;
		}
	}
}
