package org.mtr.core.tool;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public final class UtilitiesTests {

	@Test
	public void testClampSafeInt() {
		assertEquals(5, Utilities.clampSafe(5, 0, 10), "Value within range should be unchanged");
		assertEquals(0, Utilities.clampSafe(-1, 0, 10), "Value below min should clamp to min");
		assertEquals(10, Utilities.clampSafe(15, 0, 10), "Value above max should clamp to max");
	}

	@Test
	public void testClampSafeLong() {
		assertEquals(5L, Utilities.clampSafe(5L, 0L, 10L));
		assertEquals(0L, Utilities.clampSafe(-1L, 0L, 10L));
	}

	@Test
	public void testClampSafeDouble() {
		assertEquals(0.5, Utilities.clampSafe(0.5, 0.0, 1.0), 1e-10);
		assertEquals(0.0, Utilities.clampSafe(-0.5, 0.0, 1.0), 1e-10);
		assertEquals(1.0, Utilities.clampSafe(1.5, 0.0, 1.0), 1e-10);
	}

	@Test
	public void testIsBetween() {
		assertTrue(Utilities.isBetween(5, 0, 10));
		assertTrue(Utilities.isBetween(0, 0, 10));
		assertTrue(Utilities.isBetween(10, 0, 10));
		assertFalse(Utilities.isBetween(-1, 0, 10));
		assertFalse(Utilities.isBetween(11, 0, 10));
	}

	@Test
	public void testIsBetweenWithPadding() {
		assertTrue(Utilities.isBetween(11, 0, 10, 2));
		assertFalse(Utilities.isBetween(13, 0, 10, 2));
	}

	@Test
	public void testIsIntersecting() {
		assertTrue(Utilities.isIntersecting(5, 15, 10, 20));
		assertTrue(Utilities.isIntersecting(10, 20, 5, 15));
		assertFalse(Utilities.isIntersecting(0, 5, 10, 15));
	}

	@Test
	public void testRound() {
		assertEquals(3.14, Utilities.round(3.14159, 2), 1e-10);
		assertEquals(3.0, Utilities.round(3.14159, 0), 1e-10);
	}

	@Test
	public void testGetAverage() {
		assertEquals(5.0, Utilities.getAverage(3.0, 7.0), 1e-10);
		assertEquals(-2.0, Utilities.getAverage(-5.0, 1.0), 1e-10);
	}

	@Test
	public void testGetValueFromPercentage() {
		assertEquals(5.0, Utilities.getValueFromPercentage(0.5, 0.0, 10.0), 1e-10);
		assertEquals(0.0, Utilities.getValueFromPercentage(0.0, 0.0, 10.0), 1e-10);
		assertEquals(10.0, Utilities.getValueFromPercentage(1.0, 0.0, 10.0), 1e-10);
	}

	@Test
	public void testKilometersPerHourToMetersPerMillisecond() {
		final double result = Utilities.kilometersPerHourToMetersPerMillisecond(3600);
		assertEquals(1.0, result, 1e-10, "3600 km/h should equal 1 m/ms");
	}

	@Test
	public void testFormatName() {
		assertEquals("Test", Utilities.formatName("Test||Comment"));
		assertEquals("Test Name", Utilities.formatName("Test Name"));
	}

	@Test
	public void testNumberToPaddedHexString() {
		final String hex = Utilities.numberToPaddedHexString(255);
		assertTrue(hex.endsWith("FF"), "255 should be FF in hex");
	}

	@Test
	public void testGetElementWithIndex() {
		final List<String> list = List.of("a", "b", "c");
		assertEquals("a", Utilities.getElement(list, 0));
		assertEquals("c", Utilities.getElement(list, 2));
		assertEquals("c", Utilities.getElement(list, -1));
		assertEquals("b", Utilities.getElement(list, -2));
	}

	@Test
	public void testGetElementWithDefault() {
		final List<String> list = List.of("a", "b");
		assertNull(Utilities.getElement(list, 5));
		assertEquals("default", Utilities.getElement(list, 5, "default"));
	}

	@Test
	public void testCircularClamp() {
		assertEquals(350, Utilities.circularClamp(350, 0, 360, 360));
		assertEquals(10, Utilities.circularClamp(370, 0, 360, 360));
	}

	@Test
	public void testCircularClampDouble() {
		assertEquals(350.0, Utilities.circularClamp(350.0, 0.0, 360.0, 360.0), 1e-10);
		assertEquals(10.0, Utilities.circularClamp(370.0, 0.0, 360.0, 360.0), 1e-10);
	}

	@Test
	public void testCircularDifference() {
		assertEquals(10, Utilities.circularDifference(10, 0, 360));
		assertEquals(-10, Utilities.circularDifference(0, 10, 360));
	}

	@Test
	public void testConcat() {
		assertEquals("hello42world", Utilities.concat("hello", 42, "world"));
	}

	@Test
	public void testDifferentItems() {
		assertTrue(Utilities.differentItems(List.of(1, 2), List.of(1, 2, 3)));
		assertFalse(Utilities.differentItems(List.of(1, 2), List.of(1, 2)));
	}

	@Test
	public void testCompareLong() {
		assertEquals(-1, Long.compare(1, 2));
	}

	@Test
	public void testParseJson() {
		assertNotNull(Utilities.parseJson("{\"key\":\"value\"}"));
	}
}
