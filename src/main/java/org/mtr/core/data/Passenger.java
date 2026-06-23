package org.mtr.core.data;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectBooleanImmutablePair;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
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
 *   <li><b>Idle</b> &mdash; no directions queued. If {@code cooldownTime} has expired,
 *       ends the current landmark visit (if any), picks a destination (home or random
 *       landmark with 1:3 odds), and requests a CSA path.</li>
 *   <li><b>Walking</b> &mdash; the first leg has no route (walking transfer). When its end
 *       time passes, the leg is consumed and the next one begins.</li>
 *   <li><b>Awaiting vehicle</b> &mdash; {@code vehicleId == 0}. Replans if the route is
 *       jammed or the vehicle doesn't arrive within
 *       {@code REALTIME_REPLAN_THRESHOLD} of the estimated departure. Otherwise scans all
 *       sidings via {@link #waitForVehicle(Simulator)} for a vehicle at the platform on the
 *       planned route, then calls {@link #boardVehicle(int, int)} (hash-based car
 *       preference with 10% spillover to adjacent cars). If no vehicle found, waits with
 *       {@link #wait(Simulator, long, boolean)} before retrying.</li>
 *   <li><b>On vehicle</b> &mdash; {@code vehicleId != 0}. Sub-states:
 *       <ul>
 *         <li><b>Vanished (5a)</b> &mdash; vehicle not found in any siding →
 *         reset and replan.</li>
 *         <li><b>Arrived (5b)</b> &mdash; doors open at target platform (or vehicle
 *         is at depot) → {@link #alightVehicle()}, consume leg. Stays aboard if the
 *         next leg shares the same route.</li>
 *         <li><b>Missed-stop (6)</b> &mdash; leg deadline passed without arrival →
 *         alight and replan from the current end platform (or home fallback).</li>
 *       </ul></li>
 *   <li><b>Complete</b> &mdash; all legs consumed. If at a landmark, sets
 *       {@code cooldownTime} to {@code landmarkVisitEndTime}; if at home, waits the
 *       generic cooldown before going idle for the next destination.</li>
 * </ol>
 *
 * <p>All CSA direction requests are asynchronous via {@link DirectionsFinder}. While a
 * request is outstanding {@code cooldownTime = Long.MAX_VALUE} prevents duplicate
 * submissions. On callback the cooldown is reset to 0 so the next tick can proceed.
 * If the CSA budget is exhausted the passenger retries after a short backoff.</p>
 *
 * <p>Per-passenger vehicle references (which car) are reconciled on load via
 * {@link #writeVehicleCache(Simulator)} and cleaned up during population convergence
 * via {@link #clearVehicleReferences(Simulator)}.</p>
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
	@Nullable
	private Siding currentSiding;
	@Nullable
	private Vehicle currentVehicle;

	/**
	 * A general cooldown time when the passenger will just wait in place.
	 */
	private long cooldownTime;
	@Getter
	private boolean dirtySync = true;

	private static final Random RANDOM = new Random();
	/**
	 * Base cooldown applied when a passenger finishes a journey (ms).
	 */
	private static final int GENERIC_COOLDOWN = 2000;
	private static final int RETRY_COOLDOWN = 1000;
	private static final int BOARDING_COOLDOWN = 2000;
	/**
	 * 1-in-{@value} chance of going home vs. to a landmark when idle.
	 */
	private static final int GO_HOME_PROBABILITY_DENOMINATOR = 3;
	/**
	 * Random chance divisor for boarding an adjacent car if the current car is full (one in ten chance).
	 */
	private static final int BOARD_ADJACENT_CAR_CHANCE_DIVISOR = 10;
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
	 * Advance this passenger by one tick through the state machine.
	 *
	 * <p>Each tick starts with a cooldown gate: if {@code simulator.getCurrentMillis() < cooldownTime}
	 * the method returns immediately with the current dirty flag. Otherwise control enters a
	 * while-loop that processes exactly one state transition per iteration:</p>
	 *
	 * <ol>
	 *   <li><b>Idle</b> &mdash; directions empty → end current landmark visit (if any),
	 *       pick destination (home or random landmark, 1:3 odds), request CSA path</li>
	 *   <li><b>Walking leg</b> &mdash; {@code currentRoute == null} → wait for leg end time,
	 *       then consume leg (continue to next leg or complete)</li>
	 *   <li><b>Null platform</b> &mdash; route exists but platform is null → replan</li>
	 *   <li><b>Awaiting vehicle</b> &mdash; {@code vehicleId == 0} → check jammed/expired;
	 *       call {@link #waitForVehicle(Simulator)}, then {@link #boardVehicle(int, int)}
	 *       if found (with car-spillover logic), or {@link #wait(Simulator, long, boolean)}
	 *       if not; replan if all cars full</li>
	 *   <li><b>On vehicle</b> &mdash; {@code vehicleId != 0}. Sub-states:
	 *       <ul>
	 *         <li><b>Vanished (5a)</b> &mdash; vehicle not found → reset, replan</li>
	 *         <li><b>Arrived (5b)</b> &mdash; doors open at target or depot →
	 *         {@link #alightVehicle()}, consume leg; stay aboard if next leg shares route</li>
	 *         <li><b>Missed-stop (6)</b> &mdash; leg deadline passed → alight, replan</li>
	 *       </ul></li>
	 *   <li><b>Complete</b> &mdash; all legs consumed → {@link #completeJourney(Simulator)},
	 *       sets cooldown for visit or home wait</li>
	 * </ol>
	 *
	 * <p>The per-car occupancy set is managed via {@link #boardVehicle} and
	 * {@link #alightVehicle}); the persisted {@code vehicleCarNumber} survives restarts
	 * and is reconciled in {@link #writeVehicleCache(Simulator)}.</p>
	 *
	 * <p>Every iteration of the while(true) body hits exactly one {@code break} or
	 * {@code continue} — no infinite-loop path is possible.</p>
	 *
	 * @param home      owning home (used for position fallback)
	 * @param simulator current simulation engine
	 * @return {@code true} if the passenger's state became dirty during this tick
	 */
	public boolean tick(Home home, Simulator simulator) {
		homeId = home.getId();
		this.home = home;
		if (currentLandmark == null && endLandmarkId != 0) {
			currentLandmark = simulator.landmarkIdMap.get(endLandmarkId);
			if (currentLandmark == null) {
				directions.clear();
				dirtySync = true;
			}
		}

		while (true) {
			// Waiting for any reason
			if (simulator.getCurrentMillis() < cooldownTime) {
				break;
			}

			// State 1: idle — request directions if cooldown has expired
			if (directions.isEmpty()) {
				if (currentLandmark != null && landmarkVisitStartTime > 0) {
					currentLandmark.endVisit(landmarkVisitStartTime, landmarkVisitEndTime);
					landmarkVisitStartTime = 0;
					landmarkVisitEndTime = 0;
				}

				if (simulator.landmarks.isEmpty() || RANDOM.nextInt(GO_HOME_PROBABILITY_DENOMINATOR) == 0) {
					findDirections(simulator, null);
				} else {
					findDirections(simulator, new ObjectArrayList<>(simulator.landmarks).get(RANDOM.nextInt(simulator.landmarks.size())));
				}

				break;
			}

			final PassengerDirection passengerDirection = directions.getFirst();
			updateCurrentDirectionCache(passengerDirection, simulator);

			// State 2: walking leg (no route) — wait until the end time, then consume
			if (currentRoute == null) {
				resetCurrentSidingAndVehicleCache(simulator);

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

			// State 4: awaiting vehicle — replan if jammed or too late, else find a vehicle
			if (vehicleId == 0) {
				final long prevSidingId = sidingId;
				final long prevVehicleId = vehicleId;

				if (simulator.isRouteJammed(currentRoute.getId()) || simulator.getCurrentMillis() >= passengerDirection.getStartTime() + REALTIME_REPLAN_THRESHOLD) {
					replanCurrentJourney(simulator, currentStartPlatform.getMidPosition());
					break;
				}

				waitForVehicle(simulator);

				if (currentVehicle == null) {
					wait(simulator, BOARDING_COOLDOWN, true);
				} else {
					boardVehicle(getBoardingCar(), BOARD_ADJACENT_CAR_CHANCE_DIVISOR);
					if (vehicleCarNumber < 0) {
						resetCurrentSidingAndVehicleCache(simulator);
						dirtySync = true;
						replanCurrentJourney(simulator, currentStartPlatform.getMidPosition());
					} else {
						vehicleId = currentVehicle.getId();
					}
				}

				if (sidingId != prevSidingId || vehicleId != prevVehicleId) {
					dirtySync = true;
				}

				break;
			}

			// State 5a: vehicle vanished
			updateCurrentSidingAndVehicleCache(simulator);
			if (currentVehicle == null) {
				resetCurrentSidingAndVehicleCache(simulator);
				dirtySync = true;
				replanCurrentJourney(simulator, currentEndPlatform == null ? home.getCenter() : currentEndPlatform.getMidPosition());
				break;
			}

			// State 5b: vehicle reached target platform or missed stop or vehicle is at depot — alight and consume leg
			if (currentVehicle.closeToDepot() || currentVehicle.vehicleExtraData.getDoorMultiplier() > 0 && (currentVehicle.vehicleExtraData.getThisPlatformId() == currentEndPlatform.getId() || currentVehicle.vehicleExtraData.getPreviousPlatformId() == currentEndPlatform.getId())) {
				directions.removeFirst();

				// Stay on the vehicle if the next directions is also the same
				if (!directions.isEmpty() && directions.getFirst().getRouteId() == currentRoute.getId()) {
					break;
				}

				alightVehicle();
				final Position endPlatformPosition = currentEndPlatform.getMidPosition();
				resetCurrentSidingAndVehicleCache(simulator);
				clearCurrentDirectionCache();
				dirtySync = true;

				if (directions.isEmpty()) {
					completeJourney(simulator);
				} else {
					replanCurrentJourney(simulator, endPlatformPosition);
				}

				break;
			}

			break;
		}

		final boolean dirty = dirtySync;
		dirtySync = false;
		return dirty;
	}

	public long getHomeId() {
		return homeId;
	}

	public long getStartLandmarkId() {
		return startLandmarkId;
	}

	public long getEndLandmarkId() {
		return endLandmarkId;
	}

	/**
	 * @return current boarded vehicle id, or {@code 0} if the passenger is not onboard.
	 */
	public long getVehicleId() {
		return vehicleId;
	}

	public long getVehicleCarNumber() {
		return vehicleCarNumber;
	}

	public ObjectArrayList<PassengerDirection> getDirections() {
		return directions;
	}

	/**
	 * Reconcile this passenger into its vehicle car's passenger set after deserialisation.
	 * Called once during {@link Simulator} construction for every loaded passenger whose
	 * {@code vehicleCarNumber >= 0}.
	 *
	 * <p>If the siding, vehicle or car no longer exist (e.g. the network was edited while the
	 * passenger was away), the call is silently skipped — the next tick will detect the vanished
	 * vehicle and replan.</p>
	 */
	public void writeVehicleCache(Simulator simulator) {
		if (vehicleCarNumber >= 0) {
			final Siding siding = sidingId == 0 ? null : simulator.sidingIdMap.get(sidingId);
			final Vehicle vehicle = siding == null ? null : siding.getVehicleById(vehicleId);
			final ObjectArraySet<Passenger> passengersForCar = vehicle == null ? null : Utilities.getElement(vehicle.vehicleExtraData.passengers, (int) vehicleCarNumber);
			if (passengersForCar != null) {
				passengersForCar.add(this);
			}
		}
	}

	/**
	 * Remove this passenger from any vehicle car set it may be referenced in and end any
	 * active landmark visit. Called when the passenger is removed from its home during
	 * population convergence to prevent stale references in vehicle occupancy data and
	 * landmark occupancy arrays.
	 *
	 * @param simulator the simulation engine containing sidings to scan
	 */
	void clearVehicleReferences(Simulator simulator) {
		updateCurrentSidingAndVehicleCache(simulator);
		if (currentVehicle != null) {
			final ObjectArraySet<Passenger> passengersForCar = Utilities.getElement(currentVehicle.vehicleExtraData.passengers, (int) vehicleCarNumber);
			if (passengersForCar != null) {
				passengersForCar.remove(this);
			}
		}

		if (landmarkVisitStartTime > 0) {
			final Landmark landmark = currentLandmark == null ? simulator.landmarkIdMap.get(endLandmarkId) : currentLandmark;
			if (landmark != null) {
				landmark.endVisit(landmarkVisitStartTime, landmarkVisitEndTime);
			}
			landmarkVisitStartTime = 0;
			landmarkVisitEndTime = 0;
		}
	}

	boolean closeTo(Position position, double padding) {
		if (home == null) {
			return false;
		}

		if (home.inArea(position, padding) || currentLandmark != null && currentLandmark.inArea(position, padding) || currentStartPlatform != null && currentStartPlatform.closeTo(position, padding) || currentEndPlatform != null && currentEndPlatform.closeTo(position, padding)) {
			return true;
		}

		final Position position1 = currentStartPlatform == null ? home.getCenter() : currentStartPlatform.getMidPosition();
		final Position position2 = currentEndPlatform == null ? home.getCenter() : currentEndPlatform.getMidPosition();
		return Utilities.isBetween(position, position1, position2, padding);
	}

	/**
	 * Resolve {@link #currentSiding} and {@link #currentVehicle} from the persisted
	 * {@code sidingId} / {@code vehicleId}. When the fast ID-based lookup fails and
	 * {@code vehicleId != 0}, a fallback scan across all sidings is performed.
	 *
	 * <p>This method is <strong>not</strong> used in state 4 (boarding) because that state
	 * needs a platform/route-based lookup rather than an ID-based one.</p>
	 *
	 * <p>When called with {@code vehicleId == 0} (e.g. from {@link #resetCurrentSidingAndVehicleCache}),
	 * everything is reset to 0/null.</p>
	 */
	private void updateCurrentSidingAndVehicleCache(Simulator simulator) {
		if (currentSiding != null && currentSiding.getTransportMode().continuousMovement) {
			sidingId = currentSiding.getId();
			vehicleId = 0;
			currentVehicle = null;
			return;
		}

		if (vehicleId == 0) {
			alightVehicle();
			sidingId = 0;
			currentSiding = null;
			currentVehicle = null;
			vehicleCarNumber = -1;
			return;
		}

		if (currentSiding != null && currentVehicle != null && currentSiding.getId() == sidingId && currentVehicle.getId() == vehicleId) {
			return;
		}

		currentSiding = sidingId == 0 ? null : simulator.sidingIdMap.get(sidingId);
		currentVehicle = currentSiding == null ? null : currentSiding.getVehicleById(vehicleId);

		if (currentSiding != null && currentVehicle != null) {
			return;
		}

		if (vehicleId != 0 && currentVehicle == null) {
			for (final Siding siding : simulator.sidings) {
				currentVehicle = siding.getVehicleById(vehicleId);
				if (currentVehicle != null) {
					sidingId = siding.getId();
					currentSiding = siding;
					return;
				}
			}
		}

		sidingId = 0;
		currentSiding = null;
		vehicleId = 0;
		currentVehicle = null;
		vehicleCarNumber = -1;
	}

	/**
	 * Clear the persisted vehicle/siding IDs and transient cache fields.
	 * Delegates to {@link #updateCurrentSidingAndVehicleCache} which resets the
	 * transient fields when {@code vehicleId == 0}.
	 */
	private void resetCurrentSidingAndVehicleCache(Simulator simulator) {
		vehicleId = 0;
		vehicleCarNumber = -1;
		updateCurrentSidingAndVehicleCache(simulator);
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
		vehicleCarNumber = -1;
	}

	/**
	 * Mark the current trip complete and set the next planning cooldown.
	 */
	private void completeJourney(Simulator simulator) {
		clearCurrentDirectionCache();
		resetCurrentSidingAndVehicleCache(simulator);
		currentLandmark = endLandmarkId != 0 ? simulator.landmarkIdMap.get(endLandmarkId) : null;

		if (endLandmarkId != 0 && landmarkVisitEndTime > simulator.getCurrentMillis()) {
			cooldownTime = landmarkVisitEndTime;
		} else {
			wait(simulator, GENERIC_COOLDOWN, true);
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
		resetCurrentSidingAndVehicleCache(simulator);
		dirtySync = true;
		findDirections(simulator, destinationLandmark, startPosition, true);
	}

	private void wait(Simulator simulator, long millis, boolean randomize) {
		cooldownTime = simulator.getCurrentMillis() + millis + (randomize ? RANDOM.nextLong(millis) : 0);
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
			wait(simulator, GENERIC_COOLDOWN, true);
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
			wait(simulator, GENERIC_COOLDOWN, true);
			return;
		} else {
			// At a landmark, trying to go to a new landmark
			startPosition = currentLandmark.getCenter();
			endPosition = newLandmark.getCenter();
		}

		if (!simulator.tryConsumePassengerDirectionsRequestBudget()) {
			wait(simulator, RETRY_COOLDOWN, true);
			return;
		}

		cooldownTime = Long.MAX_VALUE;
		simulator.directionsFinder.addRequest(new DirectionsRequest(startPosition, endPosition, simulator.getCurrentMillis(), null, passengerDirections -> {
			if (!passengerDirections.isEmpty()) {
				cooldownTime = 0;
				if (preserveDestinationData) {
					// Replanning — refresh route legs without re-reserving the visit
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
						wait(simulator, RETRY_COOLDOWN, true);
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
				wait(simulator, GENERIC_COOLDOWN, true);
			}
		}));
	}

	private void waitForVehicle(Simulator simulator) {
		if (currentRoute != null && currentStartPlatform != null) {
			for (final Siding siding : simulator.sidings) {
				final ObjectBooleanImmutablePair<@Nullable Vehicle> vehicleDetails = siding.getVehicleDetailsAtPlatform(currentRoute.getId(), currentStartPlatform.getId());
				final Vehicle vehicle = vehicleDetails.left();
				if (vehicleDetails.rightBoolean() && vehicle != null) {
					sidingId = siding.getId();
					currentSiding = siding;
					vehicleId = vehicle.getId();
					currentVehicle = vehicle;
					vehicleCarNumber = -1;
					return;
				}
			}

			resetCurrentSidingAndVehicleCache(simulator);
		}
	}

	/**
	 * Try to board a passenger onto this vehicle. If that car is full, there is a 10% chance
	 * they will try the adjacent car; otherwise they give up so the passenger can replan.
	 */
	private void boardVehicle(int car, int retryChance) {
		vehicleCarNumber = -1;
		if (currentVehicle == null) {
			return;
		}

		final int totalCarCount = currentVehicle.vehicleExtraData.immutableVehicleCars.size();
		if (!Utilities.isBetween(car, 0, totalCarCount - 1)) {
			return;
		}

		// Try preferred car first
		final ObjectArraySet<Passenger> passengersForCar = currentVehicle.vehicleExtraData.passengers.get(car);
		if (passengersForCar.size() < currentVehicle.vehicleExtraData.immutableVehicleCars.get(car).getCapacity()) {
			passengersForCar.add(this);
			vehicleCarNumber = car;
			return;
		}

		// 10% chance to try an adjacent car when the preferred car is full
		if (retryChance > 0 && RANDOM.nextInt(retryChance) == 0) {
			boardVehicle(car + (RANDOM.nextBoolean() ? 1 : -1), 0);
		}
	}

	/**
	 * Remove a passenger from all cars of a vehicle. The passenger shouold only be on one car, but it removes from all just in case.
	 */
	private void alightVehicle() {
		if (currentVehicle != null) {
			currentVehicle.vehicleExtraData.passengers.forEach(passengersForCar -> passengersForCar.remove(this));
		}
	}

	private int getBoardingCar() {
		if (currentVehicle == null) {
			return 0;
		} else {
			final long distance = Math.floorMod(getId(), Math.round(currentVehicle.vehicleExtraData.getTotalVehicleLength()));
			double currentDistance = 0;
			final ObjectImmutableList<VehicleCar> immutableVehicleCars = currentVehicle.vehicleExtraData.immutableVehicleCars;
			for (int i = 0; i < immutableVehicleCars.size(); i++) {
				if (currentDistance >= distance) {
					return i;
				}
				currentDistance += immutableVehicleCars.get(i).getLength();
			}
			return immutableVehicleCars.size() - 1;
		}
	}
}
