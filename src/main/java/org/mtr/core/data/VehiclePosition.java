package org.mtr.core.data;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class VehiclePosition {

	private final ObjectArrayList<BlockedSegment> blockedSegments = new ObjectArrayList<>();

	public void addSegment(double startDistance, double endDistance, long id) {
		blockedSegments.add(new BlockedSegment(startDistance, endDistance, id));
	}

	public double isBlocked(long id, double startDistance, double endDistance) {
		for (final BlockedSegment blockedSegment : blockedSegments) {
			if (id != blockedSegment.id && endDistance >= blockedSegment.startDistance) {
				return blockedSegment.startDistance - startDistance;
			}
		}
		return -1;
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
