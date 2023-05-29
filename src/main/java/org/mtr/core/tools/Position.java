package org.mtr.core.tools;

import org.mtr.core.generated.PositionSchema;
import org.mtr.core.serializers.ReaderBase;

import java.util.Objects;

public class Position extends PositionSchema implements Comparable<Position> {

	public Position(long x, long y, long z) {
		super(x, y, z);
	}

	public Position(ReaderBase readerBase) {
		super(readerBase);
	}

	public Position(Vec3 railPosition) {
		this((long) Math.floor(railPosition.x), (long) Math.floor(railPosition.y), (long) Math.floor(railPosition.z));
	}

	public long getX() {
		return x;
	}

	public long getY() {
		return y;
	}

	public long getZ() {
		return z;
	}

	public Position offset(long offsetX, long offsetY, long offsetZ) {
		return offsetX == 0 && offsetY == 0 && offsetZ == 0 ? this : new Position(x + offsetX, y + offsetY, z + offsetZ);
	}

	public Position offset(Position position) {
		return offset(position.x, position.y, position.z);
	}

	public long distManhattan(Position position) {
		return Math.abs(position.x - x) + Math.abs(position.y - y) + Math.abs(position.z - z);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Position) {
			return x == ((Position) obj).x && y == ((Position) obj).y && z == ((Position) obj).z;
		} else {
			return super.equals(obj);
		}
	}

	@Override
	public int hashCode() {
		return Objects.hash(x, y, z);
	}

	@Override
	public int compareTo(Position position) {
		if (equals(position)) {
			return 0;
		} else if (x > position.x || y > position.y || z > position.z) {
			return 1;
		} else {
			return -1;
		}
	}
}
