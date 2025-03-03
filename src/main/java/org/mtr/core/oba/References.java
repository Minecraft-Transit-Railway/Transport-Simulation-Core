package org.mtr.core.oba;

import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.generated.oba.ReferencesSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;

public final class References extends ReferencesSchema {

	private final IntAVLTreeSet routeColorsUsed = new IntAVLTreeSet();
	private final LongAVLTreeSet platformIdsUsed = new LongAVLTreeSet();

	public References(ReaderBase readerBase) {
		super(readerBase);
		updateData(readerBase);
	}

	References() {
	}

	void addAgency(Agency agency) {
		agencies.add(agency);
	}

	void addRoute(int routeColor) {
		routeColorsUsed.add(routeColor);
	}

	void addStop(long platformId) {
		platformIdsUsed.add(platformId);
	}

	void addTrip(Trip trip) {
		trips.add(trip);
	}

	void build(Simulator simulator) {
		platformIdsUsed.forEach(platformId -> {
			final Platform platform = simulator.platformIdMap.get(platformId);
			if (platform != null) {
				stops.add(platform.getOBAStopElement(routeColorsUsed));
			}
		});
		routeColorsUsed.forEach(routeColor -> {
			for (final Route route : simulator.routes) {
				if (route.getColor() == routeColor) {
					routes.add(route.getOBARouteElement());
					break;
				}
			}
		});
	}
}
