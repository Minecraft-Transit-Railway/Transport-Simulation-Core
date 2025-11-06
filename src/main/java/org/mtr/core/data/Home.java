package org.mtr.core.data;

import org.mtr.core.generated.data.HomeSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;

public final class Home extends HomeSchema {

	public Home(Data data) {
		super(TransportMode.values()[0], data);
	}

	public Home(ReaderBase readerBase, Data data) {
		super(readerBase, data);
		updateData(readerBase);
	}

	public void tick(long millisElapsed) {
		if (data instanceof Simulator simulator) {
			while (passengers.size() != population) {
				if (passengers.size() > population) {
					passengers.removeFirst();
				} else {
					passengers.add(new Passenger(data));
				}
			}

			passengers.forEach(passenger -> passenger.tick(this, simulator, millisElapsed));
		}
	}
}
