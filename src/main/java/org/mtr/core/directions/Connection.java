package org.mtr.core.directions;

import org.mtr.core.data.Route;

import javax.annotation.Nullable;

/**
 * Represents a connection that is dependent of start time.
 *
 * @param route           the corresponding route or {@code null} if walking
 * @param startPlatformId the start platform ID of the connection
 * @param endPlatformId   the end platform ID of the connection
 * @param startTime       the absolute starting time (in millis after epoch) of the connection
 * @param endTime         the absolute ending time (in millis after epoch) of the connection
 * @param walkingDistance the walking distance if walking or 0 if riding
 */
public record Connection(@Nullable Route route, long startPlatformId, long endPlatformId, long startTime, long endTime, long walkingDistance) {
}
