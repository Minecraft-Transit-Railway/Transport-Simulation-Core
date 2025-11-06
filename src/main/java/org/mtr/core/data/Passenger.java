package org.mtr.core.data;

import it.unimi.dsi.fastutil.longs.LongLongImmutablePair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectBooleanImmutablePair;
import org.mtr.core.generated.data.PassengerSchema;
import org.mtr.core.map.DirectionsRequest;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;

import javax.annotation.Nullable;
import java.util.Random;

public final class Passenger extends PassengerSchema {

	@Nullable
	private Home home;
	@Nullable
	private Landmark currentLandmark;

	@Nullable
	private Platform currentStartPlatform;
	@Nullable
	private Platform currentEndPlatform;
	@Nullable
	private Route currentRoute;

	private long findDirectionsTime;

	private static final Random RANDOM = new Random();
	private static final int COOLDOWN = 2000;

	public Passenger(Data data) {
		super(TransportMode.values()[0], data);
	}

	public Passenger(ReaderBase readerBase, Data data) {
		super(readerBase, data);
		updateData(readerBase);
	}

	/**
	 * @deprecated for {@link org.mtr.core.generated.data.HomeSchema} use only
	 */
	@Deprecated
	public Passenger(ReaderBase readerBase) {
		this(readerBase, new ClientData());
	}

	@Override
	public boolean isValid() {
		return true;
	}

	public void tick(Home home, Simulator simulator, long millisElapsed) {
		// Write caches
		this.home = home;
		if (currentLandmark == null && !directions.isEmpty()) {
			currentLandmark = simulator.landmarkIdMap.get(endLandmarkId);
			if (currentLandmark == null) {
				directions.clear();
			}
		}

		if (directions.isEmpty()) {
			// If there are no directions, find directions to go somewhere
			if (System.currentTimeMillis() > findDirectionsTime) {
				if (simulator.landmarks.isEmpty() || RANDOM.nextInt(3) == 0) {
					// Go home
					findDirections(null);
				} else {
					// Go to a random landmark
					findDirections(new ObjectArrayList<>(simulator.landmarks).get(RANDOM.nextInt(simulator.landmarks.size())));
				}
			}
		} else {
			final int directionsCount = directions.size();
			final int index1 = Utilities.clampSafe(Utilities.getIndexFromConditionalList(directions, simulator.getCurrentMillis()), 0, directionsCount - 1);
			final int index2 = Math.min(index1 + 1, directionsCount - 1);
			final PassengerDirection passengerDirection1 = directions.get(index1);
			final PassengerDirection passengerDirection2 = directions.get(index2);
			final PassengerDirection passengerDirection = simulator.getCurrentMillis() >= passengerDirection1.getEndTime() ? passengerDirection2 : passengerDirection1;

			// Update start platform cache
			if (passengerDirection.getStartPlatformId() == 0) {
				currentStartPlatform = null;
			} else if (currentStartPlatform == null) {
				currentStartPlatform = simulator.platformIdMap.get(passengerDirection.getStartPlatformId());
			}

			// Update end platform cache
			if (passengerDirection.getEndPlatformId() == 0) {
				currentEndPlatform = null;
			} else if (currentEndPlatform == null) {
				currentEndPlatform = simulator.platformIdMap.get(passengerDirection.getEndPlatformId());
			}

			// Update route cache
			if (passengerDirection.getRouteId() == 0) {
				currentRoute = null;
			} else if (currentRoute == null) {
				currentRoute = simulator.routeIdMap.get(passengerDirection.getRouteId());
			}

			if (currentRoute == null) {
				sidingId = 0;
				vehicleId = 0;
			} else {
				if (currentStartPlatform != null) {
					// TODO adjust schedule and find directions based on vehicle deviation
					final LongLongImmutablePair sidingAndVehicleIds = getSidingAndVehicleIds(currentRoute, currentStartPlatform);
					sidingId = sidingAndVehicleIds == null ? 0 : sidingAndVehicleIds.leftLong();
					vehicleId = sidingAndVehicleIds == null ? 0 : sidingAndVehicleIds.rightLong();
				}
			}
		}
	}

	private void findDirections(@Nullable Landmark newLandmark) {
		if (data instanceof Simulator simulator) {
			if (home == null) {
				// This should never happen
				return;
			}

			final Position startPosition;
			final Position endPosition;

			if (newLandmark == null && currentLandmark == null) {
				// Already at home, trying to go home
				return;
			} else if (newLandmark == null) {
				// At current landmark, trying to go home
				startPosition = currentLandmark.getCenter();
				endPosition = home.getCenter();
			} else if (currentLandmark == null) {
				// At home, trying to go to landmark
				startPosition = home.getCenter();
				endPosition = newLandmark.getCenter();
			} else if (newLandmark.getId() == currentLandmark.getId()) {
				// Trying to go to the same landmark
				return;
			} else {
				// At a landmark, trying to go to a new landmark
				startPosition = currentLandmark.getCenter();
				endPosition = newLandmark.getCenter();
			}

			findDirectionsTime = Long.MAX_VALUE;
			simulator.directionsFinder.addRequest(new DirectionsRequest(startPosition, endPosition, simulator.getCurrentMillis(), null, passengerDirections -> {
				findDirectionsTime = System.currentTimeMillis() + COOLDOWN + RANDOM.nextInt(COOLDOWN);

				if (!passengerDirections.isEmpty()) {
					directions.addAll(passengerDirections);
					if (newLandmark == null) {
						// Going home
						startLandmarkId = endLandmarkId;
						endLandmarkId = 0;
						landmarkVisitStartTime = 0;
						landmarkVisitEndTime = 0;
					} else {
						// Going to a landmark
						final long visitDuration = newLandmark.reserveVisit(landmarkVisitStartTime);
						if (visitDuration > 0) {
							startLandmarkId = endLandmarkId;
							endLandmarkId = newLandmark.getId();
							landmarkVisitStartTime = passengerDirections.getLast().getEndTime();
							landmarkVisitEndTime = landmarkVisitStartTime + visitDuration;
						}
					}
				}
			}));
		}
	}

	@Nullable
	private static LongLongImmutablePair getSidingAndVehicleIds(Route route, Platform platform) {
		for (final Depot depot : route.depots) {
			for (final Siding siding : depot.savedRails) {
				final ObjectBooleanImmutablePair<Vehicle> vehicleDetails = siding.getVehicleDetailsAtPlatform(route.getId(), platform.getId());
				if (vehicleDetails.rightBoolean()) {
					final Vehicle vehicle = vehicleDetails.left();
					return new LongLongImmutablePair(siding.getId(), vehicle == null ? 0 : vehicle.getId());
				}
			}
		}

		return null;
	}
}
