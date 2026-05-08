package org.mtr.core.data;

import org.mtr.core.generated.data.HomeSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;

/**
 * A residential area that holds a steady population of {@link Passenger}s &mdash; the source
 * end of most trips simulated by the dispatcher.
 *
 * <p>Each tick the home adjusts its passenger list (capped to
 * {@value #MAX_PASSENGERS_PER_TICK} adjustments per tick to spread the cost across frames)
 * to converge on the configured {@code population}, spawning new passengers when short and
 * removing the oldest when over.</p>
 */
public final class Home extends HomeSchema {

	private static final int MAX_PASSENGERS_PER_TICK = 10;

	public Home(Data data) {
		super(TransportMode.values()[0], data);
	}

	public Home(ReaderBase readerBase, Data data) {
		super(readerBase, data);
		updateData(readerBase);
	}

	public void tick(long millisElapsed) {
		if (data instanceof Simulator simulator) {
			int adjustments = 0;
			while (passengers.size() != population && adjustments < MAX_PASSENGERS_PER_TICK) {
				if (passengers.size() > population) {
					passengers.removeFirst();
				} else {
					passengers.add(new Passenger(data));
				}
				adjustments++;
			}

			passengers.forEach(passenger -> passenger.tick(this, simulator, millisElapsed));
		}
	}
}
