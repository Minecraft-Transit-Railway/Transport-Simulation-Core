package org.mtr.core.data;

import org.mtr.core.generated.data.LandmarkSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;

import java.util.Random;

/**
 * A non-rail point of interest (a tourist attraction, monument, &hellip;) that generates
 * passenger demand throughout the day.
 *
 * <p>Each tick the landmark adjusts its visiting passenger count for the current
 * {@value #DAY_DIVISIONS}-slot time-of-day bucket, modelling passengers arriving, staying for
 * up to {@value #MAX_STAY} divisions (≈ 12 hours) and leaving again. The resulting demand
 * feeds the simulator's passenger generator. Each landmark independently chooses whether those
 * slots are interpreted using wall-clock time or in-game time via {@code useRealTime}.</p>
 */
public final class Landmark extends LandmarkSchema {

	private int addedVisitorsThisTick;
	private final int[] currentVisitingPassengers = new int[DAY_DIVISIONS];
	private final int[] currentVisitingPassengersOld = new int[DAY_DIVISIONS];

	/**
	 * The number of chunks to divide the day into, for simulation purposes.
	 */
	private static final int DAY_DIVISIONS = Utilities.HOURS_PER_DAY * 12;
	private static final int DAY_DIVISION_MILLIS = Utilities.MILLIS_PER_DAY / DAY_DIVISIONS;
	/**
	 * How many {@link #DAY_DIVISIONS} buckets fit in one density-profile hour slot.
	 */
	private static final int DIVISIONS_PER_HOUR = DAY_DIVISIONS / Utilities.HOURS_PER_DAY;
	private static final int MAX_STAY = 12 * 12; // Max stay at a landmark is 12 hours
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
