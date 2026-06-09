package org.mtr.core.data;

import it.unimi.dsi.fastutil.longs.LongLongImmutablePair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectBooleanImmutablePair;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.mtr.core.generated.data.PassengerSchema;
import org.mtr.core.map.DirectionsRequest;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;

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
	@Getter
	private boolean dirtySync = true;

	private static final Random RANDOM = new Random();
	private static final int COOLDOWN = 2000;
	private static final int GO_HOME_PROBABILITY_DENOMINATOR = 3;
	private static final int COOLDOWN_RETRY_DIVISOR = 2;

	/**
	 * Each passenger is simulated independently (not as a grouped cohort), so route decisions and
	 * landmark reservations stay consistent with per-passenger density limits.
	 */
	public Passenger(Data data) {
		super(TransportMode.values()[0], data);
	}

	/**
	 * Deserialisation constructor used by the wire / on-disk layer.
	 *
	 * @param readerBase source to read persisted data from
	 * @param data       the simulation engine or client data container
	 */
	public Passenger(ReaderBase readerBase, Data data) {
		super(readerBase, data);
		updateData(readerBase);
	}

	/**
	 * Internal-only: the single-argument constructor used by
	 * {@link org.mtr.core.generated.data.HomeSchema} when reconstructing the {@code passengers}
	 * array. Wraps the read in a fresh {@link ClientData} so the resulting passenger has somewhere
	 * to look up cached references. <strong>Do not call from user code.</strong>
	 *
	 * @deprecated Use {@link #Passenger(ReaderBase, Data)} instead for simulator-side usage.
	 */
	@Deprecated
	@SuppressWarnings("DeprecatedIsStillUsed")
	public Passenger(ReaderBase readerBase) {
		this(readerBase, new ClientData());
	}

	@Override
	public boolean isValid() {
		return true;
	}

	/**
	 * Advance this passenger by one tick, updating its position in the route graph, handling
	 * journey completion, and requesting new directions when idle.
	 *
	 * @param home      the home this passenger belongs to
	 * @param simulator the simulation engine
	 * @return {@code true} if the passenger's state changed (dirty) and should be synced to clients
	 */
	public boolean tick(Home home, Simulator simulator) {
		// Write caches
		this.home = home;
		if (currentLandmark == null && !directions.isEmpty()) {
			// Restore landmark from endLandmarkId only if it's not a homebound trip (endLandmarkId != 0)
			if (endLandmarkId != 0) {
				currentLandmark = simulator.landmarkIdMap.get(endLandmarkId);
				if (currentLandmark == null) {
					directions.clear();
					dirtySync = true;
				}
			}
		}

		if (directions.isEmpty()) {
			// If there are no directions, find directions to go somewhere
			if (simulator.getCurrentMillis() > findDirectionsTime) {
				if (simulator.landmarks.isEmpty() || RANDOM.nextInt(GO_HOME_PROBABILITY_DENOMINATOR) == 0) {
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

			final long prevSidingId = sidingId;
			final long prevVehicleId = vehicleId;

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

			if (sidingId != prevSidingId || vehicleId != prevVehicleId) {
				dirtySync = true;
			}

			// Check if the entire journey is complete (last direction has ended)
			if (simulator.getCurrentMillis() >= directions.getLast().getEndTime()) {
				directions.clear();
				currentStartPlatform = null;
				currentEndPlatform = null;
				currentRoute = null;
				sidingId = 0;
				vehicleId = 0;
				// Update currentLandmark to arrived landmark (or null = home)
				currentLandmark = endLandmarkId != 0 ? simulator.landmarkIdMap.get(endLandmarkId) : null;
				// If there is a reserved visit time remaining, wait until it expires before replanning
				if (endLandmarkId != 0 && landmarkVisitEndTime > simulator.getCurrentMillis()) {
					findDirectionsTime = landmarkVisitEndTime;
				}
				dirtySync = true;
			}
		}

		final boolean dirty = dirtySync;
		dirtySync = false;
		return dirty;
	}

	/**
	 * Request a passenger-direction path from the CSA engine. If the destination is a landmark,
	 * the visit is reserved before the route is committed; if the landmark is full the route is
	 * rejected and retried after a short backoff.
	 *
	 * @param newLandmark destination landmark, or {@code null} for a home-bound trip
	 */
	private void findDirections(@Nullable Landmark newLandmark) {
		if (data instanceof Simulator simulator) {
			if (home == null) {
				// This should never happen
				return;
			}

			final Position startPosition;
			final Position endPosition;

			if (newLandmark == null && currentLandmark == null) {
				// Already at home, trying to go home — backoff to avoid spinning every tick
				findDirectionsTime = simulator.getCurrentMillis() + COOLDOWN + RANDOM.nextInt(COOLDOWN);
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
				// Trying to go to the same landmark — backoff to avoid spinning every tick
				findDirectionsTime = simulator.getCurrentMillis() + COOLDOWN + RANDOM.nextInt(COOLDOWN);
				return;
			} else {
				// At a landmark, trying to go to a new landmark
				startPosition = currentLandmark.getCenter();
				endPosition = newLandmark.getCenter();
			}

			if (!simulator.tryConsumePassengerDirectionsRequestBudget()) {
				findDirectionsTime = simulator.getCurrentMillis() + COOLDOWN / COOLDOWN_RETRY_DIVISOR + RANDOM.nextInt(COOLDOWN / COOLDOWN_RETRY_DIVISOR);
				return;
			}

			findDirectionsTime = Long.MAX_VALUE;
			simulator.directionsFinder.addRequest(new DirectionsRequest(startPosition, endPosition, simulator.getCurrentMillis(), null, passengerDirections -> {
				if (!passengerDirections.isEmpty()) {
					// For landmark trips, try to reserve the visit BEFORE committing to the route
					if (newLandmark != null) {
						final long estimatedArrivalTime = passengerDirections.getLast().getEndTime();
						final long visitDuration = newLandmark.reserveVisit(estimatedArrivalTime);
						if (visitDuration <= 0) {
							// Reservation failed; don't accept this route
							findDirectionsTime = simulator.getCurrentMillis() + COOLDOWN / COOLDOWN_RETRY_DIVISOR + RANDOM.nextInt(COOLDOWN / COOLDOWN_RETRY_DIVISOR);
							return;
						}
						// Reservation succeeded; now commit to the route
						directions.clear();
						currentStartPlatform = null;
						currentEndPlatform = null;
						currentRoute = null;
						directions.addAll(passengerDirections);
						startLandmarkId = endLandmarkId;
						endLandmarkId = newLandmark.getId();
						landmarkVisitStartTime = estimatedArrivalTime;
						landmarkVisitEndTime = landmarkVisitStartTime + visitDuration;
						newLandmark.writeVisitCache(landmarkVisitStartTime, landmarkVisitEndTime);
					} else {
						// Going home
						directions.clear();
						currentStartPlatform = null;
						currentEndPlatform = null;
						currentRoute = null;
						directions.addAll(passengerDirections);
						startLandmarkId = endLandmarkId;
						endLandmarkId = 0;
						landmarkVisitStartTime = 0;
						landmarkVisitEndTime = 0;
					}
					dirtySync = true;
				} else {
					findDirectionsTime = simulator.getCurrentMillis() + COOLDOWN + RANDOM.nextInt(COOLDOWN);
				}
			}));
		}
	}

	@Nullable
	private static LongLongImmutablePair getSidingAndVehicleIds(Route route, Platform platform) {
		for (final Depot depot : route.depots) {
			for (final Siding siding : depot.savedRails) {
				final ObjectBooleanImmutablePair<@Nullable Vehicle> vehicleDetails = siding.getVehicleDetailsAtPlatform(route.getId(), platform.getId());
				if (vehicleDetails.rightBoolean()) {
					final Vehicle vehicle = vehicleDetails.left();
					return new LongLongImmutablePair(siding.getId(), vehicle == null ? 0 : vehicle.getId());
				}
			}
		}

		return null;
	}
}
