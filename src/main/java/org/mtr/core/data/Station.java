package org.mtr.core.data;

import org.mtr.core.generated.StationSchema;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tools.DataFixer;

public final class Station extends StationSchema {

	public Station(Simulator simulator) {
		super(TransportMode.values()[0], simulator);
	}

	public Station(ReaderBase readerBase, Simulator simulator) {
		super(DataFixer.convertStation(readerBase), simulator);
		updateData(readerBase);
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
