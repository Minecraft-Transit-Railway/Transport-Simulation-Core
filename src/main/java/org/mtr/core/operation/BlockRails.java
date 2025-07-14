package org.mtr.core.operation;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.core.data.Rail;
import org.mtr.core.generated.operation.BlockRailsSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;

public final class BlockRails extends BlockRailsSchema {

	public BlockRails(ObjectArrayList<String> railIds, IntArrayList signalColors) {
		this.railIds.addAll(railIds);
		signalColors.forEach(this.signalColors::add);
	}

	public BlockRails(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	public void blockRails(Simulator simulator) {
		railIds.forEach(railId -> {
			final Rail rail = simulator.railIdMap.get(railId);
			if (rail != null) {
				rail.blockRail(signalColors);
			}
		});
	}
}
