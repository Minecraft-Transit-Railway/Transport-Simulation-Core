package org.mtr.core.data;

import org.junit.jupiter.api.Test;
import org.mtr.core.tool.Utilities;
import org.mtr.core.tool.Vector;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for core geometry and utility classes
 */
public final class GeometryTests {

	@Test
	public void testPositionConstruction() {
		final Position position = new Position(10, 20, 30);
		assertEquals(10, position.getX(), "X coordinate should be set correctly");
		assertEquals(20, position.getY(), "Y coordinate should be set correctly");
		assertEquals(30, position.getZ(), "Z coordinate should be set correctly");
	}

	@Test
	public void testPositionEquality() {
		final Position position1 = new Position(10, 20, 30);
		final Position position2 = new Position(10, 20, 30);
		final Position position3 = new Position(10, 20, 31);

		assertEquals(position1, position2, "Positions with same coordinates should be equal");
		assertNotEquals(position1, position3, "Positions with different coordinates should not be equal");
	}

	@Test
	public void testPositionHashConsistency() {
		final Position position1 = new Position(10, 20, 30);
		final Position position2 = new Position(10, 20, 30);

		assertEquals(position1.hashCode(), position2.hashCode(),
			"Equal positions should have equal hash codes");
	}

	@Test
	public void testPositionOrdering() {
		final Position position1 = new Position(5, 20, 30);
		final Position position2 = new Position(10, 20, 30);
		final Position position3 = new Position(10, 15, 30);
		final Position position4 = new Position(10, 20, 25);

		assertTrue(position1.compareTo(position2) < 0, "Position with smaller X should be less");
		assertTrue(position2.compareTo(position1) > 0, "Position with larger X should be greater");
		assertTrue(position3.compareTo(position2) < 0, "Position with same X but smaller Y should be less");
		assertTrue(position4.compareTo(position2) < 0, "Position with same X,Y but smaller Z should be less");
		assertEquals(0, position1.compareTo(new Position(5, 20, 30)), "Equal positions should compare as 0");
	}

	@Test
	public void testPositionOffset() {
		final Position position = new Position(10, 20, 30);
		final Position offset1 = position.offset(5, 10, 15);

		assertEquals(15, offset1.getX(), "X should be incremented");
		assertEquals(30, offset1.getY(), "Y should be incremented");
		assertEquals(45, offset1.getZ(), "Z should be incremented");

		// Test zero offset optimization
		final Position offset2 = position.offset(0, 0, 0);
		assertSame(position, offset2, "Zero offset should return same object (optimization)");
	}

	@Test
	public void testPositionOffsetWithPosition() {
		final Position position1 = new Position(10, 20, 30);
		final Position position2 = new Position(5, 10, 15);
		final Position result = position1.offset(position2);

		assertEquals(15, result.getX(), "X should be sum of both positions");
		assertEquals(30, result.getY(), "Y should be sum of both positions");
		assertEquals(45, result.getZ(), "Z should be sum of both positions");
	}

	@Test
	public void testManhattanDistance() {
		final Position position1 = new Position(0, 0, 0);
		final Position position2 = new Position(3, 4, 5);

		long distance = position1.manhattanDistance(position2);
		assertEquals(12, distance, "Manhattan distance should be |x| + |y| + |z|");
	}

	@Test
	public void testManhattanDistanceNegative() {
		final Position position1 = new Position(10, 20, 30);
		final Position position2 = new Position(5, 15, 25);

		long distance = position1.manhattanDistance(position2);
		assertEquals(15, distance, "Manhattan distance should handle negative offsets");
	}

	@Test
	public void testPositionGetMin() {
		final Position position1 = new Position(10, 20, 30);
		final Position position2 = new Position(5, 25, 15);
		final Position min = Position.getMin(position1, position2);

		assertEquals(5, min.getX(), "Min X should be 5");
		assertEquals(20, min.getY(), "Min Y should be 20");
		assertEquals(15, min.getZ(), "Min Z should be 15");
	}

	@Test
	public void testPositionGetMinWithNull() {
		final Position position = new Position(10, 20, 30);

		assertSame(position, Position.getMin(position, null), "Min with null should return non-null");
		assertSame(position, Position.getMin(null, position), "Min with null should return non-null");
		assertNull(Position.getMin(null, null), "Min of two nulls should be null");
	}

	@Test
	public void testPositionGetMax() {
		final Position position1 = new Position(10, 20, 30);
		final Position position2 = new Position(5, 25, 15);
		final Position max = Position.getMax(position1, position2);

		assertEquals(10, max.getX(), "Max X should be 10");
		assertEquals(25, max.getY(), "Max Y should be 25");
		assertEquals(30, max.getZ(), "Max Z should be 30");
	}

	@Test
	public void testPositionGetMaxWithNull() {
		final Position position = new Position(10, 20, 30);

		assertSame(position, Position.getMax(position, null), "Max with null should return non-null");
		assertSame(position, Position.getMax(null, position), "Max with null should return non-null");
		assertNull(Position.getMax(null, null), "Max of two nulls should be null");
	}

	@Test
	public void testPositionFromVector() {
		final Vector vector = new Vector(10.7, 20.3, 30.9);
		final Position position = new Position(vector);

		assertEquals(10, position.getX(), "X coordinate should be floored");
		assertEquals(20, position.getY(), "Y coordinate should be floored");
		assertEquals(30, position.getZ(), "Z coordinate should be floored");
	}

	@Test
	public void testPositionFromVectorNegative() {
		final Vector vector = new Vector(-10.7, -20.3, -30.9);
		final Position position = new Position(vector);

		assertEquals(-11, position.getX(), "Negative X coordinate should be floored correctly");
		assertEquals(-21, position.getY(), "Negative Y coordinate should be floored correctly");
		assertEquals(-31, position.getZ(), "Negative Z coordinate should be floored correctly");
	}

	@Test
	public void testPositionInArea() {
		final Position position = new Position(5, 0, 5);
		final Position area1 = new Position(0, 0, 0);
		final Position area2 = new Position(10, 10, 10);
		assertTrue(Utilities.isBetween(position, area1, area2, 0));
	}

	@Test
	public void testPositionOutOfArea() {
		final Position position = new Position(15, 0, 5);
		final Position area1 = new Position(0, 0, 0);
		final Position area2 = new Position(10, 10, 10);
		assertFalse(Utilities.isBetween(position, area1, area2, 0));
	}

	@Test
	public void testVectorConstruction() {
		final Vector vector = new Vector(1.5, 2.5, 3.5);
		assertEquals(1.5, vector.x(), 1e-10);
		assertEquals(2.5, vector.y(), 1e-10);
		assertEquals(3.5, vector.z(), 1e-10);
	}
}
