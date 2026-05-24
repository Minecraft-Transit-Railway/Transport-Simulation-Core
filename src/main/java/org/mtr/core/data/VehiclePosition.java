package org.mtr.core.data;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.core.tool.Utilities;

public class VehiclePosition {

	private final ObjectArrayList<BlockedSegment> blockedSegments = new ObjectArrayList<>();

	public void addSegment(double startDistance, double endDistance, long id) {
		blockedSegments.add(new BlockedSegment(startDistance, endDistance, id));
	}

	public double getClosestOverlap(double startDistance, double endDistance, boolean reversePositions, long id) {
		double closestOverlap = Double.MAX_VALUE;
		boolean valueSet = false;

		for (final BlockedSegment blockedSegment : blockedSegments) {
			if (id != blockedSegment.id && Utilities.isIntersecting(startDistance, endDistance, blockedSegment.startDistance, blockedSegment.endDistance)) {
				if (reversePositions) {
					closestOverlap = Utilities.clampSafe(endDistance - blockedSegment.endDistance, 0, closestOverlap);
				} else {
					closestOverlap = Utilities.clampSafe(blockedSegment.startDistance - startDistance, 0, closestOverlap);
				}
				valueSet = true;
			}
		}

		return valueSet ? closestOverlap : -1;
	}

	private record BlockedSegment(double startDistance, double endDistance, long id) {
	}
}
