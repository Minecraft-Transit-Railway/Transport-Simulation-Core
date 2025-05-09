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
					closestOverlap = Math.min(closestOverlap, Math.max(0, endDistance - blockedSegment.endDistance));
				} else {
					closestOverlap = Math.min(closestOverlap, Math.max(0, blockedSegment.startDistance - startDistance));
				}
				valueSet = true;
			}
		}

		return valueSet ? closestOverlap : -1;
	}

	private static class BlockedSegment {

		private final double startDistance;
		private final double endDistance;
		private final long id;

		private BlockedSegment(double startDistance, double endDistance, long id) {
			this.startDistance = startDistance;
			this.endDistance = endDistance;
			this.id = id;
		}
	}
}
