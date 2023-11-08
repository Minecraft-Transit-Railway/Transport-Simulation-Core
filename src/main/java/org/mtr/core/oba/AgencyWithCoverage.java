package org.mtr.core.oba;

import org.mtr.core.generated.oba.AgencyWithCoverageSchema;
import org.mtr.core.tool.LatLon;

public final class AgencyWithCoverage extends AgencyWithCoverageSchema {

	public AgencyWithCoverage() {
		super("1", 0, 0, LatLon.MAX_LAT * 2, LatLon.MAX_LON * 2);
	}
}
