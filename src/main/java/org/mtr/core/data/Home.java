package org.mtr.core.data;

import org.mtr.core.generated.data.HomeSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;

import java.util.function.Consumer;

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

	/**
	 * Create a new empty home in the given simulation or client context.
	 */
	public Home(Data data) {
		super(TransportMode.values()[0], data);
	}

	/**
	 * Deserialisation constructor used by the wire / on-disk layer.
	 *
	 * @param readerBase source to read persisted data from
	 * @param data       the simulation engine or client data container
	 */
	public Home(ReaderBase readerBase, Data data) {
		super(readerBase, data);
		updateData(readerBase);
	}

	/**
	 * Advance this home's passenger simulation by one tick. The passenger count is converged
	 * towards the configured {@link #population} (at most
	 * {@value #MAX_PASSENGERS_PER_TICK} adjustments per tick), and each passenger is ticked
	 * individually. Dirty passengers are pushed to nearby clients for synchronisation.
	 *
	 */
	public void tick() {
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

			passengers.forEach(passenger -> {
				final boolean dirty = passenger.tick(this, simulator);
				simulator.clients.forEach(client -> {
					if (inArea(client.getPosition(), client.getUpdateRadius())) {
						client.update(passenger, dirty);
					}
				});
			});
		}
	}

	/**
	 * Iterate persisted passengers belonging to this home.
	 */
	public void iteratePassengers(Consumer<Passenger> consumer) {
		passengers.forEach(consumer);
	}
}
