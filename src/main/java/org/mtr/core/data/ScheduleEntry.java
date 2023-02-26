package org.mtr.core.data;

public class ScheduleEntry implements Comparable<ScheduleEntry> {

	public final long arrivalMillis;
	public final int trainCars;
	public final long routeId;
	public final int currentStationIndex;

	public ScheduleEntry(long arrivalMillis, int trainCars, long routeId, int currentStationIndex) {
		this.arrivalMillis = arrivalMillis;
		this.trainCars = trainCars;
		this.routeId = routeId;
		this.currentStationIndex = currentStationIndex;
	}

	@Override
	public int compareTo(ScheduleEntry o) {
		if (arrivalMillis == o.arrivalMillis) {
			return Long.compare(routeId, o.routeId);
		} else {
			return Long.compare(arrivalMillis, o.arrivalMillis);
		}
	}
}
