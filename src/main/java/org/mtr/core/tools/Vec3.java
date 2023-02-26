package org.mtr.core.tools;

public class Vec3 {

	public final double x;
	public final double y;
	public final double z;

	public Vec3(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public Vec3 add(double x, double y, double z) {
		return new Vec3(this.x + x, this.y + y, this.z + z);
	}

	public Vec3 rotateY(float yaw) {
		final double cos = Math.cos(yaw);
		final double sin = Math.sin(yaw);
		return new Vec3(x * cos + z * sin, y, z * cos - x * sin);
	}

	public double distanceTo(Vec3 vec) {
		final double differenceX = vec.x - x;
		final double differenceY = vec.y - y;
		final double differenceZ = vec.z - z;
		return Math.sqrt(differenceX * differenceX + differenceY * differenceY + differenceZ * differenceZ);
	}
}
