package org.mtr.core.tool;

/**
 * Immutable {@code (x, y, z)} double vector.
 *
 * <p>Used for continuous-space rail and vehicle geometry where {@link org.mtr.core.data.Position}'s
 * integer block coordinates would be too coarse — bogie placement, curve interpolation, etc.
 * Every operation returns a fresh {@link Vector} so instances can be shared safely across
 * threads.</p>
 *
 * @param x x-axis component
 * @param y y-axis component (height)
 * @param z z-axis component
 */
public record Vector(double x, double y, double z) {

	/**
	 * @return a new vector translated by {@code (x, y, z)}.
	 */
	public Vector add(double x, double y, double z) {
		return new Vector(this.x + x, this.y + y, this.z + z);
	}

	/**
	 * @return a new vector translated by {@code vector}.
	 */
	public Vector add(Vector vector) {
		return add(vector.x, vector.y, vector.z);
	}

	/**
	 * @return a new vector with each component multiplied independently.
	 */
	public Vector multiply(double x, double y, double z) {
		return new Vector(this.x * x, this.y * y, this.z * z);
	}

	/**
	 * @return a new vector with each component multiplied by the matching component of {@code vector}.
	 */
	public Vector multiply(Vector vector) {
		return multiply(vector.x, vector.y, vector.z);
	}

	/**
	 * @return this vector rotated about the X axis by {@code angle} radians.
	 */
	public Vector rotateX(double angle) {
		final double cos = Math.cos(angle);
		final double sin = Math.sin(angle);
		return new Vector(x, y * cos + z * sin, z * cos - y * sin);
	}

	/**
	 * @return this vector rotated about the Y (vertical) axis by {@code angle} radians.
	 */
	public Vector rotateY(double angle) {
		final double cos = Math.cos(angle);
		final double sin = Math.sin(angle);
		return new Vector(x * cos + z * sin, y, z * cos - x * sin);
	}

	/**
	 * @return this vector rotated about the Z axis by {@code angle} radians.
	 */
	public Vector rotateZ(double angle) {
		final double cos = Math.cos(angle);
		final double sin = Math.sin(angle);
		return new Vector(x * cos + y * sin, y * cos - x * sin, z);
	}

	/**
	 * @return Euclidean distance between {@code this} and {@code vec}.
	 */
	public double distanceTo(Vector vec) {
		final double differenceX = vec.x - x;
		final double differenceY = vec.y - y;
		final double differenceZ = vec.z - z;
		return Math.sqrt(differenceX * differenceX + differenceY * differenceY + differenceZ * differenceZ);
	}

	/**
	 * @return a unit-length vector pointing in the same direction as {@code this}, or the zero
	 * vector if {@code this} is shorter than {@code 1E-4} (avoids divide-by-zero).
	 */
	public Vector normalize() {
		final double length = Math.sqrt(x * x + y * y + z * z);
		return length < 1E-4 ? new Vector(0, 0, 0) : new Vector(x / length, y / length, z / length);
	}

	/**
	 * @return the component-wise midpoint of {@code position1} and {@code position2}.
	 */
	public static Vector getAverage(Vector position1, Vector position2) {
		return new Vector(Utilities.getAverage(position1.x, position2.x), Utilities.getAverage(position1.y, position2.y), Utilities.getAverage(position1.z, position2.z));
	}
}
