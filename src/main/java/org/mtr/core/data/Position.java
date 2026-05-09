package org.mtr.core.data;

import org.jspecify.annotations.Nullable;
import org.mtr.core.generated.data.PositionSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.tool.Vector;

/**
 * Immutable {@code (x, y, z)} integer block position in a Minecraft world.
 *
 * <p>Used throughout the simulator as the canonical key for rails, platforms, station areas and
 * any other voxel-aligned reference. Backed by the schema-generated {@link PositionSchema} so
 * the same shape round-trips through JSON / MessagePack persistence.</p>
 *
 * <p>Equality / ordering / hashing are by {@code (x, y, z)}; {@link #compareTo(Position)} sorts
 * by {@code x}, then {@code y}, then {@code z} so positions can live in {@code AVLTreeMap}-keyed
 * structures with predictable iteration order.</p>
 */
public class Position extends PositionSchema implements Comparable<Position> {

	// Bit-packing constants for hashCode(). 12 bits of x, 8 bits of y, 12 bits of z fits a single
	// 32-bit int — enough spread for typical Minecraft chunk-aligned positions while keeping the
	// hash computation cheap. Y gets fewer bits because the world is much shallower than wide.
	private static final int X_HASH_BITS = 12;
	private static final int Y_HASH_BITS = 8;
	private static final int Z_HASH_BITS = 12;
	private static final int X_HASH_MASK = (1 << X_HASH_BITS) - 1;
	private static final int Y_HASH_MASK = (1 << Y_HASH_BITS) - 1;
	private static final int Z_HASH_MASK = (1 << Z_HASH_BITS) - 1;
	private static final int X_HASH_SHIFT = Y_HASH_BITS + Z_HASH_BITS;
	private static final int Y_HASH_SHIFT = Z_HASH_BITS;

	/** Construct an explicit position. */
	public Position(long x, long y, long z) {
		super(x, y, z);
	}

	/** Deserialisation constructor used by the wire / on-disk layer. */
	public Position(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	/** Snap a continuous {@link Vector} down to its containing block (floor on each axis). */
	public Position(Vector railPosition) {
		this((long) Math.floor(railPosition.x()), (long) Math.floor(railPosition.y()), (long) Math.floor(railPosition.z()));
	}

	/** @return the world-space {@code x} coordinate */
	public long getX() {
		return x;
	}

	/** @return the world-space {@code y} (height) coordinate */
	public long getY() {
		return y;
	}

	/** @return the world-space {@code z} coordinate */
	public long getZ() {
		return z;
	}

	/**
	 * @return a new position translated by {@code (offsetX, offsetY, offsetZ)}, or {@code this}
	 * if the offset is the zero vector (avoids an allocation in the hot path).
	 */
	public Position offset(long offsetX, long offsetY, long offsetZ) {
		return offsetX == 0 && offsetY == 0 && offsetZ == 0 ? this : new Position(x + offsetX, y + offsetY, z + offsetZ);
	}

	/** @return {@code this + position} as a new {@link Position}. */
	public Position offset(Position position) {
		return offset(position.x, position.y, position.z);
	}

	/** @return Manhattan (taxicab) distance between {@code this} and {@code position}. */
	public long manhattanDistance(Position position) {
		return Math.abs(position.x - x) + Math.abs(position.y - y) + Math.abs(position.z - z);
	}

	/**
	 * Component-wise minimum with {@code null} treated as "the other operand wins". Used when
	 * accumulating bounding boxes from a stream of optional positions.
	 */
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

	/** Component-wise maximum, mirroring {@link #getMin(Position, Position)}. */
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
		if (obj instanceof final Position other) {
			return x == other.x && y == other.y && z == other.z;
		} else {
			return super.equals(obj);
		}
	}

	@Override
	public int hashCode() {
		return (int) (((x & X_HASH_MASK) << X_HASH_SHIFT) + ((y & Y_HASH_MASK) << Y_HASH_SHIFT) + (z & Z_HASH_MASK));
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
