package org.mtr.core.data;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.core.generated.data.HomeSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;

import java.util.function.Consumer;

/**
 * A residential area that holds a steady population of {@link Passenger}s &mdash; the source
 * end of most trips simulated by the dispatcher.
 *
 * <p><strong>Population convergence</strong></p>
 * <p>Each tick the home adjusts its passenger list towards the configured {@code population}.
 * The adjustment rate is capped at {@value #MAX_PASSENGERS_PER_TICK} per tick to spread the
 * cost across frames (avoiding a spike when a large zone is first loaded). The
 * {@link ObjectArrayList} is FIFO &mdash; {@code add} appends, {@code removeFirst} drops the
 * oldest &mdash; so population reduction targets the longest-lived passengers first.</p>
 *
 * <p><strong>Tick flow</strong></p>
 * <ol>
 *   <li>Converge head-count towards {@link #population} (at most
 *       {@value #MAX_PASSENGERS_PER_TICK} adds or removes).</li>
 *   <li>For every passenger, call {@link Passenger#tick(Home, Simulator)} which runs the
 *       per-passenger state machine (idle &rarr; find directions &rarr; await vehicle &rarr;
 *       ride &rarr; transfer or complete).</li>
 *   <li>Dirty passengers whose position falls within a connected client&rsquo;s update radius
 *       are pushed via {@link Client#update(Passenger, boolean)} so the dashboard / mod sees
 *       live state.</li>
 * </ol>
 */
public final class Home extends HomeSchema {

	/**
	 * Maximum number of passenger adds or removes per tick when converging population.
	 * Keeps the per-tick cost bounded; a home with population 1000 and 0 initial passengers
	 * will take 100 ticks (1 second) to reach steady state.
	 */
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
					passengers.removeFirst().clearVehicleReferences(simulator);
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

	public long getPopulation() {
		return population;
	}

	/**
	 * Iterate persisted passengers belonging to this home.
	 */
	public void iteratePassengers(Consumer<Passenger> consumer) {
		passengers.forEach(consumer);
	}

	public void setPopulation(long population) {
		this.population = population;
	}
}
