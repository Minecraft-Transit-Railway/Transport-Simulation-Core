package org.mtr.core.data;

import org.mtr.core.generated.data.LandmarkSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;

import java.util.Random;

/**
 * A non-rail point of interest (a tourist attraction, monument, &hellip;) that constrains
 * passenger demand through per-hour density limits.
 *
 * <p><strong>Time-slot model</strong></p>
 * <p>The day is split into {@value #DAY_DIVISIONS} slots ({@value #DIVISIONS_PER_HOUR} per
 * hour, each {@value #DAY_DIVISION_MILLIS} ms). A passenger visit occupies every slot between
 * its arrival and departure. The {@code reserveVisit} method finds the longest consecutive run
 * of non-full slots (up to {@value #MAX_STAY} slots ≈ 12 h) starting at the arrival time.</p>
 *
 * <p><strong>Density and back-pressure</strong></p>
 * <p>Each hour has a per-hour density limit. Slot {@code i} is &ldquo;full&rdquo; when
 * {@code currentVisitingPassengersOld[i] + addedVisitorsThisTick >= densities[i / 12]}. The
 * {@code currentVisitingPassengers} array records the cumulative number of visits that span
 * each slot; it is snapshotted into {@code currentVisitingPassengersOld} at the start of every
 * tick so that within a single tick the count is stable.</p>
 *
 * <p><strong>First-tick behavior</strong></p>
 * <p>Both arrays start zeroed. On the landmark&rsquo;s very first tick the snapshot is still
 * all zeros, so every slot appears empty &mdash; the density limit is the only active
 * constraint. From the second tick onward {@code currentVisitingPassengersOld} reflects
 * reservations made in prior ticks and the system converges to steady state.</p>
 *
 * <p><strong>Time source</strong></p>
 * <p>Each landmark independently chooses wall-clock versus in-game time via
 * {@code useRealTime}. Wall-clock uses {@code DAY_DIVISION_MILLIS} for division; in-game time
 * uses the simulator's day length and game-time mapping.</p>
 *
 * @see #reserveVisit(long)
 * @see #writeVisitCache(long, long)
 */
public final class Landmark extends LandmarkSchema {

	/**
	 * New reservations made this tick (resets to 0 at the start of each tick). Used alongside
	 * {@link #currentVisitingPassengersOld} so that multiple concurrent reservations within one
	 * tick don't oversubscribe the same slot.
	 */
	private int addedVisitorsThisTick;
	/**
	 * Cumulative per-slot visitor count. Every call to {@link #writeVisitCache} increments each
	 * slot the visit spans. Never decremented within a session; reset on save/load.
	 */
	private final int[] currentVisitingPassengers = new int[DAY_DIVISIONS];
	/**
	 * Snapshot of {@link #currentVisitingPassengers} taken at the start of the previous tick.
	 * Used by {@link #reserveVisit} for the density check so that in-flight reservations this
	 * tick don't see themselves.
	 */
	private final int[] currentVisitingPassengersOld = new int[DAY_DIVISIONS];

	/**
	 * The number of chunks to divide the day into, for simulation purposes.
	 * 24 hours × 12 = 288 slots, each {@value #DAY_DIVISION_MILLIS} ms (5 minutes).
	 */
	private static final int DAY_DIVISIONS = Utilities.HOURS_PER_DAY * 12;
	private static final int DAY_DIVISION_MILLIS = Utilities.MILLIS_PER_DAY / DAY_DIVISIONS;
	/**
	 * How many {@link #DAY_DIVISIONS} buckets fit in one density-profile hour slot.
	 */
	private static final int DIVISIONS_PER_HOUR = DAY_DIVISIONS / Utilities.HOURS_PER_DAY;
	/**
	 * Maximum consecutive slots a single visit may occupy (12 hours).
	 */
	private static final int MAX_STAY = 12 * 12;
	private static final Random RANDOM = new Random();

	/**
	 * Create a new empty landmark in the given simulation or client context.
	 */
	public Landmark(Data data) {
		super(TransportMode.values()[0], data);
	}

	/**
	 * Deserialisation constructor used by the wire / on-disk layer.
	 *
	 * @param readerBase source to read persisted data from
	 * @param data       the simulation engine or client data container
	 */
	public Landmark(ReaderBase readerBase, Data data) {
		super(readerBase, data);
		updateData(readerBase);
	}

	/**
	 * Advance the landmark's visitor simulation by one tick: snapshot the current visitor count
	 * into the "old" buffer (used by {@link #reserveVisit} for density checks) and reset the
	 * per-tick reservation counter.
	 */
	public void tick() {
		System.arraycopy(currentVisitingPassengers, 0, currentVisitingPassengersOld, 0, currentVisitingPassengers.length);
		addedVisitorsThisTick = 0;
	}

	public boolean getUseRealTime() {
		return useRealTime;
	}

	public long getDensity(int hour) {
		return hour >= 0 && hour < Math.min(Utilities.HOURS_PER_DAY, densities.size()) ? densities.getLong(hour) : 0;
	}

	public void setUseRealTime(boolean useRealTime) {
		this.useRealTime = useRealTime;
	}

	public void setDensity(int hour, int density) {
		if (hour >= 0 && hour < Utilities.HOURS_PER_DAY) {
			while (densities.size() < Utilities.HOURS_PER_DAY) {
				densities.add(0);
			}
			densities.set(hour, Math.max(0, density));
		}
	}

	/**
	 * Mark the time-division slots from {@code startTime} to {@code endTime} as occupied by one
	 * additional visitor. When the simulator is available the index is resolved through
	 * {@link #getDayDivisionIndex}; otherwise the legacy wall-clock division is used.
	 *
	 * @param startTime simulation millis at which the visit begins
	 * @param endTime   simulation millis at which the visit ends
	 */
	void writeVisitCache(long startTime, long endTime) {
		if (data instanceof Simulator simulator) {
			final int startIndex = getDayDivisionIndex(simulator, startTime);
			final int endIndex = getDayDivisionIndex(simulator, endTime);
			final int divisionsToWrite = Math.floorMod(endIndex - startIndex, DAY_DIVISIONS) + 1;
			for (int i = 0; i < divisionsToWrite; i++) {
				currentVisitingPassengers[(startIndex + i) % DAY_DIVISIONS]++;
			}
		} else {
			for (long i = startTime / DAY_DIVISION_MILLIS; i <= endTime / DAY_DIVISION_MILLIS; i++) {
				currentVisitingPassengers[(int) (i % DAY_DIVISIONS)]++;
			}
		}
	}

	/**
	 * Reserve a visit interval beginning at {@code arrivalTime}, constrained by this landmark's
	 * per-hour density profile and current in-flight reservations.
	 */
	long reserveVisit(long arrivalTime) {
		if (data instanceof Simulator simulator) {
			final int startingIndex = getDayDivisionIndex(simulator, arrivalTime);
			final Long densityBoxed = Utilities.getElement(densities, startingIndex / DIVISIONS_PER_HOUR, 0L);
			final long density = densityBoxed == null ? 0 : densityBoxed;
			long visitDuration = 0;

			for (int i = startingIndex; i < startingIndex + RANDOM.nextInt(1, MAX_STAY); i++) {
				if (currentVisitingPassengersOld[i % DAY_DIVISIONS] + addedVisitorsThisTick < density) {
					visitDuration += DAY_DIVISION_MILLIS;
				} else {
					break;
				}
			}

			if (visitDuration > 0) {
				addedVisitorsThisTick++;
			}

			return visitDuration;
		} else {
			return 0;
		}
	}

	/**
	 * Map a simulation timestamp to an index in the day-division array ({@link #DAY_DIVISIONS}
	 * slots). When {@code useRealTime} is false and the game-day length is positive, the index
	 * is computed from the in-game clock; otherwise wall-clock time is used.
	 */
	private int getDayDivisionIndex(Simulator simulator, long simulationMillis) {
		if (!useRealTime && simulator.getGameMillisPerDay() > 0) {
			return (int) (simulator.getGameMillisAt(simulationMillis) * DAY_DIVISIONS / simulator.getGameMillisPerDay());
		}

		final long normalizedMillis = Math.floorMod(simulationMillis, (long) Utilities.MILLIS_PER_DAY);
		return (int) (normalizedMillis / DAY_DIVISION_MILLIS);
	}
}
