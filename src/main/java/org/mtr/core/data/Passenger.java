package org.mtr.core.data;

import it.unimi.dsi.fastutil.longs.LongLongImmutablePair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectBooleanImmutablePair;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.mtr.core.directions.DirectionsFinder;
import org.mtr.core.generated.data.PassengerSchema;
import org.mtr.core.map.DirectionsRequest;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;

import java.util.Random;

/**
 * A single passenger agent that moves through the transit network via a state-machine tick loop.
 *
 * <p><strong>Lifecycle</strong></p>
 * <ol>
 *   <li><b>Idle</b> &mdash; no directions queued. If the cooldown has expired, picks a
 *       destination (home or random landmark with 1:3 odds) and requests a CSA path.</li>
 *   <li><b>Walking</b> &mdash; the first leg has no route (walking transfer). When its end time
 *       passes, the leg is consumed and the next one begins.</li>
 *   <li><b>Awaiting vehicle</b> &mdash; waiting at a platform for a vehicle on the planned route.
 *       Replans if the route is jammed or the vehicle doesn't arrive within
 *       {@code REALTIME_REPLAN_THRESHOLD} of the estimated departure.</li>
 *   <li><b>On vehicle</b> &mdash; riding. When the vehicle stops at the target platform the leg
 *       is consumed; if the vehicle vanishes from {@code vehicleIdMap} the journey is replanned
 *       immediately.</li>
 *   <li><b>Missed-stop guard</b> &mdash; if the leg's estimated arrival time passes without
 *       reaching the platform (server lag, missed tick, detour), force-disembark and replan.</li>
 *   <li><b>Complete</b> &mdash; all legs consumed. If at a landmark, the visit timer starts;
 *       if at home, the passenger goes idle and picks a new destination.</li>
 * </ol>
 *
 * <p>All CSA direction requests are asynchronous via {@link DirectionsFinder}. While a request
 * is outstanding {@code findDirectionsTime = Long.MAX_VALUE} prevents duplicate submissions.
 * If the CSA budget is exhausted the passenger retries after a short backoff.</p>
 */
public final class Passenger extends PassengerSchema {

	@Nullable
	private Home home;
	@Nullable
	private Landmark currentLandmark;

	/**
	 * Transient leg cache: resolved from {@link PassengerDirection} fields each tick.
	 * Null when the leg is a walking transfer (no platform / route).
	 */
	@Nullable
	private Platform currentStartPlatform;
	@Nullable
	private Platform currentEndPlatform;
	@Nullable
	private Route currentRoute;

	/**
	 * When the passenger may next request directions ({@code Long.MAX_VALUE} while a request is
	 * in flight). Set to the landmark visit end time when visiting a landmark, or to
	 * {@code now + COOLDOWN + random(COOLDOWN)} when idle at home.
	 */
	private long findDirectionsTime;
	@Getter
	private boolean dirtySync = true;

	private static final Random RANDOM = new Random();
	/**
	 * Base cooldown applied when a passenger finishes a journey (ms).
	 */
	private static final int COOLDOWN = 2000;
	/**
	 * 1-in-{@value} chance of going home vs. to a landmark when idle.
	 */
	private static final int GO_HOME_PROBABILITY_DENOMINATOR = 3;
	/**
	 * Back-off divisor for retries when the CSA budget is exhausted.
	 */
	private static final int COOLDOWN_RETRY_DIVISOR = 2;
	/**
	 * If a vehicle hasn't arrived at the boarding platform within this many ms of the planned
	 * departure, the passenger replans its journey.
	 */
	private static final long REALTIME_REPLAN_THRESHOLD = 2 * Utilities.MILLIS_PER_MINUTE;

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
	 * Advance this passenger by one tick through the six-state state machine:
	 *
	 * <ol>
	 *   <li><b>Idle / choose destination</b> &mdash; directions empty, cooldown expired → request CSA path</li>
	 *   <li><b>Walking leg</b> &mdash; no route (null currentRoute) → wait for end time, then consume leg</li>
	 *   <li><b>Null platform</b> &mdash; route exists but no platform → replan (stale data)</li>
	 *   <li><b>Awaiting vehicle</b> &mdash; vehicleId == 0 → scan sidings for a matching vehicle</li>
	 *   <li><b>On vehicle</b> &mdash; vehicleId != 0 → ride until target platform reached or vehicle lost</li>
	 *   <li><b>Missed-stop guard</b> &mdash; leg end time passed without arrival → force-disembark</li>
	 * </ol>
	 *
	 * <p>Every iteration of the while(true) body hits exactly one {@code break} or {@code continue}
	 * — no infinite-loop path is possible.</p>
	 *
	 * @param home      the home this passenger belongs to
	 * @param simulator the simulation engine
	 * @return {@code true} if the passenger's state changed (dirty) and should be synced to clients
	 */
	public boolean tick(Home home, Simulator simulator) {
		this.home = home;
		if (currentLandmark == null && !directions.isEmpty()) {
			if (endLandmarkId != 0) {
				currentLandmark = simulator.landmarkIdMap.get(endLandmarkId);
				if (currentLandmark == null) {
					directions.clear();
					dirtySync = true;
				}
			}
		}

		while (true) {
			// State 1: idle — request directions if cooldown has expired
			if (directions.isEmpty()) {
				if (simulator.getCurrentMillis() > findDirectionsTime) {
					if (simulator.landmarks.isEmpty() || RANDOM.nextInt(GO_HOME_PROBABILITY_DENOMINATOR) == 0) {
						findDirections(simulator, null);
					} else {
						findDirections(simulator, new ObjectArrayList<>(simulator.landmarks).get(RANDOM.nextInt(simulator.landmarks.size())));
					}
				}
				break;
			}

			final PassengerDirection passengerDirection = directions.getFirst();
			updateCurrentDirectionCache(passengerDirection, simulator);

			// State 2: walking leg (no route) — wait until the end time, then consume
			if (currentRoute == null) {
				sidingId = 0;
				vehicleId = 0;
				if (simulator.getCurrentMillis() >= passengerDirection.getEndTime()) {
					directions.removeFirst();
					clearCurrentDirectionCache();
					dirtySync = true;
					if (directions.isEmpty()) {
						completeJourney(simulator);
						break;
					} else {
						continue;
					}
				}
				break;
			}

			// State 3: route exists but platforms are null — stale data, replan from home
			if (currentStartPlatform == null || currentEndPlatform == null) {
				replanCurrentJourney(simulator, home.getCenter());
				break;
			}

			// State 4: awaiting vehicle — replan if jammed or too late, else scan sidings
			if (vehicleId == 0) {
				final long prevSidingId = sidingId;
				final long prevVehicleId = vehicleId;

				if (simulator.isRouteJammed(currentRoute.getId()) || simulator.getCurrentMillis() >= passengerDirection.getStartTime() + REALTIME_REPLAN_THRESHOLD) {
					replanCurrentJourney(simulator, currentStartPlatform.getMidPosition());
					break;
				}

				final LongLongImmutablePair sidingAndVehicleIds = getSidingAndVehicleIds(currentRoute, currentStartPlatform);
				sidingId = sidingAndVehicleIds == null ? 0 : sidingAndVehicleIds.leftLong();
				vehicleId = sidingAndVehicleIds == null ? 0 : sidingAndVehicleIds.rightLong();

				if (sidingId != prevSidingId || vehicleId != prevVehicleId) {
					dirtySync = true;
				}

				break;
			}

			// State 5a: vehicle vanished — replan immediately
			final Vehicle vehicle = simulator.vehicleIdMap.get(vehicleId);
			if (vehicle == null) {
				sidingId = 0;
				vehicleId = 0;
				dirtySync = true;
				replanCurrentJourney(simulator, currentEndPlatform == null ? home.getCenter() : currentEndPlatform.getMidPosition());
				break;
			}

			// State 5b: vehicle reached target platform — consume leg
			if (!vehicle.isMoving() && vehicle.vehicleExtraData.getThisPlatformId() == currentEndPlatform.getId()) {
				final Position endPlatformPosition = currentEndPlatform.getMidPosition();
				directions.removeFirst();
				sidingId = 0;
				vehicleId = 0;
				clearCurrentDirectionCache();
				dirtySync = true;

				if (directions.isEmpty()) {
					completeJourney(simulator);
				} else {
					replanCurrentJourney(simulator, endPlatformPosition);
				}

				break;
			}

			// State 6: missed-stop guard — leg deadline passed without arrival
			if (simulator.getCurrentMillis() >= passengerDirection.getEndTime()) {
				directions.removeFirst();
				sidingId = 0;
				vehicleId = 0;
				clearCurrentDirectionCache();
				dirtySync = true;

				if (directions.isEmpty()) {
					completeJourney(simulator);
				} else {
					replanCurrentJourney(simulator, home.getCenter());
				}

				break;
			}

			break;
		}

		if (vehicleId != 0) {
			simulator.vehicleIdToPassengers.computeIfAbsent(vehicleId, key -> new ObjectArraySet<>()).add(this);
		}

		final boolean dirty = dirtySync;
		dirtySync = false;
		return dirty;
	}

	/**
	 * @return current boarded vehicle id, or {@code 0} if the passenger is not onboard.
	 */
	public long getVehicleId() {
		return vehicleId;
	}

	/**
	 * Resolve the first planned leg's platform/route references against the latest simulator maps.
	 */
	private void updateCurrentDirectionCache(PassengerDirection passengerDirection, Simulator simulator) {
		if (passengerDirection.getStartPlatformId() == 0) {
			currentStartPlatform = null;
		} else {
			currentStartPlatform = simulator.platformIdMap.get(passengerDirection.getStartPlatformId());
		}

		if (passengerDirection.getEndPlatformId() == 0) {
			currentEndPlatform = null;
		} else {
			currentEndPlatform = simulator.platformIdMap.get(passengerDirection.getEndPlatformId());
		}

		if (passengerDirection.getRouteId() == 0) {
			currentRoute = null;
		} else {
			currentRoute = simulator.routeIdMap.get(passengerDirection.getRouteId());
		}
	}

	/**
	 * Clear transient references for the currently active leg.
	 */
	private void clearCurrentDirectionCache() {
		currentStartPlatform = null;
		currentEndPlatform = null;
		currentRoute = null;
	}

	/**
	 * Mark the current trip complete and set the next planning cooldown.
	 */
	private void completeJourney(Simulator simulator) {
		clearCurrentDirectionCache();
		sidingId = 0;
		vehicleId = 0;
		currentLandmark = endLandmarkId != 0 ? simulator.landmarkIdMap.get(endLandmarkId) : null;

		if (endLandmarkId != 0 && landmarkVisitEndTime > simulator.getCurrentMillis()) {
			findDirectionsTime = landmarkVisitEndTime;
		} else {
			findDirectionsTime = simulator.getCurrentMillis() + COOLDOWN + RANDOM.nextInt(COOLDOWN);
		}

		dirtySync = true;
	}

	/**
	 * Replan the remaining journey from a transfer point while preserving destination metadata.
	 */
	private void replanCurrentJourney(Simulator simulator, Position startPosition) {
		final Landmark destinationLandmark = endLandmarkId == 0 ? null : simulator.landmarkIdMap.get(endLandmarkId);
		directions.clear();
		clearCurrentDirectionCache();
		sidingId = 0;
		vehicleId = 0;
		dirtySync = true;
		findDirections(simulator, destinationLandmark, startPosition, true);
	}

	/**
	 * Request a passenger-direction path from the CSA engine. If the destination is a landmark,
	 * the visit is reserved before the route is committed; if the landmark is full the route is
	 * rejected and retried after a short backoff.
	 *
	 * @param newLandmark destination landmark, or {@code null} for a home-bound trip
	 */
	private void findDirections(Simulator simulator, @Nullable Landmark newLandmark) {
		findDirections(simulator, newLandmark, null, false);
	}

	/**
	 * Request a new passenger plan. When {@code overrideStartPosition} is supplied (transfer
	 * replanning), the destination metadata is optionally preserved so only the route legs are
	 * refreshed.
	 */
	private void findDirections(Simulator simulator, @Nullable Landmark newLandmark, @Nullable Position overrideStartPosition, boolean preserveDestinationData) {
		if (home == null) {
			// This should never happen
			return;
		}

		final Position startPosition;
		final Position endPosition;

		if (overrideStartPosition != null) {
			startPosition = overrideStartPosition;
			endPosition = newLandmark == null ? home.getCenter() : newLandmark.getCenter();
		} else if (newLandmark == null && currentLandmark == null) {
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
				if (preserveDestinationData) {
					directions.clear();
					currentStartPlatform = null;
					currentEndPlatform = null;
					currentRoute = null;
					directions.addAll(passengerDirections);
				} else if (newLandmark != null) {
					// For landmark trips, try to reserve the visit BEFORE committing to the route
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
