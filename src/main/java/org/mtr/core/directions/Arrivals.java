package org.mtr.core.directions;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.core.data.Route;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.RefreshableObject;
import org.mtr.core.tool.Utilities;

import javax.annotation.Nullable;
import java.util.Comparator;

public final class Arrivals extends RefreshableObject<ObjectArrayList<ObjectArrayList<Connection>>> {

	private long millis;

	private final Long2ObjectOpenHashMap<ObjectObjectImmutablePair<Route, LongArrayList>> tempDepartures = new Long2ObjectOpenHashMap<>();
	private final ObjectArrayList<ObjectArrayList<Connection>> routeConnectionsLists = new ObjectArrayList<>();
	private final Simulator simulator;

	public Arrivals(Simulator simulator) {
		super(new ObjectArrayList<>(), 5000);
		this.simulator = simulator;
	}

	@Nullable
	@Override
	public ObjectArrayList<ObjectArrayList<Connection>> refresh(int currentRefreshStep) {
		final int index1 = currentRefreshStep - 2;

		if (currentRefreshStep == 0) {
			tempDepartures.clear();
			routeConnectionsLists.clear();
			millis = simulator.getCurrentMillis();
			simulator.sidings.forEach(siding -> siding.getDeparturesForDirections(millis, tempDepartures));
			return null;
		} else if (currentRefreshStep == 1) {
			tempDepartures.values().forEach(departuresForRoute -> DirectionsFinder.processRoute(departuresForRoute.left(), departuresForRoute.left().getRoutePlatforms().size() - 1, (offsetTimeFromLastDeparture, duration, platform1, platform2) -> {
				for (final long departureForRoute : departuresForRoute.right()) {
					final long vehicleArrival1 = departureForRoute - offsetTimeFromLastDeparture;
					final long vehicleArrival2 = vehicleArrival1 + duration;

					if (vehicleArrival1 >= millis) {
						final int index = (int) ((vehicleArrival1 - millis) / Utilities.MILLIS_PER_HOUR);
						while (routeConnectionsLists.size() <= index) {
							routeConnectionsLists.add(new ObjectArrayList<>());
						}
						routeConnectionsLists.get(index).add(new Connection(
								departuresForRoute.left(),
								platform1.getId(), platform2.getId(),
								vehicleArrival1, vehicleArrival2,
								0
						));
					}
				}
			}));
			return null;
		} else if (index1 < routeConnectionsLists.size()) {
			// Sort by the start time of each connection
			routeConnectionsLists.get(index1).sort(Comparator.comparingLong(Connection::startTime));

			if (index1 == routeConnectionsLists.size() - 1) {
				return routeConnectionsLists;
			} else {
				return null;
			}
		} else {
			// This shouldn't be reached, but it's here just in case
			return routeConnectionsLists;
		}
	}
}
