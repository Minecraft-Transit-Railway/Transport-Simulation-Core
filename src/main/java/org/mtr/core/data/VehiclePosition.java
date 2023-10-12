package org.mtr.core.data;

import org.mtr.core.tools.Utilities;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class VehiclePosition {

	private final ObjectArrayList<BlockedSegment> blockedSegments = new ObjectArrayList<>();

	public void addSegment(double startDistance, double endDistance, long id) {
		blockedSegments.add(new BlockedSegment(startDistance, endDistance, id));
	}

	public double getOverlap(double startDistance, double endDistance, long id) {
		double maxOverlap = -1;

		for (final BlockedSegment blockedSegment : blockedSegments) {
			if (id != blockedSegment.id && Utilities.isIntersecting(startDistance, endDistance, blockedSegment.startDistance, blockedSegment.endDistance)) {
				final boolean startInside = Utilities.isBetween(startDistance, blockedSegment.startDistance, blockedSegment.endDistance);
				final boolean endInside = Utilities.isBetween(endDistance, blockedSegment.startDistance, blockedSegment.endDistance);
				final boolean blockedStartInside = Utilities.isBetween(blockedSegment.startDistance, startDistance, endDistance);
				final boolean blockedEndInside = Utilities.isBetween(blockedSegment.endDistance, startDistance, endDistance);
				return Math.max(maxOverlap, startInside && endInside || blockedStartInside && blockedEndInside ? endDistance - startDistance : startInside ? blockedSegment.endDistance - startDistance : endInside ? endDistance - blockedSegment.startDistance : -1);
			}
		}

		return maxOverlap;
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
