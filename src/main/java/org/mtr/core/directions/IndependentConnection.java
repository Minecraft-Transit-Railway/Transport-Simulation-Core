package org.mtr.core.directions;

import org.mtr.core.data.Route;

import javax.annotation.Nullable;

/**
 * Represents a connection that is independent of start time, such as walking and cable car connections.
 *
 * @param route           the corresponding route or {@code null} if walking
 * @param startPlatformId the start platform ID of the connection
 * @param endPlatformId   the end platform ID of the connection
 * @param duration        the travel time
 * @param walkingDistance the walking distance if walking or 0 if riding
 */
public record IndependentConnection(@Nullable Route route, long startPlatformId, long endPlatformId, long duration, long walkingDistance) {
}
