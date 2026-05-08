/**
 * Path-finding subsystem powering the {@code /mtr/api/map/directions} endpoint. Builds a public-transit Connection-Scan-Algorithm graph from the live routes and serves directions queries asynchronously through the simulator's tick loop.
 */
@NullMarked
package org.mtr.core.directions;

import org.jspecify.annotations.NullMarked;
