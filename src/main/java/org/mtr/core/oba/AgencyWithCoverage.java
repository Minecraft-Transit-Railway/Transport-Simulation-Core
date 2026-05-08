package org.mtr.core.oba;

import org.mtr.core.generated.oba.AgencyWithCoverageSchema;
import org.mtr.core.tool.LatLon;

/**
 * OneBusAway {@code AgencyWithCoverage} entity. Returned by the
 * {@code agencies-with-coverage} endpoint and advertises a worldwide bounding box for the
 * single hosted {@link Agency}.
 */
public final class AgencyWithCoverage extends AgencyWithCoverageSchema {

	/**
	 * Construct the singleton coverage record for this server's agency.
	 */
	public AgencyWithCoverage() {
		super("1", 0, 0, LatLon.MAX_LAT * 2, LatLon.MAX_LON * 2);
	}
}
