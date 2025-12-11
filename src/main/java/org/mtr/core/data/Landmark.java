package org.mtr.core.data;

import org.mtr.core.generated.data.LandmarkSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;

import java.util.Random;

public final class Landmark extends LandmarkSchema {

	private int addedVisitorsThisTick;
	private final int[] currentVisitingPassengers = new int[DAY_DIVISIONS];
	private final int[] currentVisitingPassengersOld = new int[DAY_DIVISIONS];

	/**
	 * The number of chunks to divide the day into, for simulation purposes.
	 */
	private static final int DAY_DIVISIONS = Utilities.HOURS_PER_DAY * 12;
	private static final int DAY_DIVISION_MILLIS = Utilities.MILLIS_PER_DAY / DAY_DIVISIONS;
	private static final int MAX_STAY = 12 * 12; // Max stay at a landmark is 12 hours
	private static final Random RANDOM = new Random();

	public Landmark(Data data) {
		super(TransportMode.values()[0], data);
	}

	public Landmark(ReaderBase readerBase, Data data) {
		super(readerBase, data);
		updateData(readerBase);
	}

	public void tick() {
		System.arraycopy(currentVisitingPassengers, 0, currentVisitingPassengersOld, 0, currentVisitingPassengers.length);
		addedVisitorsThisTick = 0;
	}

	void writeVisitCache(long startTime, long endTime) {
		for (long i = startTime / DAY_DIVISION_MILLIS; i <= endTime / DAY_DIVISION_MILLIS; i++) {
			currentVisitingPassengers[(int) (i % DAY_DIVISIONS)]++;
		}
	}

	long reserveVisit(long arrivalTime) {
		if (data instanceof Simulator simulator) {
			final double dayPercentage = (double) (arrivalTime % Utilities.MILLIS_PER_DAY) / Utilities.MILLIS_PER_DAY;
			final long density = Utilities.getElement(densities, (int) Math.floor(dayPercentage * Utilities.HOURS_PER_DAY), 0L);
			final int startingIndex = (int) Math.floor(dayPercentage * DAY_DIVISIONS);
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
}
