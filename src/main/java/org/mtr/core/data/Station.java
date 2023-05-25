package org.mtr.core.data;

import org.mtr.core.generated.StationSchema;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.simulation.Simulator;

public final class Station extends StationSchema {

	public Station(Simulator simulator) {
		super(TransportMode.TRAIN, simulator);
	}

	public Station(ReaderBase readerBase, Simulator simulator) {
		super(readerBase, simulator);
		updateData(readerBase);
	}

	@Override
	protected boolean noTransportMode() {
		return true;
	}

	public long getZone1() {
		return zone1;
	}

	public long getZone2() {
		return zone2;
	}

	public long getZone3() {
		return zone3;
	}
}
