package org.mtr.core.tools;

public class Vector {

	public final double x;
	public final double y;
	public final double z;

	public Vector(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

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

	public Vector rotateY(float yaw) {
		final double cos = Math.cos(yaw);
		final double sin = Math.sin(yaw);
		return new Vector(x * cos + z * sin, y, z * cos - x * sin);
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
}
