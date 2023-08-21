package org.mtr.core.data;

import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import org.mtr.core.generated.StationSchema;
import org.mtr.core.serializers.ReaderBase;
import org.mtr.core.tools.DataFixer;

public final class Station extends StationSchema {

	public final ObjectAVLTreeSet<Station> connectedStations = new ObjectAVLTreeSet<>();

	public Station(Data data) {
		super(TransportMode.values()[0], data);
	}

	public Station(ReaderBase readerBase, Data data) {
		super(DataFixer.convertStation(readerBase), data);
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

	public void setZone1(long zone1) {
		this.zone1 = zone1;
	}

	public void setZone2(long zone2) {
		this.zone2 = zone2;
	}

	public void setZone3(long zone3) {
		this.zone3 = zone3;
	}
}
