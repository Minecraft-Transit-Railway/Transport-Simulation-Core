package org.mtr.core.data;

import org.mtr.core.generated.data.PositionSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.tool.Vector;

import javax.annotation.Nullable;

public class Position extends PositionSchema implements Comparable<Position> {

	public Position(long x, long y, long z) {
		super(x, y, z);
	}

	public Position(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public Position(Vector railPosition) {
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

	public long manhattanDistance(Position position) {
		return Math.abs(position.x - x) + Math.abs(position.y - y) + Math.abs(position.z - z);
	}

	@Nullable
	public static Position getMin(@Nullable Position position1, @Nullable Position position2) {
		if (position1 == null) {
			return position2;
		}
		if (position2 == null) {
			return position1;
		}
		return new Position(Math.min(position1.x, position2.x), Math.min(position1.y, position2.y), Math.min(position1.z, position2.z));
	}

	@Nullable
	public static Position getMax(@Nullable Position position1, @Nullable Position position2) {
		if (position1 == null) {
			return position2;
		}
		if (position2 == null) {
			return position1;
		}
		return new Position(Math.max(position1.x, position2.x), Math.max(position1.y, position2.y), Math.max(position1.z, position2.z));
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
		return (int) (((x & 0xFFF) << 20) + ((y & 0xFF) << 12) + (z & 0xFFF));
	}

	@Override
	public int compareTo(Position position) {
		if (equals(position)) {
			return 0;
		} else {
			return x > position.x ? 1 : x < position.x ? -1 : y > position.y ? 1 : y < position.y ? -1 : z > position.z ? 1 : -1;
		}
	}
}
