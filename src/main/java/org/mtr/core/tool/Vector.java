package org.mtr.core.tool;

public record Vector(double x, double y, double z) {

	public Vector add(double x, double y, double z) {
		return new Vector(this.x + x, this.y + y, this.z + z);
	}

	public Vector add(Vector vector) {
		return add(vector.x, vector.y, vector.z);
	}

	public Vector multiply(double x, double y, double z) {
		return new Vector(this.x * x, this.y * y, this.z * z);
	}

	public Vector multiply(Vector vector) {
		return multiply(vector.x, vector.y, vector.z);
	}

	public Vector rotateX(double angle) {
		final double cos = Math.cos(angle);
		final double sin = Math.sin(angle);
		return new Vector(x, y * cos + z * sin, z * cos - y * sin);
	}

	public Vector rotateY(double angle) {
		final double cos = Math.cos(angle);
		final double sin = Math.sin(angle);
		return new Vector(x * cos + z * sin, y, z * cos - x * sin);
	}

	public Vector rotateZ(double angle) {
		final double cos = Math.cos(angle);
		final double sin = Math.sin(angle);
		return new Vector(x * cos + y * sin, y * cos - x * sin, z);
	}

	public double distanceTo(Vector vec) {
		final double differenceX = vec.x - x;
		final double differenceY = vec.y - y;
		final double differenceZ = vec.z - z;
		return Math.sqrt(differenceX * differenceX + differenceY * differenceY + differenceZ * differenceZ);
	}

	public Vector normalize() {
		final double length = Math.sqrt(x * x + y * y + z * z);
		return length < 1E-4 ? new Vector(0, 0, 0) : new Vector(x / length, y / length, z / length);
	}

	public static Vector getAverage(Vector position1, Vector position2) {
		return new Vector(Utilities.getAverage(position1.x, position2.x), Utilities.getAverage(position1.y, position2.y), Utilities.getAverage(position1.z, position2.z));
	}
}
